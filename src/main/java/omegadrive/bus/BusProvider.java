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

    Logger LOG = LogManager.getLogger(BusProvider.class.getSimpleName());

    static BusProvider createBus() {
        return new GenesisBus();
    }

    static BusProvider createLegacyBus(GenesisProvider genesis, MemoryProvider memory, JoypadProvider joypad, SoundProvider sound) {
        return new GenBusLegacy(genesis, memory, null, null, joypad, null, sound);
    }

    BusProvider attachDevice(Object device);

    GenesisProvider getEmulator();

    MemoryProvider getMemory();

    VdpProvider getVdp();

    JoypadProvider getJoypad();

    SoundProvider getSound();

    void setSsf2Mapper(boolean value);

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
