package mcd.cdd;

import mcd.bus.McdSubInterruptHandler;
import mcd.cdc.Cdc;
import mcd.cdd.CdModel.ExtendedTrackData;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDD;
import static mcd.cdd.Cdd.CddCommand.Request;
import static mcd.cdd.Cdd.CddStatus.*;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;
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

    void tryInsert(SystemProvider.RomContext romContext);
    void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size);

    default void advance() {
        throw new RuntimeException("not implemented");
    }

    default void sample() {
        throw new RuntimeException("not implemented");
    }

    default double position(int sector) {
        //convert sector# to normalized sector position on the CD-ROM surface for seek latency calculation

        double sectors = 7500.0 + 330000.0 + 6750.0;
        double radius = 0.058 - 0.024;
        double innerRadius = 0.024 * 0.024;  //in mm
        double outerRadius = 0.058 * 0.058;  //in mm

        sector += 150; //session.leadIn.lba;  //convert to natural //TODO check
        return Math.sqrt(sector / sectors * (outerRadius - innerRadius) + innerRadius) / radius;
    }

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

    private ExtendedCueSheet extCueSheet;
    private boolean hasFile;


    public CddImpl(MegaCdMemoryContext mc, McdSubInterruptHandler ih, Cdc c) {
        memoryContext = mc;
        interruptHandler = ih;
        cdc = c;

        checksum();
    }

    @Override
    public void tryInsert(SystemProvider.RomContext romContext) {
        tryInsert(romContext.romSpec.file);
    }

    private void tryInsert(Path p) {
        extCueSheet = new ExtendedCueSheet(p);
        hasFile = extCueSheet.cueSheet != null;
        if (!hasFile) {
            cddContext.io.status = NoDisc;
            return;
        }

        cddContext.io.status = ReadingTOC;
        cddContext.io.sector = 150; //TODO session.leadIn.lba, should be 150 sectors (2 seconds)
        cddContext.io.track = cddContext.io.sample = cddContext.io.tocRead = 0;
        LOG.info("Using disc: {}", extCueSheet);
    }

    @Override
    public void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size) {
        writeBufferRaw(memoryContext.commonGateRegsBuf, address & MDC_SUB_GATE_REGS_MASK, value, size);
        switch (regSpec) {
            case MCD_CDD_CONTROL -> {
                int v = readBuffer(memoryContext.commonGateRegsBuf, regSpec.addr, Size.WORD);
                //TODO this should be only when HOCK 0->1 but bios requires it
                if ((true || cddContext.hostClockEnable == 0) && (v & 4) > 0) { //HOCK set
                    interruptHandler.raiseInterrupt(INT_CDD);
                }
                cddContext.hostClockEnable = (v & 4);
                assert (v & 0xFEFB) == 0 : th(v); //DRS,DTS, invalid bits are 0
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
            case MCD_CD_FADER -> LogHelper.logWarnOnce(LOG, "Write {}: {} {}", regSpec, th(value), size);
            default -> {
                LOG.error("S CDD Write {}: {} {} {}", regSpec, th(address), th(value), size);
                assert false;
            }
        }
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

    /**
     * this should be called at 75hz
     */
    public void step(int cycles) {
        if (cddContext.hostClockEnable == 0) {
            return;
        }
        if (cddContext.statusPending > 0) {
            interruptHandler.raiseInterrupt(INT_CDD);
        }
        cddContext.statusPending = 0;

        switch (cddContext.io.status) {
            case NoDisc, Paused -> {
                //do nothing
            }
            case Stopped -> {
                if (!hasFile) cddContext.io.status = NoDisc;
                else if (cddContext.io.tocRead == 0) cddContext.io.status = ReadingTOC;
                cddContext.io.sector = cddContext.io.sample = cddContext.io.track = 0;
            }
            case ReadingTOC -> {
                cddContext.io.sector++;
//                if(!session.inLeadIn(io.sector)) { //TODO
                if (cddContext.io.sector > 150) {
                    cddContext.io.status = Paused;
                    int trackNow = inTrack(cddContext.io.sector);
                    if (trackNow > 0) {
                        cddContext.io.track = trackNow;
                    }
                    cddContext.io.tocRead = 1;
                }
            }
            case Seeking -> {
                if (cddContext.io.latency > 0 && --cddContext.io.latency > 0) break;
                cddContext.io.status = statusVals[cddContext.io.seeking];
                int trackNow = inTrack(cddContext.io.sector);
                if (trackNow > 0) {
                    cddContext.io.track = trackNow;
                }
            }
            case Playing -> {
                assert false; //TODO untested
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

    @Override
    public void process() {
        LOG.info("CDD {}: {}", cddContext.command[0], cddContext.command[3]);
        if (!valid()) {
            //unverified
            LOG.error("CDD checksum error");
            cddContext.io.status = CddStatus.ChecksumError;
            processDone();
            return;
        }
        CddCommand cddCommand = CddCommand.values()[cddContext.command[0]];
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
                cddContext.io.status = hasFile ? Stopped : NoDisc;
                for (int i = 1; i < 9; i++) {
                    updateStatus(i, 0);
                }
            }
            case Request -> handleRequestCommand();
            case SeekPause -> {
                int minute = cddContext.command[2] * 10 + cddContext.command[3];
                int second = cddContext.command[4] * 10 + cddContext.command[5];
                int frame = cddContext.command[6] * 10 + cddContext.command[7];
                int lba = minute * 60 * 75 + second * 75 + frame - 3;

//                cddCounter    = 0; //TODO
                cddContext.io.status = CddStatus.Seeking;
                cddContext.io.seeking = CddStatus.Paused.ordinal();
                cddContext.io.latency = (int) (11 + 112.5 * Math.abs(position(cddContext.io.sector) - position(lba)));
                cddContext.io.sector = lba;
                cddContext.io.sample = 0;

                updateStatuses(0xf, 0, 0, 0, 0, 0, 0, 0);
            }
            default -> {
                LOG.error("Unsupported Cdd command: {}({}), parameter: {}", cddCommand, cddCommand.ordinal(),
                        cddContext.command[3]);
                assert false;
            }
        }
        processDone();
    }

    private final int[] msfRes = new int[3];

    //"sonic_cd_audio.cue"
    //14471856 (bin1) + 19618032 (bin2) = 34089888 bytes
    //34089888/2352 = 14494 sectors
    // + 2 seconds (150 sectors, for lead-in) = 14496 sectors
    private void handleRequestCommand() {
        CddRequest request = CddRequest.values()[cddContext.command[3]];
        boolean isAudio = ExtendedCueSheet.isAudioTrack(extCueSheet, cddContext.io.track);
        switch (request) {
            case AbsoluteTime -> {
                CueFileParser.toMSF(cddContext.io.sector, msfRes);
                int minute = msfRes[2], second = msfRes[1], frame = msfRes[0];
                int status8 = (isAudio ? 0 : 1) << 2;
                updateStatuses(cddContext.command[3], minute / 10, minute % 10,
                        second / 10, second % 10, frame / 10, frame % 10, status8);
            }
            case RelativeTime -> {
                int relSector = cddContext.io.sector -
                        ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track).absoluteSectorStart;
                assert relSector > 0; //TODO can be < 0?
                CueFileParser.toMSF(relSector, msfRes);
                int minute = msfRes[2], second = msfRes[1], frame = msfRes[0];
                int status8 = (isAudio ? 0 : 1) << 2;
                updateStatuses(cddContext.command[3], minute / 10, minute % 10,
                        second / 10, second % 10, frame / 10, frame % 10, status8);
            }
            case TrackInformation -> {
                updateStatuses(cddContext.command[3], cddContext.io.track / 10, cddContext.io.track % 10,
                        0, 0, 0, 0, 0);
//                if(session.inLeadIn (io.sector)) { status[2] = 0x0; status[3] = 0x0; }
//                if(session.inLeadOut(io.sector)) { status[2] = 0xa; status[3] = 0xa; }
            }
            case DiscCompletionTime -> {
                int lba = extCueSheet.sectorEnd;
                CueFileParser.toMSF(lba, msfRes);
                int minute = msfRes[2], second = msfRes[1], frame = msfRes[0];
                updateStatuses(cddContext.command[3], minute / 10, minute % 10,
                        second / 10, second % 10, frame / 10, frame % 10, 0);
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
                CueFileParser.toMSF(extTrackData.absoluteSectorStart, msfRes);
                int minute = msfRes[2], second = msfRes[1], frame = msfRes[0];
                //                status[6].bit(3) = session.tracks[track].isData();
                int status6 = frame / 10;
                status6 &= ~(1 << 3);
                status6 |= ((isAudio ? 0 : 1) << 3);
//                auto [minute, second, frame] = CD::MSF(session.tracks[track].indices[1].lba);
                updateStatuses(cddContext.command[3], minute / 10, minute % 10,
                        second / 10, second % 10, status6, frame % 10, track % 10);
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

    /**
     * status 1..8, status0 is ignored
     */
    private void updateStatuses(int... vals) {
        assert vals.length == 8;
        for (int i = 1; i < 9; i++) {
            updateStatus(i, vals[i - 1]);
        }
    }

    private void updateStatus(int pos, int val) {
        cddContext.status[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM0.addr + pos, val, Size.BYTE);
    }

    private void updateCommand(int pos, int val) {
        cddContext.command[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM5.addr + pos, val, Size.BYTE);
    }

    public static void main(String[] args) throws IOException {
        Path misc = Paths.get("./test_roms", "misc_cue");
        List<Path> miscCues = Files.list(misc).
                filter(f -> f.toFile().isFile() && f.toAbsolutePath().toString().endsWith(".cue")).collect(Collectors.toList());
        Collections.sort(miscCues);

        String[][] paths = {
                {"sonic_cd.cue"},
                {"sonic_cd_audio.cue"},
                {"projectcd.iso"},
                {"snatcher", "snatcher.cue"},
                {"finalfight", "finalfight.cue"},
        };
        ExtendedCueSheet cueSheet;
        Path p1;

        for (String[] path : paths) {
            p1 = Paths.get("./test_roms", path);
            cueSheet = new ExtendedCueSheet(p1);
            System.out.println(cueSheet);
        }

        for (Path path : miscCues) {
            cueSheet = new ExtendedCueSheet(path);
            System.out.println(cueSheet);
        }
    }
}