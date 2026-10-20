package mcd.cdd;

import mcd.bus.McdSubInterruptHandler;
import mcd.cdc.Cdc;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.BlipPcmProvider;
import omegadrive.sound.PcmProvider;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Size;
import omegadrive.util.VideoMode;
import org.slf4j.Logger;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDD;
import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_SUBCODE;
import static mcd.cdd.Cdd.CddRequest.AbsoluteTime;
import static mcd.cdd.Cdd.CddRequest.NotReady;
import static mcd.cdd.Cdd.CddStatus.*;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.pcm.McdPcm.PCM_VOLUME_SHIFT;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.Util.th;

/***
 * CddImpl
 *
 * TODO check mute bit from blastem
 */
public class CddImplNew implements Cdd {

    private final static Logger LOG = LogHelper.getLogger(CddImpl.class.getSimpleName());

    private final boolean verbose = false;
    private final boolean verboseReg = false;

    private static final int CD_LATENCY = 1;

    public final CddContext cddContext = CddContext.create(CddIo.create());

    private final MegaCdMemoryContext memoryContext;
    private final McdSubInterruptHandler interruptHandler;
    private final Cdc cdc;
    private PcmProvider playSupport;
    private ExtendedCueSheet extCueSheet;
    private SubcodeHelper subcodeHelper;

    private final CueFileParser.MsfHolder msfHolder = new CueFileParser.MsfHolder();
    private boolean hasMedia;


    public CddImplNew(MegaCdMemoryContext mc, McdSubInterruptHandler ih, Cdc c, boolean soundEnabled) {
        memoryContext = mc;
        interruptHandler = ih;
        cdc = c;
        playSupport = soundEnabled ?
                new BlipPcmProvider("CDDA", RegionDetector.Region.USA, 44100) : BlipPcmProvider.NO_SOUND;
        setDataOrMusicBit(CddControl_DM_bit.DATA_1);
        setIoStatus(NoDisc);
        updateStatus(1, NotReady.ordinal());
        statusChecksum();
        commandChecksum();
    }

    @Override
    public void setPcmProvider(PcmProvider pp) {
        playSupport = pp;
    }

    @Override
    public void tryInsert(ExtendedCueSheet cueSheet) {
        cueSheet.assertReady();
        extCueSheet = cueSheet;
        hasMedia = extCueSheet.cueSheet != null;
        if (!hasMedia) {
            setIoStatus(NoDisc);
            return;
        }
        initReadingTOC();
        subcodeHelper = SubcodeHelper.create(memoryContext, extCueSheet.cuePath);
        LOG.info("Using disc: {}", extCueSheet);
    }

    private void initReadingTOC() {
        setSector(-300); //TODO session.leadIn.lba, should be 150 sectors (2 seconds)
        cddContext.io.track = cddContext.io.sample = cddContext.io.tocRead = 0;
        cdc.setMedia(extCueSheet);
        cddContext.commandRegs[0] = CddCommand.Stop.ordinal();
        cddContext.io.latency = -1;
        cddContext.faderVol = cddContext.faderTarget = FADER_MAX;
        cdd_process();
    }

    @Override
    public void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size) {
        if (verboseReg) LOG.info("CDD,regW,{},{},{},{}", th(address), th(value), size, regSpec);
        switch (regSpec) {
            case MCD_CDD_CONTROL -> {
                //TODO check word writes, even byte writes
                assert (value & 3) == 0; //DRS and DTS can only be set to 0
                value &= 7;
                assert memoryContext.getRegBuffer(CpuDeviceAccess.SUB_M68K, regSpec) == memoryContext.commonGateRegsBuf;
                writeBufferRaw(memoryContext.commonGateRegsBuf, address & MDC_SUB_GATE_REGS_MASK, value, size);
                if ((cddContext.hostClockEnable == 0) && (value & 4) > 0) { //HOCK set
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
                cddContext.faderTarget = (value >> 4) & 0xFFF;
                if (verboseReg) LOG.info("CD fader target: {}", cddContext.faderTarget);

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
        if (verboseReg) LOG.info("CDD,regR,{},{},{},{}", th(address), th(res), size, regSpec);
        return res;
    }


    private void writeCommByte(MegaCdDict.RegSpecMcd regSpec, int addr, int value) {
        int index = (addr & MDC_SUB_GATE_REGS_MASK) - MCD_CDD_COMM5.addr;
        updateCommand(index, value & 0xF);
        switch (regSpec) {
            case MCD_CDD_COMM9 -> {
                //Transmission Command 9
                if ((addr & 1) == 1) { //unconfirmed
//                    logStatus(true);
                    cdd_process();
                    logStatus(true);
                }
            }
        }
    }

    private void statusChecksum() {
        updateStatus(CDD_CHECKSUM_BYTE, Cdd.getCddChecksum(cddContext.statusRegs));
    }

    private boolean commandChecksum() {
        int checksum = Cdd.getCddChecksum(cddContext.commandRegs);
        updateCommand(CDD_CHECKSUM_BYTE, checksum);
        return checksum == cddContext.commandRegs[CDD_CHECKSUM_BYTE];
    }

    private String statusString(int[] status, int[] command) {
        String head = "", tail = "";
        for (int i = 0; i < status.length; i++) {
            head += Integer.toHexString(status[i]);
            tail += Integer.toHexString(command[i]);
            if (i == 1 || i == 7) {
                head += ".";
                tail += ".";
            }
        }
        return head.toUpperCase() + " - " + tail.toUpperCase();
    }

    String prev = "";

    private void logStatus(boolean process) {
        logStatus(process, false);
    }

    private void logStatus(boolean process, boolean force) {
        String stat = statusString(cddContext.statusRegs, cddContext.commandRegs);
        if (force || !stat.equals(prev)) {
            if (verbose) System.out.println(stat + (process ? " <-" : ""));
            prev = stat;
        }
    }

    @Override
    public void logStatus() {
        logStatus(false);
    }

    int cnt = 0;
    int pauseLbaTarget = -1;

    public void setPauseLbaTarget(int plt) {
        if (plt != pauseLbaTarget) {
            if (verbose) LOG.info("pauseLbaTarget: {} -> {}", pauseLbaTarget, plt);
            pauseLbaTarget = plt;
        }
    }

    public void step(int cycles) {
//        clearStatusRegs();
        stepInternal(cycles);
        assert commandChecksum();
        logStatus(false, true);
    }

    /**
     * this should be called at 75hz
     */
    private void stepInternal(int cycles) {
//        logStatus(false, true);
        if (!hasMedia || cddContext.hostClockEnable == 0) {
            return;
        }
        if (verbose) LOG.info("CDD interrupt");
        interruptHandler.raiseInterrupt(INT_CDD);
        /* drive latency */
        if (cddContext.io.latency > 0) {
            /**
             * TODO
             * One little detail that's worth mentioning, is that seeking has two consequences for CDD status.
             * The first is that it's not consistently happening every 1/75th of a second anymore.
             * The other is that periodically the drive seems to lose sync and the CDD gives a not-ready status.
             * This not-ready status is actually important because the BIOS is a buggy piece of crap and
             * only updates it's knowledge of the current track number after receiving such a status even though
             * it's periodically getting track number updates from the CDD.
             */
            cddContext.io.latency--;
            //ie. go back to 0x2f after 0x25
            //[2f.000000.59, 2f.000000.59, 25.000280.1d, 2f.000000.1d,
            if (cddContext.statusRegs[0] == Seeking.ordinal() && cddContext.statusRegs[1] != NotReady.ordinal()) {
                cnt++;
                if (cnt > 1) {
                    updateStatus(1, NotReady.ordinal());
                    updateStatusesMsfOnly(CueFileParser.EMPTY);
                    updateStatus(8, getFlags());
                    cnt = 0;
                }
            }

            if (cddContext.io.latency == 0) {
                updateStatus(0, cddContext.io.status.ordinal()); //for seek_pause and seek_play
                CueFileParser.lbaToMsfAdjustPregap(cddContext.io.sector, msfHolder);
                updateStatusesMsf(AbsoluteTime.ordinal(), getFlags(), msfHolder);
                if (cddContext.io.status == Paused) {
                    setPauseLbaTarget(cddContext.io.sector + LBA_READAHEAD_LEN);
                }
            }
            return;
        }

        /* reading disc */
        if (cddContext.io.status == Playing) {
            /* end of disc detection */
            if (cddContext.io.sector >= extCueSheet.sectorEnd) { //cdd.toc.last){
                setIoStatus(LeadOut);
                LOG.warn("LeadOut");
                return;
            }

            /* subcode data processing */
            cdd_read_subcode(cddContext.io.sector);
            /* track type */
            CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track);
            boolean isDataTrack = etd.trackDataType != CdModel.TrackDataType.AUDIO;
            if (isDataTrack) {
                /* CD-ROM sector header */
                /* decode CD-ROM track sector */
                cdc.cdc_decoder_update(cddContext.io.sector, cddContext.io.track, cddContext.io.status);
                setSector(cddContext.io.sector + 1);
            } else {
                /* check against audio track start index */
                if (cddContext.io.sector >= etd.absoluteSectorStart) {
                    /* audio track playing */
                    setDataOrMusicBit(CddControl_DM_bit.MUSIC_0);
                }

                /* audio blocks are still sent to CDC as well as CD DAC/Fader */
                cdc.cdc_decoder_update(cddContext.io.sector, cddContext.io.track, cddContext.io.status);
                //stepCdda increases the sector
            }
//            if (cddContext.statusRegs[1] != NotReady.ordinal()) {
//                CueFileParser.lbaToMsfAdjustPregap(cddContext.io.sector, msfHolder);
//                updateStatusesMsfOnly(msfHolder);
//            }
        } else {
            if (cddContext.io.status == Scanning) {
                int lba = cddContext.io.sector + cddContext.io.seeking;
                setSector(lba);
                setDataOrMusicBit(CddControl_DM_bit.DATA_1);
            }
            /* CDC decoder is still running while disc is not being read (fixes MCD-verificator CDC Flags Test #30) */
            cdc.cdc_decoder_update(cddContext.io.sector, cddContext.io.track, cddContext.io.status);
        }
        int nt = inTrack(cddContext.io.sector);
        if (cddContext.io.sector >= 0 && nt != cddContext.io.track) {
            if (verbose)
                LOG.warn("Track changed: {}->{}, EOD track sector: {}", cddContext.io.track, nt, cddContext.io.sector);
            setTrack(nt);
        }
    }

    private void cdd_read_subcode(int lba) {
        subcodeHelper.cdd_process_subcode(lba);
        interruptHandler.raiseInterrupt(INT_SUBCODE);
    }

    private boolean isAudioTrack(int tno) {
        return tno > 0 ?
                ExtendedCueSheet.isAudioTrack(extCueSheet, tno) : false;
    }

    void cdd_process() {
        /* Process CDD command */
        CddCommand cddCommand = Cdd.commandVals[cddContext.commandRegs[0]];
        StringBuilder extraInfo = new StringBuilder();
        switch (cddCommand) {
            /* Get Drive Status */
            case DriveStatus -> {
                /* RS0-RS1 are normally unchanged unless reported drive status needs to be updated
                (i.e previous drive command has been processed) */
                /* Note: this function is called one 75hz frame ahead of CDD update so latency counter
                is always one step ahead of upcoming status */
                /* Radical Rex and Annet Futatabi both need at least respectively 2 and 3 interrupts
                with 'playing' status returned before sectors start getting incremented */
                handleDriveStatus();
            }
            /* Stop Drive */
            case Stop -> {
                /* update status */
                setIoStatus(hasMedia ? ReadingTOC : NoDisc);
                /* no audio track playing */
                setDataOrMusicBit(CddControl_DM_bit.DATA_1);
                /* RS1-RS8 ignored, expects 0x0 (CD_STOP) in RS0 once */
                updateStatus(0, Stopped.ordinal());
                updateStatuses(NotReady.ordinal(), 0, 0, 0, 0, 0, 0, getFlags());
            }
            /* Report TOC infos */
            case Request -> handleRequestCommand(extraInfo);
            /* Seek */
            case SeekPause, SeekPlay -> handleSeek(cddCommand, extraInfo);
            /* Pause */
            case Pause -> {
                /* update status (RS1-RS8 unchanged) */
                setIoStatus(Paused);
                updateStatus(0, Paused.ordinal());
                setPauseLbaTarget(cddContext.io.sector - LBA_READAHEAD_LEN);
            }
            /* Resume */
            case Play -> {
                /* update status (RS1-RS8 unchanged) */
                setIoStatus(Playing);
                updateStatus(0, Playing.ordinal());
            }
            /* Forward Scan */
            case Forward -> {
                /* reset scanning direction / speed */
                cddContext.io.seeking = CD_SCAN_SPEED;
                /* update status (RS1-RS8 unchanged) */
                setIoStatus(Scanning);
                updateStatus(0, Scanning.ordinal());
                LogHelper.logWarnOnce(LOG, "{} {}", cddCommand, Cdd.toShortString(cddContext.commandRegs));
            }
            /* Rewind Scan */
            case Reverse -> {
                /* reset scanning direction / speed */
                cddContext.io.seeking = -CD_SCAN_SPEED;
                /* update status (RS1-RS8 unchanged) */
                setIoStatus(Scanning);
                updateStatus(0, Scanning.ordinal());
                LogHelper.logWarnOnce(LOG, "{} {}", cddCommand, Cdd.toShortString(cddContext.commandRegs));
            }
            /* N-Track Jump Control ? (usually sent before CD_SEEK or CD_PLAY commands) */
            case TrackSkip -> {
                /* TC3 corresponds to seek direction (00=forward, FF=reverse) */
                /* TC4-TC7 are related to seek length (4x4 bits i.e parameter values are between -65535 and +65535) */
                /* Maybe related to number of auto-sequenced track jumps/moves for CD DSP (cf. CXD2500BQ datasheet) */
                /* also see US Patent nr. 5222054 for a detailed description of seeking operation using Track Jump */
                /* NOTE: this is used to move the laser to a certain position, it can be ignored as the following
                   SEEK command will give a precise MSF */
                /* no audio track playing */
                setDataOrMusicBit(CddControl_DM_bit.DATA_1);
                /* update status (RS1-RS8 unchanged) */
                setIoStatus(Paused);
                updateStatus(0, Paused.ordinal());
                if (verbose) LogHelper.logWarnOnce(LOG, "{} {}", cddCommand, Cdd.toShortString(cddContext.commandRegs));
            }

            /* Close Tray */
            case DoorClose -> {
                /* no audio track playing */
                setDataOrMusicBit(CddControl_DM_bit.DATA_1);
                /* update status */
                setIoStatus(hasMedia ? ReadingTOC : NoDisc);
                /* RS1-RS8 ignored, expects CD_STOP in RS0 once */
                updateStatus(0, Stopped.ordinal());
                updateStatuses(0, 0, 0, 0, 0, 0, 0, 0);
                LogHelper.logWarnOnce(LOG, "{} {}", cddCommand, Cdd.toShortString(cddContext.commandRegs));
            }
            /* Open Tray */
            case DoorOpen -> {
                /* no audio track playing */
                setDataOrMusicBit(CddControl_DM_bit.DATA_1);

                /* update status (RS1-RS8 ignored) */
                setIoStatus(DoorOpened);
                updateStatus(0, DoorOpened.ordinal());
                updateStatuses(0, 0, 0, 0, 0, 0, 0, 0);
                LogHelper.logWarnOnce(LOG, "{} {}", cddCommand, Cdd.toShortString(cddContext.commandRegs));
            }
            /* Unknown command */
            default -> {
                updateStatus(0, cddContext.io.status.ordinal());
                LOG.error("Unsupported Cdd command: {}({}), parameter: {}", cddCommand, cddCommand.ordinal(),
                        cddContext.commandRegs[3]);
                assert false;
            }
        }
        if (verbose) LOG.info("CDD cmd, {}({}) {}", cddCommand, cddCommand.ordinal(), extraInfo);
        //TODO does this happen?
//      clearCommandRegs();
        statusChecksum();
        logStatus(true, true);
    }

    private void handleSeek(CddCommand seek, StringBuilder extraInfo) {
        assert seek == CddCommand.SeekPlay || seek == CddCommand.SeekPause;
        /* reset track index */
        int index = 0;

        /* new LBA position */
        int lba = CueFileParser.msfToSectorAdjustPregap(cddContext.commandRegs[2], cddContext.commandRegs[3],
                cddContext.commandRegs[4], cddContext.commandRegs[5], cddContext.commandRegs[6],
                cddContext.commandRegs[7]);
        CueFileParser.lbaToMsfAdjustPregap(lba, msfHolder);
        extraInfo.append("lba " + lba + ", msf " + msfHolder);
        if (cddContext.io.status != Playing) {
            lba -= LBA_READAHEAD_LEN;
        }

        /* CD drive latency */
        if (cddContext.io.latency == 0) {
            /* SeekPlay
             * Fixes a few games hanging because they expect data to be read with some delay */
            /* Wolf Team games (Annet Futatabi, Aisle Lord, Cobra Command, Earnest Evans, Road Avenger & Time Gal) need at least 11 interrupts delay  */
            /* Space Adventure Cobra (2nd morgue scene) needs at least 13 interrupts delay (incl. seek time, so 11 is OK) */
            /* By default, at least one interrupt latency is required by current emulation model (BIOS hangs otherwise) */
            cddContext.io.latency = 1 + 10 * CD_LATENCY;
        }

        /* CD drive seek time */
        /* max. seek time = 1.5 s = 1.5 x 75 = 112.5 CDD interrupts (rounded to 120) for 270000 sectors max on disc. */
        /* Note: This is only a rough approximation since, on real hardware, seek time is much likely not linear and */
        /* latency much larger than above value, but this model works fine for Sonic CD (track 26 playback needs to  */
        /* be enough delayed to start in sync with intro sequence, as compared with real hardware recording).        */
            /* SeekPause
            /* We are using similar linear model as above, although still not exactly accurate, */
        /* it works fine for Switch/Panic! intro (Switch needs at least 30 interrupts while */
        /* seeking from 00:05:63 to 24:03:19, Panic! when seeking from 00:05:60 to 24:06:07) */
        if (lba > cddContext.io.sector) {
            cddContext.io.latency += (((lba - cddContext.io.sector) * 120 * CD_LATENCY) / 270000);
        } else {
            cddContext.io.latency += (((cddContext.io.sector - lba) * 120 * CD_LATENCY) / 270000);
        }

        int minLatency = 1 + 10 * CD_LATENCY;
        cddContext.io.latency = Math.max(cddContext.io.latency, minLatency);

        /* update current LBA */
        setSector(lba);

        /* get track index */
        index = inTrack(cddContext.io.sector);
        if (index == LEADINNUM) { //sector < 0, use track 1
            //TODO should not seek in lead-in?
            index = 1;
        }
        if (index == LEADOUTNUM) {
            //TODO should not seek in lead-out?
            index = 1; //leadout, use last track
        }

        /* update current track index */
        setTrack(index);

        /* seek to current subcode position */
        cdd_read_subcode(cddContext.io.sector);

        /* no audio track playing (yet) */
        setDataOrMusicBit(CddControl_DM_bit.DATA_1);

        /* update status (reported to host once seeking has ended) */
        setIoStatus(seek == CddCommand.SeekPlay ? Playing : Paused);

        /* RS0 should indicate seeking until drive is ready (fixes audio delay in Bari Arm) */
        /* RS1=0xf to invalidate track infos in RS2-RS8 until drive is ready (fixes Snatcher Act 2 start cutscene) */
            /* SeekPause: RS1=0xf to invalidate track infos in RS2-RS8 while seeking
            (fixes Final Fight CD intro when seek time is emulated) */
        updateStatus(0, Seeking.ordinal());
        updateStatuses(NotReady.ordinal(), 0, 0, 0, 0, 0, 0, getFlags());
    }

    private void handleDriveStatus() {
        if (cddContext.io.latency <= 3) {
            /* update reported drive status */
            updateStatus(0, cddContext.io.status.ordinal());
            CddRequest request = Cdd.requestVals[cddContext.commandRegs[1]];
            switch (request) {
                /* check if RS1 indicated invalid track infos (during seeking) */
                case NotReady -> {
                        /* seeking has ended so we return valid track infos,
                        e.g current absolute time by default (fixes Lunar - The Silver Star) */
                    CueFileParser.lbaToMsfAdjustPregap(cddContext.io.sector, msfHolder);
                    /* Current block flags in RS8 (bit0 = mute status, bit1: pre-emphasis status, bit2: track type) */
                    updateStatusesMsf(0, getFlags(), msfHolder);
                }
                /* otherwise, check if RS2-RS8 need to be updated */
                case AbsoluteTime -> {
                    /* current absolute time */
                    int lba = hasMedia ? cddContext.io.sector : 0;
                    CueFileParser.lbaToMsfAdjustPregap(lba, msfHolder);
                    updateStatusesMsf(cddContext.commandRegs[1], getFlags(), msfHolder);
                }
                case RelativeTime -> {
                    CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track);
                    /* current track relative time */
                    int lba = Math.abs(cddContext.io.sector - etd.absoluteSectorStart);
                    CueFileParser.lbaToMsf(lba, msfHolder);
                    updateStatusesMsf(cddContext.commandRegs[1], getFlags(), msfHolder);
                }
                case TrackInformation -> {
                    /* current track number */
//                        scd.regs[0x3a>>1].w = (cdd.index < cdd.toc.last) ? lut_BCD_16[cdd.index + 1] : 0x0A0A;
                    updateStatus(2, cddContext.io.track / 10);
                    updateStatus(3, cddContext.io.track % 10);
                }
                case TrackStartTime -> {
//                    int track = cddContext.commandRegs[4] * 10 + cddContext.commandRegs[5];
//                    CdModel.ExtendedTrackData extTrackData = ExtendedCueSheet.getExtTrack(extCueSheet, track);
//                    if (extTrackData == CdModel.ExtendedTrackData.NO_TRACK) {
//                        updateStatus(1, NotReady.ordinal());
//                        updateStatus(8, getFlags());
//                        break;
//                    }
//                    CueFileParser.MsfHolder msf = extTrackData.trackStartMsf;
//                    /* RS6 bit 3 is set for CD-ROM track, Track Number (low digit) */
//                    final boolean isAudio = cddContext.io.track > 0 ?
//                            ExtendedCueSheet.isAudioTrack(extCueSheet, cddContext.io.track) : true;
//                    int status6 = (isAudio ? 0 : 0x8) | (msf.frame / 10);
//                    updateStatuses(request.ordinal(), msf.minute / 10, msf.minute % 10,
//                            msf.second / 10, msf.second % 10, status6, msf.frame % 10, track % 10);
//                    updateStatus(8, getFlags());
                    assert false;
                }
                default -> {
                    assert false : request;
                }
            }
        }
    }

    private void handleRequestCommand(StringBuilder extraInfo) {
        /* Infos automatically retrieved by CDD processor from Q-Channel */
        /* commands 0x00-0x02 (current block) and 0x03-0x05 (Lead-In) */
        CddRequest request = Cdd.requestVals[cddContext.commandRegs[3]];
        extraInfo.append(" " + request + "(" + request.ordinal() + ") ");
        if (cddContext.io.track == LEADINNUM || cddContext.io.track == LEADOUTNUM) {
            LogHelper.logWarnOnce(LOG, "{} while in lead-{}, track {}, lba: {}",
                    request, cddContext.io.track == LEADINNUM ? "in" : "out", th(cddContext.io.track), cddContext.io.sector);
//            if (cddContext.io.track == LEADINNUM) {
//                updateStatus(1, CddRequest.NotReady.ordinal());
//                return;
//            }
        }
        switch (request) {
            /* Current Absolute Time (MM:SS:FF) */
            case AbsoluteTime -> {
                CueFileParser.lbaToMsfAdjustPregap(cddContext.io.sector, msfHolder);
                updateStatusesMsf(request.ordinal(), getFlags(), msfHolder);
            }
            /* Current Track Relative Time (MM:SS:FF) */
            case RelativeTime -> {
                if (cddContext.io.track == LEADINNUM) {
                    updateStatus(1, NotReady.ordinal());
                    break;
                }
                //TODO test: relative time in LEAD_OUT, track number becomes 0xAA and relative time keeps
                // increasing from the last track. Thunder Storm FX (Japan)
                int trackNumRef = cddContext.io.track == LEADOUTNUM ? extCueSheet.numTracks : cddContext.io.track;
                CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, trackNumRef);

                /* current track relative time */
                int lba = Math.abs(cddContext.io.sector - etd.absoluteSectorStart);
                CueFileParser.lbaToMsf(lba, msfHolder);
                updateStatusesMsf(request.ordinal(), getFlags(), msfHolder);
            }
            /* Current Track Number */
            case TrackInformation -> {
                /* Disk Control Code (?) in RS6 */
                int rs6 = 0;
                //rs2-rs3
                int tno = cddContext.io.track <= extCueSheet.numTracks ? cddContext.io.track : LEADOUTNUM;
                int rs2 = tno / 10;
                int rs3 = tno % 10;
                updateStatuses(request.ordinal(), rs2, rs3, 0, 0, rs6, 0, getFlags());
            }
            /* Total length (MM:SS:FF), TOCO */
            case DiscCompletionTime -> {
                int lba = hasMedia ? extCueSheet.sectorEnd : 0;
                CueFileParser.lbaToMsfAdjustPregap(lba, msfHolder);
                updateStatusesMsf(request.ordinal(), getFlags(), msfHolder);
            }
            /* First & Last Track Numbers, TOCT */
            case DiscTracks -> {
                int firstTrack = hasMedia ? 1 : 0;
                int lastTrack = hasMedia ? extCueSheet.numTracks : 0;
                /* Drive Version (?) in RS6-RS7 */
                int rs6 = 0, rs7 = 0;
                int rs8 = 0;//getFlags(); //gpgx has 0, TODO bios 2.00 maybe needs it?
                updateStatuses(request.ordinal(), firstTrack / 10, firstTrack % 10,
                        lastTrack / 10, lastTrack % 10, rs6, rs7, rs8);
            }
            /* Track Start Time (MM:SS:FF), TOCN */
            //INDEX 00 -> LBA -150 -> MSF 00:00:00
            //INDEX 01 -> LBA 0    -> MSF 00:02:00
            case TrackStartTime -> {
                int track = cddContext.commandRegs[4] * 10 + cddContext.commandRegs[5];
                extraInfo.append("\n\tTrack: 0x" + th(track));
                CdModel.ExtendedTrackData extTrackData = ExtendedCueSheet.getExtTrack(extCueSheet, track);
                if (extTrackData == CdModel.ExtendedTrackData.NO_TRACK) {
                    updateStatus(1, NotReady.ordinal());
                    updateStatus(8, getFlags());
                    break;
                }
                CueFileParser.MsfHolder msf = extTrackData.trackStartMsf;
                /* RS6 bit 3 is set for CD-ROM track, Track Number (low digit) */
                int status6 = (isAudioTrack(track) ? 0 : 0x8) | (msf.frame / 10);

                updateStatuses(request.ordinal(), msf.minute / 10, msf.minute % 10,
                        msf.second / 10, msf.second % 10, status6, msf.frame % 10, track % 10);
            }
            /* Latest Error Information */
            case ErrorInformation -> {
                updateStatus(0, cddContext.io.status.ordinal());
                /* no error */
                updateStatuses(request.ordinal(), 0, 0, 0, 0, 0, 0, 0);
                assert false;
            }
            default -> {
                assert false;
            }
        }
    }

    private int getFlags() {
        return getFlags(cddContext.io.track);
    }

    private int getFlags(int track) {
        final boolean isAudio = track > 0 ?
                ExtendedCueSheet.isAudioTrack(extCueSheet, track) : true;
        int rs8 = isAudio ? FLAGS_AUDIO : FLAGS_DATA;
        boolean nonMute = isAudio && cddContext.io.status == Playing;
        //leadout shows the absolute time as well
        //TODO mute is linked to D/M bit ??
        //TODO audio with emphasis ??
        rs8 |= FLAGS_iEMPHASIS | (nonMute ? FLAGS_iMUTE : FLAGS_aMUTE);
        return rs8;
    }

    //should be called at 44.1 khz
    @Override
    public void stepCdda() {
        if (cddContext.io.latency > 0) {
//            if(verbose) LOG.info("CDDA nop, still seeking, latency left: {}", cddContext.io.latency);
            return;
        }
        int left = 0, right = 0;
        if (cddContext.io.status == Playing) {
            if (ExtendedCueSheet.isAudioTrack(extCueSheet, cddContext.io.track)) {
                CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track);
                var data = etd.data;
                //CDDA: 2352 audio data (+ 96 subcode, not dumped)
                int sectorSize = etd.trackDataType.size.s_size;
                int trackRelSector = cddContext.io.sector - etd.absoluteSectorStart;
                //if trackRelSector < 0, silence
                if (trackRelSector >= 0) {
                    try {
                        data.seek((sectorSize * trackRelSector) + cddContext.io.sample);
                        left = data.readShortLE();
                        right = data.readShortLE();
                    } catch (Exception e) {
                        LOG.error("Unable to seek to sector: {}", cddContext.io.sector, e);
                    }
                }
                cddContext.io.sample += 4;
                if (cddContext.io.sample >= sectorSize) {
                    advance();
                }
            }
        }
        left = applyFader(left);
        right = applyFader(right);
        stepFader();
        playSupport.playSample(left >> PCM_VOLUME_SHIFT, right >> PCM_VOLUME_SHIFT);
    }

    private void advance() {
        int track = inTrack(cddContext.io.sector + 1);
        //TODO clear DRS
        setBit(memoryContext.getRegBuffer(CpuDeviceAccess.SUB_M68K, MCD_CDD_CONTROL),
                MCD_CDD_CONTROL.addr + 1, 1, 0, Size.BYTE);
        if (track > 0) {
            setTrack(track);
            setSector(cddContext.io.sector + 1);
            cddContext.io.sample = 0;
            return;
        }
        LogHelper.logWarnOnce(LOG, "Check leadOut??");
        setIoStatus(LeadOut);
        setTrack(LEADOUTNUM);
    }

    //fader multiplier, for vol >= 4 use the top 10 bits, for 0..3 use the raw vol value
    // 0,1,2,3,4,8, ...., 1020, 1024

    private int applyFader(int sample) {
        int curVol = cddContext.faderVol;
        int mul = ((curVol & 0x7FC) != 0) ? curVol & 0x7FC : curVol & 3;
        return (sample * mul) >> FADER_MAX_BITS;
    }

    private void stepFader() {
        int cur = cddContext.faderVol, tgt = cddContext.faderTarget;
        if (cur < tgt) {
            cddContext.faderVol = cur + 1;
        } else if (cur > tgt) {
            cddContext.faderVol = cur - 1;
        }
    }

    //reg36 MSB
    private void setDataOrMusicBit(CddControl_DM_bit val) {
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, val.ordinal(), Size.BYTE);
    }

    private int inTrack(int lba) {
        for (CdModel.ExtendedTrackData ext : extCueSheet.extTracks) {
            if (lba >= ext.absoluteSectorStart && lba < ext.absoluteSectorEnd) {
                return ext.trackData.getNumber();
            }
        }
        //this is absolute-time LBA
        if (lba < -CueFileParser.PREGAP_LEN_LBA) {
            LogHelper.logWarnOnce(LOG, "LBA in LEAD-IN: {}", lba);
            return LEADINNUM;
        } else if (lba >= extCueSheet.sectorEnd) {
            LogHelper.logWarnOnce(LOG, "LBA in LEAD-OUT: {}", lba);
            return LEADOUTNUM;
        } else if (lba < 0) {
            LogHelper.logWarnOnce(LOG, "LBA in TRACK1 PREGAP: {}", lba);
            return 1;
        }
        LogHelper.logWarnOnce(LOG, "LBA in OOPS: {}", lba);
        return 0;
    }

    private void setTrack(int track) {
        assert track > 0;
        if (track != cddContext.io.track) {
            if (verbose) LOG.info("Track changed: {} -> {}", cddContext.io.track, track);
            cddContext.io.track = track;
            CddControl_DM_bit bit = ExtendedCueSheet.isAudioTrack(extCueSheet, track) ?
                    CddControl_DM_bit.MUSIC_0 : CddControl_DM_bit.DATA_1;
            setDataOrMusicBit(bit);
        }
    }

    private void setIoStatus(CddStatus status) {
        if (status != cddContext.io.status) {
            if (verbose) LOG.info("Status changed: {} -> {}", cddContext.io.status, status);
            cddContext.io.status = status;
        }
    }

    private void setSector(int s) {
        if (s != cddContext.io.sector) {
            if (verbose) {
                CueFileParser.lbaToMsfAdjustPregap(s, msfHolder);
                if (verbose) LOG.info("Sector changed: {} -> {}, msf: {}", cddContext.io.sector, s, msfHolder);
            }
            cddContext.io.sector = s;
        }
    }

    /**
     * status 1..8, status0 is ignored
     */
    private void updateStatuses(int... vals) {
        assert vals.length == 8;
        for (int i = 1; i < CDD_CHECKSUM_BYTE; i++) {
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
    private void updateStatusesMsf(int status1, int status8, CueFileParser.MsfHolder msfHolder) {
        updateStatuses(status1, msfHolder.minute / 10, msfHolder.minute % 10,
                msfHolder.second / 10, msfHolder.second % 10, msfHolder.frame / 10, msfHolder.frame % 10, status8);
    }

    private void updateStatusesMsfOnly(CueFileParser.MsfHolder msfHolder) {
        updateStatuses(cddContext.statusRegs[1], msfHolder.minute / 10, msfHolder.minute % 10,
                msfHolder.second / 10, msfHolder.second % 10, msfHolder.frame / 10, msfHolder.frame % 10, cddContext.statusRegs[8]);
    }

    private void updateStatus(int pos, int val) {
        cddContext.statusRegs[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM0.addr + pos, val, Size.BYTE);
        if (pos != CDD_CHECKSUM_BYTE) {
            statusChecksum();
//            logStatus(false);
        }
    }

    private void updateCommand(int pos, int val) {
        cddContext.commandRegs[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM5.addr + pos, val, Size.BYTE);
//        logStatus(false);
    }

    private void clearCommandRegs() {
        for (int i = 0; i < 8; i++) {
            updateCommand(i, 0);
        }
        commandChecksum();
    }

    private void clearStatusRegs() {
        for (int i = 0; i < 8; i++) {
            updateStatus(i, 0);
        }
        statusChecksum();
    }

    @Override
    public CddContext getCddContext() {
        return cddContext;
    }

    @Override
    public void updateVideoMode(VideoMode videoMode) {
        playSupport.updateRegion(videoMode.getRegion());
    }

    @Override
    public void close() {
        playSupport.close();
    }

    @Override
    public void reset() {
        playSupport.reset();
    }

    @Override
    public void onNewFrame() {
        playSupport.onNewFrame();
    }
}