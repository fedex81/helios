package omegadrive.util;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.md.BusArbiter;
import omegadrive.bus.md.MdBus;
import omegadrive.bus.md.MdZ80BusProviderImpl;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.bus.model.MdZ80BusProvider;
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.bus.z80.ColecoBus;
import omegadrive.bus.z80.MsxBus;
import omegadrive.bus.z80.Sg1000Bus;
import omegadrive.bus.z80.SmsBus;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.JoypadProvider;
import omegadrive.joypad.MdJoypad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.PcmProvider;
import omegadrive.sound.PwmProvider;
import omegadrive.sound.SoundDevice;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SystemProvider;
import omegadrive.system.Z80BaseSystem;
import omegadrive.ui.DisplayWindow;
import omegadrive.vdp.SmsVdp;
import omegadrive.vdp.Tms9918aVdp;
import omegadrive.vdp.md.MdVdp;
import omegadrive.vdp.md.MdVdpMemoryInterface;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.junit.Assert;
import s32x.bus.S32xBusIntf;

import java.nio.file.Path;

import static omegadrive.SystemLoader.SystemType.SMS;
import static omegadrive.system.MediaSpecHolder.NO_ROM;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class SystemTestUtil {

    public static MdMainBusProvider setupNewMdSystem() {
        return setupNewMdSystem(MemoryProvider.createMdInstance(), MdVdpMemoryInterface.createInstance());
    }

    public static MdMainBusProvider setupNewMdSystem(VdpMemoryInterface vdpMem) {
        return setupNewMdSystem(MemoryProvider.createMdInstance(), vdpMem);
    }

    public static MdMainBusProvider setupNewMdSystem(IMemoryProvider cpuMem) {
        return setupNewMdSystem(cpuMem, MdVdpMemoryInterface.createInstance());
    }

    public static MdMainBusProvider setupNewMdSystem(IMemoryProvider cpuMem1, VdpMemoryInterface vdpMem) {
        SystemProvider systemProvider = createTestMdProvider(cpuMem1);
        MdMainBusProvider busProvider = new MdBus();
        MdZ80BusProvider z80bus = new MdZ80BusProviderImpl();
        MdVdpProvider vdpProvider1 = MdVdp.createInstance(busProvider, vdpMem);
        MC68000Wrapper cpu = new MC68000Wrapper(BufferUtil.CpuDeviceAccess.M68K, busProvider);
        MdJoypad joypad = new MdJoypad(SystemProvider.NO_CLOCK);
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(SystemLoader.SystemType.MD, busProvider);
        FmProvider fm1 = new Ym2612Nuke(AbstractSoundManager.audioFormat, 0);
        SoundProvider sp1 = getSoundProvider(fm1);
        z80bus.attachDevice(BusArbiter.NO_OP).attachDevice(busProvider);
        busProvider.attachDevice(vdpProvider1).attachDevice(cpu).attachDevice(joypad).attachDevice(z80bus).
                attachDevice(cpuMem1).attachDevice(z80p1).attachDevice(sp1).attachDevice(systemProvider);
        busProvider.init();
        return busProvider;
    }

    public static SmsBus setupNewSmsSystem(SystemProvider sp) {
        SmsBus busProvider1 = new SmsBus();
        IMemoryProvider cpuMem1 = MemoryProvider.createSmsInstance();
        cpuMem1.setRomData(new byte[0xFFFF]);

        SmsVdp vdp1 = new SmsVdp(SMS, RegionDetector.Region.USA);
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(SystemLoader.SystemType.SMS, busProvider1);
        busProvider1.attachDevice(sp).attachDevice(cpuMem1).attachDevice(z80p1).attachDevice(vdp1);
        busProvider1.init();
        return busProvider1;
    }

    public static MdMainBusProvider setupNewMdSystem(S32xBusIntf busProvider, IMemoryProvider cpuMem1) {
        VdpMemoryInterface vdpMem = MdVdpMemoryInterface.createInstance();
        MdZ80BusProvider z80bus = new MdZ80BusProviderImpl();
        MdVdpProvider vdpProvider1 = MdVdp.createInstance(busProvider, vdpMem);
        MC68000Wrapper cpu = new MC68000Wrapper(BufferUtil.CpuDeviceAccess.M68K, busProvider);
        SystemProvider systemProvider = createTestMdProvider(cpuMem1);
        MdJoypad joypad = new MdJoypad(null);
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(SystemLoader.SystemType.MD, busProvider);
        FmProvider fm1 = new Ym2612Nuke(AbstractSoundManager.audioFormat, 0);
        SoundProvider sp1 = getSoundProvider(fm1);

        z80bus.attachDevice(BusArbiter.NO_OP).attachDevice(busProvider);
        busProvider.attachDevice(vdpProvider1).attachDevice(cpu).attachDevice(joypad).attachDevice(z80bus).
                attachDevice(cpuMem1).attachDevice(z80p1).attachDevice(sp1).attachDevice(systemProvider);
        busProvider.attachDevice(systemProvider);
        busProvider.init();
        return busProvider;
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
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(systemType, bus);
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
        public void init(RegionDetector.Region region) {
        }

        @Override
        public PsgProvider getPsg() {
            return null;
        }

        @Override
        public FmProvider getFm() {
            return null;
        }

        @Override
        public PwmProvider getPwm() {
            return null;
        }

        @Override
        public PcmProvider getPcm() {
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

        @Override
        public void updateDeviceRate(SoundDevice.SoundDeviceType sdt, RegionDetector.Region region, int clockRateHz) {
        }
    }

    public static SystemProvider createTestMdProvider(IMemoryProvider memoryProvider) {
        return new SystemProvider() {
            private MediaSpecHolder msh;

            {
                msh = NO_ROM;
                assert msh.cartFile != null;
                assert memoryProvider.getRomData() != null;
                msh.cartFile.mediaInfoProvider = MdCartInfoProvider.createMdInstance(memoryProvider.getRomData());
                msh.reload();
            }

            @Override
            public RegionDetector.Region getRegion() {
                return null;
            }

            @Override
            public void handleSystemEvent(SystemEvent event, Object parameter) {

            }

            @Override
            public boolean isRomRunning() {
                return false;
            }

            @Override
            public void init() {

            }

            @Override
            public MediaSpecHolder getMediaSpec() {
                return msh;
            }

            @Override
            public Path getRomPath() {
                return null;
            }


            @Override
            public SystemLoader.SystemType getSystemType() {
                return SystemLoader.SystemType.MD;
            }

            @Override
            public void reset() {

            }
        };
    }


    public static JoypadProvider createTestJoypadProvider() {
        return new MdJoypad(SystemProvider.NO_CLOCK) {

            @Override
            public void init() {
            }

            @Override
            public void setButtonAction(InputProvider.PlayerNumber number, JoypadButton button, JoypadAction action) {
                System.out.println(number + "," + button + "," + action);
            }

            @Override
            public boolean hasDirectionPressed(InputProvider.PlayerNumber number) {
                return false;
            }

            @Override
            public String getState(InputProvider.PlayerNumber number) {
                return "test";
            }

            @Override
            public void newFrame() {

            }
        };
    }
}
