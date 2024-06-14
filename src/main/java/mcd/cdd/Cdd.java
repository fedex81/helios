package mcd.cdd;

import mcd.bus.McdSubInterruptHandler;
import mcd.cdc.Cdc;
import mcd.cdd.CdModel.ExtendedTrackData;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.BlipPcmProvider;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.sound.msumd.CueFileParser.MsfHolder;
import omegadrive.util.*;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDD;
import static mcd.cdd.Cdd.CddCommand.Request;
import static mcd.cdd.Cdd.CddCommand.SeekPause;
import static mcd.cdd.Cdd.CddStatus.*;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.Util.th;

/**
 * Cdd
 * Adapted from the Ares emulator
 * <p>
 * NEC uPD75006 (G-631)
 * 4-bit MCU HLE
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public interface Cdd extends BufferUtil.StepDevice {

    enum CddStatus {
        Stopped,  //motor disabled
        Playing,  //data or audio playback in progress
        Seeking,  //move to specified time
        Scanning,  //skipping to a specified track
        Paused,  //paused at a specific time
        DoorOpened,  //drive tray is open
        ChecksumError,  //invalid communication checksum
        CommandError,  //missing command
        FunctionError,  //error during command execution
        ReadingTOC,  //reading table of contents
        Tracking,  //currently skipping tracks
        NoDisc,  //no disc in tray or cannot focus
        LeadOut,  //paused in the lead-out area of the disc
        LeadIn,  //paused in the lead-in area of the disc
        TrayMoving,  //drive tray is moving in response to open/close commands
        Test,  //in test mode
    }

    enum CddCommand {
        Idle,  //no operation
        Stop,  //stop motor
        Request,  //change report type
        SeekPlay,  //read ROM data
        SeekPause,  //seek to a specified location
        Pause,  //pause the drive
        Play,  //start playing from the current location
        Forward,  //forward skip and playback
        Reverse,  //reverse skip and playback
        TrackSkip,  //start track skipping
        TrackCue,  //start track cueing
        DoorClose,  //close the door
        DoorOpen,  //open the door
    }

    enum CddRequest {
        AbsoluteTime,
        RelativeTime,
        TrackInformation,
        DiscCompletionTime,
        DiscTracks,  //start/end track numbers
        TrackStartTime,  //start time of specific track
        ErrorInformation,
        SubcodeError,
        NotReady,  //not ready to comply with the current command
    }

    void tryInsert(Path cueSheet);
    void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size);

    int read(MegaCdDict.RegSpecMcd regSpec, int address, Size size);

    default double position(int sector) {
        //convert sector# to normalized sector position on the CD-ROM surface for seek latency calculation

        double sectors = 7500.0 + 330000.0 + 6750.0;
        double radius = 0.058 - 0.024;
        double innerRadius = 0.024 * 0.024;  //in mm
        double outerRadius = 0.058 * 0.058;  //in mm

        sector += 150; //session.leadIn.lba;  //convert to natural //TODO check
        return Math.sqrt(sector / sectors * (outerRadius - innerRadius) + innerRadius) / radius;
    }

    //should be called at 44.1 khz
    void stepCdda();


    void updateVideoMode(VideoMode videoMode);

    void newFrame();

    default void process() {
        throw new RuntimeException("not implemented");
    }

    default boolean valid() {
        throw new RuntimeException("not implemented");
    }

    default void checksum() {
        throw new RuntimeException("not implemented");
    }

    default void insert() {
        throw new RuntimeException("not implemented");
    }

    default void eject() {
        throw new RuntimeException("not implemented");
    }

    default void power(boolean reset) {
        throw new RuntimeException("not implemented");
    }

    //status after seeking (Playing or Paused)
    //sector = current frame#
    //sample = current audio sample# within current frame
    //track = current track#
    class CddIo {
        public CddStatus status;
        public int seeking, latency, sector, sample, track, tocRead;

        static CddIo create() {
            CddIo c = new CddIo();
            c.status = NoDisc;
            return c;
        }
    }

    class CddContext {
        public CddIo io;
        public int hostClockEnable, statusPending;
        public int[] status = new int[10];
        public int[] command = new int[10];

        static CddContext create(CddIo cddIo) {
            CddContext c = new CddContext();
            c.io = cddIo;
            return c;
        }
    }

    CddStatus[] statusVals = CddStatus.values();

    static Cdd createInstance(MegaCdMemoryContext memoryContext, McdSubInterruptHandler ih, Cdc cdc) {
        return new CddImpl(memoryContext, ih, cdc);
    }
}

class CddImpl implements Cdd {

    private final static Logger LOG = LogHelper.getLogger(CddImpl.class.getSimpleName());

    public final CddContext cddContext = CddContext.create(CddIo.create());

    private final MegaCdMemoryContext memoryContext;
    private final McdSubInterruptHandler interruptHandler;
    private final Cdc cdc;
    private final BlipPcmProvider playSupport;
    private ExtendedCueSheet extCueSheet;

    private final MsfHolder msfHolder = new MsfHolder();
    private boolean hasMedia;


    public CddImpl(MegaCdMemoryContext mc, McdSubInterruptHandler ih, Cdc c) {
        memoryContext = mc;
        interruptHandler = ih;
        cdc = c;
        playSupport = new BlipPcmProvider("CDDA", RegionDetector.Region.USA, 44100);
        checksum();
    }

    @Override
    public void tryInsert(Path cueSheet) {
        extCueSheet = new ExtendedCueSheet(cueSheet);
        hasMedia = extCueSheet.cueSheet != null;
        if (!hasMedia) {
            setIoStatus(NoDisc);
            return;
        }

        setIoStatus(ReadingTOC);
        setSector(0); //TODO session.leadIn.lba, should be 150 sectors (2 seconds)
        cddContext.io.track = cddContext.io.sample = cddContext.io.tocRead = 0;
        cdc.setMedia(extCueSheet);
        LOG.info("Using disc: {}", extCueSheet);
    }

    @Override
    public void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size) {
        LOG.info("CDD,regW,{},{},{},{}", th(address), th(value), size, regSpec);
        switch (regSpec) {
            case MCD_CDD_CONTROL -> {
                //TODO check word writes, even byte writes
                assert (value & 3) == 0; //DRS and DTS can only be set to 0
                value &= 7;
                assert memoryContext.getRegBuffer(CpuDeviceAccess.SUB_M68K, regSpec) == memoryContext.commonGateRegsBuf;
                writeBufferRaw(memoryContext.commonGateRegsBuf, address & MDC_SUB_GATE_REGS_MASK, value, size);
                //TODO this should be only when HOCK 0->1 but bios requires it
                if ((true || cddContext.hostClockEnable == 0) && (value & 4) > 0) { //HOCK set
//                if ((cddContext.hostClockEnable == 0) && (value & 4) > 0) { //HOCK set
                    interruptHandler.raiseInterrupt(INT_CDD);
                }
                cddContext.hostClockEnable = (value & 4);
                assert (value & 0xFEFB) == 0 : th(value); //DRS,DTS, invalid bits are 0
            }
            case MCD_CDD_COMM5, MCD_CDD_COMM6, MCD_CDD_COMM7, MCD_CDD_COMM8, MCD_CDD_COMM9 -> {
                switch (size) {
                    case BYTE -> writeCommByte(regSpec, address, value);
                    case WORD -> {
                        writeCommByte(regSpec, address, value >>> 8);
                        writeCommByte(regSpec, address + 1, value);
                    }
                    case LONG -> {
                        writeCommByte(regSpec, address, value >>> 24);
                        writeCommByte(regSpec, address + 1, value >>> 16);
                        writeCommByte(regSpec, address + 2, value >>> 8);
                        writeCommByte(regSpec, address + 3, value);
                    }
                }
            }
            case MCD_CD_FADER -> {
                assert size == Size.WORD; //ignore write
                //bit15 should be 0 when reading, end of fade data transfer
            }
            default -> {
                LOG.error("S CDD Write {}: {} {} {}", regSpec, th(address), th(value), size);
                assert false;
            }
        }
    }

    @Override
    public int read(MegaCdDict.RegSpecMcd regSpec, int address, Size size) {
        assert memoryContext.getRegBuffer(CpuDeviceAccess.SUB_M68K, regSpec) == memoryContext.commonGateRegsBuf;
        int res = readBuffer(memoryContext.commonGateRegsBuf, address & MDC_SUB_GATE_REGS_MASK, size);
        LOG.info("CDD,regR,{},{},{},{}", th(address), th(res), size, regSpec);
        return res;
    }


    private void writeCommByte(MegaCdDict.RegSpecMcd regSpec, int addr, int value) {
        int index = (addr & MDC_SUB_GATE_REGS_MASK) - MCD_CDD_COMM5.addr;
        updateCommand(index, value & 0xF);
        switch (regSpec) {
            case MCD_CDD_COMM9 -> {
                //Transmission Command 9
                if ((addr & 1) == 1) { //unconfirmed
                    process();
                }
            }
        }
    }

    @Override
    public void checksum() {
        int checksum = 0;
        for (int i = 0; i < cddContext.command.length - 1; i++) {
            checksum += cddContext.status[i];
        }
        checksum = ~checksum;
        updateStatus(9, checksum & 0xF);
        //TODO check 0x40,it can be 1 -> 0x103 != 0x3
        assert (readBuffer(memoryContext.commonGateRegsBuf, MCD_CDD_COMM4.addr, Size.WORD) & 0xFF) == (checksum & 0xF);
    }

    @Override
    public boolean valid() {
        int checksum = 0;
        for (int i = 0; i < cddContext.command.length - 1; i++) {
            checksum += cddContext.command[i];
        }
        checksum = ~checksum;
        return (checksum & 0xF) == cddContext.command[9];
    }

    //TODO mcd-ver requires 10, bios seems to like 0
    static int limit = 10;

    static {
        if (limit > 0) {
            LOG.error("XXXX mcd-ver interruptDelay mode!!!");
        }
    }
    AtomicInteger interruptDelay = new AtomicInteger(limit);

    /**
     * this should be called at 75hz
     */
    public void step(int cycles) {
        if (cddContext.hostClockEnable == 0) {
            return;
        }
        if (cddContext.statusPending > 0) {
            if (interruptDelay.decrementAndGet() <= 0) {
                interruptHandler.raiseInterrupt(INT_CDD);
                cddContext.statusPending = 0;
                interruptDelay.set(limit);
            }
        }
        switch (cddContext.io.status) {
            case NoDisc, Paused, LeadOut -> {
                //do nothing
            }
            case Stopped -> {
                if (!hasMedia) setIoStatus(NoDisc);
                else if (cddContext.io.tocRead == 0) setIoStatus(ReadingTOC);
                cddContext.io.sample = cddContext.io.track = 0;
                setSector(0);
            }
            case ReadingTOC -> {
                setSector(cddContext.io.sector + 1);
//                if(!session.inLeadIn(io.sector)) { //TODO
                if (cddContext.io.sector > 150) {
                    setIoStatus(Paused);
                    updateTrackIfLegal();
                    cddContext.io.tocRead = 1;
                    //TODO hack, mcd-ver is waiting for an interrupt CDD4
                    cddContext.statusPending = 1;
                    //TODO hack
                }
            }
            case Seeking -> {
                if (cddContext.io.latency > 0 && --cddContext.io.latency > 0) break;
                setIoStatus(statusVals[cddContext.io.seeking]);
                updateTrackIfLegal();
            }
            case Playing -> {
//                readSubcode();
                if (ExtendedCueSheet.isAudioTrack(extCueSheet, cddContext.io.track)) break;
                cdc.decode(cddContext.io.sector);
                advance();
            }
            default -> {
                LOG.error("CDD status: {}", cddContext.io.status);
                assert false;
            }
        }
    }

    //should be called at 44.1 khz
    @Override
    public void stepCdda() {
        int left = 0, right = 0;
        if (cddContext.io.status == Playing) {
            if (ExtendedCueSheet.isAudioTrack(extCueSheet, cddContext.io.track)) {
                ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track);
                //CDDA: 2352 audio data (+ 96 subcode, not dumped)
                int sectorSize = etd.trackDataType.size.s_size;
                int trackRelSector = cddContext.io.sector - etd.absoluteSectorStart;
                assert trackRelSector >= 0;
                try {
                    etd.file.seek((sectorSize * trackRelSector) + cddContext.io.sample);
                    left = FileUtil.readShortLE(etd.file);
                    right = FileUtil.readShortLE(etd.file);
                    cddContext.io.sample += 4;
                    if (cddContext.io.sample >= sectorSize) {
                        advance();
                    }
                } catch (Exception e) {
                    LOG.error("Unable to seek to sector: {}", cddContext.io.sector, e);
                }
            }
        }
        playSupport.playSample(left, right);
    }

    private void advance() {
        int track = inTrack(cddContext.io.sector + 1);
        if (track > 0) {
            setTrack(track);
            setSector(cddContext.io.sector + 1);
            cddContext.io.sample = 0;
            return;
        }
        setIoStatus(LeadOut);
        setTrack(0xAA);
    }

    @Override
    public void process() {
        CddCommand cddCommand = CddCommand.values()[cddContext.command[0]];
        LOG.info("CDD {}({}): {}({})", cddCommand, cddCommand.ordinal(), cddCommand == Request ?
                CddRequest.values()[cddContext.command[3]] : cddContext.command[3], cddContext.command[3]);
        if (!valid()) {
            //unverified
            LOG.error("CDD checksum error");
            setIoStatus(CddStatus.ChecksumError);
            processDone();
            return;
        }
        switch (cddCommand) {
            case Idle -> {
                //fixes Lunar: The Silver Star
                if (cddContext.io.latency == 0 && cddContext.status[1] == 0xf) {
                    updateStatus(1, 0x2);
                    updateStatus(2, cddContext.io.track / 10);
                    updateStatus(3, cddContext.io.track % 10);
                }
            }
            case Stop -> {
                setIoStatus(hasMedia ? Stopped : NoDisc);
                for (int i = 1; i < 9; i++) {
                    updateStatus(i, 0);
                }
            }
            case Request -> handleRequestCommand();
            case SeekPause, SeekPlay -> {
                int minute = cddContext.command[2] * 10 + cddContext.command[3];
                int second = cddContext.command[4] * 10 + cddContext.command[5];
                int frame = cddContext.command[6] * 10 + cddContext.command[7];
                int lba = minute * 60 * 75 + second * 75 + frame - 3;

//                cddCounter    = 0; //TODO
                setIoStatus(CddStatus.Seeking);
                cddContext.io.seeking = cddCommand == SeekPause ? CddStatus.Paused.ordinal() : Playing.ordinal();
                cddContext.io.latency = (int) (11 + 112.5 * Math.abs(position(cddContext.io.sector) - position(lba)));
                setSector(lba);
                cddContext.io.sample = 0;

                updateStatuses(0xf, 0, 0, 0, 0, 0, 0, 0);
                CueFileParser.toMSF(lba, msfHolder);
                LOG.info(cddCommand + " to sector: {}({})", msfHolder, th(lba));
            }
            case Play -> setIoStatus(Playing);
            case Pause -> setIoStatus(Paused);
            default -> {
                LOG.error("Unsupported Cdd command: {}({}), parameter: {}", cddCommand, cddCommand.ordinal(),
                        cddContext.command[3]);
                assert false;
            }
        }
        processDone();
    }

    //"sonic_cd_audio.cue"
    //14471856 (bin1) + 19618032 (bin2) = 34089888 bytes
    //34089888/2352 = 14494 sectors
    // + 2 seconds (150 sectors, for lead-in) = 14496 sectors
    private void handleRequestCommand() {
        CddRequest request = CddRequest.values()[cddContext.command[3]];
        boolean isAudio = ExtendedCueSheet.isAudioTrack(extCueSheet, cddContext.io.track);
        switch (request) {
            case AbsoluteTime -> {
                CueFileParser.toMSF(cddContext.io.sector, msfHolder);
                int status8 = (isAudio ? 0 : 1) << 2;
                updateStatusesMsf(cddContext.command[3], status8, msfHolder);
            }
            case RelativeTime -> {
                int relSector = cddContext.io.sector -
                        ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track).absoluteSectorStart;
                assert relSector > 0; //TODO can be < 0?
                CueFileParser.toMSF(relSector, msfHolder);
                int status8 = (isAudio ? 0 : 1) << 2;
                updateStatusesMsf(cddContext.command[3], status8, msfHolder);
            }
            case TrackInformation -> {
                updateStatuses(cddContext.command[3], cddContext.io.track / 10, cddContext.io.track % 10,
                        0, 0, 0, 0, 0);
//                if(session.inLeadIn (io.sector)) { status[2] = 0x0; status[3] = 0x0; }
//                if(session.inLeadOut(io.sector)) { status[2] = 0xa; status[3] = 0xa; }
            }
            case DiscCompletionTime -> {
                int lba = extCueSheet.sectorEnd;
                CueFileParser.toMSF(lba, msfHolder);
                updateStatusesMsf(cddContext.command[3], 0, msfHolder);
            }
            case DiscTracks -> {
                int firstTrack = 1;
                int lastTrack = extCueSheet.numTracks;
                updateStatuses(cddContext.command[3], firstTrack / 10, firstTrack % 10,
                        lastTrack / 10, lastTrack % 10, 0, 0, 0);
            }
            case TrackStartTime -> {
                if (cddContext.command[4] > 9 || cddContext.command[5] > 9) break;
                int track = cddContext.command[4] * 10 + cddContext.command[5];
                ExtendedTrackData extTrackData = ExtendedCueSheet.getExtTrack(extCueSheet, track);
                CueFileParser.toMSF(extTrackData.absoluteSectorStart, msfHolder);
                //status[6].bit(3) = session.tracks[track].isData();
                int status6 = ((isAudio ? 0 : 1) << 3) | (msfHolder.frame / 10);
//                auto [minute, second, frame] = CD::MSF(session.tracks[track].indices[1].lba);
                updateStatuses(cddContext.command[3], msfHolder.minute / 10, msfHolder.minute % 10,
                        msfHolder.second / 10, msfHolder.second % 10, status6, msfHolder.frame % 10, track % 10);
            }
            default -> {
                LOG.error("Unsupported Cdd {} command: {}({})", Request, request, request.ordinal());
                assert false;
            }
        }
    }

    private void processDone() {
        updateStatus(0, cddContext.io.status.ordinal());
        checksum();
        cddContext.statusPending = 1;
    }

    private int inTrack(int lba) {
        for (CdModel.ExtendedTrackData ext : extCueSheet.extTracks) {
            if (lba >= ext.absoluteSectorStart && lba <= ext.absoluteSectorEnd) {
                return ext.trackData.getNumber();
            }
        }
        return 0;
    }

    private void updateTrackIfLegal() {
        int trackNow = inTrack(cddContext.io.sector);
        if (trackNow > 0) {
            setTrack(trackNow);
        }
    }

    private void setTrack(int track) {
        if (track != cddContext.io.track) {
            LOG.info("Track changed: {} -> {}", cddContext.io.track, track);
            cddContext.io.track = track;
            //set D/M bit: 0 = MUSIC, 1 = DATA
            int val = ExtendedCueSheet.isAudioTrack(extCueSheet, track) ? 0 : 1;
            assert memoryContext.getRegBuffer(CpuDeviceAccess.SUB_M68K, MCD_CDD_CONTROL) == memoryContext.commonGateRegsBuf;
            setBit(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 8, val, Size.WORD);
        }
    }

    private void setIoStatus(CddStatus status) {
        if (status != cddContext.io.status) {
            LOG.info("Status changed: {} -> {}", cddContext.io.status, status);
            cddContext.io.status = status;
        }
    }

    private void setSector(int s) {
        if (s != cddContext.io.sector) {
            LOG.info("Sector changed: {} -> {}", cddContext.io.sector, s);
            cddContext.io.sector = s;
        }
    }

    /**
     * status 1..8, status0 is ignored
     */
    private void updateStatuses(int... vals) {
        assert vals.length == 8;
        for (int i = 1; i < 9; i++) {
            updateStatus(i, vals[i - 1]);
        }
    }

    /**
     * status 1
     * status 2 <- minute/10
     * status 3 <- minute%10
     * status 4 <- second/10
     * status 5 <- second%10
     * status 6 <- frame/10
     * status 7 <- frame%10
     * status 8
     */
    private void updateStatusesMsf(int status1, int status8, MsfHolder msfHolder) {
        updateStatuses(status1, msfHolder.minute / 10, msfHolder.minute % 10,
                msfHolder.second / 10, msfHolder.second % 10, msfHolder.frame / 10, msfHolder.frame % 10, status8);
    }

    private void updateStatus(int pos, int val) {
        cddContext.status[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM0.addr + pos, val, Size.BYTE);
    }

    private void updateCommand(int pos, int val) {
        cddContext.command[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM5.addr + pos, val, Size.BYTE);
    }

    @Override
    public void updateVideoMode(VideoMode videoMode) {
        playSupport.updateRegion(videoMode.getRegion());
    }

    @Override
    public void newFrame() {
        playSupport.newFrame();
    }

    @Override
    public void reset() {
        playSupport.reset();
    }
}