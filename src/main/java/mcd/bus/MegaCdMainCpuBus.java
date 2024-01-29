package mcd.bus;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import mcd.dict.MegaCdMemoryContext.WordRamMode;
import omegadrive.bus.md.GenesisBus;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdMemoryContext.MCD_GATE_REGS_MASK;
import static mcd.dict.MegaCdMemoryContext.WramSetup;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 * <p>
 */
public class MegaCdMainCpuBus extends GenesisBus {

    private static final Logger LOG = LogHelper.getLogger(MegaCdMainCpuBus.class.getSimpleName());

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

    private boolean enableMCDBus = true, enableMode1 = true;

    static String biosBasePath = "res/bios/mcd";

    static String masterBiosName = "bios_us.bin";
    private static ByteBuffer bios;
    private LogHelper logHelper = new LogHelper();
    private MegaCdMemoryContext memCtx;

    public MC68000Wrapper subCpu;
    public MegaCdSubCpuBus subCpuBus;
    private CpuDeviceAccess cpu;

    static {
        Path p = Paths.get(biosBasePath, masterBiosName);
        loadBios(RegionDetector.Region.USA, p);
    }

    public MegaCdMainCpuBus(MegaCdMemoryContext ctx) {
        cpu = M68K;
        prgRam = ByteBuffer.wrap(ctx.prgRam);
        sysGateRegs = ctx.getGateSysRegs(cpu);
        commonGateRegs = ByteBuffer.wrap(ctx.commonGateRegs);
        memCtx = ctx;
        writeBufferRaw(sysGateRegs, MCD_RESET.addr, 2, Size.WORD); //DMNA=0, RET=1
        writeBufferRaw(sysGateRegs, MCD_MEM_MODE.addr + 1, 1, Size.BYTE); //DMNA=0, RET=1
        writeBufferRaw(sysGateRegs, MCD_CDC_REG_DATA.addr, 0xFFFF, Size.WORD);
    }

    @Override
    public void init() {
        super.init();
        enableMode1 = true;
        boolean isBios = cartridgeInfoProvider.getRomName().toLowerCase().contains("bios") &&
                cartridgeInfoProvider.getSerial().startsWith("BR ");
        //bios aka bootRom
        if (isBios) {
            enableMode1 = false;
            LOG.info("Bios detected with serial: {}, disabling mode1 mapper", cartridgeInfoProvider.getSerial());
            //TODO doesnt work
            loadBios(systemProvider.getRegion(), systemProvider.getRomPath());
        }
    }

    private static void loadBios(RegionDetector.Region region, Path p) {
        try {
            assert p.toFile().exists();
            byte[] b = FileUtil.readFileSafe(p);
            assert b.length > 0;
            bios = ByteBuffer.wrap(b);
            LOG.info("Loading bios at {}, region: {}, size: {}", p.toAbsolutePath(), region, b.length);
        } catch (Error | Exception e) {
            LOG.error("Unable to load bios at {}", p.toAbsolutePath());
            bios = ByteBuffer.allocate(MCD_BOOT_ROM_SIZE);
        }
    }

    @Override
    public int read(int address, Size size) {
        if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
            if (!enableMCDBus) {
                LOG.info("Enabling MegaCD bus mapping");
                enableMCDBus = true;
            }
            return handleMegaCdExpRead(address, size) & size.getMask();
        }
        int res = size.getMask();
        if (enableMCDBus) {
            if (enableMode1) {
                if (address >= START_MCD_WORD_RAM_MODE1 && address < END_MCD_WORD_RAM_MODE1) {
                    assert memCtx.wramSetup.mode == WordRamMode._1M ? address < END_MCD_WORD_RAM_1M_MODE1 : true;
                    res = memCtx.readWordRam(cpu, address, size);
                } else if (address >= START_MCD_MAIN_PRG_RAM_MODE1 && address < END_MCD_MAIN_PRG_RAM_MODE1) {
                    int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    res = readBuffer(prgRam, addr, size);
                } else if (address >= START_MCD_BOOT_ROM_MODE1 && address < END_MCD_BOOT_ROM_MODE1) {
                    res = readBuffer(bios, address & MCD_BOOT_ROM_MASK, size);
                } else {
                    res = super.read(address, size);
                }
            } else {
                //TODO test
                if (address >= START_MCD_MAIN_PRG_RAM && address < END_MCD_MAIN_PRG_RAM) {
                    int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    res = readBuffer(prgRam, addr, size);
                } else if (address >= START_MCD_BOOT_ROM && address < END_MCD_BOOT_ROM) {
                    res = readBuffer(bios, address & MCD_BOOT_ROM_MASK, size);
                } else if (address >= START_MCD_WORD_RAM && address < END_MCD_WORD_RAM) {
                    assert memCtx.wramSetup.mode == WordRamMode._1M ? address < END_MCD_WORD_RAM_1M_MODE1 : true;
                    res = memCtx.readWordRam(cpu, address, size);
                } else {
                    res = super.read(address, size);
                }
            }
        }
        return res & size.getMask();
    }

    @Override
    public void write(int address, int data, Size size) {
        if (enableMCDBus) {
            if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
                handleMegaCdExpWrite(address, data, size);
                return;
            }
            if (enableMode1) {
                if (address >= START_MCD_MAIN_PRG_RAM_MODE1 && address < END_MCD_MAIN_PRG_RAM_MODE1) {
                    int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    writeBufferRaw(prgRam, addr, data, size);
                    return;
                } else if (address >= START_MCD_WORD_RAM_MODE1 && address < END_MCD_WORD_RAM_MODE1) {
                    assert memCtx.wramSetup.mode == WordRamMode._1M ? address < END_MCD_WORD_RAM_1M_MODE1 : true;
                    memCtx.writeWordRam(cpu, address, data, size);
                    return;
                }
            } else {
                //TODO test
                if (address >= START_MCD_MAIN_PRG_RAM && address < END_MCD_MAIN_PRG_RAM) {
                    int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    writeBufferRaw(prgRam, addr, data, size);
                    return;
                } else if (address >= START_MCD_WORD_RAM && address < END_MCD_WORD_RAM) {
                    assert memCtx.wramSetup.mode == WordRamMode._1M ? address < END_MCD_WORD_RAM_1M_MODE1 : true;
                    memCtx.writeWordRam(cpu, address, data, size);
                    return;
                }
            }
        }
        super.write(address, data, size);
    }

    private int handleMegaCdExpRead(int address, Size size) {
        RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpu, address);
        logAccess(regSpec, cpu, address, 0, size, true);
        if (regSpec == RegSpecMcd.INVALID) {
            LOG.error("M read unknown MEGA_CD_EXP reg: {}", th(address));
            return 0;
        }
        checkRegLongAccess(regSpec, size);
        ByteBuffer regs = memCtx.getRegBuffer(cpu, regSpec);
        int res = readBuffer(regs, address & MCD_GATE_REGS_MASK, size);
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
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
                //main can only write to MSB
                assert size == Size.BYTE && (address & 1) == 0;
                LOG.info("M write COMM_FLAG: {} {}", th(data), size);
                writeBufferRaw(sysGateRegs, address & MCD_GATE_REGS_MASK, data, size);
                writeBufferRaw(memCtx.getRegBuffer(SUB_M68K, regSpec), address & MCD_GATE_REGS_MASK, data, size);
            }
            case MCD_HINT_VECTOR -> {
                LOG.info("M write MCD_HINT_VECTOR: {} {}", th(data), size);
                writeBufferRaw(sysGateRegs, regSpec.addr, data, size);
            }
            default -> LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }

    private void handleReg0Write(int address, int data, Size size) {
        int curr = readBuffer(sysGateRegs, MCD_RESET.addr, Size.WORD);
        int res = memCtx.handleRegWrite(cpu, MCD_RESET, address, data, size);
        int sreset = res & 1;
        int sbusreq = (res >> 1) & 1;
        int subIntReg = (res >> 8) & 1;
        assert subCpu != null && subCpuBus != null;

        if (subIntReg > 0) {
            boolean doIt = subCpuBus.checkInterruptEnabled(2);
            LOG.info("M trigger SubCpu int2 request, masked : {}", !doIt);
            if (doIt) {
                subCpu.raiseInterrupt(2);
            }
        }
        if ((address & 1) == 0 && size == Size.BYTE) {
            return;
        }
        LOG.info("M SubCpu reset: {}, busReq: {}", (sreset == 0 ? "Reset" : "Run"), (sbusreq == 0 ? "Cancel" : "Request"));
        if ((curr & 3) == (res & 3)) {
            return;
        }
        if (sreset > 0) {
            if (sbusreq == 0) {
                subCpu.reset();
                subCpuBus.resetDone();
                LOG.info("M SubCpu reset done, now running");
            }
            subCpu.setStop(sbusreq > 0);
        } else { //sreset = 0
            subCpu.setStop(true);
            LOG.info("M SubCpu stopped");
        }
    }

    private void handleReg2Write(int address, int data, Size size) {
        int res = memCtx.handleRegWrite(cpu, MCD_MEM_MODE, address, data, size);
        if ((res & 0xFF00) != 0) {
            logHelper.logWarningOnce(LOG, "M Mem Write protect bits set: {}", th(data));
        }
        WramSetup ws = memCtx.update(cpu, res);
        if (ws == WramSetup.W_2M_SUB) { //set RET=0
            res = setBitVal(sysGateRegs, MCD_MEM_MODE.addr + 1, 0, 0, Size.BYTE);
            memCtx.setSharedBit(M68K, MegaCdMemoryContext.SharedBit.RET, 0);
        }
        //bk0,1
        int bval = (res >> 5) & 3;
        if (bval != prgRamBankValue) {
            prgRamBankValue = bval;
            prgRamBankShift = prgRamBankValue << 17;
            LOG.info("M PRG_RAM bank set: {} {}", prgRamBankValue, th(prgRamBankShift));
        }
    }

    private void handleCommWrite(RegSpecMcd regSpec, int address, int data, Size size) {
        if (address >= START_MCD_MAIN_GA_COMM_W && address < END_MCD_MAIN_GA_COMM_W) { //MAIN COMM
            LOG.info("M Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBufferRaw(commonGateRegs, address & MCD_GATE_REGS_MASK, data, size);
            return;
        }
        if (address >= END_MCD_MAIN_GA_COMM_W && address < END_MCD_MAIN_GA_COMM_R) { //MAIN COMM READ ONLY
            LOG.error("M illegal write read-only MEGA_CD_COMM reg: {}", th(address));
            return;
        }
    }

    public void logAccess(RegSpecMcd regSpec, CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        logHelper.logWarningOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }
}
