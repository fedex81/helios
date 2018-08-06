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

    int ADDRESS_UPPER_LIMIT = 0xFFFFFF;
    int ADDRESS_RAM_MAP_START = 0xE00000;

    Logger LOG = LogManager.getLogger(BusProvider.class.getSimpleName());

    static BusProvider createBus() {
        return new GenesisBus();
    }

    BusProvider attachDevice(Object device);

    GenesisProvider getEmulator();

    MemoryProvider getMemory();

    VdpProvider getVdp();

    JoypadProvider getJoypad();

    SoundProvider getSound();

    boolean checkInterrupts();

    long read(long address, Size size);

    void write(long address, long data, Size size);

    void reset();

    default PsgProvider getPsg() {
        return getSound().getPsg();
    }

    default FmProvider getFm() {
        return getSound().getFm();
    }

}
