package omegadrive.bus.gen;

import omegadrive.SystemProvider;
import omegadrive.bus.BaseBusProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface GenesisBusProvider extends BaseBusProvider {

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

    int FIFO_FULL_MASK = 0x01;
    int DMA_IN_PROGRESS_MASK = 0x02;

    Logger LOG = LogManager.getLogger(GenesisBusProvider.class.getSimpleName());


    enum VdpIntState {
        NONE,
        PROCESS_INT,
        ACK_INT,
        INT_DONE
    }

    static GenesisBusProvider createBus() {
        return new GenesisBus();
    }

    void handleVdpInterrupts68k();

    void handleVdpInterruptsZ80();


    /**
     * VRES is fed to 68000 for 128 VCLKs (16.7us); ZRES is fed
     * to the z80 and ym2612, and remains asserted until the 68000 does something to
     * deassert it; VDP and IO chip are unaffected.
     */
    void resetFrom68k();

    boolean shouldStop68k();

    //VDP setting this
    void setStop68k(int mask);

    boolean isZ80Running();

    boolean isZ80ResetState();

    boolean isZ80BusRequested();

    void setZ80ResetState(boolean z80ResetState);

    void setZ80BusRequested(boolean z80BusRequested);

    PsgProvider getPsg();

    FmProvider getFm();

    SystemProvider getSystem();

    //Z80 for genesis doesnt do IO
    @Override
    default int readIoPort(int port) {
        //TF4 calls this by mistake
        LOG.debug("inPort: {}", port);
        return 0xFF;
    }

    //Z80 for genesis doesnt do IO
    @Override
    default void writeIoPort(int port, int value) {
        LOG.warn("outPort: " + port + ", data: " + value);
        return;
    }
}
