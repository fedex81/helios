package mcd.cdc;


import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import static mcd.dict.MegaCdDict.RegSpecMcd;
import static mcd.dict.MegaCdDict.SUB_CPU_REGS_MASK;
import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;
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

    void poll();

    void clock();

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


    class CdcContext {
        public int address, stopwatch;
        public byte[] ram;
        public CdcStatus status;
        public CdcCommand command;

        public CdcContext() {
            status = new CdcStatus();
            command = new CdcCommand();
        }
    }

    void write(RegSpecMcd regSpec, int address, int value, Size size);

    int read(RegSpecMcd regSpec, int address, Size size);

    static Cdc createInstance(MegaCdMemoryContext memoryContext) {
        return new CdcImpl(memoryContext);
    }

}

class CdcImpl implements Cdc {

    private final static Logger LOG = LogHelper.getLogger(CdcImpl.class.getSimpleName());

    private final MegaCdMemoryContext memoryContext;
    private final CdcContext cdcContext;

    public CdcImpl(MegaCdMemoryContext mc) {
        memoryContext = mc;
        cdcContext = new CdcContext();
    }

    @Override
    public void write(RegSpecMcd regSpec, int address, int value, Size size) {
        writeBufferRaw(memoryContext.commonGateRegsBuf, address & SUB_CPU_REGS_MASK, value, size);
        switch (regSpec) {
            case MCD_CDC_REG_DATA -> {
                assert size == Size.BYTE;
                controllerWrite(value);
            }
            case MCD_CDC_MODE -> {
                assert size == Size.BYTE;
                if ((address & 1) == 1) {
                    cdcContext.address = value & 0xF;
                } else {
//                    cdc.transfer.destination = data.bit(8,10);
                }
            }
        }
    }

    @Override
    public int read(RegSpecMcd regSpec, int address, Size size) {
        switch (regSpec) {
            case MCD_CDC_REG_DATA -> {
                assert size == Size.BYTE;
                return controllerRead();
            }
        }
        return readBuffer(memoryContext.commonGateRegsBuf, address & SUB_CPU_REGS_MASK, size);
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
//                    irq.command.pending = 0;
                    poll();
                }
            }
            break;
            default:
                LOG.error("CDC READ unknown address: {}", th(cdcContext.address));
        }
        //COMIN reads do not increment the address; STAT3 reads wrap the address to 0x0
        if (cdcContext.address > 0) {
            cdcContext.address = (cdcContext.address + 1) & 0xF;
        }
        return data;
    }

    private void controllerWrite(int data) {
        switch (cdcContext.address) {

            //SBOUT: status byte output
            case 0x0: {
                CdcStatus status = cdcContext.status;
                if (status.wait > 0/*&& transfer.busy*/) break;
                if (status.read == status.write && status.empty == 0) status.read++;  //unverified: discard oldest byte?
                status.fifo[status.write++] = (byte) data;
                status.empty = 0;
                status.active = 1;
                status.busy = 1;
            }
            break;
            //RESET: software reset
            case 0xf: {
                cdcContext.status.reset();
                poll();
            }
            break;
            default:
                LOG.error("CDC WRITE unknown address: {}, data {}", th(cdcContext.address), th(data));
        }
        //SBOUT writes do not increment the address; RESET reads wrap the address to 0x0
        if (cdcContext.address > 0) {
            cdcContext.address = (cdcContext.address + 1) & 0xF;
        }
    }

    public void step(int cycles) {

    }

    @Override
    public void poll() {

    }

    @Override
    public void clock() {

    }

    @Override
    public void decode(int sector) {

    }
}