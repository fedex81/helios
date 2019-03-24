package omegadrive.bus.gen;

import omegadrive.SystemProvider;
import omegadrive.bus.BaseBusProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.IMemoryRam;
import omegadrive.sound.SoundProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public interface GenesisZ80BusProvider extends BaseBusProvider {

    Logger LOG = LogManager.getLogger(GenesisZ80BusProvider.class.getSimpleName());


    static GenesisZ80BusProvider createInstance(GenesisBusProvider genesisBusProvider, IMemoryRam memory) {
        GenesisZ80BusProvider b = new GenesisZ80BusProviderImpl();
        b.attachDevice(genesisBusProvider).attachDevice(memory);
        return b;
    }

    void setRomBank68kSerial(int romBank68kSerial);

    int getRomBank68kSerial();

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

    static int getRomBank68kSerial(Z80Provider z80) {
        if (z80.getZ80BusProvider() instanceof GenesisZ80BusProvider) {
            GenesisZ80BusProvider g = (GenesisZ80BusProvider) z80.getZ80BusProvider();
            return g.getRomBank68kSerial();
        }
        return -1;
    }

    static void setRomBank68kSerial(Z80Provider z80, int romBank68kSerial) {
        BaseBusProvider bus = z80.getZ80BusProvider();
        if (bus instanceof GenesisZ80BusProvider) {
            GenesisZ80BusProvider genBus = (GenesisZ80BusProvider) bus;
            genBus.setRomBank68kSerial(romBank68kSerial);
        }
    }

    @Override
    default GenesisVdpProvider getVdp() {
        return null;
    }

    @Override
    default IMemoryProvider getMemory() {
        return null;
    }

    @Override
    default JoypadProvider getJoypad() {
        return null;
    }

    @Override
    default SoundProvider getSound() {
        return null;
    }

    @Override
    default SystemProvider getEmulator() {
        return null;
    }
}
