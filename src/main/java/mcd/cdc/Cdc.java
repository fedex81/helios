package mcd.cdc;


import mcd.bus.McdSubInterruptHandler;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDC;
import static mcd.cdc.CdcModel.CdcAddress.RESET;
import static mcd.cdc.CdcModel.NUM_CDC_REG_MASK;
import static mcd.cdc.CdcModel.cdcAddrVals;
import static mcd.dict.MegaCdDict.MDC_SUB_GATE_REGS_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_CDC_MODE;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_STOPWATCH;
import static omegadrive.util.ArrayEndianUtil.getByteInWordBE;
import static omegadrive.util.ArrayEndianUtil.setByteInWordBE;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
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

    static Cdc createInstance(MegaCdMemoryContext memoryContext, McdSubInterruptHandler interruptHandler) {
        return new CdcImpl(memoryContext, interruptHandler);
    }

}

class CdcImpl implements Cdc {

    private final static Logger LOG = LogHelper.getLogger(CdcImpl.class.getSimpleName());

    private final MegaCdMemoryContext memoryContext;
    private final McdSubInterruptHandler interruptHandler;
    private final CdcModel.CdcContext cdcContext;

    public CdcImpl(MegaCdMemoryContext mc, McdSubInterruptHandler ih) {
        memoryContext = mc;
        interruptHandler = ih;
        cdcContext = new CdcModel.CdcContext();
    }

    @Override
    public void write(RegSpecMcd regSpec, int address, int value, Size size) {
        ByteBuffer regBuffer = memoryContext.getRegBuffer(SUB_M68K, regSpec);
        writeBufferRaw(regBuffer, address & MDC_SUB_GATE_REGS_MASK, value, size);
        switch (regSpec) {
            case MCD_CDC_REG_DATA -> {
                //word writes MSB is ignored??
                controllerWrite((byte) value);
            }
            case MCD_CDC_MODE -> {
                int resWord = memoryContext.handleRegWrite(SUB_M68K, regSpec, address, value, size);
                cdcContext.address = resWord & NUM_CDC_REG_MASK;
                cdcContext.transfer.destination = (resWord >> 7) & 3;
                if (cdcContext.transfer.destination > 1) {
                    LOG.warn("Unsupported cdc transfer destination: {}", cdcContext.transfer.destination);
                }
            }
            case MCD_CDC_DMA_ADDRESS -> LOG.error("Write {} not supported: {} {}", regSpec, th(value), size);
            case MCD_STOPWATCH -> {
                cdcContext.stopwatch = 0;
                setTimerBuffer();
            }
        }
    }

    @Override
    public int read(RegSpecMcd regSpec, int address, Size size) {
        switch (regSpec) {
            case MCD_CDC_REG_DATA -> {
                //word reads only populate LSB??
                return controllerRead();
            }
        }
        assert false;
        return size.getMask();
    }

    private int controllerRead() {
        int data = 0;
        if (cdcContext.address > RESET.ordinal()) {
            increaseCdcAddress();
            return data;
        }
        CdcModel.CdcAddress cdcAddress = cdcAddrVals[cdcContext.address];
        switch (cdcAddress) {
            //COMIN: command input
            case COMIN_SBOUT: {
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
            break;
            //DBCL: data byte counter low
            case DBCL:
                data = getByteInWordBE(cdcContext.transfer.length, 1);
                break;
            //DBCH: data byte counter high
            case DBCH:
                data = getByteInWordBE(cdcContext.transfer.length, 0);
                break;
            default:
                LOG.error("CDC READ unknown address: {} {}", cdcAddress, th(cdcContext.address));
        }
        //COMIN reads do not increment the address; STAT3 reads wrap the address to 0x0
        increaseCdcAddress();
//        logCd("CDC R: " + th(cdcContext.address) + "," + th(data));
        return data;
    }

    private void controllerWrite(byte data) {
//        logCd("CDC W: " + th(cdcContext.address) + "," + th(data));
        if (cdcContext.address > RESET.ordinal()) {
            increaseCdcAddress();
            return;
        }
        CdcModel.CdcAddress cdcAddress = cdcAddrVals[cdcContext.address];
        switch (cdcAddress) {
            //SBOUT: status byte output
            case COMIN_SBOUT -> {
                CdcModel.CdcStatus status = cdcContext.status;
                if (status.wait > 0/*&& transfer.busy*/) break;
                if (status.read == status.write && status.empty == 0) status.read++;  //unverified: discard oldest byte?
                status.fifo[status.write++] = data;
                status.empty = 0;
                status.active = 1;
                status.busy = 1;
            }
            case IFCTRL -> {
                cdcContext.status.enable = getBitFromByte(data, 0);
                cdcContext.transfer.enable = getBitFromByte(data, 1);
                cdcContext.status.wait = getInvertedBitFromByte(data, 2);
                cdcContext.transfer.wait = getInvertedBitFromByte(data, 3);
                cdcContext.control.commandBreak = getInvertedBitFromByte(data, 4);
                cdcContext.irq.decoder.enable = getBitFromByte(data, 5);
                cdcContext.irq.transfer.enable = getBitFromByte(data, 6);
                cdcContext.irq.command.enable = getBitFromByte(data, 7);
                poll();

                //abort data transfer if data output is disabled
                if (cdcContext.transfer.enable == 0) cdcContext.transfer.stop();
            }

            //DBCL: data byte counter low
            case DBCL -> cdcContext.transfer.length = setByteInWordBE(cdcContext.transfer.length, data, 1);

            //DBCH: data byte counter high
            case DBCH -> cdcContext.transfer.length = setByteInWordBE(cdcContext.transfer.length, data & 0xF, 0);

            //WAL: write address low
            case WAL -> cdcContext.transfer.target = setByteInWordBE(cdcContext.transfer.target, data, 1);

            //WAH: write address high
            case WAH -> cdcContext.transfer.target = setByteInWordBE(cdcContext.transfer.target, data, 0);

            //PTL: block pointer low
            case PTL -> cdcContext.transfer.pointer = setByteInWordBE(cdcContext.transfer.pointer, data, 1);

            //PTH: block pointer high
            case PTH -> cdcContext.transfer.pointer = setByteInWordBE(cdcContext.transfer.pointer, data, 0);

            //RESET: software reset
            case RESET -> {
                cdcContext.status.reset();
                cdcContext.transfer.reset();
                cdcContext.irq.reset();
                cdcContext.control.reset();
                cdcContext.decoder.reset();
                //TODO header, subheader
                poll();
            }
            default ->
                    LOG.error("CDC WRITE unknown address: {} {}, data {}", cdcAddress, th(cdcContext.address), th(data));
        }
        //SBOUT writes do not increment the address; RESET reads wrap the address to 0x0
        increaseCdcAddress();
    }

    private void increaseCdcAddress() {
        if (cdcContext.address > 0) {
            cdcContext.address = (cdcContext.address + 1) & NUM_CDC_REG_MASK;
            setCdcAddress();
        }
    }


    /**
     * TODO stopwatch increments every 30.72micros -> 32_552hz
     * this needs to be called at ~ 35.55 Khz
     */
    public void step(int cycles) {
        cdcContext.stopwatch = (cdcContext.stopwatch + 1) & 0xFFF;
        setTimerBuffer();
    }

    private void setCdcAddress() {
        writeBufferRaw(memoryContext.getRegBuffer(SUB_M68K, MCD_CDC_MODE), MCD_CDC_MODE.addr + 1, cdcContext.address, Size.BYTE);
    }

    private void setTimerBuffer() {
        writeBufferRaw(memoryContext.getRegBuffer(SUB_M68K, MCD_STOPWATCH), MCD_STOPWATCH.addr, cdcContext.stopwatch, Size.WORD);
        writeBufferRaw(memoryContext.getRegBuffer(M68K, MCD_STOPWATCH), MCD_STOPWATCH.addr, cdcContext.stopwatch, Size.WORD);
    }

    private void poll() {
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

    private void decode(int sector) {
        assert cdcContext.decoder.enable == 0;
    }

    public static int getInvertedBitFromByte(byte b, int bitPos) {
        return ~getBitFromByte(b, bitPos) & 1;
    }
}