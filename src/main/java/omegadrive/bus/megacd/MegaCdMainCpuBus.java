package omegadrive.bus.megacd;

import omegadrive.bus.md.GenesisBus;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.system.MegaCd;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static omegadrive.bus.megacd.MegaCdMemoryContext.*;
import static omegadrive.util.S32xUtil.readBuffer;
import static omegadrive.util.S32xUtil.writeBuffer;
import static omegadrive.util.Util.random;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
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
    private static final int MCD_MAIN_PRG_RAM_WINDOW_SIZE = 0x20_000;
    public static final int MCD_MAIN_PRG_RAM_WINDOW_MASK = MCD_MAIN_PRG_RAM_WINDOW_SIZE - 1;
    private static final int START_MCD_MAIN_PRG_RAM = 0x20_000;
    private static final int END_MCD_MAIN_PRG_RAM = START_MCD_MAIN_PRG_RAM +
            MCD_MAIN_PRG_RAM_WINDOW_SIZE;

    private static final int START_MCD_MAIN_PRG_RAM_MODE1 = 0x400_000 | START_MCD_MAIN_PRG_RAM;
    private static final int END_MCD_MAIN_PRG_RAM_MODE1 = START_MCD_MAIN_PRG_RAM_MODE1 +
            MCD_MAIN_PRG_RAM_WINDOW_SIZE;
    private static final int START_MCD_WORD_RAM = 0x200_000;
    private static final int END_MCD_WORD_RAM = START_MCD_WORD_RAM + MCD_WORD_RAM_SIZE;

    private static final int START_MCD_WORD_RAM_MODE1 = 0x600_000;
    private static final int END_MCD_WORD_RAM_MODE1 = START_MCD_WORD_RAM_MODE1 + MCD_WORD_RAM_SIZE;

    private static final int MCD_BOOT_ROM_SIZE = 0x20_000;
    private static final int MCD_BOOT_ROM_MASK = MCD_BOOT_ROM_SIZE - 1;
    private static final int START_MCD_BOOT_ROM = 0;
    private static final int END_MCD_BOOT_ROM = MCD_BOOT_ROM_SIZE;
    private static final int START_MCD_BOOT_ROM_MODE1 = START_MCD_BOOT_ROM + 0x400_000;
    private static final int END_MCD_BOOT_ROM_MODE1 = START_MCD_BOOT_ROM_MODE1 + MCD_BOOT_ROM_SIZE;

    private static final int START_MCD_MAIN_GA_COMM_R = MEGA_CD_EXP_START + 0x10;
    private static final int END_MCD_MAIN_GA_COMM_R = START_MCD_MAIN_GA_COMM_R + 0x20;
    private static final int START_MCD_MAIN_GA_COMM_W = START_MCD_MAIN_GA_COMM_R;
    private static final int END_MCD_MAIN_GA_COMM_W = START_MCD_MAIN_GA_COMM_W + 0x10;

    private ByteBuffer prgRam, gateRegs, wordRam, subGateRegs;
    private int prgRamBankValue = 0, prgRamBankShift = 0;

    private boolean enableMCDBus = true, enableMode1 = true;

    static String biosBasePath = "res/bios/mcd";

    static String masterBiosName = "bios_us.bin";
    private static ByteBuffer bios;
    private LogHelper logHelper = new LogHelper();

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
        prgRam = ByteBuffer.wrap(ctx.prgRam);
        gateRegs = ByteBuffer.wrap(ctx.mainGateRegs);
        subGateRegs = ByteBuffer.wrap(ctx.subGateRegs);
        wordRam = ByteBuffer.wrap(ctx.wordRam);
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
                    return readBuffer(wordRam, address & MCD_WORD_RAM_MASK, size);
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
                    return readBuffer(wordRam, address & MCD_WORD_RAM_MASK, size);
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
                    writeBuffer(wordRam, address & MCD_WORD_RAM_MASK, data, size);
                    return;
                }
            } else {
                //TODO test
                if (address >= START_MCD_MAIN_PRG_RAM && address < END_MCD_MAIN_PRG_RAM) {
                    int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                    writeBuffer(prgRam, addr, data, size);
                    return;
                } else if (address >= START_MCD_WORD_RAM && address < END_MCD_WORD_RAM) {
                    writeBuffer(wordRam, address & MCD_WORD_RAM_MASK, data, size);
                    return;
                }
            }
        }
        super.write(address, data, size);
    }

    private int handleMegaCdExpRead(int address, Size size) {
        int res = readBuffer(gateRegs, address & MCD_GATE_REGS_MASK, size);
        logHelper.logWarnOnce(LOG, "M Read MEGA_CD_EXP: {}, {}, {}", th(address),
                th(res), size);
        if (address >= START_MCD_MAIN_GA_COMM_R && address < END_MCD_MAIN_GA_COMM_R) { //MAIN,SUB COMM
            LOG.info("M Read MEGA_CD_COMM: {}, {}, {}", th(address), th(res), size);
        }
        //TODO bios_us.bin RET bit
        if (address == 0xA12003 && size == Size.BYTE) {
            res |= random.nextInt(2);
        }
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        LOG.info("M Write MEGA_CD_EXP: {}, {}, {}", th(address),
                th(data), size);
        if (address >= START_MCD_MAIN_GA_COMM_W && address < END_MCD_MAIN_GA_COMM_W) { //MAIN COMM
            LOG.info("M Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBuffer(gateRegs, address & MCD_GATE_REGS_MASK, data, size);
            writeBuffer(subGateRegs, address & MDC_SUB_GATE_REGS_SIZE, data, size); //write to sub reg copy
            return;
        }
        assert size != Size.LONG;
        int regEven = (address & MCD_GATE_REGS_MASK) & ~1;
        int regVal = readBuffer(gateRegs, regEven, Size.WORD);
        writeBuffer(gateRegs, address & MCD_GATE_REGS_MASK, data, size);
        int newVal = readBuffer(gateRegs, regEven, Size.WORD);
        if (regVal == newVal) {
            return;
        }
        switch (regEven) {
            case 0:
                int sreset = newVal & 1;
                int sbusreq = newVal & 2;
                int secIntReg = (newVal >> 8) & 1;
                LOG.info("M SubCpu reset: {}, busReq: {}", (sreset == 0 ? "Reset" : "Run"), (sbusreq == 0 ? "Cancel" : "Request"));
                MC68000Wrapper m68k = (MC68000Wrapper) MegaCd.secCpuBusHack.getBusDeviceIfAny(M68kProvider.class).get();
                if (sreset > 0) {
                    if (sbusreq == 0) {
                        m68k.reset();
                        MegaCd.secCpuBusHack.resetDone();
                        LOG.info("M SubCpu reset done, now running");
                    }
                    m68k.setStop(sbusreq > 0);
                } else { //sreset = 0
                    m68k.setStop(true);
                    LOG.info("M SubCpu stopped");
                }
                if (secIntReg > 0) {
                    LOG.info("M trigger SubCpu int2 request");
                    m68k.raiseInterrupt(2);
                }
                break;
            case 2:
                if ((newVal & 0xFF00) != 0) {
                    LOG.warn("M Mem Write protect bits set: {}", th(newVal));
                }
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
                //bk0,1
                int bval = (newVal >> 5) & 3;
                if (bval != prgRamBankValue) {
                    prgRamBankValue = (newVal >> 5) & 3;
                    prgRamBankShift = prgRamBankValue << 17;
                    LOG.info("M PRG_RAM bank set: {} {}", prgRamBankValue, th(prgRamBankShift));
                }
                break;
            case 0xE:
                //main can only write to MSB
                assert size == Size.BYTE && (address & 1) == 0;
                writeBuffer(subGateRegs, address & MDC_SUB_GATE_REGS_SIZE, data, size); //write to sub reg copy
                LOG.info("M write COMM_FLAG: {} {}", th(data), size);
                break;
            default:
                LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }
}
