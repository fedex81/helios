package omegadrive.z80;

import omegadrive.bus.BusProvider;
import omegadrive.z80.jsanchezv.MemIoOps;
import omegadrive.z80.jsanchezv.Z80;
import omegadrive.z80.jsanchezv.Z80State;
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
//            LOG.info(toString(z80Core.getZ80State()));
            z80Core.execute();
        } catch (Exception e) {
            LOG.error("z80 exception", e);
            LOG.error("Z80State: " + toString(z80Core.getZ80State()));
            LOG.error("Halting Z80");
            z80Core.setHalted(true);
        }
        return 0;
    }

    private static String toString(Z80State state) {
        String str = "\n";
        str += String.format("SP: %04x   PC: %04x  I : %02x   R : %02x  IX: %04x  IY: %04x\n",
                state.getRegSP(), state.getRegPC(), state.getRegI(), state.getRegR(), state.getRegIX(), state.getRegIY());
        str += String.format("A : %02x   B : %02x  C : %02x   D : %02x  E : %02x  F : %02x   L : %02x   H : %02x\n",
                state.getRegA(), state.getRegB(),
                state.getRegC(), state.getRegD(), state.getRegE(), state.getRegF(), state.getRegL(), state.getRegH());
        str += String.format("Ax: %02x   Bx: %02x  Cx: %02x   Dx: %02x  Ex: %02x  Fx: %02x   Lx: %02x   Hx: %02x\n",
                state.getRegAx(), state.getRegBx(),
                state.getRegCx(), state.getRegDx(), state.getRegEx(), state.getRegFx(), state.getRegLx(), state.getRegHx());
        str += String.format("AF : %04x   BC : %04x  DE : %04x   HL : %04x\n",
                state.getRegAF(), state.getRegBC(), state.getRegDE(), state.getRegHL());
        str += String.format("AFx: %04x   BCx: %04x  DEx: %04x   HLx: %04x\n",
                state.getRegAFx(), state.getRegBCx(), state.getRegDEx(), state.getRegHLx());
        str += String.format("IM: %s  iff1: %s  iff2: %s  memPtr: %04x  flagQ: %s\n",
                state.getIM().name(), state.isIFF1(), state.isIFF2(), state.getMemPtr(), state.isFlagQ());
        str += String.format("NMI: %s  INTLine: %s  pendingE1: %s\n", state.isNMI(), state.isINTLine(),
                state.isPendingEI());
        return str;
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
                return 0xFF;
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
