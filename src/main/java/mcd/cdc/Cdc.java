package mcd.cdc;


import mcd.bus.McdSubInterruptHandler;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_CDC;
import static mcd.dict.MegaCdDict.RegSpecMcd;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_CDC_MODE;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_STOPWATCH;
import static mcd.dict.MegaCdDict.SUB_CPU_REGS_MASK;
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

    //some Cdc models have 16, others 32; mcd_verificator expects 32
    int NUM_CDC_REG = 32;
    int NUM_CDC_REG_MASK = 31;

    void poll();

    void decode(int sector);

    class CdcCommand {
        public byte[] fifo = new byte[8];  //COMIN
        public int read; //n3
        public int write; //n3
        public int empty = 1; //n1
    }

    class CdcStatus {
        public byte[] fifo = new byte[8];    //SBOUT
        int read, write; //n3
        int empty = 1; //n1
        int enable;     //SOUTEN, n1
        int active;     //STEN, n1
        int busy;       //STBSY, n1
        int wait;       //STWAI, n1

        public void reset() {
            active = busy = enable = 0;
            wait = 1;
        }
    }

    class CdcDecoder {
        int enable;  //DECEN, n1
        int mode;    //MODE, n1
        int form;    //FORM, n1
        int valid;   //!VALST, n1

        public void reset() {
            enable = form = mode = 0;
        }
    }

    //cdc-transfer.cpp
    interface CdcTransferAction {
        default void dma() {
            throw new RuntimeException();
        }

        default int read() {
            throw new RuntimeException();
        } //n16

        default void start() {
            throw new RuntimeException();
        }

        default void complete() {
            throw new RuntimeException();
        }

        default void stop() {
            LogHelper.logWarnOnce(LOG, "Not supported, CDC transfer stop");
        }
    }

    class CdcTransfer implements CdcTransferAction {
        int destination; //n3
        int address; //n19

        int source; //n16
        int target; //n16
        int pointer; //n16
        int length; //n12

        int enable;     //DOUTEN, n1
        int active;     //DTEN, n1
        int busy;       //DTBSY, n1
        int wait;       //DTWAI, n1
        int ready;      //DSR, n1
        int completed;  //EDT, n1

        public void reset() {
            enable = active = busy = 0;
            wait = 1;
//            stop(); //TODO
        }
    }

    class McdIrq {
        public int enable;     //n1
        public int pending;     //n1
    }

    class CdcIrq extends McdIrq {
        public McdIrq decoder;   //DECEIN + DECI
        public McdIrq transfer;  //DTEIEN + DTEI
        public McdIrq command;   //CMDIEN + CMDI

        public CdcIrq() {
            decoder = new McdIrq();
            transfer = new McdIrq();
            command = new McdIrq();
        }

        boolean raise() {
            if (pending > 0) return false;
            pending = enable;
            return true;
        }

        boolean lower() {
            if (pending == 0) return false;
            return true;
        }

        void reset() {
            decoder.pending = transfer.pending = command.pending = 0;
            decoder.enable = transfer.enable = command.enable = 0;
        }
    }

    class CdcControl {
        //all n1
        int head;               //SHDREN: 0 = read header, 1 = read subheader
        int mode;               //MODE
        int form;               //FORM
        int commandBreak;       //CMDBK
        int modeByteCheck;      //MBCKRQ
        int erasureRequest;     //ERAMRQ
        int writeRequest;       //WRRQ
        int pCodeCorrection;    //PRQ
        int qCodeCorrection;    //QRQ
        int autoCorrection;     //AUTOQ
        int errorCorrection;    //E01RQ
        int edcCorrection;      //EDCRQ
        int correctionWrite;    //COWREN
        int descramble;         //DSCREN
        int syncDetection;      //SYDEN
        int syncInterrupt;      //SYIEN
        int erasureCorrection;  //ERAMSL
        int statusTrigger;      //STENTRG
        int statusControl;      //STENCTL

        public void reset() {
            commandBreak = 1;
            pCodeCorrection = qCodeCorrection = writeRequest = erasureRequest = autoCorrection =
                    errorCorrection = edcCorrection = 0;
            head = modeByteCheck = form = mode = correctionWrite = descramble = syncDetection = syncInterrupt = 0;
            statusTrigger = statusControl = erasureCorrection = 0;
        }
    }

    class CdcContext {
        public int address, stopwatch;
        public byte[] ram;
        public CdcStatus status;
        public CdcCommand command;
        public CdcDecoder decoder;
        public CdcTransfer transfer;
        public CdcIrq irq;
        public CdcControl control;


        public CdcContext() {
            status = new CdcStatus();
            command = new CdcCommand();
            decoder = new CdcDecoder();
            transfer = new CdcTransfer();
            irq = new CdcIrq();
            control = new CdcControl();
        }
    }

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
    private final CdcContext cdcContext;

    public CdcImpl(MegaCdMemoryContext mc, McdSubInterruptHandler ih) {
        memoryContext = mc;
        interruptHandler = ih;
        cdcContext = new CdcContext();
    }

    @Override
    public void write(RegSpecMcd regSpec, int address, int value, Size size) {
        ByteBuffer regBuffer = memoryContext.getRegBuffer(SUB_M68K, regSpec);
        writeBufferRaw(regBuffer, address & SUB_CPU_REGS_MASK, value, size);
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
        switch (cdcContext.address) {
            //COMIN: command input
            case 0x0: {
                CdcCommand command = cdcContext.command;
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
            case 0x2:
                data = getByteInWordBE(cdcContext.transfer.length, 1);
                break;
            //DBCH: data byte counter high
            case 0x3:
                data = getByteInWordBE(cdcContext.transfer.length, 0);
                break;
            default:
                LOG.error("CDC READ unknown address: {}", th(cdcContext.address));
        }
        //COMIN reads do not increment the address; STAT3 reads wrap the address to 0x0
        increaseCdcAddress();
//        logCd("CDC R: " + th(cdcContext.address) + "," + th(data));
        return data;
    }

    private void controllerWrite(byte data) {
//        logCd("CDC W: " + th(cdcContext.address) + "," + th(data));
        switch (cdcContext.address) {

            //SBOUT: status byte output
            case 0x0: {
                CdcStatus status = cdcContext.status;
                if (status.wait > 0/*&& transfer.busy*/) break;
                if (status.read == status.write && status.empty == 0) status.read++;  //unverified: discard oldest byte?
                status.fifo[status.write++] = data;
                status.empty = 0;
                status.active = 1;
                status.busy = 1;
            }
            break;
            case 0x1: {  //IFCTRL
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
            break;
            //DBCL: data byte counter low
            case 0x2:
                cdcContext.transfer.length = setByteInWordBE(cdcContext.transfer.length, data, 1);
                break;
            //DBCH: data byte counter high
            case 0x3:
                cdcContext.transfer.length = setByteInWordBE(cdcContext.transfer.length, data & 0xF, 0);
                break;
            //WAL: write address low
            case 0x8: {
                int t = cdcContext.transfer.target;
                t = (t & 0xFF00) | data;
                cdcContext.transfer.target = t;
            }
            break;
            //WAH: write address high
            case 0x9: {
                int t = cdcContext.transfer.target;
                t = (t & 0xFF) | data << 8;
                cdcContext.transfer.target = t;
            }
            break;
            //PTL: block pointer low
            case 0xc: {
                int t = cdcContext.transfer.pointer;
                t = (t & 0xFF00) | data;
                cdcContext.transfer.pointer = t;
            }
            break;

            //PTH: block pointer high
            case 0xd: {
                int t = cdcContext.transfer.pointer;
                t = (t & 0xFF) | data << 8;
                cdcContext.transfer.pointer = t;
            }
            break;
            //RESET: software reset
            case 0xf: {
                cdcContext.status.reset();
                cdcContext.transfer.reset();
                cdcContext.irq.reset();
                cdcContext.control.reset();
                cdcContext.decoder.reset();
                //TODO header, subheader
                poll();
            }
            break;
            default:
                LOG.error("CDC WRITE unknown address: {}, data {}", th(cdcContext.address), th(data));
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

    @Override
    public void poll() {
        CdcIrq irq = cdcContext.irq;
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
        assert cdcContext.decoder.enable == 0;
    }

    public static int getInvertedBitFromByte(byte b, int bitPos) {
        return ~getBitFromByte(b, bitPos) & 1;
    }
}