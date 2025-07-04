package s32x.bus;

import omegadrive.Device;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.bus.md.MdBus;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.joypad.MdJoypad;
import omegadrive.sound.PwmProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.*;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport;
import omegadrive.vdp.model.MdVdpProvider;
import org.slf4j.Logger;
import s32x.DmaFifo68k;
import s32x.S32XMMREG;
import s32x.dict.S32xDict;
import s32x.savestate.Gs32xStateHandler;
import s32x.sh2.Sh2;
import s32x.sh2.Sh2Context;
import s32x.util.BiosHolder;
import s32x.vdp.MarsVdp;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;

import static m68k.cpu.Cpu.PC_MASK;
import static omegadrive.util.BufferUtil.assertionsEnabled;
import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.LogHelper.logWarnOnceWhenEn;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class S32xBus extends DeviceAwareBus<MdVdpProvider, MdJoypad> implements S32xBusIntf {

    private static final Logger LOG = LogHelper.getLogger(S32xBus.class.getSimpleName());
    static final boolean verboseMd = false;
    private BiosHolder.BiosData bios68k;
    private S32XMMREG s32XMMREG;
    public Sh2Context masterCtx, slaveCtx;
    private Sh2 sh2;
    private S32xBusContext busContext;
    private int bankSetShift;

    protected MdMainBusProvider mdBus;

    static class S32xBusContext implements Serializable {
        @Serial
        private static final long serialVersionUID = 3705180248407931780L;
        public final byte[] writeableHint = new byte[4];
        public int bankSetValue;
    }

    public static S32xBusIntf createS32xBus(MdMainBusProvider mdBus) {
        return new S32xBus(mdBus);
    }

    public static S32xBusIntf createS32xBus() {
        return new S32xBus(new MdBus());
    }

    private S32xBus(MdMainBusProvider mdBus) {
        busContext = new S32xBusContext();
        bankSetShift = busContext.bankSetValue << 20;
        Util.writeData(busContext.writeableHint, 0, -1, Size.LONG);
        this.mdBus = mdBus;
        Gs32xStateHandler.addDevice(this);
    }

    @Override
    public void init() {
        super.init();
        mdBus.init();
    }

    @Override
    public MdMainBusProvider attachDevice(Device device) {
        super.attachDevice(device);
        if (device instanceof Sh2) {
            sh2 = (Sh2) device;
        } else if (device instanceof S32XMMREG) {
            s32XMMREG = (S32XMMREG) device;
        } else if (vdpProvider != null) {
            vdpProvider.addVdpEventListener(this);
        }
        mdBus.attachDevice(device);
        return this;
    }

    public void setRom(ByteBuffer b) {
        s32XMMREG.setCart(b.capacity());
    }

    @Override
    public void setSh2Context(Sh2Context master, Sh2Context slave) {
        this.masterCtx = master;
        this.slaveCtx = slave;
    }

    public int getBankSetValue() {
        return busContext.bankSetValue;
    }

    @Override
    public int read(int address, Size size) {
        int res = 0;
        if (s32XMMREG.aden > 0) {
            res = readAdapterEnOn(address & PC_MASK, size);
        } else {
            res = readAdapterEnOff(address & PC_MASK, size);
        }
        return res & size.getMask();
    }

    @Override
    public void write(int address, int data, Size size) {
        data &= size.getMask();
        address &= PC_MASK;
        if (verboseMd) {
            LOG.info("Write address: {}, data: {}, size: {}", th(address), th(data), size);
        }
        if (s32XMMREG.aden > 0) {
            writeAdapterEnOn(address, data, size);
        } else {
            writeAdapterEnOff(address, data, size);
        }
    }

    @Override
    public void writeIoPort(int port, int value) {
        mdBus.writeIoPort(port, value);
    }

    @Override
    public MdCartInfoProvider getCartridgeInfoProvider() {
        return mdBus.getCartridgeInfoProvider();
    }

    @Override
    public void handleVdpInterrupts68k() {
        mdBus.handleVdpInterrupts68k();
    }

    @Override
    public void handleVdpInterruptsZ80() {
        mdBus.handleVdpInterruptsZ80();
    }

    @Override
    public void ackInterrupt68k(int level) {
        mdBus.ackInterrupt68k(level);
    }

    @Override
    public void resetFrom68k() {
        mdBus.resetFrom68k();
    }

    @Override
    public boolean is68kRunning() {
        return mdBus.is68kRunning();
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
    public int readIoPort(int port) {
        return mdBus.readIoPort(port);
    }

    private int readAdapterEnOn(int address, Size size) {
        int res = 0;
        if (address < S32xDict.M68K_END_VECTOR_ROM) {
            res = bios68k.readBuffer(address, size);
            if (address >= S32xDict.M68K_START_HINT_VECTOR_WRITEABLE && address < S32xDict.M68K_END_HINT_VECTOR_WRITEABLE) {
                res = readHIntVector(address, size);
            }
        } else if (address >= S32xDict.M68K_START_ROM_MIRROR && address < S32xDict.M68K_END_ROM_MIRROR) {
            if (!DmaFifo68k.rv) {
                res = mdBus.read(address & S32xDict.M68K_ROM_WINDOW_MASK, size);
            } else {
                LOG.warn("Ignoring read access to ROM mirror when RV={}, addr: {} {}", DmaFifo68k.rv, th(address), size);
            }
        } else if (address >= S32xDict.M68K_START_ROM_MIRROR_BANK && address < S32xDict.M68K_END_ROM_MIRROR_BANK) {
            if (!DmaFifo68k.rv) {
                res = mdBus.read(bankSetShift | (address & S32xDict.M68K_ROM_MIRROR_MASK), size);
            } else {
                LOG.warn("Ignoring read access to ROM mirror bank when RV={}, addr: {} {}", DmaFifo68k.rv, th(address), size);
            }
        } else if (address >= S32xDict.M68K_START_FRAME_BUFFER && address < S32xDict.M68K_END_FRAME_BUFFER) {
            res = read32xWord((address & S32xDict.DRAM_MASK) | S32xDict.START_DRAM, size);
        } else if (address >= S32xDict.M68K_START_OVERWRITE_IMAGE && address < S32xDict.M68K_END_OVERWRITE_IMAGE) {
            res = read32xWord((address & S32xDict.DRAM_MASK) | S32xDict.START_OVER_IMAGE, size);
        } else if (address >= S32xDict.M68K_START_32X_SYSREG && address < S32xDict.M68K_END_32X_SYSREG) {
            res = read32xWord((address & S32xDict.M68K_MASK_32X_SYSREG) | S32xDict.SH2_SYSREG_32X_OFFSET, size);
        } else if (address >= S32xDict.M68K_START_32X_VDPREG && address < S32xDict.M68K_END_32X_VDPREG) {
            res = read32xWord((address & S32xDict.M68K_MASK_32X_VDPREG) | S32xDict.SH2_VDPREG_32X_OFFSET, size);
        } else if (address >= S32xDict.M68K_START_32X_COLPAL && address < S32xDict.M68K_END_32X_COLPAL) {
            res = read32xWord((address & S32xDict.M68K_MASK_32X_COLPAL) | S32xDict.SH2_COLPAL_32X_OFFSET, size);
        } else if (address >= S32xDict.M68K_START_MARS_ID && address < S32xDict.M68K_END_MARS_ID) {
            assert address == S32xDict.M68K_START_MARS_ID;
            res = 0x4d415253; //'MARS'
        } else {
            if (!DmaFifo68k.rv && address <= DEFAULT_ROM_END_ADDRESS) {
                logWarnOnce(LOG, "Ignoring read access to ROM when RV={}, addr: {} {}", DmaFifo68k.rv, th(address), size);
                return size.getMask();
            }
            //NOTE: only works when assertions are enabled
            if (assertionsEnabled && romReadQuirk(address)) {
                return size.getMask();
            }
            res = mdBus.read(address, size);
        }
        if (verboseMd) {
            LOG.info("Read address: {}, size: {}, result: {}", th(address), size, th(res));
        }
        return res;
    }

    private int readAdapterEnOff(int address, Size size) {
        int res = 0;
        if (address >= S32xDict.M68K_START_MARS_ID && address < S32xDict.M68K_END_MARS_ID) {
            res = 0x4d415253; //'MARS'
        } else if (address >= S32xDict.M68K_START_32X_SYSREG && address < S32xDict.M68K_END_32X_SYSREG) {
            res = read32xWord((address & S32xDict.M68K_MASK_32X_SYSREG) | S32xDict.SH2_SYSREG_32X_OFFSET, size);
        } else if (address >= S32xDict.M68K_START_32X_VDPREG && address < S32xDict.M68K_END_32X_VDPREG) {
            res = read32xWord((address & S32xDict.M68K_MASK_32X_VDPREG) | S32xDict.SH2_VDPREG_32X_OFFSET, size);
        } else if (address >= S32xDict.M68K_START_32X_COLPAL && address < S32xDict.M68K_END_32X_COLPAL) {
            res = read32xWord((address & S32xDict.M68K_MASK_32X_COLPAL) | S32xDict.SH2_COLPAL_32X_OFFSET, size);
        } else {
            res = mdBus.read(address, size);
        }
        if (verboseMd) {
            LOG.info("Read address: {}, size: {}, result: {}", th(address), size, th(res));
        }
        return res;
    }

    //quirk: https://github.com/viciious/32XDK/wiki/Bugs-and-quirks#about-rom-read-when-rv1
    private boolean romReadQuirk(int address) {
        if ((address & 0xFFFF_CFFC) == 0x70 && address > 0x100) {
            LogHelper.logWarnOnce(LOG, "Unable to read from ROM address: {} when RV=1", th(address));
            return true;
        }
        return false;
    }

    private void writeAdapterEnOn(int address, int data, Size size) {
        if (address >= S32xDict.M68K_START_FRAME_BUFFER && address < S32xDict.M68K_END_FRAME_BUFFER) {
            write32xWord((address & S32xDict.DRAM_MASK) | S32xDict.START_DRAM, data, size);
        } else if (address >= S32xDict.M68K_START_OVERWRITE_IMAGE && address < S32xDict.M68K_END_OVERWRITE_IMAGE) {
            write32xWord((address & S32xDict.DRAM_MASK) | S32xDict.START_OVER_IMAGE, data, size);
        } else if (address >= S32xDict.M68K_START_32X_SYSREG && address < S32xDict.M68K_END_32X_SYSREG) {
            int addr = (address & S32xDict.M68K_MASK_32X_SYSREG) | S32xDict.SH2_SYSREG_32X_OFFSET;
            write32xWordDirect(addr, data, size);
            checkBankSetRegister(addr, size);
        } else if (address >= S32xDict.M68K_START_32X_VDPREG && address < S32xDict.M68K_END_32X_VDPREG) {
            write32xWord((address & S32xDict.M68K_MASK_32X_VDPREG) | S32xDict.SH2_VDPREG_32X_OFFSET, data, size);
        } else if (address >= S32xDict.M68K_START_32X_COLPAL && address < S32xDict.M68K_END_32X_COLPAL) {
            write32xWord((address & S32xDict.M68K_MASK_32X_COLPAL) | S32xDict.SH2_COLPAL_32X_OFFSET, data, size);
        } else if (address >= S32xDict.M68K_START_ROM_MIRROR_BANK && address < S32xDict.M68K_END_ROM_MIRROR_BANK) {
            if (!DmaFifo68k.rv) {
                //NOTE it could be writing to SRAM via the rom mirror
                mdBus.write((address & S32xDict.M68K_ROM_MIRROR_MASK) | bankSetShift, data, size);
            } else {
                LOG.warn("Ignoring write access to ROM mirror bank when RV={}, addr: {}, addr68k: {}, val: {} {}",
                        DmaFifo68k.rv, th(address), Util.th(address & S32xDict.M68K_ROM_WINDOW_MASK), th(data), size);
            }
        } else if (address >= S32xDict.M68K_START_ROM_MIRROR && address < S32xDict.M68K_END_ROM_MIRROR) {
            //NOTE should not happen, SoulStar buggy?
            if (!DmaFifo68k.rv) {
                mdBus.write(address & S32xDict.M68K_ROM_WINDOW_MASK, data, size);
            } else {
                LOG.warn("Ignoring write access to ROM mirror when RV={}, addr: {}, addr68k: {}, val: {} {}",
                        DmaFifo68k.rv, th(address), Util.th(address & S32xDict.M68K_ROM_WINDOW_MASK), th(data), size);
            }
        } else if (address >= S32xDict.M68K_START_HINT_VECTOR_WRITEABLE && address < S32xDict.M68K_END_HINT_VECTOR_WRITEABLE) {
            if (verboseMd) LOG.info("HINT vector write, address: {}, data: {}, size: {}", th(address),
                    th(data), size);
            Util.writeData(busContext.writeableHint, address & 3, data, size);
        } else {
            if (address < S32xDict.M68K_END_VECTOR_ROM) {
                logWarnOnceWhenEn(LOG, "Ignoring write access to vector rom, RV={}, addr: {} {}", DmaFifo68k.rv, th(address), size);
                return;
            }
            if (!DmaFifo68k.rv && address <= DEFAULT_ROM_END_ADDRESS) {
                LOG.warn("Ignoring write access to ROM when RV={}, addr: {} {}", DmaFifo68k.rv, th(address), size);
                return;
            }
            mdBus.write(address, data, size);
        }
    }

    private void writeAdapterEnOff(int address, int data, Size size) {
        if (address >= S32xDict.M68K_START_32X_SYSREG && address < S32xDict.M68K_END_32X_SYSREG) {
            int addr = (address & S32xDict.M68K_MASK_32X_SYSREG) | S32xDict.SH2_SYSREG_32X_OFFSET;
            write32xWordDirect(addr, data, size);
            checkBankSetRegister(addr, size);
        } else if (address >= S32xDict.M68K_START_32X_VDPREG && address < S32xDict.M68K_END_32X_VDPREG) {
            write32xWord((address & S32xDict.M68K_MASK_32X_VDPREG) | S32xDict.SH2_VDPREG_32X_OFFSET, data, size);
        } else if (address >= S32xDict.M68K_START_32X_COLPAL && address < S32xDict.M68K_END_32X_COLPAL) {
            write32xWord((address & S32xDict.M68K_MASK_32X_COLPAL) | S32xDict.SH2_COLPAL_32X_OFFSET, data, size);
        } else {
            mdBus.write(address, data, size);
        }
    }

    private void checkBankSetRegister(int address, Size size) {
        S32xDict.RegSpecS32x r = S32xDict.getRegSpec(BufferUtil.CpuDeviceAccess.M68K, address);
        if (r == S32xDict.RegSpecS32x.MD_BANK_SET) {
            int val = BufferUtil.readWordFromBuffer(s32XMMREG.regContext, r);
            busContext.bankSetValue = (val & 3);
            bankSetShift = busContext.bankSetValue << 20;
            if (verboseMd) LOG.info("BankSet now: {}", busContext.bankSetValue);
        }
        assert r == S32xDict.RegSpecS32x.MD_INT_CTRL ? size != Size.LONG : true;
    }

    private void write32xWord(int address, int data, Size size) {
        if (s32XMMREG.fm > 0) {
            logWarnOnce(LOG, "Ignoring access to S32X memory from MD when FM={}, addr: {} {}", s32XMMREG.fm, th(address), size);
            return;
        }
        write32xWordDirect(address, data, size);
    }

    private void write32xWordDirect(int address, int data, Size size) {
        if (size != Size.LONG) {
            s32XMMREG.write(address, data, size);
        } else {
            s32XMMREG.write(address, (data >> 16) & 0xFFFF, Size.WORD);
            s32XMMREG.write(address + 2, data & 0xFFFF, Size.WORD);
        }
    }

    private int read32xWord(int address, Size size) {
        //TODO needs to be more granular, ie. md can access sysRegs when fm = 1
//        if (ENFORCE_FM_BIT_ON_READS && s32XMMREG.fm > 0) {
//            LOG.warn("Ignoring access to S32X memory from MD when FM={}, addr: {} {}", s32XMMREG.fm, th(address), size);
//            return size.getMask();
//        }
        if (size != Size.LONG) {
            return s32XMMREG.read(address, size);
        } else {
            int res = s32XMMREG.read(address, Size.WORD) << 16;
            return res | (s32XMMREG.read(address + 2, Size.WORD) & 0xFFFF);
        }
    }

    private int readHIntVector(int address, Size size) {
        int res = Util.readData(busContext.writeableHint, 0, Size.LONG);
        if (res != -1) {
            res = Util.readData(busContext.writeableHint, address & 3, size);
            if (verboseMd) LOG.info("HINT vector read, rv {}, address: {}, {} {}", DmaFifo68k.rv,
                    th(address), th(res), size);
        } else {
            res = 0;
        }
        return res;
    }

    //read rom area via the md memory mapper (if any)
    @Override
    public int readRom(int address, Size size) {
        assert address < DEFAULT_ROM_END_ADDRESS;
        return mdBus.read(address, size);
    }

    public MarsVdp getMarsVdp() {
        return s32XMMREG.getVdp();
    }

    @Override
    public void onVdpEvent(BaseVdpAdapterEventSupport.VdpEvent event, Object value) {
        ((BaseVdpAdapterEventSupport.VdpEventListener) mdBus).onVdpEvent(event, value); //TODO
        switch (event) {
            case V_BLANK_CHANGE -> s32XMMREG.setVBlank((boolean) value);
            case H_BLANK_CHANGE -> s32XMMREG.setHBlank((boolean) value);
            case VIDEO_MODE -> s32XMMREG.updateVideoMode((VideoMode) value);
        }
    }

    public S32XMMREG getS32XMMREG() {
        return s32XMMREG;
    }

    public void setBios68k(BiosHolder.BiosData bios68k) {
        this.bios68k = bios68k;
    }

    public PwmProvider getPwm() {
        return soundProvider.getPwm();
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        super.saveContext(buffer);
        buffer.put(Util.serializeObject(busContext));
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        super.loadContext(buffer);
        Serializable s = Util.deserializeObject(buffer);
        assert s instanceof S32xBusContext;
        busContext = (S32xBusContext) s;
        bankSetShift = busContext.bankSetValue << 20;
    }

    public void resetSh2() {
        BufferUtil.CpuDeviceAccess cpu = MdRuntimeData.getAccessTypeExt();
        //NOTE this changes the access type
        sh2.reset(masterCtx);
        sh2.reset(slaveCtx);
        masterCtx.devices.sh2MMREG.reset();
        slaveCtx.devices.sh2MMREG.reset();
        s32XMMREG.fm = 0;
        MdRuntimeData.setAccessTypeExt(cpu);
    }
}
