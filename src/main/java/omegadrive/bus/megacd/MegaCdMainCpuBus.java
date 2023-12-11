package omegadrive.bus.megacd;

import omegadrive.bus.md.GenesisBus;
import omegadrive.system.MegaCd;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Random;

import static omegadrive.bus.megacd.MegaCdMemoryContext.*;
import static omegadrive.util.S32xUtil.readBuffer;
import static omegadrive.util.S32xUtil.writeBuffer;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MegaCdMainCpuBus extends GenesisBus {

    private static final Logger LOG = LogHelper.getLogger(MegaCdMainCpuBus.class.getSimpleName());

    private static final int MCD_MAIN_PRG_RAM_WINDOW_SIZE = 0x20_000;
    public static final int MCD_MAIN_PRG_RAM_WINDOW_MASK = MCD_MAIN_PRG_RAM_WINDOW_SIZE - 1;
    private static final int START_MCD_MAIN_PRG_RAM = 0x20_000;
    private static final int END_MCD_MAIN_PRG_RAM = START_MCD_MAIN_PRG_RAM +
            MCD_MAIN_PRG_RAM_WINDOW_SIZE;
    private static final int START_MCD_WORD_RAM = 0x200_000;
    private static final int END_MCD_WORD_RAM = START_MCD_WORD_RAM + MCD_WORD_RAM_SIZE;
    private static final int START_MCD_BOOT_ROM = 0x400_000;
    private static final int END_MCD_BOOT_ROM = 0x420_000;

    private static final int START_MCD_MAIN_GA_COMM_R = MEGA_CD_EXP_START + 0x10;
    private static final int END_MCD_MAIN_GA_COMM_R = START_MCD_MAIN_GA_COMM_R + 0x20;
    private static final int START_MCD_MAIN_GA_COMM_W = START_MCD_MAIN_GA_COMM_R;
    private static final int END_MCD_MAIN_GA_COMM_W = START_MCD_MAIN_GA_COMM_W + 0x10;

    //TODO ??
    private static final int START_MCD_WORD_RAM_WINDOW = 0x600_000;
    private ByteBuffer prgRam, gateRegs, wordRam;
    private int prgRamBankValue = 0, prgRamBankShift = 0;

    private boolean enableMCDBus = true;

    Random random = new Random(0L);

    public MegaCdMainCpuBus(MegaCdMemoryContext ctx) {
        prgRam = ByteBuffer.wrap(ctx.prgRam);
        gateRegs = ByteBuffer.wrap(ctx.mainGateRegs);
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
            if (address >= START_MCD_MAIN_PRG_RAM && address < END_MCD_MAIN_PRG_RAM) {
                int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                return readBuffer(prgRam, addr, size);
            } else if (address >= START_MCD_BOOT_ROM && address < END_MCD_BOOT_ROM) {
                address &= DEFAULT_ROM_END_ADDRESS;
                //cart not asserted, mode 1
            } else if (address >= 0x420_000 && address < 0x420_000 + MCD_MAIN_PRG_RAM_WINDOW_SIZE) {
                return read(address & DEFAULT_ROM_END_ADDRESS, size);
            } else if (address >= START_MCD_WORD_RAM && address < END_MCD_WORD_RAM) {
                return readBuffer(wordRam, address & MCD_WORD_RAM_MASK, size);
            }
        }
        return super.read(address, size);
    }

    @Override
    public void write(int address, int data, Size size) {
        if (enableMCDBus) {
            if (address >= START_MCD_MAIN_PRG_RAM && address < END_MCD_MAIN_PRG_RAM) {
                int addr = prgRamBankShift | (address & MCD_MAIN_PRG_RAM_WINDOW_MASK);
                writeBuffer(prgRam, addr, data, size);
                return;
            } else if (address >= MEGA_CD_EXP_START && address <= MEGA_CD_EXP_END) {
                handleMegaCdExpWrite(address, data, size);
                return;
            } else if (address >= START_MCD_WORD_RAM && address < END_MCD_WORD_RAM) {
                writeBuffer(wordRam, address & MCD_WORD_RAM_MASK, data, size);
                return;
            } else if (address >= 0x420_000 && address < 0x420_000 + MCD_MAIN_PRG_RAM_WINDOW_SIZE) {
                write(address & DEFAULT_ROM_END_ADDRESS, data, size);
                return;
            }
        }
        super.write(address, data, size);
    }

    private int handleMegaCdExpRead(int address, Size size) {
        int res = readBuffer(gateRegs, address & MCD_GATE_REGS_MASK, size);
        LOG.info("M Read MEGA_CD_EXP: {}, {}, {}", th(address),
                th(res), size);
        if (address >= START_MCD_MAIN_GA_COMM_R && address < END_MCD_MAIN_GA_COMM_R) { //MAIN,SUB COMM
            LOG.info("M Read MEGA_CD_COMM: {}, {}, {}", th(address), th(res), size);
        }
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        LOG.info("M Write MEGA_CD_EXP: {}, {}, {}", th(address),
                th(data), size);
        writeBuffer(gateRegs, address & MCD_GATE_REGS_MASK, data, size);
        if (address >= START_MCD_MAIN_GA_COMM_W && address < END_MCD_MAIN_GA_COMM_W) { //MAIN COMM
            LOG.info("M Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            return;
        }
        assert size != Size.LONG;
        int regEven = (address & MCD_GATE_REGS_MASK) & ~1;
        int regVal = readBuffer(gateRegs, regEven, Size.WORD);
        switch (regEven) {
            case 0:
                int sreset = regVal & 1;
                int sbusreq = regVal & 2;
                int secIntReg = (regVal >> 8) & 1;
                LOG.info("M SubCpu reset: {}, busReq: {}", (sreset == 0 ? "Reset" : "Run"), (sbusreq == 0 ? "Cancel" : "Request"));
                if (sreset == 0 && MegaCd.secCpuResetFrameCounter == 0) {
                    MegaCd.secCpuResetFrameCounter = 7;
                    LOG.info("M SecCpu reset started");
                }
                if (secIntReg > 0) {
                    //TODO
                    LOG.info("M trigger SubCpu int2 request");
                }
                break;
            case 2:
                //bk0,1
                int bval = (regVal >> 5) & 3;
                if (bval != prgRamBankValue) {
                    prgRamBankValue = (regVal >> 5) & 3;
                    prgRamBankShift = prgRamBankValue << 17;
                    LOG.info("M PRG_RAM bank set: {} {}", prgRamBankValue, th(prgRamBankShift));
                }
                break;
            case 0xE:
                LOG.info("M write COMM_FLAG: {} {}", th(data), size);
                break;
            default:
                LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }
}
