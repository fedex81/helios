package omegadrive.bus.megacd;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.START_MCD_SUB_GATE_ARRAY_REGS;
import static omegadrive.bus.model.GenesisBusProvider.MEGA_CD_EXP_START;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdWordRamTest extends McdRegTestBase {

    public static final int MAIN_MEM_MODE_REG = MEGA_CD_EXP_START + 2;
    public static final int SUB_MEM_MODE_REG = START_MCD_SUB_GATE_ARRAY_REGS + 2;
    public static final int DMNA_BIT_POS = 1;
    public static final int DMNA_BIT_MASK = 1 << DMNA_BIT_POS;

    public static final int RET_BIT_POS = 0;
    public static final int RET_BIT_MASK = 1 << RET_BIT_POS;

    //mainCpu gives 2M WRAM to subCpu
    @Test
    public void test2M_WRAMtoSub() {
        int reg = mainCpuBus.read(MAIN_MEM_MODE_REG + 1, Size.BYTE);
        int sreg = subCpuBus.read(SUB_MEM_MODE_REG + 1, Size.BYTE);
        Assertions.assertEquals(reg, sreg);
        //DMNA=0, RET=1
        Assertions.assertEquals(RET_BIT_MASK, reg & 3);

        //main sets DMNA=1, RET=0 immediately (inaccurate but should be ok)
        mainCpuBus.write(MAIN_MEM_MODE_REG + 1, DMNA_BIT_MASK, Size.BYTE);
        reg = mainCpuBus.read(MAIN_MEM_MODE_REG + 1, Size.BYTE);
        sreg = subCpuBus.read(SUB_MEM_MODE_REG + 1, Size.BYTE);
        Assertions.assertEquals(reg, sreg);
        //subCpu has WRAM
        Assertions.assertEquals(DMNA_BIT_MASK, reg & 3);
    }

    //subCpu gives 2M WRAM to mainCpu
    @Test
    public void test2M_WRAMtoMain() {
        //give WRAM to sub
        test2M_WRAMtoSub();
        int reg = subCpuBus.read(SUB_MEM_MODE_REG + 1, Size.BYTE);
        int mreg = mainCpuBus.read(MAIN_MEM_MODE_REG + 1, Size.BYTE);
        Assertions.assertEquals(reg, mreg);
        //RET=1, DMNA=0
        Assertions.assertEquals(DMNA_BIT_MASK, reg & 3);

        //sub sets RET=1, DMNA=0 immediately (inaccurate but should be ok)
        subCpuBus.write(SUB_MEM_MODE_REG + 1, RET_BIT_MASK, Size.BYTE);
        reg = subCpuBus.read(SUB_MEM_MODE_REG + 1, Size.BYTE);
        mreg = mainCpuBus.read(MAIN_MEM_MODE_REG + 1, Size.BYTE);
        Assertions.assertEquals(reg, mreg);
        //mainCpu has WRAM
        Assertions.assertEquals(RET_BIT_MASK, reg & 3);
    }
}
