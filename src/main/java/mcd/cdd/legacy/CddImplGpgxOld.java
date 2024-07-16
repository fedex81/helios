package mcd.cdd.legacy;

import mcd.bus.McdSubInterruptHandler;
import mcd.cdc.Cdc;
import mcd.cdd.CdModel;
import mcd.cdd.Cdd;
import mcd.cdd.ExtendedCueSheet;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.BlipPcmProvider;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.util.*;
import org.slf4j.Logger;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDD;
import static mcd.cdd.Cdd.CddStatus.*;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.Util.th;

/***
 * CddImplGpgx
 */
class CddImplGpgxOld implements Cdd {

    private final static Logger LOG = LogHelper.getLogger(CddImplGpgxOld.class.getSimpleName());

    private final boolean verbose = true;

    private static final int CD_LATENCY = 1;

    public final CddContext cddContext = CddContext.create(CddIo.create());

    private final MegaCdMemoryContext memoryContext;
    private final McdSubInterruptHandler interruptHandler;
    private final Cdc cdc;
    private final BlipPcmProvider playSupport;
    private ExtendedCueSheet extCueSheet;

    private final CueFileParser.MsfHolder msfHolder = new CueFileParser.MsfHolder();
    private boolean hasMedia;


    public CddImplGpgxOld(MegaCdMemoryContext mc, McdSubInterruptHandler ih, Cdc c) {
        memoryContext = mc;
        interruptHandler = ih;
        cdc = c;
        playSupport = new BlipPcmProvider("CDDA", RegionDetector.Region.USA, 44100);
        setIoStatus(Stopped);
        checksum();
    }

    @Override
    public void tryInsert(ExtendedCueSheet cueSheet) {
        extCueSheet = cueSheet;
        hasMedia = extCueSheet.cueSheet != null;
        if (!hasMedia) {
            setIoStatus(NoDisc);
            return;
        }

        setIoStatus(ReadingTOC);
        setSector(150); //TODO session.leadIn.lba, should be 150 sectors (2 seconds)
        cddContext.io.track = cddContext.io.sample = cddContext.io.tocRead = 0;
        cdc.setMedia(extCueSheet);
        LOG.info("Using disc: {}", extCueSheet);
    }

    @Override
    public void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size) {
        if (verbose) LOG.info("CDD,regW,{},{},{},{}", th(address), th(value), size, regSpec);
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
        if (verbose) LOG.info("CDD,regR,{},{},{},{}", th(address), th(res), size, regSpec);
        return res;
    }


    private void writeCommByte(MegaCdDict.RegSpecMcd regSpec, int addr, int value) {
        int index = (addr & MDC_SUB_GATE_REGS_MASK) - MCD_CDD_COMM5.addr;
        updateCommand(index, value & 0xF);
        switch (regSpec) {
            case MCD_CDD_COMM9 -> {
                //Transmission Command 9
                if ((addr & 1) == 1) { //unconfirmed
                    cdd_process();
                }
            }
        }
    }

    private void checksum() {
        int checksum = 0;
        for (int i = 0; i < cddContext.commandRegs.length - 1; i++) {
            checksum += cddContext.statusRegs[i];
        }
        checksum = ~checksum;
        updateStatus(9, checksum & 0xF);
        //TODO check 0x40,it can be 1 -> 0x103 != 0x3
        assert (readBuffer(memoryContext.commonGateRegsBuf, MCD_CDD_COMM4.addr, Size.WORD) & 0xFF) == (checksum & 0xF);
    }

    private boolean valid() {
        int checksum = 0;
        for (int i = 0; i < cddContext.commandRegs.length - 1; i++) {
            checksum += cddContext.commandRegs[i];
        }
        checksum = ~checksum;
        updateCommand(9, checksum & 0xF);
        return (checksum & 0xF) == cddContext.commandRegs[9];
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
        String stat = "XXCDD," + statusString(cddContext.statusRegs, cddContext.commandRegs) + (process ? " <-" : "");
        if (!stat.equals(prev)) {
            if (verbose) System.out.println(stat);
            prev = stat;
        }
    }


    /**
     * this should be called at 75hz
     */
    public void step(int cycles) {
        logStatus(false);
        if (cddContext.hostClockEnable == 0) {
            return;
        }
        interruptHandler.raiseInterrupt(INT_CDD);
        /* drive latency */
        if (cddContext.io.latency > 0) {
            cddContext.io.latency--;
            return;
        }

        /* reading disc */
        if (cddContext.io.status == Playing) {
            /* end of disc detection */
            if (cddContext.io.sector >= extCueSheet.sectorEnd) { //cdd.toc.last){
                setIoStatus(LeadOut);
                return;
            }

            /* subcode data processing */
            //no subcode file
//            if (cdd.toc.sub) {
//                cdd_read_subcode();
//            }
            /* track type */
            CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track);
            boolean isDataTrack = etd.trackDataType != CdModel.TrackDataType.AUDIO;
            if (isDataTrack) {
                /* CD-ROM sector header */
                int[] header = new int[4];
                int msf = cddContext.io.sector + 150;
                CueFileParser.toMSF(msf, msfHolder);
                header[0] = msfHolder.minute;
                header[1] = msfHolder.second;
                header[2] = msfHolder.frame;
                header[3] = 1;

                /* decode CD-ROM track sector */
                cdc.cdc_decoder_update(cddContext.io.sector);
            } else {
                /* check against audio track start index */
                if (cddContext.io.sector >= etd.absoluteSectorStart) {
                    /* audio track playing */
                    writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 0, Size.BYTE);
//                    scd.regs[0x36>>1].byte.h = 0x00;
                }

                /* audio blocks are still sent to CDC as well as CD DAC/Fader */
                cdc.cdc_decoder_update(cddContext.io.sector);
//                cdc_decoder_update(0); //TODO
            }
            setSector(cddContext.io.sector + 1);
        } else if (cddContext.io.status == Scanning) {
            assert false;
        }
    }

    void cdd_process() {
        /* Process CDD command */
        CddCommand cddCommand = CddCommand.values()[cddContext.commandRegs[0]];
        if (verbose) LOG.info("CDD {}({})", cddCommand, cddCommand.ordinal());
        final boolean isAudio = cddContext.io.track > 0 ?
                ExtendedCueSheet.isAudioTrack(extCueSheet, cddContext.io.track) : false;
        switch (cddCommand.ordinal()) {
            case 0x00:  /* Get Drive Status */ {
                /* RS0-RS1 are normally unchanged unless reported drive status needs to be updated (i.e previous drive command has been processed) */
                /* Note: this function is called one 75hz frame ahead of CDD update so latency counter is always one step ahead of upcoming status */
                /* Radical Rex and Annet Futatabi both need at least respectively 2 and 3 interrupts with 'playing' status returned before sectors start getting incremented */
                if (cddContext.io.latency <= 3) {
                    /* update reported drive status */
                    updateStatus(0, cddContext.io.status.ordinal());
//                    scd.regs[0x38>>1].byte.h = cdd.status;

                    /* check if RS1 indicated invalid track infos (during seeking) */
//                    if (scd.regs[0x38>>1].byte.l == 0x0f)
                    if (cddContext.statusRegs[1] == 0xF) {
                        /* seeking has ended so we return valid track infos,
                        e.g current absolute time by default (fixes Lunar - The Silver Star) */
                        int lba = cddContext.io.sector + 150;
                        CueFileParser.toMSF(lba, msfHolder);
                        /* Current block flags in RS8 (bit0 = mute status, bit1: pre-emphasis status, bit2: track type) */
                        updateStatusesMsf(0, (isAudio ? 0 : 1) << 2, msfHolder);
                    }
                    /* otherwise, check if RS2-RS8 need to be updated */
                    else if (cddContext.statusRegs[1] == 0x00) {
                        /* current absolute time */
                        int lba = cddContext.io.sector + 150;
                        CueFileParser.toMSF(lba, msfHolder);
                        updateStatusesMsf(cddContext.statusRegs[1], (isAudio ? 0 : 1) << 2, msfHolder);
                    } else if (cddContext.statusRegs[1] == 0x01) {
                        CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track);
                        /* current track relative time */
                        int lba = Math.abs(cddContext.io.sector - etd.absoluteSectorStart);
                        CueFileParser.toMSF(lba, msfHolder);
                        updateStatusesMsf(cddContext.statusRegs[1], (isAudio ? 0 : 1) << 2, msfHolder);
                    } else if (cddContext.statusRegs[1] == 0x02) {
                        /* current track number */
//                        scd.regs[0x3a>>1].w = (cdd.index < cdd.toc.last) ? lut_BCD_16[cdd.index + 1] : 0x0A0A;
                        updateStatus(2, cddContext.io.track / 10);
                        updateStatus(3, cddContext.io.track % 10);
                    }
                }
                break;
            }

            case 0x01:  /* Stop Drive */ {
                /* update status */
                setIoStatus(hasMedia ? ReadingTOC : NoDisc);
//                cdd.status = cdd.loaded ? CD_TOC : NO_DISC;

                /* no audio track playing */
                writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 1, Size.BYTE);
//                scd.regs[0x36>>1].byte.h = 0x01;

                /* RS1-RS8 ignored, expects 0x0 (CD_STOP) in RS0 once */
                updateStatus(0, Stopped.ordinal());
                updateStatuses(0, 0, 0, 0, 0, 0, 0, 0);
//                scd.regs[0x38>>1].w = Stopped.ordinal() << 8;
//                scd.regs[0x3a>>1].w = 0x0000;
//                scd.regs[0x3c>>1].w = 0x0000;
//                scd.regs[0x3e>>1].w = 0x0000;
//                scd.regs[0x40>>1].w = ~CD_STOP & 0x0f;
                return;
            }

            case 0x02:  /* Report TOC infos */ {
                handleRequestCommand(isAudio);
                break;
            }

            case 0x03:  /* Play */ {
                /* reset track index */
                int index = 0;

                /* new LBA position */
                int lba = CueFileParser.toSector(cddContext.commandRegs[2], cddContext.commandRegs[3],
                        cddContext.commandRegs[4], cddContext.commandRegs[5], cddContext.commandRegs[6], cddContext.commandRegs[7]) - 150;
//                    ((scd.regs[0x44>>1].byte.h * 10 + scd.regs[0x44>>1].byte.l) * 60 +
//                    (scd.regs[0x46>>1].byte.h * 10 + scd.regs[0x46>>1].byte.l)) * 75 +
//                    (scd.regs[0x48>>1].byte.h * 10 + scd.regs[0x48>>1].byte.l) - 150;

                /* CD drive latency */
                if (cddContext.io.latency == 0) {
                    /* Fixes a few games hanging because they expect data to be read with some delay */
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
                if (lba > cddContext.io.sector) {
                    cddContext.io.latency += (((lba - cddContext.io.sector) * 120 * CD_LATENCY) / 270000);
                } else {
                    cddContext.io.latency += (((cddContext.io.sector - lba) * 120 * CD_LATENCY) / 270000);
                }

                /* update current LBA */
                cddContext.io.sector = lba;

                /* get track index */
                index = inTrack(cddContext.io.sector);
                CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, index);
                /* audio track ? */
                if (ExtendedCueSheet.isAudioTrack(extCueSheet, index)) {
                    if (lba < etd.absoluteSectorStart) {
                        setSector(etd.absoluteSectorStart);
                    }
//                    /* stay within track limits when seeking files */
//                    if (lba < cdd.toc.tracks[index].start)
//                    {
//                        lba = cdd.toc.tracks[index].start;
//                    }
//
//                    /* seek to current track sector */
//                    cdd_seek_audio(index, lba);
                }

                /* update current track index */
                setTrack(index);

                /* seek to current subcode position */
//                if (cdd.toc.sub)
//                {
//                    cdStreamSeek(cdd.toc.sub, lba * 96, SEEK_SET);
//                }

                /* no audio track playing (yet) */
//                scd.regs[0x36>>1].byte.h = 0x01;
                writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 1, Size.BYTE);

                /* update status (reported to host once seeking has ended) */
                setIoStatus(Playing);
//                cdd.status = CD_PLAY;

                /* RS0 should indicates seeking until drive is ready (fixes audio delay in Bari Arm) */
                /* RS1=0xf to invalidate track infos in RS2-RS8 until drive is ready (fixes Snatcher Act 2 start cutscene) */
                updateStatus(0, Seeking.ordinal());
                updateStatuses(0xF, 0, 0, 0, 0, 0, 0, 0);
//                scd.regs[0x38>>1].w = (CD_SEEK << 8) | 0x0f;
//                scd.regs[0x3a>>1].w = 0x0000;
//                scd.regs[0x3c>>1].w = 0x0000;
//                scd.regs[0x3e>>1].w = 0x0000;
//                scd.regs[0x40>>1].w = ~(CD_SEEK + 0xf) & 0x0f;
                return;
            }

            case 0x04:  /* Seek */ {
                /* reset track index */
                int index = 0;

                /* new LBA position */
                int lba = CueFileParser.toSector(cddContext.commandRegs[2], cddContext.commandRegs[3],
                        cddContext.commandRegs[4], cddContext.commandRegs[5], cddContext.commandRegs[6], cddContext.commandRegs[7]) - 150;

                /* CD drive seek time  */
                /* We are using similar linear model as above, although still not exactly accurate, */
                /* it works fine for Switch/Panic! intro (Switch needs at least 30 interrupts while */
                /* seeking from 00:05:63 to 24:03:19, Panic! when seeking from 00:05:60 to 24:06:07) */
                if (lba > cddContext.io.sector) {
                    cddContext.io.latency = ((lba - cddContext.io.sector) * 120 * CD_LATENCY) / 270000;
                } else {
                    cddContext.io.latency = ((cddContext.io.sector - lba) * 120 * CD_LATENCY) / 270000;
                }

                /* update current LBA */
                cddContext.io.sector = lba;

                /* get current track index */
                index = inTrack(cddContext.io.sector);
                CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, index);

                /* audio track ? */
                if (ExtendedCueSheet.isAudioTrack(extCueSheet, index)) {
//                    /* stay within track limits when seeking files */
//                    if (lba < cdd.toc.tracks[index].start)
                    if (lba < etd.absoluteSectorStart) {
                        setSector(etd.absoluteSectorStart);
                    }
                    //TODO what about seeking??
//                    if (lba < cdd.toc.tracks[index].start)
//                    {
//                        lba = cdd.toc.tracks[index].start;
//                    }
//
//                    /* seek to current track sector */
//                    cdd_seek_audio(index, lba);
                }

                /* update current track index */
                setTrack(index);

                /* seek to current subcode position */
//                if (cdd.toc.sub)
//                {
//                    cdStreamSeek(cdd.toc.sub, lba * 96, SEEK_SET);
//                }

                /* no audio track playing */
                writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 1, Size.BYTE);

                /* update status (reported to host once seeking has ended) */
//                cdd.status = CD_PAUSE;
                setIoStatus(Paused);
                /* RS1=0xf to invalidate track infos in RS2-RS8 while seeking (fixes Final Fight CD intro when seek time is emulated) */
                updateStatus(0, Seeking.ordinal());
                updateStatuses(0xF, 0, 0, 0, 0, 0, 0, 0);
//                scd.regs[0x38>>1].w = (CD_SEEK << 8) | 0x0f;
//                scd.regs[0x3a>>1].w = 0x0000;
//                scd.regs[0x3c>>1].w = 0x0000;
//                scd.regs[0x3e>>1].w = 0x0000;
//                scd.regs[0x40>>1].w = ~(CD_SEEK + 0xf) & 0x0f;
                return;
            }

            case 0x06:  /* Pause */ {
                /* no audio track playing */
                writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 1, Size.BYTE);
//                scd.regs[0x36>>1].byte.h = 0x01;

                /* update status (RS1-RS8 unchanged) */
                setIoStatus(Paused);
                updateStatus(0, Paused.ordinal());
//                cdd.status = scd.regs[0x38>>1].byte.h = CD_PAUSE;
                break;
            }

            case 0x07:  /* Resume */ {
                /* update status (RS1-RS8 unchanged) */
                setIoStatus(Playing);
                updateStatus(0, Playing.ordinal());
//                cdd.status = scd.regs[0x38>>1].byte.h = CD_PLAY;
                break;
            }

            case 0x08:  /* Forward Scan */ {
                assert false;
                /* reset scanning direction / speed */
//                cdd.scanOffset = CD_SCAN_SPEED;

                /* update status (RS1-RS8 unchanged) */
//                cdd.status = scd.regs[0x38>>1].byte.h = CD_SCAN;
                setIoStatus(Scanning);
                updateStatus(0, Scanning.ordinal());
                break;
            }

            case 0x09:  /* Rewind Scan */ {
                assert false;
//                /* reset scanning direction / speed */
//                cdd.scanOffset = -CD_SCAN_SPEED;
//
//                /* update status (RS1-RS8 unchanged) */
//                cdd.status = scd.regs[0x38>>1].byte.h = CD_SCAN;
                setIoStatus(Scanning);
                updateStatus(0, Scanning.ordinal());
                break;
            }

            case 0x0a:  /* N-Track Jump Control ? (usually sent before CD_SEEK or CD_PLAY commands) */ {
                /* TC3 corresponds to seek direction (00=forward, FF=reverse) */
                /* TC4-TC7 are related to seek length (4x4 bits i.e parameter values are between -65535 and +65535) */
                /* Maybe related to number of auto-sequenced track jumps/moves for CD DSP (cf. CXD2500BQ datasheet) */
                /* also see US Patent nr. 5222054 for a detailled description of seeking operation using Track Jump */

                /* no audio track playing */
//                scd.regs[0x36>>1].byte.h = 0x01;
                writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 1, Size.BYTE);

                /* update status (RS1-RS8 unchanged) */
//                cdd.status = scd.regs[0x38>>1].byte.h = CD_PAUSE;
                setIoStatus(Paused);
                updateStatus(0, Paused.ordinal());
                break;
            }

            case 0x0c:  /* Close Tray */ {
                /* no audio track playing */
//                scd.regs[0x36>>1].byte.h = 0x01;
                writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 1, Size.BYTE);

                /* update status */
//                cdd.status = cdd.loaded ? CD_TOC : NO_DISC;
                setIoStatus(hasMedia ? ReadingTOC : NoDisc);
                updateStatus(0, Stopped.ordinal());
                updateStatuses(0, 0, 0, 0, 0, 0, 0, 0);
                /* RS1-RS8 ignored, expects CD_STOP in RS0 once */
//                scd.regs[0x38>>1].w = CD_STOP << 8;
//                scd.regs[0x3a>>1].w = 0x0000;
//                scd.regs[0x3c>>1].w = 0x0000;
//                scd.regs[0x3e>>1].w = 0x0000;
//                scd.regs[0x40>>1].w = ~CD_STOP & 0x0f;
                return;
            }

            case 0x0d:  /* Open Tray */ {
                /* no audio track playing */
                writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 1, Size.BYTE);
//                scd.regs[0x36>>1].byte.h = 0x01;

                /* update status (RS1-RS8 ignored) */
                updateStatus(0, DoorOpened.ordinal());
                updateStatuses(0, 0, 0, 0, 0, 0, 0, 0);
//                cdd.status = CD_OPEN;
//                scd.regs[0x38>>1].w = CD_OPEN << 8;
//                scd.regs[0x3a>>1].w = 0x0000;
//                scd.regs[0x3c>>1].w = 0x0000;
//                scd.regs[0x3e>>1].w = 0x0000;
//                scd.regs[0x40>>1].w = ~CD_OPEN & 0x0f;
                return;
            }

            default:  /* Unknown command */
//#ifdef LOG_ERROR
//                error("Unsupported CDD command %02X (%X)\n", scd.regs[0x42>>1].byte.h & 0x0f, s68k.pc);
//#endif
//                scd.regs[0x38>>1].byte.h = cdd.status;
                assert false;
                updateStatus(0, cddContext.io.status.ordinal());
                break;
        }

        /* only compute checksum when necessary */
        checksum();
//        scd.regs[0x40>>1].byte.l = ~(scd.regs[0x38>>1].byte.h + scd.regs[0x38>>1].byte.l +
//            scd.regs[0x3a>>1].byte.h + scd.regs[0x3a>>1].byte.l +
//            scd.regs[0x3c>>1].byte.h + scd.regs[0x3c>>1].byte.l +
//            scd.regs[0x3e>>1].byte.h + scd.regs[0x3e>>1].byte.l +
//            scd.regs[0x40>>1].byte.h) & 0x0f;
    }

    private void handleRequestCommand(boolean isAudio) {
        /* Infos automatically retrieved by CDD processor from Q-Channel */
        /* commands 0x00-0x02 (current block) and 0x03-0x05 (Lead-In) */
        int cmd3 = cddContext.commandRegs[3];
        switch (cmd3) {
            case 0x00:  /* Current Absolute Time (MM:SS:FF) */ {
                int lba = cddContext.io.sector + 150;
                CueFileParser.toMSF(lba, msfHolder);
                updateStatusesMsf(cddContext.statusRegs[1], (isAudio ? 0 : 1) << 2, msfHolder);
                updateStatus(0, cddContext.io.status.ordinal());
//                        scd.regs[0x38>>1].w = cdd.status << 8;
//                        scd.regs[0x3a>>1].w = lut_BCD_16[(lba/75)/60];
//                        scd.regs[0x3c>>1].w = lut_BCD_16[(lba/75)%60];
//                        scd.regs[0x3e>>1].w = lut_BCD_16[(lba%75)];
//                        scd.regs[0x40>>1].byte.h = cdd.toc.tracks[cdd.index].type ? 0x04 : 0x00; /* Current block flags in RS8 (bit0 = mute status, bit1: pre-emphasis status, bit2: track type) */
                break;
            }

            case 0x01:  /* Current Track Relative Time (MM:SS:FF) */ {
                CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track);
                /* current track relative time */
                int lba = Math.abs(cddContext.io.sector - etd.absoluteSectorStart);
                CueFileParser.toMSF(lba, msfHolder);
                updateStatus(0, cddContext.io.status.ordinal());
                updateStatusesMsf(1, (isAudio ? 0 : 1) << 2, msfHolder);
//                        scd.regs[0x38>>1].w = (cdd.status << 8) | 0x01;
//                        scd.regs[0x3a>>1].w = lut_BCD_16[(lba/75)/60];
//                        scd.regs[0x3c>>1].w = lut_BCD_16[(lba/75)%60];
//                        scd.regs[0x3e>>1].w = lut_BCD_16[(lba%75)];
//                        scd.regs[0x40>>1].byte.h = cdd.toc.tracks[cdd.index].type ? 0x04 : 0x00; /* Current block flags in RS8 (bit0 = mute status, bit1: pre-emphasis status, bit2: track type) */
                break;
            }

            case 0x02:  /* Current Track Number */ {
                updateStatus(0, cddContext.io.status.ordinal());
                int rs6 = 0; /* Disk Control Code (?) in RS6 */
                updateStatuses(2, cddContext.io.track / 10, cddContext.io.track % 10, 0, 0, rs6, 0, 0);
//                        scd.regs[0x38>>1].w = (cdd.status << 8) | 0x02;
//                        scd.regs[0x3a>>1].w = (cdd.index < cdd.toc.last) ? lut_BCD_16[cdd.index + 1] : 0x0A0A;
//                        scd.regs[0x3c>>1].w = 0x0000;
//                        scd.regs[0x3e>>1].w = 0x0000; /* Disk Control Code (?) in RS6 */
//                        scd.regs[0x40>>1].byte.h = 0x00;
                break;
            }

            case 0x03:  /* Total length (MM:SS:FF) */ {
                int lba = extCueSheet.sectorEnd + 150;
                CueFileParser.toMSF(lba, msfHolder);
                updateStatus(0, cddContext.io.status.ordinal());
                updateStatusesMsf(3, 0, msfHolder);
//                        scd.regs[0x38>>1].w = (cdd.status << 8) | 0x03;
//                        scd.regs[0x3a>>1].w = lut_BCD_16[(lba/75)/60];
//                        scd.regs[0x3c>>1].w = lut_BCD_16[(lba/75)%60];
//                        scd.regs[0x3e>>1].w = lut_BCD_16[(lba%75)];
//                        scd.regs[0x40>>1].byte.h = 0x00;
                break;
            }

            case 0x04:  /* First & Last Track Numbers */ {
                setIoStatus(cddContext.io.status);
                int firstTrack = 1;
                int lastTrack = extCueSheet.numTracks;
                updateStatus(0, cddContext.io.status.ordinal());
                updateStatuses(4, firstTrack / 10, firstTrack % 10,
                        lastTrack / 10, lastTrack % 10, 0, 0, 0);
//                        scd.regs[0x38>>1].w = (cdd.status << 8) | 0x04;
//                        scd.regs[0x3a>>1].w = 0x0001;
//                        scd.regs[0x3c>>1].w = lut_BCD_16[cdd.toc.last];
//                        scd.regs[0x3e>>1].w = 0x0000; /* Drive Version (?) in RS6-RS7 */
//                        scd.regs[0x40>>1].byte.h = 0x00;  /* Lead-In flags in RS8 (bit0 = mute status, bit1: pre-emphasis status, bit2: track type) */
                break;
            }

            case 0x05:  /* Track Start Time (MM:SS:FF) */ {
                int track = cddContext.commandRegs[4] * 10 + cddContext.commandRegs[5];
//                        extraInfo += "Track: 0x" + th(track);
                CdModel.ExtendedTrackData extTrackData = ExtendedCueSheet.getExtTrack(extCueSheet, track);
                CueFileParser.toMSF(extTrackData.absoluteSectorStart + 150, msfHolder);
                /* RS6 bit 3 is set for CD-ROM track */
                int status6 = ((isAudio ? 0 : 1) << 3) | (msfHolder.frame / 10);
                /* Track Number (low digit) */
                updateStatus(0, cddContext.io.status.ordinal());
                updateStatuses(cddContext.commandRegs[3], msfHolder.minute / 10, msfHolder.minute % 10,
                        msfHolder.second / 10, msfHolder.second % 10, status6, msfHolder.frame % 10, track % 10);
//                        int track = scd.regs[0x46>>1].byte.h * 10 + scd.regs[0x46>>1].byte.l;
//                        int lba = cdd.toc.tracks[track-1].start + 150;
//                        scd.regs[0x38>>1].w = (cdd.status << 8) | 0x05;
//                        scd.regs[0x3a>>1].w = lut_BCD_16[(lba/75)/60];
//                        scd.regs[0x3c>>1].w = lut_BCD_16[(lba/75)%60];
//                        scd.regs[0x3e>>1].w = lut_BCD_16[(lba%75)];
//                        scd.regs[0x3e>>1].byte.h |= cdd.toc.tracks[track-1].type ? 0x08 : 0x00; /* RS6 bit 3 is set for CD-ROM track */
//                        scd.regs[0x40>>1].byte.h = track % 10;  /* Track Number (low digit) */
                break;
            }

            case 0x06:  /* Latest Error Information */ {
                updateStatus(0, cddContext.io.status.ordinal());
                updateStatuses(6, 0, 0, 0, 0, 0, 0, 0);
//                        scd.regs[0x38>>1].w = (cdd.status << 8) | 0x06;
//                        scd.regs[0x3a>>1].w = 0x0000; /* no error */
//                        scd.regs[0x3c>>1].w = 0x0000;
//                        scd.regs[0x3e>>1].w = 0x0000;
//                        scd.regs[0x40>>1].byte.h = 0x00;
                break;
            }
        }
    }

    //should be called at 44.1 khz
    @Override
    public void stepCdda() {
        int left = 0, right = 0;
        if (cddContext.io.status == Playing) {
            if (ExtendedCueSheet.isAudioTrack(extCueSheet, cddContext.io.track)) {
                CdModel.ExtendedTrackData etd = ExtendedCueSheet.getExtTrack(extCueSheet, cddContext.io.track);
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
        //TODO clear DRS
        setBit(memoryContext.getRegBuffer(CpuDeviceAccess.SUB_M68K, MCD_CDD_CONTROL),
                MCD_CDD_CONTROL.addr + 1, 1, 0, Size.BYTE);
        if (track > 0) {
            setTrack(track);
            setSector(cddContext.io.sector + 1);
            cddContext.io.sample = 0;
            return;
        }
        setIoStatus(LeadOut);
        setTrack(0xAA);
    }

    private int inTrack(int lba) {
        for (CdModel.ExtendedTrackData ext : extCueSheet.extTracks) {
            if (lba >= ext.absoluteSectorStart && lba <= ext.absoluteSectorEnd) {
                return ext.trackData.getNumber();
            }
        }
        assert lba < 0;
        return 1;
    }

    private void updateTrackIfLegal() {
        int trackNow = inTrack(cddContext.io.sector);
        if (trackNow > 0) {
            setTrack(trackNow);
        }
    }

    private void setTrack(int track) {
        assert track > 0;
        if (track != cddContext.io.track) {
            if (verbose) LOG.info("Track changed: {} -> {}", cddContext.io.track, track);
            cddContext.io.track = track;
            //set D/M bit: 0 = MUSIC, 1 = DATA
            int val = ExtendedCueSheet.isAudioTrack(extCueSheet, track) ? 0 : 1;
            assert memoryContext.getRegBuffer(CpuDeviceAccess.SUB_M68K, MCD_CDD_CONTROL) == memoryContext.commonGateRegsBuf;
            setBit(memoryContext.commonGateRegsBuf, MCD_CDD_CONTROL.addr, 8, val, Size.WORD);
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
            if (verbose) LOG.info("Sector changed: {} -> {}", cddContext.io.sector, s);
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
    private void updateStatusesMsf(int status1, int status8, CueFileParser.MsfHolder msfHolder) {
        updateStatuses(status1, msfHolder.minute / 10, msfHolder.minute % 10,
                msfHolder.second / 10, msfHolder.second % 10, msfHolder.frame / 10, msfHolder.frame % 10, status8);
    }

    private void updateStatus(int pos, int val) {
        cddContext.statusRegs[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM0.addr + pos, val, Size.BYTE);
        if (pos != 9) {
            checksum();
            logStatus(false);
        }
    }

    private void updateCommand(int pos, int val) {
        cddContext.commandRegs[pos] = val;
        writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_CDD_COMM5.addr + pos, val, Size.BYTE);
        logStatus(false);
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