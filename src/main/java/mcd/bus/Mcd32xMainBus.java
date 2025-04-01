package mcd.bus;

import mcd.McdDeviceHelper;
import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.joypad.MdJoypad;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.vdp.model.MdVdpProvider;
import org.slf4j.Logger;
import s32x.bus.S32xBus;
import s32x.bus.S32xBusIntf;
import s32x.dict.S32xDict;

import java.nio.ByteBuffer;

import static m68k.cpu.Cpu.PC_MASK;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 *
 */
public class Mcd32xMainBus extends DeviceAwareBus<MdVdpProvider, MdJoypad> implements MdMainBusProvider {

    private static final Logger LOG = LogHelper.getLogger(Mcd32xMainBus.class.getSimpleName());
    public final S32xBusIntf s32xBus;
    public final MegaCdMainCpuBusIntf mcdMainBus;
    private final MdMainBusProvider mdBus;

    private boolean cartBoot;


    public Mcd32xMainBus(McdDeviceHelper.McdLaunchContext mcdLaunchContext) {
        this.mdBus = mcdLaunchContext.mdBus;
        s32xBus = S32xBus.createS32xBus(mdBus);
        mcdMainBus = mcdLaunchContext.mainBus;
    }

    @Override
    public void init() {
        super.init();
        mcdMainBus.init();
        s32xBus.init();
        cartBoot = true;
        //TODO improve, 32x set cart = NOT_PRESENT
        if (getSystem().getMediaSpec().cdFile.bootable) {
            s32xBus.setRom(ByteBuffer.allocate(0));
            cartBoot = false;
        }
    }

    @Override
    public MdMainBusProvider attachDevice(Device device) {
        super.attachDevice(device);
        mcdMainBus.attachDevice(device);
        s32xBus.attachDevice(device);
        return (MdMainBusProvider) mdBus.attachDevice(device);
    }

    @Override
    public int read(int address, Size size) {
        assert cartBoot == mcdMainBus.isEnableMode1();
        address &= PC_MASK;
//        SystemLoader.SystemType st = byAddress(address);
//        LogHelper.logWarnOnceForce(LOG, "{} Read {}", st, th(address));
        return switch (byAddress(address, cartBoot)) {
            case S32X -> s32xBus.read(address, size);
            case MEGACD -> mcdMainBus.read(address, size);
            default -> throw new RuntimeException(byAddress(address, cartBoot).toString());
        };
    }

    @Override
    public void write(int address, int data, Size size) {
        assert cartBoot == mcdMainBus.isEnableMode1();
        address &= PC_MASK;
//        SystemLoader.SystemType st = byAddress(address);
//        LogHelper.logWarnOnceForce(LOG, "{} Write {}", st, th(address));
        switch (byAddress(address, cartBoot)) {
            case S32X -> s32xBus.write(address, data, size);
            case MEGACD -> mcdMainBus.write(address, data, size);
            default -> throw new RuntimeException(byAddress(address, cartBoot).toString());
        }
    }

    /**
     * 0x84_0000 ... 0x88_0000 FB
     * 0xA1_5100 ... 0xA1_5400 regs
     */
    private static SystemLoader.SystemType byAddress(int address, boolean cartBoot) {
        if (cartBoot) {    //TODO doom fusion
            if (address >= S32xDict.M68K_START_ROM_MIRROR && address < S32xDict.M68K_END_ROM_MIRROR) {
                return SystemLoader.SystemType.S32X;
            }
            if ((address < S32xDict.M68K_END_VECTOR_ROM)) {
                return SystemLoader.SystemType.S32X;
            }
        } else {
            //rom mirror should not work as there is no cart
            assert !(address >= S32xDict.M68K_START_ROM_MIRROR && address < S32xDict.M68K_END_ROM_MIRROR) : th(address);
        }
        boolean is32xAddr =
                (address >= S32xDict.M68K_START_FRAME_BUFFER && address < S32xDict.M68K_END_OVERWRITE_IMAGE) ||
                        (address >= S32xDict.M68K_START_32X_SYSREG && address < S32xDict.M68K_END_32X_COLPAL) ||
                        (address >= S32xDict.M68K_START_MARS_ID && address < S32xDict.M68K_END_MARS_ID);
        return is32xAddr ? SystemLoader.SystemType.S32X : SystemLoader.SystemType.MEGACD;
    }

    @Override
    public void handleVdpInterrupts68k() {
        mdBus.handleVdpInterrupts68k();
    }

    @Override
    public void ackInterrupt68k(int level) {
        mdBus.ackInterrupt68k(level);
    }

    @Override
    public boolean is68kRunning() {
        return mdBus.is68kRunning();
    }

    @Override
    public void handleVdpInterruptsZ80() {
        mdBus.handleVdpInterruptsZ80();
    }

    @Override
    public void resetFrom68k() {
        mdBus.resetFrom68k();
    }

    @Override
    public void setVdpBusyState(MdVdpProvider.VdpBusyState state) {
        mdBus.setVdpBusyState(state);
    }

    @Override
    public boolean isZ80Running() {
        return mdBus.isZ80Running();
    }

    @Override
    public boolean isZ80ResetState() {
        return mdBus.isZ80ResetState();
    }

    @Override
    public boolean isZ80BusRequested() {
        return mdBus.isZ80BusRequested();
    }

    @Override
    public void setZ80ResetState(boolean z80ResetState) {
        mdBus.setZ80ResetState(z80ResetState);
    }

    @Override
    public void setZ80BusRequested(boolean z80BusRequested) {
        mdBus.setZ80BusRequested(z80BusRequested);
    }

    @Override
    public PsgProvider getPsg() {
        return mdBus.getPsg();
    }

    @Override
    public FmProvider getFm() {
        return mdBus.getFm();
    }

    @Override
    public SystemProvider getSystem() {
        return mdBus.getSystem();
    }

    @Override
    public MdVdpProvider getVdp() {
        return mdBus.getVdp();
    }

    @Override
    public MdCartInfoProvider getCartridgeInfoProvider() {
        return mdBus.getCartridgeInfoProvider();
    }
}
