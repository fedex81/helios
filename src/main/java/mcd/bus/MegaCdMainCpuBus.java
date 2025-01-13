package mcd.bus;

import mcd.cdd.CdBiosHelper;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.util.McdBiosHolder;
import omegadrive.Device;
import omegadrive.bus.DeviceAwareBus;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.joypad.MdJoypad;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.vdp.model.MdVdpProvider;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Objects;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_LEVEL2;
import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.util.McdRegBitUtil.setSharedBitBothCpu;
import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static omegadrive.util.Util.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 * <p>
 */
public class MegaCdMainCpuBus extends DeviceAwareBus<MdVdpProvider, MdJoypad> implements MegaCdMainCpuBusIntf {

    private static final Logger LOG = LogHelper.getLogger(MegaCdMainCpuBus.class.getSimpleName());

    public static final int MCD_GATE_REGS_SIZE = 0x40;
    public static final int MCD_GATE_REGS_MASK = MCD_GATE_REGS_SIZE - 1;

    /**
     * 3 states
     * - mega cd disconnected
     * - mega cd connected, cart inserted (mode 1)
     * - mega cd connected, cd inserted
     * <p>
     * When a cart is inserted in the MD, the CD hardware is mapped to
     * 0x400000 instead of 0x000000. So the BIOS ROM is at 0x400_000, the
     * Program RAM bank is at 0x420_000, and the Word RAM is at 0x600_000.
     */
    private ByteBuffer prgRam, sysGateRegs, commonGateRegs;
    private int prgRamBankValue = 0, prgRamBankShift = 0;

    private boolean enableMCDBus = true, enableMode1 = false, isBios;

    public ByteBuffer bios;
    private LogHelper logHelper = new LogHelper();
    private MegaCdMemoryContext memCtx;

    private McdBiosHolder biosHolder;

    public MC68000Wrapper subCpu;
    public MegaCdSubCpuBusIntf subCpuBus;
    private CpuDeviceAccess cpu;

    private int maskMode1;

    protected MdMainBusProvider mdBus;

    @Deprecated
    public static boolean subCpuReset = false;

    @Deprecated
    //detects 0->1, 1->0 transitions only when written to
    public static int ifl2Trigger = 0;

    public MegaCdMainCpuBus(MegaCdMemoryContext ctx, MdMainBusProvider mdBus) {
        cpu = M68K;
        prgRam = ByteBuffer.wrap(ctx.prgRam);
        sysGateRegs = ctx.getGateSysRegs(cpu);
        commonGateRegs = ByteBuffer.wrap(ctx.commonGateRegs);
        memCtx = ctx;
        writeBufferRaw(sysGateRegs, MCD_RESET.addr, 2, Size.WORD); //SBRQ = 1, SRES = 0
        writeBufferRaw(sysGateRegs, MCD_MEM_MODE.addr + 1, 1, Size.BYTE); //DMNA=0, RET=1
        writeBufferRaw(sysGateRegs, MCD_CDC_REG_DATA.addr, 0xFFFF, Size.WORD);
        biosHolder = McdBiosHolder.getInstance();
        maskMode1 = !enableMode1 ? MCD_MAIN_MODE1_MASK : 0;
        this.mdBus = mdBus;
    }

    @Override
    public void init() {
        mdBus.init();
        enableMode1 = true;
        MdCartInfoProvider cartridgeInfoProvider = mdBus.getCartridgeInfoProvider();
        isBios = cartridgeInfoProvider.getSerial().startsWith("BR ");
        //bios aka bootRom
        if (isBios) {
            enableMode1 = false;
            LOG.info("Bios detected with serial: {}, disabling mode1 mapper", cartridgeInfoProvider.getSerial());
            //NOTE: load bios as a file, use it as the current bios, disregarding the default bios
            bios = McdBiosHolder.loadBios(systemProvider.getRegion(), systemProvider.getRomPath());
            return;
        }
        if (cartridgeInfoProvider.getRomContext().romFileType.isDiscImage()) {
            enableMode1 = false;
            LOG.info("CUE/ISO file detected, disabling mode1 mapper");
        }
        //mode1 and cue
        bios = biosHolder.getBiosBuffer(systemProvider.getRegion());
        maskMode1 = !enableMode1 ? MCD_MAIN_MODE1_MASK : 0;
    }

    @Override
    public MdMainBusProvider attachDevice(Device device) {
        super.attachDevice(device);
        return (MdMainBusProvider) mdBus.attachDevice(device); //TODO
    }

    @Override
    public int read(int address, Size size) {
        assert assertCheckBusOp(address, size);
        assert MdRuntimeData.getAccessTypeExt() == M68K || MdRuntimeData.getAccessTypeExt() == Z80;
        address &= MD_PC_MASK;
        if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
            if (!enableMCDBus) {
                LOG.info("Enabling MegaCD bus mapping");
                enableMCDBus = true;
            }
            return handleMegaCdExpRead(address & MCD_GATE_REGS_MASK, size) & size.getMask();
        }
        int res = size.getMask();
        if (enableMCDBus) {
            int addr = address | maskMode1;
            if (addr >= M68K_START_HINT_VECTOR_WRITEABLE_M1 && addr < M68K_END_HINT_VECTOR_WRITEABLE_M1) {
                return readHintVector(addr, size);
            }
            if (addr >= START_MCD_MAIN_WORD_RAM_MODE1 && addr < END_MCD_MAIN_WORD_RAM_MIRROR_MODE1) {
                addr &= MCD_WORD_RAM_2M_MASK;
                if (memCtx.wramSetup.mode == _1M && addr >= MCD_WORD_RAM_1M_SIZE) {
                    res = memCtx.wramHelper.readCellMapped(address, size);
                } else {
                    res = memCtx.wramHelper.readWordRam(cpu, addr, size);
                }
            } else if (addr >= START_MCD_BOOT_ROM_MODE1 && addr < END_MCD_BOOT_ROM_MIRROR_MODE1) {
                addr &= MCD_BOOT_ROM_PRGRAM_WINDOW_MASK;
                if (addr >= MCD_BOOT_ROM_WINDOW_SIZE) {
                    addr = prgRamBankShift | (addr & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    res = readBuffer(prgRam, addr, size);
                } else {
                    res = readBuffer(bios, addr & MCD_BOOT_ROM_MASK, size);
                }
            } else {
                res = mdBus.read(address, size);
            }
        }
        return res & size.getMask();
    }

    @Override
    public void write(int address, int data, Size size) {
        assert MdRuntimeData.getAccessTypeExt() == M68K || MdRuntimeData.getAccessTypeExt() == Z80;
        assert assertCheckBusOp(address, size);
        address &= MD_PC_MASK;
        if (enableMCDBus) {
            if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
                handleMegaCdExpWrite(address & MCD_GATE_REGS_MASK, data, size);
                return;
            }
            int addr = address | maskMode1;
            if (addr >= START_MCD_MAIN_PRG_RAM_MODE1 && addr < END_MCD_BOOT_ROM_MIRROR_MODE1) {
                addr &= MCD_BOOT_ROM_PRGRAM_WINDOW_MASK;
                if (addr >= MCD_BOOT_ROM_WINDOW_SIZE) {
                    if (subCpu.isStopped()) {
                        addr = prgRamBankShift | (addr & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                        writeBufferRaw(prgRam, addr, data, size);
                    } else {
                        LogHelper.logWarnOnce(LOG, "Ignoring {} writing to PRG_RAM when SUB is running",
                                MdRuntimeData.getAccessTypeExt());
                    }
                } else {
                    LOG.error("Writing to boot rom: {}({})", th(addr), th(address));
                }
                return;
            } else if (addr >= START_MCD_MAIN_WORD_RAM_MODE1 && addr < END_MCD_MAIN_WORD_RAM_MIRROR_MODE1) {
                /* WRAM-1M :
                    Word-RAM 0/1 assigned to MAIN-CPU
                    VRAM cell image mapped at $220_000-$23F_FFF
                */
                addr &= MCD_WORD_RAM_2M_MASK;
                memCtx.wramHelper.writeWordRam(cpu, addr, data, size);
                if (addr < MCD_WORD_RAM_1M_SIZE) {
                    //TODO breaks corpse killer
                    if (memCtx.wramSetup.mode == _1M) {
                        memCtx.wramHelper.writeCellMapped(addr, data, size);
                    }
                }
                return;
            }
        }
        mdBus.write(address, data, size);
        CdBiosHelper.checkMainMemRegion(memoryProvider.getRamData(), address);
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

    private int readHintVector(int addr, Size size) {
        //LONG read on 0x72 not supported
        assert size == Size.LONG ? addr == M68K_START_HINT_VECTOR_WRITEABLE_M1 : true;
        int res = Util.readData(memCtx.writeableHint, 0, Size.LONG);
        //only used if it has been set, 240p suite bios crc check
        if (res != WRITABLE_HINT_UNUSED) {
            res = Util.readData(memCtx.writeableHint, addr & 3, size);
        }
        return res;
    }

    private int handleMegaCdExpRead(int address, Size size) {
        return switch (size) {
            case WORD, BYTE -> handleMegaCdExpReadInternal(address, size);
            case LONG -> ((handleMegaCdExpReadInternal(address, Size.WORD) & 0xFFFF) << 16) |
                    (handleMegaCdExpReadInternal(address + 2, Size.WORD) & 0xFFFF);
        };
    }

    private int handleMegaCdExpReadInternal(int address, Size size) {
        assert (address & 0xFF) < MCD_GATE_REGS_SIZE;
        assert size != Size.LONG;
        RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpu, address);
        logAccess(regSpec, cpu, address, 0, size, true);
        if (regSpec == RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return 0;
        }
        checkRegLongAccess(regSpec, size);
        int res = readBuffer(memCtx.getRegBuffer(cpu, regSpec), address & MCD_GATE_REGS_MASK, size);
        if (regSpec == MCD_COMM_FLAGS) {
//            LogHelper.logInfo(LOG, "M read COMM_FLAG {}: {} {}", th(address), th(res), size);
        }
        //TODO hack
        if (regSpec == MCD_CDC_HOST) {
            int addr = END_MCD_SUB_PCM_AREA + regSpec.addr + (address & 1);
            res = subCpuBus.read(addr, size);
        }
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        assert (address & 0xFF) < MCD_GATE_REGS_SIZE;
        RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpu, address);
        MegaCdDict.logAccess(regSpec, cpu, address, data, size, false);
        if (regSpec == RegSpecMcd.INVALID) {
            LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
            return;
        }
//        checkRegLongAccess(regSpec, size);
        switch (regSpec.deviceType) {
            case SYS -> handleSysRegWrite(regSpec, address, data, size);
            case COMM -> handleCommWrite(regSpec, address, data, size);
            default -> LOG.error("M illegal write MEGA_CD_EXP reg: {} ({}), {} {}", th(address), regSpec, data, size);
        }
    }

    private void handleSysRegWrite(RegSpecMcd regSpec, int address, int data, Size size) {
        switch (size) {
            case WORD, BYTE -> handleSysRegWriteInternal(regSpec, address, data, size);
            case LONG -> {
                handleMegaCdExpWrite(address, data >> 16, Size.WORD);
                handleMegaCdExpWrite(address + 2, data, Size.WORD);
            }
        }
    }

    private void handleSysRegWriteInternal(RegSpecMcd regSpec, int address, int data, Size size) {
        assert size != Size.LONG;
        switch (regSpec) {
            case MCD_RESET -> handleReg0Write(address, data, size);
            case MCD_MEM_MODE -> handleReg2Write(address, data, size);
            case MCD_CDC_MODE, MCD_CDC_HOST -> {
                assert false : regSpec;
            } //not writable
            case MCD_COMM_FLAGS -> {
                //main can only write to MSB (even byte), WORD write becomes a BYTE write
//                assert size == Size.BYTE ? (address & 1) == 0 : true;
                address &= ~1;
                LogHelper.logInfo(LOG, "M write COMM_FLAG {}: {} {}", th(address), th(data), size);
                writeBufferRaw(sysGateRegs, address & MCD_GATE_REGS_MASK, data, Size.BYTE);
                writeBufferRaw(memCtx.getRegBuffer(SUB_M68K, regSpec), address & MCD_GATE_REGS_MASK, data, Size.BYTE);
            }
            case MCD_HINT_VECTOR -> {
                assert size == Size.WORD;
                if (assertionsEnabled) {
                    int prev = Util.readData(memCtx.writeableHint, 2, Size.WORD);
                    if (prev != data) {
                        LOG.info("M write MCD_HINT_VECTOR: {} {}", th(data), size);
                    }
                }
                writeBufferRaw(sysGateRegs, regSpec.addr, data, size);
                Util.writeData(memCtx.writeableHint, 2, data, size);
            }
            default -> LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }
    private void handleReg0Write(int address, int data, Size size) {
        int curr = readBufferWord(sysGateRegs, MCD_RESET.addr);
        int res = memCtx.handleRegWrite(cpu, MCD_RESET, address, data, size);

        int sreset = res & 1;
        int sbusreq = (res >> 1) & 1;
        assert subCpu != null && subCpuBus != null;
        handleIfl2(curr, res, address, size);

        if ((address & 1) == 0 && size == Size.BYTE) {
            return;
        }
        LogHelper.logInfo(LOG, "M SubCpu reset: {}, busReq: {}", (sreset == 0 ? "Reset" : "Run"), (sbusreq == 0 ? "Cancel" : "Request"));
        if ((curr & 3) == (res & 3)) {
            return;
        }
        boolean stopped = sreset == 0 || sbusreq > 0;
        boolean triggerReset = ((curr & 1) == 0) && sreset > 0;
        if (triggerReset) {
            subCpu.reset();
        }
        boolean prevStop = subCpu.isStopped();
        subCpu.setStop(stopped);
        if (prevStop != stopped) {
            LOG.info("M SubCpu stopped: {}", stopped);
        }
    }

    private void handleIfl2(int prev, int res, int address, Size size) {
        if ((address & 1) == 1 && size == Size.BYTE) {
            return;
        }
        int subIntReg = (res >> 8) & 1; //IFL2
        if (subIntReg > 0) {
            if (((prev >> 8) & 1) == 0) {
                ifl2Trigger = 1;
                LogHelper.logInfo(LOG, "M SubCpu int2 request");
                subCpuBus.getInterruptHandler().raiseInterrupt(INT_LEVEL2);
            }
        } else if (subIntReg == 0) {
            //explicit set ifl2 to 0
            ifl2Trigger = 0;
        }
    }

    private void handleReg2Write(int address, int data, Size size) {
//        int before = readBuffer(memCtx.getRegBuffer(cpu, MCD_MEM_MODE), MCD_MEM_MODE.addr, Size.WORD);
        int resWord = memCtx.handleRegWrite(cpu, MCD_MEM_MODE, address, data, size);
        WramSetup prev = memCtx.wramSetup;
        WramSetup ws = memCtx.wramHelper.update(cpu, resWord);

        if (ws.mode == WordRamMode._2M) { //set RET=0 for sub, RET=1 for main
            int val = ws.cpu == M68K ? 1 : 0;
            setSharedBitBothCpu(memCtx, SharedBitDef.RET, val);
        }
//        int after2 = readBuffer(memCtx.getRegBuffer(cpu, MCD_MEM_MODE), MCD_MEM_MODE.addr, Size.WORD);
//        LOG.info("{} write: {} {} {}, regBefore: {}, regAfter: {}, " +
//                "wramBefore: {}, wramAfter: {}, regAfter2: {}",
//                MCD_MEM_MODE, th(address), th(data), size, th(before), th(resWord), prev, ws, th(after2));
        subCpuBus.handleWramSetupChange(prev, ws);
        //bk0,1
        int bval = (resWord >> 6) & 3;
        if (bval != prgRamBankValue) {
            prgRamBankValue = bval;
            prgRamBankShift = prgRamBankValue << 17;
            LOG.info("M PRG_RAM bank set: {} {}", prgRamBankValue, th(prgRamBankShift));
        }
    }

    private void handleCommWrite(RegSpecMcd regSpec, int address, int data, Size size) {
        if (address >= MCD_COMM0.addr && address < MCD_COMM8.addr) { //MAIN COMM
            LogHelper.logInfo(LOG, "M Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBufferRaw(commonGateRegs, address & MCD_GATE_REGS_MASK, data, size);
            return;
        }
        if (address >= MCD_COMM8.addr && address < MCD_TIMER_INT3.addr) { //MAIN COMM READ ONLY
            LOG.error("M illegal write read-only MEGA_CD_COMM reg: {}", th(address));
            return;
        }
    }

    @Override
    public void setSubDevices(MC68000Wrapper subCpu, MegaCdSubCpuBusIntf subBus) {
        Objects.requireNonNull(subBus);
        Objects.requireNonNull(subCpu);
        this.subCpu = subCpu;
        this.subCpuBus = subBus;
    }

    public boolean isEnableMode1() {
        return enableMode1;
    }

    @Override
    public void setEnableMode1(boolean enableMode1) {
        this.enableMode1 = enableMode1;
    }

    public boolean isBios() {
        return isBios;
    }

    @Override
    public void setBios(ByteBuffer buffer) {
        this.bios = buffer;
    }

    public void logAccess(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        logHelper.logWarningOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }
}
