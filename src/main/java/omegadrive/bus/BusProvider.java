package omegadrive.bus;

import omegadrive.GenesisProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.Size;
import omegadrive.vdp.VdpProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface BusProvider {

    int Z80_ADDRESS_SPACE_START = 0xA00000;
    int Z80_ADDRESS_SPACE_END = 0xA0FFFF;
    int IO_ADDRESS_SPACE_START = 0xA10000;
    int IO_ADDRESS_SPACE_END = 0xA10FFF;
    int INTERNAL_REG_ADDRESS_SPACE_START = 0xA11000;
    int INTERNAL_REG_ADDRESS_SPACE_END = 0xBFFFFF;
    int VDP_ADDRESS_SPACE_START = 0xC00000;
    int VDP_ADDRESS_SPACE_END = 0xDFFFFF;
    int ADDRESS_UPPER_LIMIT = 0xFFFFFF;
    int ADDRESS_RAM_MAP_START = 0xE00000;
    int M68K_TO_Z80_MEMORY_MASK = 0x7FFF;

    Logger LOG = LogManager.getLogger(BusProvider.class.getSimpleName());


    enum VdpIntState {
        NONE,
        PROCESS_INT,
        ACK_INT,
        INT_DONE
    }

    static BusProvider createBus() {
        return new GenesisBus();
    }

    BusProvider attachDevice(Object device);

    GenesisProvider getEmulator();

    MemoryProvider getMemory();

    VdpProvider getVdp();

    JoypadProvider getJoypad();

    SoundProvider getSound();

    void handleVdpInterrupts68k();

    void handleVdpInterruptsZ80();

    long read(long address, Size size);

    void write(long address, long data, Size size);

    void reset();

    /**
     * VRES is fed to 68000 for 128 VCLKs (16.7us); ZRES is fed
     * to the z80 and ym2612, and remains asserted until the 68000 does something to
     * deassert it; VDP and IO chip are unaffected.
     */
    void resetFrom68k();

    boolean shouldStop68k();

    //VDP setting this
    void setStop68k(boolean value);

    void closeRom();

    default PsgProvider getPsg() {
        return getSound().getPsg();
    }

    default FmProvider getFm() {
        return getSound().getFm();
    }


}
