package s32x.util;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.md.BusArbiter;
import omegadrive.bus.md.GenesisZ80BusProviderImpl;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.bus.model.GenesisZ80BusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.joypad.GenesisJoypad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.PcmProvider;
import omegadrive.sound.PwmProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil;
import omegadrive.util.RegionDetector;
import omegadrive.vdp.md.GenesisVdp;
import omegadrive.vdp.md.GenesisVdpMemoryInterface;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import s32x.bus.S32xBus;

import java.nio.file.Path;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class SystemTestUtil {

    public static GenesisBusProvider setupNewMdSystem(S32xBus busProvider, IMemoryProvider cpuMem1) {
        VdpMemoryInterface vdpMem = GenesisVdpMemoryInterface.createInstance();
        GenesisZ80BusProvider z80bus = new GenesisZ80BusProviderImpl();
        GenesisVdpProvider vdpProvider1 = GenesisVdp.createInstance(busProvider, vdpMem);
        MC68000Wrapper cpu = new MC68000Wrapper(BufferUtil.CpuDeviceAccess.M68K, busProvider);
        SystemProvider systemProvider = createTestGenesisProvider(cpuMem1);
        GenesisJoypad joypad = new GenesisJoypad(null);
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(SystemLoader.SystemType.GENESIS, busProvider);
        FmProvider fm1 = new Ym2612Nuke(AbstractSoundManager.audioFormat, 0);
        SoundProvider sp1 = getSoundProvider(fm1);

        z80bus.attachDevice(BusArbiter.NO_OP).attachDevice(busProvider);
        busProvider.attachDevice(vdpProvider1).attachDevice(cpu).attachDevice(joypad).attachDevice(z80bus).
                attachDevice(cpuMem1).attachDevice(z80p1).attachDevice(sp1).attachDevice(systemProvider);
        busProvider.init();
        return busProvider;
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
    }

    public static SystemProvider createTestGenesisProvider(IMemoryProvider memoryProvider) {
        return new SystemProvider() {

            private RomContext romContext;

            {
                romContext = new RomContext();
                romContext.cartridgeInfoProvider = MdCartInfoProvider.createInstance(memoryProvider, null);
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
            public RomContext getRomContext() {
                return romContext;
            }

            @Override
            public Path getRomPath() {
                return null;
            }


            @Override
            public SystemLoader.SystemType getSystemType() {
                return SystemLoader.SystemType.GENESIS;
            }

            @Override
            public void reset() {

            }
        };
    }
}
