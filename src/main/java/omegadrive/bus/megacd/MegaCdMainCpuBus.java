package omegadrive.bus.megacd;

import omegadrive.bus.md.GenesisBus;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.system.MegaCd;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;

import static omegadrive.bus.megacd.MegaCdDict.*;
import static omegadrive.bus.megacd.MegaCdDict.RegSpecMcd.*;
import static omegadrive.bus.megacd.MegaCdMemoryContext.MCD_GATE_REGS_MASK;
import static omegadrive.bus.megacd.MegaCdMemoryContext.WramSetup;
import static omegadrive.bus.megacd.MegaCdRegWriteHandlers.setByteHandlersMain;
import static omegadrive.util.S32xUtil.*;
import static omegadrive.util.Util.random;
import static omegadrive.util.Util.th;
import static s32x.util.S32xUtil.CpuDeviceAccess.M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 * <p>
 * TODO DMNA, RET etc bit/reg sync
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
    private S32xUtil.CpuDeviceAccess cpu;

    static {
        Path p = Paths.get(biosBasePath, masterBiosName);
        try {
            assert p.toFile().exists();
            byte[] b = FileUtil.readFileSafe(p);
            assert b.length > 0;
            bios = ByteBuffer.wrap(b);
            LOG.info("Loading bios at {}, size: {}", p.toAbsolutePath(), b.length);
        } catch (Error | Exception e) {
            LOG.error("Unable to load bios at {}", p.toAbsolutePath());
            bios = ByteBuffer.allocate(MCD_BOOT_ROM_SIZE);
        }
    }

    public MegaCdMainCpuBus(MegaCdMemoryContext ctx) {
        cpu = M68K;
        prgRam = ByteBuffer.wrap(ctx.prgRam);
        sysGateRegs = ByteBuffer.wrap(ctx.getGateSysRegs(cpu));
        commonGateRegs = ByteBuffer.wrap(ctx.commonGateRegs);
        memCtx = ctx;
        writeBuffer(sysGateRegs, MCD_RESET.addr, 2, Size.WORD); //DMNA=0, RET=1
        writeBuffer(sysGateRegs, MCD_MEM_MODE.addr + 1, 1, Size.BYTE); //DMNA=0, RET=1
        writeBuffer(sysGateRegs, MCD_CDC_REG_DATA.addr, 0xFFFF, Size.WORD);
    }

    @Override
    public void init() {
        super.init();
        //bios_us.bin
        if ("BR 000006-2.00".equals(cartridgeInfoProvider.getSerial())) {
            enableMode1 = false;
        }
    }

    @Override
    public int read(int address, Size size) {
        if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
            if (!enableMCDBus) {
                LOG.info("Enabling MegaCD bus mapping");
                enableMCDBus = true;
            }
            return handleMegaCdExpRead(address, size);
        }
        if (enableMCDBus) {
            if (enableMode1) {
                if (address >= START_MCD_WORD_RAM_MODE1 && address < END_MCD_WORD_RAM_MODE1) {
                    return memCtx.readWordRam(cpu, address, size);
                } else if (address >= START_MCD_MAIN_PRG_RAM_MODE1 && address < END_MCD_MAIN_PRG_RAM_MODE1) {
                    int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    return readBuffer(prgRam, addr, size);
                } else if (address >= START_MCD_BOOT_ROM_MODE1 && address < END_MCD_BOOT_ROM_MODE1) {
                    return readBuffer(bios, address & MCD_BOOT_ROM_MASK, size);
                }
            } else {
                //TODO test
                if (address >= START_MCD_MAIN_PRG_RAM && address < END_MCD_MAIN_PRG_RAM) {
                    int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    return readBuffer(prgRam, addr, size);
                } else if (address >= START_MCD_BOOT_ROM && address < END_MCD_BOOT_ROM) {
                    return readBuffer(bios, address & MCD_BOOT_ROM_MASK, size);
                } else if (address >= START_MCD_WORD_RAM && address < END_MCD_WORD_RAM) {
                    return memCtx.readWordRam(cpu, address, size);
                }
            }
        }
        return super.read(address, size);
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
                    writeBuffer(prgRam, addr, data, size);
                    return;
                } else if (address >= START_MCD_WORD_RAM_MODE1 && address < END_MCD_WORD_RAM_MODE1) {
                    memCtx.writeWordRam(cpu, address, data, size);
                    return;
                }
            } else {
                //TODO test
                if (address >= START_MCD_MAIN_PRG_RAM && address < END_MCD_MAIN_PRG_RAM) {
                    int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    writeBuffer(prgRam, addr, data, size);
                    return;
                } else if (address >= START_MCD_WORD_RAM && address < END_MCD_WORD_RAM) {
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
        ByteBuffer regs = getRegBuffer(regSpec);
        int res = readBuffer(regs, address & MCD_GATE_REGS_MASK, size);
        //TODO bios_us.bin RET bit
        //TODO subCpu never returns WRAM to main as subCpu is stuck in CDD
        if (!enableMode1 && address == 0xA12003 && size == Size.BYTE) {
            res |= random.nextInt(2);
        }
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        RegSpecMcd regSpec = MegaCdDict.getRegSpec(cpu, address);
        MegaCdDict.logAccess(regSpec, cpu, address, data, size, false);
        if (regSpec == RegSpecMcd.INVALID) {
            LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
            return;
        }
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
            }
            case MCD_HINT_VECTOR -> {
                LOG.info("M write MCD_HINT_VECTOR: {} {}", th(data), size);
                writeBuffer(sysGateRegs, regSpec.addr, data, size);
            }
            default -> LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }

    private void handleReg0Write(int address, int data, Size size) {
        int curr = readBuffer(sysGateRegs, MCD_RESET.addr, Size.WORD);
        int res = handleRegWrite(MCD_RESET, address, data, size);
        int sreset = res & 1;
        int sbusreq = (res >> 1) & 1;
        int subIntReg = (res >> 8) & 1;

        MC68000Wrapper m68k = (MC68000Wrapper) MegaCd.subCpuBusHack.getBusDeviceIfAny(M68kProvider.class).get();
        if (subIntReg > 0) {
            LOG.info("M trigger SubCpu int2 request");
            m68k.raiseInterrupt(2);
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
                m68k.reset();
                MegaCd.subCpuBusHack.resetDone();
                LOG.info("M SubCpu reset done, now running");
            }
            m68k.setStop(sbusreq > 0);
        } else { //sreset = 0
            m68k.setStop(true);
            LOG.info("M SubCpu stopped");
        }
    }

    private void handleReg2Write(int address, int data, Size size) {
        int res = handleRegWrite(MCD_MEM_MODE, address, data, size);
        if ((res & 0xFF00) != 0) {
            LOG.warn("M Mem Write protect bits set: {}", th(data));
        }
        WramSetup ws = memCtx.update(cpu, res);
        if (ws == WramSetup.W_2M_SUB) { //set RET=0
            res = setBitVal(sysGateRegs, MCD_MEM_MODE.addr + 1, 0, 0, Size.BYTE);
        }
        logWram(res);
        //bk0,1
        int bval = (res >> 5) & 3;
        if (bval != prgRamBankValue) {
            prgRamBankValue = bval;
            prgRamBankShift = prgRamBankValue << 17;
            LOG.info("M PRG_RAM bank set: {} {}", prgRamBankValue, th(prgRamBankShift));
        }
    }

    private int handleRegWrite(RegSpecMcd regSpec, int address, int data, Size size) {
        BiConsumer<ByteBuffer, Integer>[] setByteRegHandler = setByteHandlersMain[regSpec.addr];
        ByteBuffer b = getRegBuffer(regSpec);
        assert setByteRegHandler != null;
        switch (size) {
            case WORD -> {
                setByteRegHandler[1].accept(b, data); //LSB
                setByteRegHandler[0].accept(b, data >> 8); //MSB
            }
            case BYTE -> setByteRegHandler[address & 1].accept(b, data);
        }
        return readBuffer(b, 0, Size.WORD);
    }

    private void handleCommWrite(RegSpecMcd regSpec, int address, int data, Size size) {
        if (address >= START_MCD_MAIN_GA_COMM_W && address < END_MCD_MAIN_GA_COMM_W) { //MAIN COMM
            LOG.info("M Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBuffer(commonGateRegs, address & MCD_GATE_REGS_MASK, data, size);
            return;
        }
        if (address >= END_MCD_MAIN_GA_COMM_W && address < END_MCD_MAIN_GA_COMM_R) { //MAIN COMM READ ONLY
            LOG.error("M illegal write read-only MEGA_CD_COMM reg: {}", th(address));
            return;
        }
    }

    public void logAccess(RegSpecMcd regSpec, S32xUtil.CpuDeviceAccess cpu, int address, int value, Size size, boolean read) {
        logHelper.logWarningOnce(LOG, "{} MCD reg {} {} ({}) {} {}", cpu, read ? "read" : "write",
                size, regSpec.getName(), th(address), !read ? ": " + th(value) : "");
    }

    private ByteBuffer getRegBuffer(MegaCdDict.RegSpecMcd regSpec) {
        if (regSpec.addr >= 8) {
            return commonGateRegs;
        }
        return sysGateRegs;
    }

    private void logWram(int newVal) {
        int mode = newVal & 4;
        int dmna = newVal & 2;
        int ret = newVal & 1;
        String str = mode == 0 ? "2M" : "1M";
        //MASTER bios sets it
//                assert ret == 0; //only SUB able to write to
        //1M, only dmna = 1 is significant, 0 is set by hw when the request is done
        if (mode > 0 && dmna > 0) {
            str += ",SWAP_REQ";
        }
        //2M
        if (mode == 0 && dmna > 0) {
            str += dmna > 0 ? ",sub(2M WRAM)" : ",main(2M WRAM)";
        }
        LOG.info(str);
    }
}
