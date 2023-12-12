package omegadrive.bus.megacd;

import omegadrive.bus.md.GenesisBus;
import omegadrive.system.MegaCd;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

import static omegadrive.bus.megacd.MegaCdMemoryContext.*;
import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.util.S32xUtil.readBuffer;
import static omegadrive.util.S32xUtil.writeBuffer;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MegaCdSecCpuBus extends GenesisBus {

    private static final Logger LOG = LogHelper.getLogger(MegaCdSecCpuBus.class.getSimpleName());

    private static final int START_MCD_SUB_PRG_RAM = 0;
    private static final int END_MCD_SUB_PRG_RAM = START_MCD_SUB_PRG_RAM + MCD_PRG_RAM_SIZE;

    private static final int START_MCD_SUB_GATE_ARRAY_REGS = 0xFF_8000;
    private static final int END_MCD_SUB_GATE_ARRAY_REGS = 0xFF_81FF;

    private static final int START_MCD_SUB_GA_COMM_W = START_MCD_SUB_GATE_ARRAY_REGS + 0x20;
    private static final int END_MCD_SUB_GA_COMM_W = START_MCD_SUB_GA_COMM_W + 0x10;
    private static final int START_MCD_SUB_GA_COMM_R = START_MCD_SUB_GATE_ARRAY_REGS + 0x10;
    private static final int END_MCD_SUB_GA_COMM_R = END_MCD_SUB_GA_COMM_W;
    private static final int SEC_CPU_REGS_MASK = MDC_SUB_GATE_REGS_SIZE - 1;
    private ByteBuffer subCpuRam, gateRegs, wordRam, mainGateRegs;

    public MegaCdSecCpuBus(MegaCdMemoryContext ctx) {
        subCpuRam = ByteBuffer.wrap(ctx.prgRam);
        gateRegs = ByteBuffer.wrap(ctx.subGateRegs);
        mainGateRegs = ByteBuffer.wrap(ctx.mainGateRegs);
        wordRam = ByteBuffer.wrap(ctx.wordRam);

        writeBuffer(gateRegs, 1, 1, Size.BYTE); //not reset
    }

    @Override
    public int read(int address, Size size) {
        address &= MD_PC_MASK;
        if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            return readBuffer(subCpuRam, address & MCD_PRG_RAM_MASK, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address < END_MCD_SUB_GATE_ARRAY_REGS) {
            return handleMegaCdExpRead(address, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Read Reserved: {} {} {}", address, size);
        }
        return super.read(address, size);
    }

    @Override
    public void write(int address, int data, Size size) {
        address &= MD_PC_MASK;
        if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            writeBuffer(subCpuRam, address & MCD_PRG_RAM_MASK, data, size);
            return;
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address <= END_MCD_SUB_GATE_ARRAY_REGS) {
            handleMegaCdExpWrite(address, data, size);
            return;
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS) {
            LOG.error("S Write Reserved: {} {} {}", address, data, size);
        }
        super.write(address, data, size);
    }

    private int handleMegaCdExpRead(int address, Size size) {
        int res = readBuffer(gateRegs, address & SEC_CPU_REGS_MASK, size);
        LOG.info("S Read MEGA_CD_EXP: {}, {}, {}", th(address),
                th(res), size);
        if (address >= START_MCD_SUB_GA_COMM_R && address < END_MCD_SUB_GA_COMM_R) { //MAIN,SUB COMM
            LOG.info("S Read MEGA_CD_COMM: {}, {}, {}", th(address), th(res), size);
            return res;
        }
        return res;
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        LOG.info("S Write MEGA_CD_EXP: {}, {}, {}", th(address),
                th(data), size);
        if (address >= START_MCD_SUB_GA_COMM_W && address < END_MCD_SUB_GA_COMM_W) { //MAIN COMM
            LOG.info("S Write MEGA_CD_COMM: {}, {}, {}", th(address), th(data), size);
            writeBuffer(gateRegs, address & SEC_CPU_REGS_MASK, data, size);
            writeBuffer(mainGateRegs, address & SEC_CPU_REGS_MASK, data, size); //write to main reg copy
            return;
        }
        assert size != Size.LONG;
        int regEven = (address & MCD_GATE_REGS_MASK) & ~1;
        int regVal = readBuffer(gateRegs, regEven, Size.WORD);
        writeBuffer(gateRegs, address & SEC_CPU_REGS_MASK, data, size);
        int newVal = readBuffer(gateRegs, regEven, Size.WORD);
        if (regVal == newVal) {
            return;
        }
        switch (regEven) {
            case 0:
                int sreset = newVal & 1;
                LOG.info("S SubCpu reset: {}", (sreset == 0 ? "Reset" : "Ignore"));
                if (sreset == 0 && MegaCd.secCpuResetFrameCounter == 0) {
                    //TODO reset CD drive, part of cddinit
                    LOG.info("S SecCpu peripheral reset started");
                    writeBuffer(gateRegs, 0, newVal | 1, Size.WORD); //cd drive done resetting
                }
                break;
            case 0xE:
                //sub can only write to MSB
                assert size == Size.BYTE && (address & 1) == 1;
                writeBuffer(mainGateRegs, address & SEC_CPU_REGS_MASK, data, size); //write to main reg copy
                LOG.info("M write COMM_FLAG: {} {}", th(data), size);
                break;
            case 0x32:
                LOG.info("S Write Interrupt mask control: {}, {}, {}", th(address), th(data), size);
                break;
            case 0x36:
                LOG.info("S Write CDD control: {}, {}, {}", th(address), th(data), size);
                break;
            default:
                LOG.error("M write unknown MEGA_CD_EXP reg: {}", th(address));
        }
    }

    @Override
    public void resetFrom68k() {
        //do nothing??
    }

    public void resetDone() {
        int regVal = readBuffer(gateRegs, 0, Size.WORD) | 1;
        writeBuffer(gateRegs, 0, regVal, Size.WORD);
        LOG.info("S SecCpu reset done");
    }
}
