package omegadrive.bus.megacd;

import omegadrive.bus.md.GenesisBus;
import omegadrive.system.MegaCd;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Random;

import static omegadrive.bus.megacd.MegaCdMemoryContext.MCD_PRG_RAM_MASK;
import static omegadrive.bus.megacd.MegaCdMemoryContext.MCD_PRG_RAM_SIZE;
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
    private static final int END_MCD_SUB_GATE_ARRAY_REGS = 0xFF_FFFF;
    private ByteBuffer subCpuRam, cpuRegs, wordRam;

    Random random = new Random(0L);

    public MegaCdSecCpuBus(MegaCdMemoryContext ctx) {
        subCpuRam = ByteBuffer.wrap(ctx.prgRam);
        cpuRegs = ByteBuffer.wrap(ctx.gateRegs);
        wordRam = ByteBuffer.wrap(ctx.wordRam);
    }

    @Override
    public int read(int address, Size size) {
        address &= MD_PC_MASK;
        if (address >= START_MCD_SUB_PRG_RAM && address < END_MCD_SUB_PRG_RAM) {
            return readBuffer(subCpuRam, address & MCD_PRG_RAM_MASK, size);
        } else if (address >= START_MCD_SUB_GATE_ARRAY_REGS && address < END_MCD_SUB_GATE_ARRAY_REGS) {
            return handleMegaCdExpRead(address, size);
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
        }
        super.write(address, data, size);
    }

    private int handleMegaCdExpRead(int address, Size size) {
        int res = readBuffer(cpuRegs, address & 0xFF, size);
        LOG.warn("S Read MEGA_CD_EXP: {}, {}, {}", th(address),
                th(res), size);
        return (res & 0xFC) | random.nextInt(4); //TODO hack
    }

    private void handleMegaCdExpWrite(int address, int data, Size size) {
        LOG.warn("S Write MEGA_CD_EXP: {}, {}, {}", th(address),
                th(data), size);
        writeBuffer(cpuRegs, address & 0xFF, data, size);
//        //bk0,1
//        subCpuProgRamBankValue = (readBuffer(mainCpuRegs, 3, Size.BYTE) >> 5) & 3;
//        subCpuProgRamBankShift = subCpuProgRamBankValue << 17;

        int reg1 = readBuffer(cpuRegs, 1, Size.BYTE) & 1;
        LOG.info("S SubCpu reset: {}", reg1);
        if (reg1 == 1 && MegaCd.secCpuResetFrameCounter == 0) {
            MegaCd.secCpuResetFrameCounter = 7;
            LOG.info("S SecCpu reset started");
        }
    }

    @Override
    public void resetFrom68k() {
        //do nothing??
    }
}
