package mcd.bus;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.Device;
import omegadrive.bus.md.MdBus;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.Size;
import omegadrive.vdp.model.MdVdpProvider;
import s32x.bus.S32xBus;
import s32x.bus.S32xBusIntf;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 *
 * @Deprecated rewrite
 */
public class Mcd32xMainBus extends MdBus {
    public final S32xBusIntf s32xBus;
    public final MegaCdMainCpuBusIntf mcdMainBus;

    private final MdMainBusProvider busHack;

    private final static AtomicBoolean notFound = new AtomicBoolean();

    public Mcd32xMainBus(MegaCdMemoryContext cdMemoryContext) {
        busHack = getBusHack();
        s32xBus = S32xBus.createS32xBus(this);
        mcdMainBus = new MegaCdMainCpuBus(cdMemoryContext, this);
    }

    private static MdMainBusProvider getBusHack() {
        return new MdMainBusProviderAdapter() {
            @Override
            public int read(int address, Size size) {
                notFound.set(true);
                return 0;
            }

            @Override
            public void write(int address, int data, Size size) {
                notFound.set(true);
            }
        };
    }

    private final AtomicBoolean inited = new AtomicBoolean(false);

    @Override
    public void init() {
        if (inited.compareAndSet(false, true)) {
            super.init();
            mcdMainBus.init();
            s32xBus.init();
        } else {
            System.err.println("Recursive init call");
        }
    }

    @Override
    public MdMainBusProvider attachDevice(Device device) {
        if (getBusDeviceIfAny(device.getClass()).isEmpty()) {
            super.attachDevice(device);
        }
        if (mcdMainBus.getBusDeviceIfAny(device.getClass()).isEmpty()) {
            mcdMainBus.attachDevice(device);
        }
        if (s32xBus.getBusDeviceIfAny(device.getClass()).isEmpty()) {
            s32xBus.attachDevice(device);
        }
        return this;
    }

    int depth = 0;

    @Override
    public int read(int address, Size size) {
        if (++depth >= 2) {
            notFound.set(true);
            return 0;
        }
        notFound.set(false);
        int res = s32xBus.read(address, size);
        if (notFound.compareAndSet(true, false)) {
            res = mcdMainBus.read(address, size);
            if (notFound.compareAndSet(true, false)) {
                res = readData(address, size);
                assert !notFound.get();
            }
        }
        depth = 0;
        return res;
    }

    @Override
    public void write(int address, int data, Size size) {
        if (++depth >= 2) {
            notFound.set(true);
            return;
        }
        notFound.set(false);
        s32xBus.write(address, data, size);
        if (notFound.compareAndSet(true, false)) {
            mcdMainBus.write(address, data, size);
            if (notFound.compareAndSet(true, false)) {
                writeData(address, data, size);
                assert !notFound.get();
            }
        }
        depth = 0;
    }

    static class MdMainBusProviderAdapter implements MdMainBusProvider {

        @Override
        public int read(int address, Size size) {
            return 0;
        }

        @Override
        public void write(int address, int data, Size size) {
        }

        @Override
        public BaseBusProvider attachDevice(Device device) {
            return null;
        }

        @Override
        public <T extends Device> Optional<T> getBusDeviceIfAny(Class<T> clazz) {
            return Optional.empty();
        }

        @Override
        public <T extends Device> Set<T> getAllDevices(Class<T> clazz) {
            return null;
        }

        @Override
        public void handleVdpInterrupts68k() {

        }

        @Override
        public void ackInterrupt68k(int level) {

        }

        @Override
        public boolean is68kRunning() {
            return false;
        }

        @Override
        public void handleVdpInterruptsZ80() {

        }

        @Override
        public void resetFrom68k() {

        }

        @Override
        public void setVdpBusyState(MdVdpProvider.VdpBusyState state) {

        }

        @Override
        public boolean isZ80Running() {
            return false;
        }

        @Override
        public boolean isZ80ResetState() {
            return false;
        }

        @Override
        public boolean isZ80BusRequested() {
            return false;
        }

        @Override
        public void setZ80ResetState(boolean z80ResetState) {

        }

        @Override
        public void setZ80BusRequested(boolean z80BusRequested) {

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
        public SystemProvider getSystem() {
            return null;
        }

        @Override
        public MdVdpProvider getVdp() {
            return null;
        }

        @Override
        public MdCartInfoProvider getCartridgeInfoProvider() {
            return null;
        }
    }
}
