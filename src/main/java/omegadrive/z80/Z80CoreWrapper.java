package omegadrive.z80;

import omegadrive.bus.BusProvider;
import omegadrive.z80.jsanchezv.MemIoOps;
import omegadrive.z80.jsanchezv.Z80;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * https://github.com/codesqueak/Z80Processor
 * <p>
 * https://github.com/scoffey/jz80sim
 * https://github.com/lesniakbj/Vizu-80
 */
public class Z80CoreWrapper implements Z80Provider {

    private Z80 z80Core;
    private Z80Memory memory;
    private MemIoOps memIoOps;

    private boolean resetState;
    private boolean busRequested;
    private boolean wasRunning;
    public static boolean activeInterrupt;

    private static Logger LOG = LogManager.getLogger(Z80CoreWrapper.class.getSimpleName());

    public Z80CoreWrapper(BusProvider busProvider) {
        memory = new Z80Memory(busProvider, this);
        memIoOps = createGenesisIo(this);
        memIoOps.setRam(memory.getMemory());
        this.z80Core = new Z80(memIoOps, null);
    }

    @Override
    public void initialize() {
        z80Core.reset();
        this.unrequestBus();
        wasRunning = false;
        LOG.info("Z80 init, reset: " + resetState + ", busReq: " + busRequested);
    }

    private boolean updateRunningFlag() {
        //TODO check why this breaks Z80 WAV PLAYER
        if (z80Core.isHalted()) {
            wasRunning = false;
            return false;
        }
        boolean nowRunning = isRunning();
        if (wasRunning != nowRunning) {
//            LOG.info("Z80: " + (nowRunning ? "ON" : "OFF"));
            wasRunning = nowRunning;
        }
        if (!nowRunning) {
            return false;
        }
        return true;
    }

    @Override
    public int executeInstruction() {
        if (!updateRunningFlag()) {
            return 0;
        }
        try {
            z80Core.execute();
        } catch (Exception e) {
            LOG.error("z80 exception", e);
            LOG.error("Halting Z80");
            z80Core.setHalted(true);
        }
        return 0;
    }


    @Override
    public int getPC() {
        return z80Core.getRegPC();
    }

    @Override
    public void setPC(int pc) {
        z80Core.setRegPC(pc);
    }

    @Override
    public void requestBus() {
        busRequested = true;
    }

    @Override
    public void unrequestBus() {
        busRequested = false;
    }

    @Override
    public boolean isBusRequested() {
        return busRequested;
    }

    //From the Z80UM.PDF document, a reset clears the interrupt enable, PC and
    //registers I and R, then sets interrupt status to mode 0.
    @Override
    public void reset() {
        z80Core.reset();
        resetState = true;
    }

    @Override
    public boolean isReset() {
        return resetState;
    }

    @Override
    public void disableReset() {
        resetState = false;
    }

    @Override
    public boolean isRunning() {
        return !busRequested && !resetState;
    }

    //    If the Z80 has interrupts disabled when the frame interrupt is supposed
//    to occur, it will be missed, rather than made pending.
    @Override
    public void interrupt() {
        boolean interruptDisabled = !z80Core.isIFF1() && !z80Core.isIFF2();
        if (!interruptDisabled) {
            activeInterrupt = true;
        }
    }

    @Override
    public int readMemory(int address) {
        return memory.readByte(address);
    }

    @Override
    public void writeByte(int addr, long data) {
        memory.writeByte(addr, (int) data);
    }

    //	https://emu-docs.org/Genesis/gen-hw.txt
    //	When doing word-wide writes to Z80 RAM, only the MSB is written, and the LSB is ignored
    @Override
    public void writeWord(int addr, long data) {
        memory.writeByte(addr, (int) (data >> 8));
    }


    /**
     * Z80 for genesis doesnt do IO
     *
     * @return
     */
    private static MemIoOps createGenesisIo(Z80Provider provider) {
        return new MemIoOps() {
            @Override
            public int inPort(int port) {
                LOG.warn("inPort: " + port);
                return 0;
            }

            @Override
            public void outPort(int port, int value) {
                LOG.warn("outPort: " + port + ", data: " + value);
            }

            @Override
            public void poke8(int address, int value) {
                provider.writeByte(address, value);
            }

            @Override
            public int peek8(int address) {
                return provider.readMemory(address);
            }

            @Override
            public boolean isActiveINT() {
                boolean res = activeInterrupt;
                activeInterrupt = false;
                return res;
            }
        };
    }
}
