package omegadrive.bus.megacd;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static omegadrive.bus.megacd.MegaCdDict.START_MCD_SUB_GATE_ARRAY_REGS;
import static omegadrive.bus.model.GenesisBusProvider.MEGA_CD_EXP_START;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdGateArrayRegTest extends McdRegTestBase {

    public static final int MAIN_RESET_REG = MEGA_CD_EXP_START;
    public static final int SUB_RESET_REG = START_MCD_SUB_GATE_ARRAY_REGS;

    @Test
    public void testSubResetReg() {
        int mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        int sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg, sreg);
        //RES0=1 on init
        Assertions.assertEquals(1, mreg);

        //subCpu reset instantly (should be 100ms delay)
        subCpuBus.write(SUB_RESET_REG + 1, 0xFFFE, Size.BYTE);
        mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg, sreg);
        //RES0 geos back to 1
        Assertions.assertEquals(1, mreg & 1);
    }

    @Test
    public void testMainResetReg() {
        int mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        int sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg, sreg);
        //RES0=1 on init
        Assertions.assertEquals(1, mreg);

        //mainCpu busReq=0 (Cancel) and reset=1 (Run)
        mainCpuBus.write(MAIN_RESET_REG, 0xFFFD, Size.WORD);
        mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg, sreg);
        Assertions.assertFalse(subCpu.isStopped()); //cpu running
        //RES0 geos back to 1
        Assertions.assertEquals(1, mreg & 1);

        //mainCpu reset=0 (Reset)
        mainCpuBus.write(MAIN_RESET_REG, 0xFFFE, Size.WORD);
        mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg, sreg);
        Assertions.assertTrue(subCpu.isStopped());
        //RES0 stays 0
        Assertions.assertEquals(0, mreg & 1);

        //mainCpu reset=1 (Run), busReq=1 (Request)
        mainCpuBus.write(MAIN_RESET_REG, 0xFFFF, Size.WORD);
        mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg, sreg);
        Assertions.assertTrue(subCpu.isStopped());
        Assertions.assertEquals(1, mreg & 1); //TODO ??


        //mainCpu busReq=0 (Cancel)
        mainCpuBus.write(MAIN_RESET_REG, 0xFFFD, Size.WORD);
        mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg, sreg);
        Assertions.assertFalse(subCpu.isStopped());
        Assertions.assertEquals(1, mreg & 1);
    }
}
