package mcd.cdc;

import mcd.bus.McdSubInterruptHandler;
import mcd.cdd.CdModel;
import mcd.cdd.CdModel.ExtendedTrackData;
import mcd.cdd.ExtendedCueSheet;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.sound.msumd.CueFileParser.MsfHolder;
import omegadrive.system.SysUtil.RomFileType;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDC;
import static mcd.cdc.CdcModel.*;
import static mcd.cdd.CdModel.SECTOR_2352;
import static mcd.cdd.CdModel.SectorSize.S_2048;
import static mcd.cdd.CdModel.SectorSize.S_2352;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_CDC_MODE;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_STOPWATCH;
import static mcd.util.McdRegBitUtil.getInvertedBitFromByte;
import static omegadrive.sound.msumd.CueFileParser.toBcdByte;
import static omegadrive.util.ArrayEndianUtil.getByteInWordBE;
import static omegadrive.util.ArrayEndianUtil.setByteInWordBE;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;
import static omegadrive.util.Util.getBitFromByte;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CdcImpl implements Cdc {

    private final static Logger LOG = LogHelper.getLogger(CdcImpl.class.getSimpleName());

    protected static final boolean verbose = false;
    private final MegaCdMemoryContext memoryContext;
    private final McdSubInterruptHandler interruptHandler;
    private final CdcContext cdcContext;

    private final MsfHolder msfHolder = new MsfHolder();

    private ExtendedTrackData track01;
    private ExtendedCueSheet cueSheet;

    private ByteBuffer ram;

    private CdcTransfer transfer;
    private CdcTransferHelper transferHelper;

    private boolean hasMedia;

    public CdcImpl(MegaCdMemoryContext mc, McdSubInterruptHandler ih) {
        memoryContext = mc;
        interruptHandler = ih;
        cdcContext = new CdcContext();
        transfer = cdcContext.transfer;
        ram = ByteBuffer.allocate(RAM_SIZE); //16 Kbytes
        transferHelper = new CdcTransferHelper(this, memoryContext, ram);
    }

    @Override
    public void write(MegaCdDict.RegSpecMcd regSpec, int address, int value, Size size) {
        ByteBuffer regBuffer = memoryContext.getRegBuffer(SUB_M68K, regSpec);
        if (verbose) LOG.info("CDC,regW,{},{},{},{}", th(address), th(value), size, regSpec);
        switch (regSpec) {
            case MCD_CDC_REG_DATA -> {
                //mcd-ver writes WORD
                writeBufferRaw(regBuffer, address & MDC_SUB_GATE_REGS_MASK, value, size);
                //word writes MSB is ignored??
                controllerWrite((byte) value);
            }
            case MCD_CDC_MODE -> {
                int resWord = memoryContext.handleRegWrite(SUB_M68K, regSpec, address, value, size);
                cdcContext.address = resWord & NUM_CDC_REG_MASK;
                transfer.destination = (resWord >> 8) & 7;
                transfer.completed = (resWord >> 15) & 1;
                transfer.ready = (resWord >> 14) & 1;
            }
            case MCD_CDC_DMA_ADDRESS -> {
                assert size == Size.WORD;
                value <<= 3;
                //[0-15] maps to [3-18]
                writeBufferRaw(regBuffer, address & MDC_SUB_GATE_REGS_MASK, value, size);
                transfer.address = value;
            }
            case MCD_CDC_HOST -> {
                LOG.error("CDC write {} not supported: {} {}", regSpec, th(value), size);
                assert false;
            }
            case MCD_STOPWATCH -> {
                assert size == Size.WORD;
                //mcd-ver writes != 0
                cdcContext.stopwatch = 0;
                setTimerBuffer();
            }
            default -> {
                LOG.error("CDC unknown register {} write: {} {}", regSpec, th(value), size);
                assert false;
            }
        }
    }

    @Override
    public int read(MegaCdDict.RegSpecMcd regSpec, int address, Size size) {
        int res = switch (regSpec) {
            case MCD_CDC_MODE -> {
                int res2 = readBuffer(memoryContext.getRegBuffer(BufferUtil.CpuDeviceAccess.SUB_M68K, regSpec),
                        address & MDC_SUB_GATE_REGS_MASK, size);
                //according to mcd-ver, reading DSR,EDT resets them to 0 (only for DMA transfers?)
                if (transfer.destination != 2 && transfer.destination != 3) {
                    transfer.completed = 0;
                    transfer.ready = 0;
                    updateCdcMode4();
                }
                yield res2;
            }
            case MCD_CDC_REG_DATA -> {
                //word reads only populate LSB??
                //mcd_ver, reads odd byte, word
                assert (address & 1) == 0 ? size == Size.WORD : true;
                yield controllerRead();
            }
            case MCD_CDC_HOST -> {
                assert size == Size.WORD; //bios_us word
                yield transferHelper.read();
            }
            case MCD_CDC_DMA_ADDRESS -> {
                assert false;
                yield transfer.address >> 3; // map [3,18] to [0-15]
            }
            case MCD_STOPWATCH -> {
                assert false;
                yield cdcContext.stopwatch;
            }
            default -> {
                assert false;
                yield size.getMask();
            }
        };
        if (verbose) LOG.info("CDC,regR,{},{},{},{}", th(address), th(res), size, regSpec);
        return res;
    }

    @Override
    public void setMedia(ExtendedCueSheet extCueSheet) {
        cueSheet = extCueSheet;
        track01 = cueSheet.extTracks.get(0);
        hasMedia = true;
        assert track01 != null && track01 != ExtendedTrackData.NO_TRACK;
    }

    private int controllerRead() {
        int data = 0;
        if (cdcContext.address > CdcAddressRead.STAT3.ordinal()) {
            increaseCdcAddress();
            return 0xFF;
        }
        CdcAddressRead addressRead = cdcAddrReadVals[cdcContext.address];
        switch (addressRead) {
            //COMIN: command input
            case COMIN -> {
                //do nothing
                data = 0xFF;
            }
            //IFSTAT: interface status
            case IFSTAT -> {
                CdcContext c = cdcContext;
                data = (~c.status.active & 1) |
                        ((~c.status.active & 1) << 1) |
                        ((~c.status.busy & 1) << 2) |
                        ((~c.transfer.busy & 1) << 3) |
                        (1 << 4) |
                        ((~c.irq.decoder.pending & 1) << 5) |
                        ((~c.irq.transfer.pending & 1) << 6);
//                       | ((~c.irq.command.pending & 1) << 7); //unused
            }
            //DBCL: data byte counter low
            case DBCL -> data = getByteInWordBE(transfer.length, 1);
            //DBCH: data byte counter high
            case DBCH -> data = getByteInWordBE(transfer.length, 0);
            //HEAD0: header or subheader data
            case HEAD0 -> {
                if (cdcContext.control.head == 0) {
                    data = cdcContext.header.minute;
                } else {
                    assert false;
                    data = 0;
                }
            }
            //HEAD1: header or subheader data
            case HEAD1 -> {
                if (cdcContext.control.head == 0) {
                    data = cdcContext.header.second;
                } else {
                    assert false;
                    data = 0;
                }
            }
            //HEAD2: header or subheader data
            case HEAD2 -> {
                if (cdcContext.control.head == 0) {
                    data = cdcContext.header.frame;
                } else {
                    assert false;
                    data = 0;
                }
            }
            //HEAD3: header or subheader data
            case HEAD3 -> {
                if (cdcContext.control.head == 0) {
                    data = cdcContext.header.mode;
                } else {
                    assert false;
                    data = 0;
                }
            }
            //PTL: block pointer low
            case PTL -> data = getByteInWordBE(transfer.pointer, 1);
            //PTH: block pointer high
            case PTH -> data = getByteInWordBE(transfer.pointer, 0);
            //WAL: write address low
            case WAL -> data = getByteInWordBE(transfer.target, 1);
            //WAH: write address high
            case WAH -> data = getByteInWordBE(transfer.target, 0);

            //STAT0: status 0
            case STAT0 -> {
                //CRCOK: CRC check OK (set only when decoder is enabled)
                data = cdcContext.decoder.enable << 7;
            }
            //STAT1: status 1
            //reports error when reading header or subheader data from blocks
            case STAT1 -> data = 0;
            //STAT2: status 2
            //RFORMx and RMODx are undocumented even across other ICs that emulate the Sanyo interface
            case STAT2 -> {
                //FORM (also referred to as NOCOR), MODE
                data = (cdcContext.decoder.mode << 3) | (cdcContext.decoder.form << 2);
            }
            //STAT3: status 3
            case STAT3 -> {
                //!VALST: valid status
                data = (~cdcContext.decoder.valid & 1) << 7;
                cdcContext.decoder.valid = 0;            //note: not accurate, supposedly
                cdcContext.irq.decoder.pending = 0;
                poll();
            }
            default -> {
                LOG.error("CDC READ unknown address: {}", th(cdcContext.address));
//                assert false; //TODO mcd-ver reads all the regs
            }
        }
        if (verbose) LOG.info("CDC,R,{}({}),{}", addressRead, th(cdcContext.address), th(data));
        //COMIN reads do not increment the address; STAT3 reads wrap the address to 0x0
        increaseCdcAddress();
        return data;
    }

    private void controllerWrite(byte data) {
        if (cdcContext.address > CdcAddressWrite.RESET.ordinal()) {
            increaseCdcAddress();
            return;
        }
        CdcAddressWrite addressWrite = cdcAddrWriteVals[cdcContext.address];
        if (verbose) LOG.info("CDC,W,{}({}),{}", addressWrite, th(cdcContext.address), th(data));
        switch (addressWrite) {
            //SBOUT: status byte output
            case SBOUT -> {
                //mcd-ver, ignore
                LOG.info("ignored write: {}", addressWrite);
            }
            case IFCTRL -> {
                cdcContext.status.enable = getBitFromByte(data, 0);
                transfer.enable = getBitFromByte(data, 1);
                cdcContext.status.wait = getInvertedBitFromByte(data, 2);
                transfer.wait = getInvertedBitFromByte(data, 3);
                cdcContext.control.commandBreak = getInvertedBitFromByte(data, 4);
                cdcContext.irq.decoder.enable = getBitFromByte(data, 5);
                cdcContext.irq.transfer.enable = getBitFromByte(data, 6);
//                cdcContext.irq.command.enable = getBitFromByte(data, 7); //unused
                poll();

                //abort data transfer if data output is disabled
                if (transfer.enable == 0) transferHelper.stop();
            }

            //DBCL: data byte counter low
            case DBCL -> transfer.length = setByteInWordBE(transfer.length, data, 1);

            //DBCH: data byte counter high
            //TODO this should only overwrite bits 8-11
            case DBCH -> transfer.length = setByteInWordBE(transfer.length, data & 0xF, 0);

            //DACL: data address counter low
            case DACL -> transfer.source = setByteInWordBE(transfer.source, data, 1);

            //DACH: data address counter high
            case DACH -> transfer.source = setByteInWordBE(transfer.source, data, 0);

            //DTRG: data trigger
            case DTRG -> transferHelper.start();

            case DTACK -> {
                /* clear pending data transfer end interrupt */
                cdcContext.irq.transfer.pending = 0;
                /* clear DBCH bits 4-7 */
                transfer.length = setByteInWordBE(transfer.length, 0, 0);
            }

            //WAL: write address low
            case WAL -> transfer.target = setByteInWordBE(transfer.target, data, 1);

            //WAH: write address high
            case WAH -> transfer.target = setByteInWordBE(transfer.target, data, 0);

            //CTRL0: control 0
            case CTRL0 -> {
                cdcContext.control.writeRequest = getBitFromByte(data, 2);
                cdcContext.control.autoCorrection = getBitFromByte(data, 4);
                cdcContext.control.errorCorrection = getBitFromByte(data, 5);
                cdcContext.decoder.enable = getBitFromByte(data, 7);

                cdcContext.decoder.mode = cdcContext.control.mode;
                cdcContext.decoder.form = cdcContext.control.form & cdcContext.control.autoCorrection;
            }
            //CTRL1: control 1
            case CTRL1 -> {
                cdcContext.control.head = getBitFromByte(data, 0);
                cdcContext.control.form = getBitFromByte(data, 2);
                cdcContext.control.mode = getBitFromByte(data, 3);
                cdcContext.control.syncInterrupt = getBitFromByte(data, 7);

                cdcContext.decoder.mode = cdcContext.control.mode;
                cdcContext.decoder.form = cdcContext.control.form & cdcContext.control.autoCorrection;
            }

            //PTL: block pointer low
            case PTL -> transfer.pointer = setByteInWordBE(transfer.pointer, data, 1);

            //PTH: block pointer high
            case PTH -> transfer.pointer = setByteInWordBE(transfer.pointer, data, 0);

            case CTRL2 -> {/*ignored*/}

            //RESET: software reset
            case RESET -> {
                CdcContext c = cdcContext;
                c.status.reset();
                c.transfer.reset();
                transferHelper.stop();
                c.irq.reset();
                c.control.reset();
                c.decoder.reset();
                c.header.reset();
                //TODO subheader
//                c.subheader.reset();
                poll();
            }
            default -> {
                LOG.error("CDC WRITE unknown address: {}, data {}", th(cdcContext.address), th(data));
                assert false;
            }
        }
        //SBOUT writes do not increment the address; RESET reads wrap the address to 0x0
        increaseCdcAddress();
    }

    private void increaseCdcAddress() {
        if (cdcContext.address > 0) {
            cdcContext.address = (cdcContext.address + 1) & NUM_CDC_REG_MASK;
            updateCdcMode4();
        }
    }


    /**
     * this needs to be called at ~ 32.55 Khz
     */
    public void step(int cycles) {
        cdcContext.stopwatch = (cdcContext.stopwatch + 1) & 0xFFF;
        setTimerBuffer();
    }

    private void updateCdcMode4() {
        int val = cdcContext.address | (transfer.destination << 8) |
                (transfer.ready << 14) | (transfer.completed << 15);
        //TODO improve, cannot use the regWriteHandlers as bus writes cannot modify EDT,DSR
        writeBufferRaw(memoryContext.getRegBuffer(SUB_M68K, MCD_CDC_MODE),
                MCD_CDC_MODE.addr, val, Size.WORD);
        //sync main M68K
        writeBufferRaw(memoryContext.getRegBuffer(M68K, MCD_CDC_MODE),
                MCD_CDC_MODE.addr, val >> 8, Size.BYTE);

    }

    private void setTimerBuffer() {
        writeBufferRaw(memoryContext.getRegBuffer(SUB_M68K, MCD_STOPWATCH), MCD_STOPWATCH.addr, cdcContext.stopwatch, Size.WORD);
        writeBufferRaw(memoryContext.getRegBuffer(M68K, MCD_STOPWATCH), MCD_STOPWATCH.addr, cdcContext.stopwatch, Size.WORD);
    }

    @Override
    public void poll() {
        CdcIrq irq = cdcContext.irq;
        int pending = 0;
        pending |= irq.decoder.enable & irq.decoder.pending;
        pending |= irq.transfer.enable & irq.transfer.pending;
//        pending |= irq.command.enable & irq.command.pending; //unused
        if (pending > 0) {
            interruptHandler.raiseInterrupt(INT_CDC);
        } else {
            interruptHandler.lowerInterrupt(INT_CDC);
        }
    }

    @Override
    public void decode(int sector) {
        assert false;
    }

    @Override
    public void cdc_decoder_update(int sector) {
        /* data decoding enabled ? */
        if (cdcContext.decoder.enable > 0) {
            /* update HEADx registers with current block header */
            CueFileParser.toMSF(sector + 150, msfHolder);
            //bcd format
            cdcContext.header.minute = toBcdByte.apply(msfHolder.minute);
            cdcContext.header.second = toBcdByte.apply(msfHolder.second);
            cdcContext.header.frame = toBcdByte.apply(msfHolder.frame);
            //cdd.toc.tracks[cdd.index].type;
            /**
             * #define TYPE_AUDIO 0x00
             * #define TYPE_MODE1 0x01
             * #define TYPE_MODE2 0x02
             */
            cdcContext.header.mode = track01.trackDataType == CdModel.TrackDataType.AUDIO ? 0 : 1;
            /* set !VALST */
            cdcContext.decoder.valid = 1;

            /* pending decoder interrupt */
            cdcContext.irq.decoder.pending = 1;
//            cdc.ifstat &= ~BIT_DECI;

            /* decoder interrupt enabled ? */
            if (cdcContext.irq.decoder.enable > 0) {
                poll();
                /* pending level 5 interrupt */
//                scd.pending |= (1 << 5);

                /* level 5 interrupt enabled ? */
//                if (scd.regs[0x32>>1].byte.l & 0x20)
//                {
//                    /* update IRQ level */
//                    s68k_update_irq((scd.pending & scd.regs[0x32>>1].byte.l) >> 1);
//                }
            }

            /* buffer RAM write enabled ? */
            if (cdcContext.control.writeRequest > 0) {
                int offset;
                /* increment block pointer  */
                transfer.pointer = (transfer.pointer + SECTOR_2352);
                /* increment write address */
                transfer.target = (transfer.target + SECTOR_2352);

                /* CDC buffer address */
                offset = transfer.pointer & 0x3fff;
                ram.position(offset);

                /* write current block header to RAM buffer (4 bytes) */
                ram.put((byte) cdcContext.header.minute);
                ram.put((byte) cdcContext.header.second);
                ram.put((byte) cdcContext.header.frame);
                ram.put((byte) cdcContext.header.mode);
                offset += 4;
                if (sector < 0) {
                    return;
                }

                /* check decoded block mode */
                if (cdcContext.header.mode == 0x01) {
                    assert track01.trackDataType != CdModel.TrackDataType.AUDIO;
                    /* write Mode 1 user data to RAM buffer (2048 bytes) */
                    cdd_read_data(sector, offset, track01);
                } else {
                    assert track01.trackDataType == CdModel.TrackDataType.AUDIO;
                    //NOTE cdda play
                }
            }
        }
    }

    /**
     * Only reads DATA tracks
     */
    private void cdd_read_data(int sector, int offset, ExtendedTrackData track) {
        /* only allow reading (first) CD-ROM track sectors */
        if (track.trackDataType != CdModel.TrackDataType.AUDIO && sector >= 0) {
            //header(4)
            LOG.info("Decoding data track sector: {}, cdcRamOffset: {}", sector, th(offset - 4));
            if (cueSheet.romFileType == RomFileType.ISO) {
                assert track.trackDataType == CdModel.TrackDataType.MODE1_2048;
                /* read Mode 1 user data (2048 bytes) */
                int seekPos = sector * S_2048.s_size;
                doFileRead(track.file, seekPos, S_2048.s_size, offset);
            } else if (cueSheet.romFileType == RomFileType.BIN_CUE) {
                /* skip block sync pattern (12 bytes) + block header (4 bytes) then read Mode 1 user data (2048 bytes) */
                assert track.trackDataType.size == S_2352;
                int seekPos = (sector * track.trackDataType.size.s_size) + 12 + 4;
                checkMode1Data(track, msfHolder, seekPos);
                doFileRead(track.file, seekPos, S_2048.s_size, offset);
//                    cdStreamSeek(cdd.toc.tracks[0].fd, (cdd.lba * 2352) + 12 + 4, SEEK_SET);
//                    cdStreamRead(dst, 2048, 1, cdd.toc.tracks[0].fd);
            }
        }
    }

    private void doFileRead(final RandomAccessFile file, int seekPos, int readChunkSize, int ramOffset) {
        assert ramOffset < RAM_SIZE;
        try {
            file.seek(seekPos);
            if (verbose) LOG.info(th(ramOffset) + "," + th(seekPos) + "," + readChunkSize);
            int len = Math.min(RAM_SIZE - ramOffset, readChunkSize);
            int readN = file.read(ram.array(), ramOffset, len);
            assert readN == len;
            if (len < readChunkSize) {
                readN += file.read(ram.array(), 0, readChunkSize - len);
            }
            assert readN == readChunkSize;
        } catch (Exception e) {
            LOG.error("decode error: {}", e.getMessage());
            e.printStackTrace();
            assert false;
        }
    }


    static final byte[] expSync = {0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0};

    private boolean checkMode1Data(ExtendedTrackData track, MsfHolder holder, int dataSeekPos) {
        byte[] header = new byte[4];
        byte[] syncHeader = new byte[12];
        boolean ok = false;
        try {
            track.file.seek(dataSeekPos - 16);
            int readN = track.file.read(syncHeader, 0, syncHeader.length);
            assert readN == syncHeader.length;
            readN = track.file.read(header, 0, header.length);
            assert readN == header.length;
            ok = header[0] == holder.minute && header[1] == holder.second
                    && header[2] == holder.frame && header[3] == 1; //MODE1
            ok &= Arrays.equals(expSync, syncHeader);
        } catch (Exception e) {
            LOG.error("decode error: {}", e.getMessage());
            e.printStackTrace();
            assert false;
        }
        return ok;
    }

    @Override
    public CdcContext getContext() {
        return cdcContext;
    }

    @Override
    public void recalcRegValue(MegaCdDict.RegSpecMcd regSpec) {
        assert regSpec == MCD_CDC_MODE;
        updateCdcMode4();
    }

    @Override
    public void dma() {
        transferHelper.dma();
    }

    @Override
    public void step75hz() {
        assert false;
    }
}
