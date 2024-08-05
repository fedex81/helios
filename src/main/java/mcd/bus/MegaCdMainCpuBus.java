package mcd.bus;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.util.McdBiosHolder;
import omegadrive.bus.md.GenesisBus;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_LEVEL2;
import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdMemoryContext.WRITABLE_HINT_UNUSED;
import static mcd.dict.MegaCdMemoryContext.WramSetup;
import static mcd.util.McdRegBitUtil.setSharedBit;
import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.readBufferWord;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 * <p>
 */
public class MegaCdMainCpuBus extends GenesisBus {

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

    private static ByteBuffer bios;
    private LogHelper logHelper = new LogHelper();
    private MegaCdMemoryContext memCtx;

    private McdBiosHolder biosHolder;

    public MC68000Wrapper subCpu;
    public MegaCdSubCpuBus subCpuBus;
    private CpuDeviceAccess cpu;

    private int maskMode1;

    public MegaCdMainCpuBus(MegaCdMemoryContext ctx) {
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
    }

    @Override
    public void init() {
        super.init();
        enableMode1 = true;
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
    public int read(int address, Size size) {
        address &= MD_PC_MASK;
        if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
            if (!enableMCDBus) {
                LOG.info("Enabling MegaCD bus mapping");
                enableMCDBus = true;
            }
            return handleMegaCdExpRead(address, size) & size.getMask();
        }
        int res = size.getMask();
        if (enableMCDBus) {
            int addr = address | maskMode1;
            if (addr >= M68K_START_HINT_VECTOR_WRITEABLE_M1 && addr < M68K_END_HINT_VECTOR_WRITEABLE_M1) {
                return readHintVector(addr, size);
            }
            if (addr >= START_MCD_WORD_RAM_MODE1 && addr < END_MCD_WORD_RAM_MODE1) {
                res = memCtx.readWordRam(cpu, addr, size);
            } else if (addr >= START_MCD_MAIN_PRG_RAM_MODE1 && addr < END_MCD_MAIN_PRG_RAM_MODE1) {
                addr = prgRamBankShift | (addr & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                res = readBuffer(prgRam, addr, size);
            } else if (addr >= START_MCD_BOOT_ROM_MODE1 && addr < END_MCD_BOOT_ROM_MODE1) {
                res = readBuffer(bios, addr & MCD_BOOT_ROM_MASK, size);
            } else {
                res = super.read(address, size);
            }
        }
        return res & size.getMask();
    }

    @Override
    public void write(int address, int data, Size size) {
        address &= MD_PC_MASK;
        if (enableMCDBus) {
            if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
                handleMegaCdExpWrite(address, data, size);
                return;
            }
            int addr = address | maskMode1;
            if (addr >= START_MCD_MAIN_PRG_RAM_MODE1 && addr < END_MCD_MAIN_PRG_RAM_MODE1) {
                addr = prgRamBankShift | (addr & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                writeBufferRaw(prgRam, addr, data, size);
                return;
            } else if (addr >= START_MCD_WORD_RAM_MODE1 && addr < END_MCD_WORD_RAM_MODE1) {
                memCtx.writeWordRam(cpu, addr, data, size);
                return;
            }
        }
        super.write(address, data, size);
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
        assert (address & 0xFF) < MCD_GATE_REGS_SIZE;
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
        checkRegLongAccess(regSpec, size);
        switch (regSpec.deviceType) {
            case SYS -> handleSysRegWrite(regSpec, address, data, size);
            case COMM -> handleCommWrite(regSpec, address, data, size);
            default -> LOG.error("M illegal write MEGA_CD_EXP reg: {} ({}), {} {}", th(address), regSpec, data, size);
        }
    }

    private void handleSysRegWrite(RegSpecMcd regSpec, int address, int data, Size size) {
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
                LOG.info("M write MCD_HINT_VECTOR: {} {}", th(data), size);
                writeBufferRaw(sysGateRegs, regSpec.addr, data, size);
                Util.writeData(memCtx.writeableHint, 2, data, size);
            }
            default -> LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }

    public static boolean subCpuReset = false;

    private void handleReg0Write(int address, int data, Size size) {
        int curr = readBufferWord(sysGateRegs, MCD_RESET.addr);
        int res = memCtx.handleRegWrite(cpu, MCD_RESET, address, data, size);
        int sreset = res & 1;
        int sbusreq = (res >> 1) & 1;
        int subIntReg = (res >> 8) & 1; //IFL2
        assert subCpu != null && subCpuBus != null;

        //TODO this triggers only 0 -> 1 ??
        subCpuBus.getInterruptHandler().setIFL2(subIntReg > 0);
        int prevSubIntReg = 0;//(curr >> 8) & 1;
        if (prevSubIntReg == 0 && subIntReg > 0) {
            LogHelper.logInfo(LOG, "M SubCpu int2 request");
            subCpuBus.getInterruptHandler().raiseInterrupt(INT_LEVEL2);
        }
        if ((address & 1) == 0 && size == Size.BYTE) {
            return;
        }
        LogHelper.logInfo(LOG, "M SubCpu reset: {}, busReq: {}", (sreset == 0 ? "Reset" : "Run"), (sbusreq == 0 ? "Cancel" : "Request"));
        if ((curr & 3) == (res & 3)) {
            return;
        }
        //reset only when 0->1
        if (sreset > 0) {
            if (sbusreq == 0) {
                subCpuReset = true;
            } else {
                subCpu.setStop(sbusreq > 0);
            }
        } else { //sreset = 0
            subCpu.setStop(true);
            LOG.info("M SubCpu stopped");
        }
    }

    private void handleReg2Write(int address, int data, Size size) {
        int resWord = memCtx.handleRegWrite(cpu, MCD_MEM_MODE, address, data, size);
        WramSetup ws = memCtx.update(cpu, resWord);
        if (ws == WramSetup.W_2M_SUB) { //set RET=0
            setBitVal(sysGateRegs, MCD_MEM_MODE.addr + 1, 0, 0, Size.BYTE);
            setSharedBit(memCtx, M68K, 0, SharedBitDef.RET);
        }
        //bk0,1
        int bval = (resWord >> 6) & 3;
        if (bval != prgRamBankValue) {
            prgRamBankValue = bval;
            prgRamBankShift = prgRamBankValue << 17;
            LOG.info("M PRG_RAM bank set: {} {}", prgRamBankValue, th(prgRamBankShift));
        }
    }

    private void handleCommWrite(RegSpecMcd regSpec, int address, int data, Size size) {
        if (address >= START_MCD_MAIN_GA_COMM_W && address < END_MCD_MAIN_GA_COMM_W) { //MAIN COMM
            LogHelper.logInfo(LOG, "M Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBufferRaw(commonGateRegs, address & MCD_GATE_REGS_MASK, data, size);
            return;
        }
        if (address >= END_MCD_MAIN_GA_COMM_W && address < END_MCD_MAIN_GA_COMM_R) { //MAIN COMM READ ONLY
            LOG.error("M illegal write read-only MEGA_CD_COMM reg: {}", th(address));
            return;
        }
    }

    public boolean isEnableMode1() {
        return enableMode1;
    }

    public boolean isBios() {
        return isBios;
    }

    public void logAccess(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        logHelper.logWarningOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }
}
