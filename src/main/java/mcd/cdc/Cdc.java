package mcd.cdc;


import mcd.bus.McdSubInterruptHandler;
import mcd.cdd.CdModel;
import mcd.cdd.ExtendedCueSheet;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.util.BufferUtil;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDC;
import static mcd.cdc.CdcModel.*;
import static mcd.cdd.CdModel.SECTOR_2352;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_CDC_MODE;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_STOPWATCH;
import static omegadrive.util.ArrayEndianUtil.getByteInWordBE;
import static omegadrive.util.ArrayEndianUtil.setByteInWordBE;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;
import static omegadrive.util.Util.getBitFromByte;
import static omegadrive.util.Util.th;

/**
 * Cdc
 * Adapted from the Ares emulator
 * <p>
 * Sanyo LC8951x (CD controller)
 *
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public interface Cdc extends BufferUtil.StepDevice {

    Logger LOG = LogHelper.getLogger(Cdc.class.getSimpleName());
    void write(RegSpecMcd regSpec, int address, int value, Size size);

    int read(RegSpecMcd regSpec, int address, Size size);

    void setMedia(ExtendedCueSheet extCueSheet);
    void decode(int sector);

    void poll();

    void dma();

    CdcModel.CdcContext getContext();

    void recalcRegValue(RegSpecMcd regSpec);

    static Cdc createInstance(MegaCdMemoryContext memoryContext, McdSubInterruptHandler interruptHandler) {
        return new CdcImpl(memoryContext, interruptHandler);
    }
}

class CdcImpl implements Cdc {

    private final static Logger LOG = LogHelper.getLogger(CdcImpl.class.getSimpleName());

    private final MegaCdMemoryContext memoryContext;
    private final McdSubInterruptHandler interruptHandler;
    private final CdcModel.CdcContext cdcContext;

    private final CueFileParser.MsfHolder msfHolder = new CueFileParser.MsfHolder();

    private CdModel.ExtendedTrackData track01;

    private ByteBuffer ram;

    private CdcModel.CdcTransfer transfer;
    private CdcTransferHelper transferHelper;

    private boolean hasMedia;

    public CdcImpl(MegaCdMemoryContext mc, McdSubInterruptHandler ih) {
        memoryContext = mc;
        interruptHandler = ih;
        cdcContext = new CdcModel.CdcContext();
        transfer = cdcContext.transfer;
        ram = ByteBuffer.allocate(0x4000); //16 Kbytes
        transferHelper = new CdcTransferHelper(this, memoryContext, ram);
    }

    @Override
    public void write(RegSpecMcd regSpec, int address, int value, Size size) {
        ByteBuffer regBuffer = memoryContext.getRegBuffer(SUB_M68K, regSpec);
        LOG.info("CDC,regW,{},{},{},{}", th(address), th(value), size, regSpec);
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
    public int read(RegSpecMcd regSpec, int address, Size size) {
        int res = switch (regSpec) {
            case MCD_CDC_MODE -> {
                int res2 = readBuffer(memoryContext.getRegBuffer(CpuDeviceAccess.SUB_M68K, regSpec),
                        address & MDC_SUB_GATE_REGS_MASK, size);
                //according to mcd-ver, reading DSR,EDT resets them to 0
                transfer.completed = 0;
                transfer.ready = 0;
                updateCdcMode4();
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
        return res;
    }

    @Override
    public void setMedia(ExtendedCueSheet extCueSheet) {
        track01 = extCueSheet.extTracks.get(0);
        hasMedia = true;
        assert track01 != null && track01 != CdModel.ExtendedTrackData.NO_TRACK;
    }

    private int controllerRead() {
        int data = 0;
        if (cdcContext.address > CdcAddressRead.STAT3.ordinal()) {
            increaseCdcAddress();
            return data;
        }
        CdcAddressRead addressRead = cdcAddrReadVals[cdcContext.address];
        switch (addressRead) {
            //COMIN: command input
            case COMIN -> {
                CdcModel.CdcCommand command = cdcContext.command;
                if (command.empty > 0) {
                    data = 0xff;
                    break;
                }
                data = command.fifo[command.read++];
                if (command.read == command.write) {
                    command.empty = 1;
                    cdcContext.irq.command.pending = 0;
                    poll();
                }
            }
            //IFSTAT: interface status
            case IFSTAT -> {
                CdcModel.CdcContext c = cdcContext;
                data = (~c.status.active & 1) |
                        ((~c.status.active & 1) << 1) |
                        ((~c.status.busy & 1) << 2) |
                        ((~c.transfer.busy & 1) << 3) |
                        (1 << 4) |
                        ((~c.irq.decoder.pending & 1) << 5) |
                        ((~c.irq.transfer.pending & 1) << 6) |
                        ((~c.irq.command.pending & 1) << 7);
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
        LOG.info("CDC,R,{}({}),{}", addressRead, th(cdcContext.address), th(data));
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
        LOG.info("CDC,W,{}({}),{}", addressWrite, th(cdcContext.address), th(data));
        switch (addressWrite) {
            //SBOUT: status byte output
            case SBOUT -> {
                CdcModel.CdcStatus status = cdcContext.status;
                if (status.wait > 0 && transfer.busy > 0) break;
                if (status.read == status.write && status.empty == 0) status.read++;  //unverified: discard oldest byte?
                status.fifo[status.write++] = data;
                status.empty = 0;
                status.active = 1;
                status.busy = 1;
            }
            case IFCTRL -> {
                cdcContext.status.enable = getBitFromByte(data, 0);
                transfer.enable = getBitFromByte(data, 1);
                cdcContext.status.wait = getInvertedBitFromByte(data, 2);
                transfer.wait = getInvertedBitFromByte(data, 3);
                cdcContext.control.commandBreak = getInvertedBitFromByte(data, 4);
                cdcContext.irq.decoder.enable = getBitFromByte(data, 5);
                cdcContext.irq.transfer.enable = getBitFromByte(data, 6);
                cdcContext.irq.command.enable = getBitFromByte(data, 7);
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
                cdcContext.irq.transfer.pending = 0;
                poll();
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
                CdcModel.CdcContext c = cdcContext;
                c.status.reset();
                c.transfer.reset();
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
        CdcModel.CdcIrq irq = cdcContext.irq;
        int pending = 0;
        pending |= irq.decoder.enable & irq.decoder.pending;
        pending |= irq.transfer.enable & irq.transfer.pending;
        pending |= irq.command.enable & irq.command.pending;
        if (pending > 0) {
            interruptHandler.raiseInterrupt(INT_CDC);
        } else {
            interruptHandler.lowerInterrupt(INT_CDC);
        }
    }

    @Override
    public void decode(int sector) {
        if (cdcContext.decoder.enable == 0 || !hasMedia) {
            return;
        }
        CueFileParser.toMSF(sector, msfHolder);
        //bcd format
        cdcContext.header.minute = ((msfHolder.minute / 10) << 4) | (msfHolder.minute % 10);
        cdcContext.header.second = ((msfHolder.second / 10) << 4) | (msfHolder.second % 10);
        cdcContext.header.frame = ((msfHolder.frame / 10) << 4) | (msfHolder.frame % 10);
        cdcContext.header.mode = 1;

        cdcContext.decoder.valid = 1;
        cdcContext.irq.decoder.pending = 1;
        poll();

        if (cdcContext.control.writeRequest > 0) {
            transfer.pointer = (transfer.pointer + SECTOR_2352) & 0x3FFF;
            transfer.target = (transfer.target + SECTOR_2352) & 0x3FFF;
            LOG.info("Decoding track {}, sector: {}({}), startByte: {}",
                    track01.trackData.getNumber(), sector, msfHolder, sector * SECTOR_2352);
            sector -= 150; //ignore 2s pause
            if (sector < 0) {
                return;
            }

            //MODE 1: 2352 bytes
            //12 sync header | 2340 header + data (where data is 2048 bytes)
            //the sync header is written at the tail instead of head.
            try {
                final RandomAccessFile file = track01.file;
                file.seek(sector * SECTOR_2352);
                final int limit = 2340;
                final int syncPos = (transfer.pointer + limit) & 0x3FFF;
                final int headerPos = transfer.pointer;
                //header 12 byte
                for (int i = 0; i < 12; i++) {
                    int pos = (syncPos + i) & 0x3FFF;
                    ram.put(pos, file.readByte());
                }
                for (int i = 0; i < 2340; i++) {
                    int pos = (headerPos + i) & 0x3FFF;
                    ram.put(pos, file.readByte());
                }
                assert checkMode1(msfHolder, syncPos, headerPos);
            } catch (Exception e) {
                LOG.error("decode error: {}", e.getMessage());
            }
        }
    }

    static final byte[] expSync = {0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0};

    private boolean checkMode1(CueFileParser.MsfHolder holder, int syncPos, int headerPos) {
        int[] header = new int[4];
        byte[] syncHeader = new byte[12];
        for (int i = 0; i < header.length; i++) {
            header[i] = ram.get((headerPos + i) & 0x3FFF);
        }
        for (int i = 0; i < syncHeader.length; i++) {
            syncHeader[i] = ram.get((syncPos + i) & 0x3FFF);
        }
        boolean ok = header[0] == holder.minute && header[1] == holder.second
                && header[2] == holder.frame && header[3] == 1; //MODE1
        ok &= Arrays.equals(expSync, syncHeader);
        if (!ok) {
            System.out.println("here");
            assert false;
        }
        return ok;
    }

    @Override
    public CdcModel.CdcContext getContext() {
        return cdcContext;
    }

    @Override
    public void recalcRegValue(RegSpecMcd regSpec) {
        assert regSpec == MCD_CDC_MODE;
        updateCdcMode4();
    }

    @Override
    public void dma() {
        transferHelper.dma();
    }

    public static int getInvertedBitFromByte(byte b, int bitPos) {
        return ~getBitFromByte(b, bitPos) & 1;
    }
}