package mcd.cdd;

import com.google.common.base.MoreObjects;
import mcd.bus.McdSubInterruptHandler;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.Position;
import org.digitalmediaserver.cuelib.TrackData;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDD;
import static mcd.cdd.Cdd.CddCommand.Request;
import static mcd.cdd.Cdd.CddStatus.*;
import static mcd.cdd.CddImpl.MyTrackData.SectorSize.S_2352;
import static mcd.cdd.CddImpl.MyTrackData.TrackContent.T_AUDIO;
import static mcd.cdd.CddImpl.MyTrackData.TrackContent.T_DATA;
import static mcd.cdd.CddImpl.MyTrackData.TrackMode.MODE1;
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

    void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size);

    default void advance() {
        throw new RuntimeException("not implemented");
    }

    default void sample() {
        throw new RuntimeException("not implemented");
    }

    default double position(int sector) {
        throw new RuntimeException("not implemented");
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

    static Cdd createInstance(MegaCdMemoryContext memoryContext, McdSubInterruptHandler ih) {
        return new CddImpl(memoryContext, ih);
    }
}

class CddImpl implements Cdd {

    private final static Logger LOG = LogHelper.getLogger(CddImpl.class.getSimpleName());

    public final CddContext cddContext = CddContext.create(CddIo.create());

    private final MegaCdMemoryContext memoryContext;
    private final McdSubInterruptHandler interruptHandler;

    static final Path p = Path.of("./test_roms", "sonic_cd.cue");
    private CueSheet cueSheet;
    private boolean hasFile;


    public CddImpl(MegaCdMemoryContext mc, McdSubInterruptHandler ih) {
        memoryContext = mc;
        interruptHandler = ih;

        checksum();
        tryInsert();
    }

    private void tryInsert() {
        cueSheet = CueFileParser.parse(p);
        hasFile = cueSheet != null;
        if (!hasFile) {
            cddContext.io.status = NoDisc;
            return;
        }

        cddContext.io.status = ReadingTOC;
        cddContext.io.sector = 0; //TODO session.leadIn.lba;
        cddContext.io.track = cddContext.io.sample = cddContext.io.tocRead = 0;
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
            default -> LOG.error("S CDD Write {}: {} {} {}", regSpec, th(address), th(value), size);
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
                if (true) {
                    cddContext.io.status = Paused;
                    cddContext.io.track = 1; //TODO
                    cddContext.io.tocRead = 1;
                }
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
            default -> {
                LOG.error("Unsupported Cdd command: {}({}), parameter: {}", cddCommand, cddCommand.ordinal(),
                        cddContext.command[3]);
                assert false;
            }
        }
        processDone();
    }

    private void handleRequestCommand() {
        CddRequest request = CddRequest.values()[cddContext.command[3]];
        switch (request) {
            case AbsoluteTime -> {
                int minute = 0, second = 0, frame = 0;
//                auto [minute, second, frame] = CD::MSF(io.sector);
                //                status[8] = session.tracks[io.track].isData() << 2;
                updateStatuses(cddContext.command[3], minute / 10, minute % 10,
                        second / 10, second % 10, frame / 10, frame % 10, 0);
            }
            case DiscCompletionTime -> {
                int minute = 0, second = 0, frame = 0;
//                auto [minute, second, frame] = CD::MSF(session.leadOut.lba);
                updateStatuses(cddContext.command[3], minute / 10, minute % 10,
                        second / 10, second % 10, frame / 10, frame % 10, 0);
            }
            case DiscTracks -> {
                int firstTrack = 1;
                int lastTrack = 1; //cueSheet.getAllTrackData().size(); TODO
                updateStatuses(cddContext.command[3], firstTrack / 10, firstTrack % 10,
                        lastTrack / 10, lastTrack % 10, 0, 0, 0);
            }
            case TrackStartTime -> {
                if (cddContext.command[4] > 9 || cddContext.command[5] > 9) break;
                int track = cddContext.command[4] * 10 + cddContext.command[5];
                TrackData trackData = getTrack(track);
                String dataType = trackData.getDataType();
                boolean isAudio = "AUDIO".equalsIgnoreCase(dataType);
                assert isAudio;
                int minute, second, frame;

                Position indexPos = trackData.getLastIndex().getPosition();
                minute = indexPos.getMinutes();
                second = indexPos.getSeconds();
                frame = indexPos.getFrames();
                //                status[6].bit(3) = session.tracks[track].isData();
                int status6 = frame / 10;
                status6 &= ~(1 << 3);
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

    private TrackData getTrack(int number) {
        assert number > 0;
        TrackData td = cueSheet.getAllTrackData().get(number - 1);
        assert td.getNumber() == number;
        return td;
    }

    public static void main(String[] args) throws IOException {
        processCue("sonic_cd.cue");
        processCue("sonic_cd_audio.cue");
    }

    private static void processCue(String cueName) throws IOException {
        Path p = Path.of("./test_roms", cueName);
        CueSheet cueSheet = CueFileParser.parse(p);
        List<TrackData> tracks = cueSheet.getAllTrackData();
        assert tracks.size() > 0;
        TrackData track01 = tracks.get(0);
        String file = track01.getParent().getFile();
        Path fp = p.resolveSibling(file);
        RandomAccessFile raf = new RandomAccessFile(fp.toFile(), "r");
        MyTrackData trackData = new MyTrackData(track01, raf);
        byte[] header = new byte[16];
        raf.read(header, 0, header.length);
        String scdsys = "SEGADISCSYSTEM";
        byte ff = (byte) 0xff;
        byte[] sync = {0x00, ff, ff, ff, ff, ff, ff, ff, ff, ff, ff, 0x00};
        byte[] scdsysb = scdsys.getBytes();
        if (Arrays.equals(scdsysb, 0, scdsysb.length, header, 0, scdsysb.length)) {
            System.out.println("valid Sega CD image: " + track01.getDataType());
        } else if (Arrays.equals(sync, 0, sync.length, header, 0, sync.length)) {
            System.out.println("CD-ROM synchro pattern: " + track01.getDataType());
        }
        trackData.trackDataType = MyTrackData.TrackDataType.parse(track01.getDataType());
        /* read Sega CD image header + security code */
        byte[] sec = new byte[0x200];
        raf.read(sec, 0, sec.length);

        MyTrackData.SectorSize sectorSize = trackData.trackDataType.size;
        trackData.lenBytes = (int) raf.length();
        trackData.absoluteSectorStart = 0;
        trackData.absoluteSectorEnd = trackData.lenBytes / sectorSize.s_size;
        assert sectorSize.s_size * (trackData.lenBytes / sectorSize.s_size) == sectorSize.s_size; //divides with no remainder
        assert sectorSize.s_size >= 150; /* DATA track length should be at least 2s (BIOS requirement) */
        System.out.println(trackData);
        System.out.println(cueSheet);
        raf.close();
    }

    static class MyTrackData {

        enum TrackContent {T_AUDIO, T_DATA}

        enum TrackMode {MODE1, MODE2}

        enum SectorSize {
            S_2048(2048), S_2352(2352);

            public final int s_size;

            SectorSize(int s) {
                this.s_size = s;
            }
        }

        /**
         * AUDIO	    Audio/Music (2352 — 588 samples)
         * CDG	        Karaoke CD+G (2448)
         * MODE1/2048	CD-ROM Mode 1 Data (cooked)
         * MODE1/2352	CD-ROM Mode 1 Data (raw)
         * MODE2/2048	CD-ROM XA Mode 2 Data (form 1)
         * MODE2/2324	CD-ROM XA Mode 2 Data (form 2)
         * MODE2/2336	CD-ROM XA Mode 2 Data (form mix)
         * MODE2/2352	CD-ROM XA Mode 2 Data (raw)
         * CDI/2336	    CDI Mode 2 Data
         * CDI/2352	    CDI Mode 2 Data
         * <p>
         * https://www.gnu.org/software/ccd2cue/manual/html_node/MODE-_0028Compact-Disc-fields_0029.html
         */
        enum TrackDataType {
            MODE1_2352(MODE1, S_2352, T_DATA),
            AUDIO(MODE1, S_2352, T_AUDIO);

            public final TrackMode mode;
            public final SectorSize size;
            public final TrackContent content;

            TrackDataType(TrackMode mode, SectorSize sectorSize, TrackContent content) {
                this.mode = mode;
                this.size = sectorSize;
                this.content = content;
            }

            public static TrackDataType parse(String spec) {
                TrackMode tm = null;
                SectorSize ss = null;
                for (TrackMode m : TrackMode.values()) {
                    if (spec.toUpperCase().contains(m.name())) {
                        tm = m;
                        break;
                    }
                }
                for (SectorSize s : SectorSize.values()) {
                    if (spec.contains("" + s.s_size)) {
                        ss = s;
                        break;
                    }
                }
                for (TrackDataType tdt : TrackDataType.values()) {
                    if (tdt.size == ss && tdt.mode == tm) {
                        return tdt;
                    }
                    if (tdt.name().equalsIgnoreCase(spec)) {
                        return tdt;
                    }
                }
                LOG.error("Unable to parse: {}", spec);
                return null;
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this.name())
                        .add("mode", mode)
                        .add("size", size)
                        .add("content", content)
                        .toString();
            }
        }

        public final TrackData trackData;
        public final RandomAccessFile file;
        public TrackDataType trackDataType;
        public int absoluteSectorStart, absoluteSectorEnd, lenBytes;

        public MyTrackData(TrackData trackData, RandomAccessFile file) {
            this.trackData = trackData;
            this.file = file;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("trackData", trackData)
                    .add("file", file)
                    .add("trackDataType", trackDataType)
                    .add("absoluteSectorStart", absoluteSectorStart)
                    .add("absoluteSectorEnd", absoluteSectorEnd)
                    .add("lenBytes", lenBytes)
                    .toString();
        }
    }
}