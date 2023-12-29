package omegadrive.bus.megacd;

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.util.S32xUtil.CpuDeviceAccess;

import static omegadrive.bus.megacd.MegaCdDict.RegSpecMcd.MCD_HINT_VECTOR;
import static omegadrive.bus.megacd.MegaCdDict.RegSpecMcd.MCD_RESET;
import static omegadrive.bus.megacd.MegaCdDict.START_MCD_SUB_GATE_ARRAY_REGS;
import static omegadrive.bus.model.GenesisBusProvider.MEGA_CD_EXP_START;
import static s32x.util.S32xUtil.CpuDeviceAccess.M68K;
import static s32x.util.S32xUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdGateArrayRegTest extends McdRegTestBase {

    public static final int MAIN_RESET_REG = MEGA_CD_EXP_START;
    public static final int SUB_RESET_REG = START_MCD_SUB_GATE_ARRAY_REGS;

    @BeforeEach
    @Override
    public void setup() {
        super.setup();
        //ignore interrupts
        subCpu.getM68k().setSR(0x2700);
    }

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

    @Test
    public void testReg0() {
        MegaCdDict.RegSpecMcd regSpec = MCD_RESET;
        int mainAddr = MEGA_CD_EXP_START + regSpec.addr;
        int subAddr = START_MCD_SUB_GATE_ARRAY_REGS + regSpec.addr;
        Assertions.assertEquals(regSpec.addr, regSpec.addr);
        int mreg = readWordReg(M68K, mainAddr);

        int mainMask = 0x103;
        int subMask = 0x301;

        //SUB write doesn't change MAIN
        int val = 0x103;
        subCpuBus.write(subAddr, val, Size.WORD);
        int mreg2 = readWordReg(M68K, mainAddr);
        int sreg2 = readWordReg(SUB_M68K, subAddr);
//        Assertions.assertEquals(val & subMask, sreg2);
//        Assertions.assertEquals(mreg, mreg2);

        //MAIN write doesn't change SUB
        val = 0x301;
        int sreg = readWordReg(SUB_M68K, subAddr);
        mainCpuBus.write(mainAddr, val, Size.WORD);
        mreg2 = readWordReg(M68K, mainAddr);
        sreg2 = readWordReg(SUB_M68K, subAddr);
        Assertions.assertEquals(val & mainMask, mreg2);
        Assertions.assertEquals(sreg, sreg2);
    }

    private int readWordReg(CpuDeviceAccess cpu, int address) {
        assert cpu == M68K || cpu == SUB_M68K;
        GenesisBusProvider bus = cpu == M68K ? mainCpuBus : subCpuBus;
        return bus.read(address, Size.WORD);
    }

    @Test
    public void testReg6() {
        testRegInternal(MCD_HINT_VECTOR);
    }

    @Test
    public void testReg0_A() {
        testRegInternal(MCD_RESET);
    }

    private void testRegInternal(MegaCdDict.RegSpecMcd regSpec) {
        int mainAddr = MEGA_CD_EXP_START + regSpec.addr;
        int subAddr = START_MCD_SUB_GATE_ARRAY_REGS + regSpec.addr;
        Assertions.assertEquals(regSpec.addr, regSpec.addr);
        int sreg = subCpuBus.read(subAddr, Size.WORD);

        //MAIN write doesnt change SUB
        int val = 0xFEDC;
        mainCpuBus.write(mainAddr, val, Size.WORD);
        int mreg = mainCpuBus.read(mainAddr, Size.WORD);
        int sreg2 = subCpuBus.read(subAddr, Size.WORD);
        Assertions.assertEquals(val, mreg & 0xFFFF);
        Assertions.assertEquals(sreg, sreg2);

        //SUB write doesnt change MAIN
        val = 0x1234;
        mreg = mainCpuBus.read(mainAddr, Size.WORD);
        subCpuBus.write(subAddr, val, Size.WORD);
        int mreg2 = mainCpuBus.read(mainAddr, Size.WORD);
        sreg = subCpuBus.read(subAddr, Size.WORD);
        Assertions.assertEquals(val, sreg & 0xFFFF);
        Assertions.assertEquals(mreg, mreg2);
    }
}
