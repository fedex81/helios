package omegadrive.util;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.bus.z80.ColecoBus;
import omegadrive.bus.z80.MsxBus;
import omegadrive.bus.z80.Sg1000Bus;
import omegadrive.bus.z80.SmsBus;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.system.Z80BaseSystem;
import omegadrive.ui.DisplayWindow;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.SmsVdp;
import omegadrive.vdp.Tms9918aVdp;
import omegadrive.vdp.model.GenesisVdpProvider;
import org.junit.Assert;

import static omegadrive.SystemLoader.SystemType.SMS;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class SystemTestUtil {

    public static GenesisBusProvider setupNewMdSystem() {
        return setupNewMdSystem(MemoryProvider.createGenesisInstance());
    }

    public static GenesisBusProvider setupNewMdSystem(IMemoryProvider cpuMem1) {
        GenesisBusProvider busProvider = GenesisBusProvider.createBus();

        GenesisVdpProvider vdpProvider1 = GenesisVdpProvider.createVdp(busProvider);
        MC68000Wrapper cpu = new MC68000Wrapper(busProvider);

        Z80Provider z80p1 = Z80CoreWrapper.createGenesisInstance(busProvider);
        FmProvider fm1 = new Ym2612Nuke(AbstractSoundManager.audioFormat, 0);
        SoundProvider sp1 = getSoundProvider(fm1);
        SystemProvider systemProvider = MdVdpTestUtil.createTestGenesisProvider();
        busProvider.attachDevice(vdpProvider1).attachDevice(cpu).
                attachDevice(cpuMem1).attachDevice(z80p1).attachDevice(sp1).attachDevice(systemProvider);
        return busProvider;
    }

    public static SmsBus setupNewSmsSystem(SystemProvider sp) {
        SmsBus busProvider1 = new SmsBus();
        IMemoryProvider cpuMem1 = MemoryProvider.createSmsInstance();
        cpuMem1.setRomData(new int[0xFFFF]);

        SmsVdp vdp1 = new SmsVdp(SMS, RegionDetector.Region.USA);
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(busProvider1);
        busProvider1.attachDevice(sp).attachDevice(cpuMem1).attachDevice(z80p1).attachDevice(vdp1);
        busProvider1.init();
        return busProvider1;
    }

    public static Z80BusProvider setupNewZ80System(SystemLoader.SystemType systemType) {
        IMemoryProvider memory = null;
        Z80BusProvider bus = null;

        switch (systemType) {
            case SG_1000:
                memory = MemoryProvider.createSg1000Instance();
                bus = new Sg1000Bus();
                break;
            case MSX:
                memory = MemoryProvider.createMsxInstance();
                bus = new MsxBus();
                break;
            case COLECO:
                memory = MemoryProvider.createSg1000Instance();
                bus = new ColecoBus();
                break;
            default:
                Assert.fail("Unkonwn system: " + systemType);
        }
        SystemProvider system = Z80BaseSystem.createNewInstance(systemType, DisplayWindow.HEADLESS_INSTANCE);
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(bus);
        Tms9918aVdp vdp = new Tms9918aVdp();
        bus.attachDevice(system).attachDevice(memory).attachDevice(z80p1).attachDevice(vdp);
        bus.init();
        return bus;
    }

    private static SoundProvider getSoundProvider(FmProvider fm) {
        return new SoundProviderAdapter() {
            @Override
            public FmProvider getFm() {
                return fm;
            }
        };
    }

    public static class SoundProviderAdapter implements SoundProvider {

        @Override
        public PsgProvider getPsg() {
            return null;
        }

        @Override
        public FmProvider getFm() {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public boolean isMute() {
            return false;
        }

        @Override
        public void setEnabled(boolean mute) {

        }

        @Override
        public void setEnabled(Device device, boolean enabled) {

        }
    }
}
