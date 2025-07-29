package mcd;

import mcd.bus.McdSubInterruptHandler;
import mcd.dict.MegaCdDict;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdDict.START_MCD_SUB_GATE_ARRAY_REGS;
import static omegadrive.bus.model.MdMainBusProvider.MEGA_CD_EXP_START;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdGateArrayRegTest extends McdRegTestBase {

    public static final int MAIN_RESET_REG = MEGA_CD_EXP_START;
    public static final int SUB_RESET_REG = START_MCD_SUB_GATE_ARRAY_REGS;

    public static final int SUB_INT_MASK_ODD = START_MCD_SUB_GATE_ARRAY_REGS | MCD_INT_MASK.addr + 1;

    @BeforeEach
    public void setup() {
        super.setupBase();
        //ignore interrupts
        subCpu.getM68k().setSR(0x2700);
    }

    //TODO fix
    @Ignore
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

    //TODO fix

    @Ignore
    public void testMainResetReg() {
        int mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        int sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg, sreg);
        //RES0=1 on init
        Assertions.assertEquals(1, mreg);

        //mainCpu busReq=0 (Cancel) and reset=1 (Run)
        mainCpuBus.write(MAIN_RESET_REG, 0x7FFD, Size.WORD);
        mreg = mainCpuBus.read(MAIN_RESET_REG, Size.WORD);
        sreg = subCpuBus.read(SUB_RESET_REG, Size.WORD);
        Assertions.assertEquals(mreg & 3, sreg & 3);
        Assertions.assertFalse(subCpu.isStopped()); //cpu running
        //RES0 geos back to 1
        Assertions.assertEquals(1, mreg & 1);

        //mainCpu reset=0 (Reset)
        mainCpuBus.write(MAIN_RESET_REG, 0x7FFE, Size.WORD);
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

    static int reg0MainMask = 0x103;
    static int reg0SubMask = 0x301;

    @Test
    public void testReg0() {
        MegaCdDict.RegSpecMcd regSpec = MCD_RESET;
        int mainAddr = MEGA_CD_EXP_START + regSpec.addr;
        int subAddr = START_MCD_SUB_GATE_ARRAY_REGS + regSpec.addr;
        Assertions.assertEquals(regSpec.addr, regSpec.addr);
        int mreg = readWordReg(M68K, mainAddr);

        //SUB write doesn't change MAIN
        int val = reg0SubMask;
        writeAddressSize(SUB_M68K, subAddr, val, Size.WORD);
        int mreg2 = readWordReg(M68K, mainAddr);
        int sreg2 = readWordReg(SUB_M68K, subAddr);
//        Assertions.assertEquals(val & subMask, sreg2);
//        Assertions.assertEquals(mreg, mreg2);

        //MAIN write doesn't change SUB
        val = 0x301;
        int sreg = readWordReg(SUB_M68K, subAddr);
        writeAddressSize(M68K, mainAddr, val, Size.WORD);
        mreg2 = readWordReg(M68K, mainAddr);
        sreg2 = readWordReg(SUB_M68K, subAddr);
        Assertions.assertEquals(val & reg0MainMask, mreg2);
        Assertions.assertEquals(sreg, sreg2);
    }

    private int readWordReg(CpuDeviceAccess cpu, int address) {
        assert cpu == M68K || cpu == SUB_M68K;
        BaseBusProvider bus = cpu == M68K ? mainCpuBus : subCpuBus;
        MdRuntimeData.setAccessTypeExt(cpu);
        return bus.read(address, Size.WORD);
    }

    @Test
    public void testReg6() {
        testReg6Internal(MCD_HINT_VECTOR);
    }

    @Test
    public void testReg0_A() {
        testReg0Internal(MCD_RESET);
    }

    private void testReg0Internal(MegaCdDict.RegSpecMcd regSpec) {
        int mainAddr = MEGA_CD_EXP_START + regSpec.addr;
        int subAddr = START_MCD_SUB_GATE_ARRAY_REGS + regSpec.addr;
        Assertions.assertEquals(regSpec.addr, regSpec.addr);
        int sreg = readWordReg(SUB_M68K, subAddr);

        //MAIN write doesnt change SUB
        int val = 0x7EDC;
        writeAddressSize(M68K, mainAddr, val, Size.WORD);

        int mreg = readWordReg(M68K, mainAddr);
        int sreg2 = readWordReg(SUB_M68K, subAddr);
        Assertions.assertEquals(val & reg0MainMask, mreg);
        Assertions.assertEquals(sreg, sreg2);

        //SUB write doesnt change MAIN
        val = 0x300;
        mreg = readWordReg(M68K, mainAddr);
        writeAddressSize(SUB_M68K, subAddr, val, Size.WORD);
        int mreg2 = readWordReg(M68K, mainAddr);
        sreg = readWordReg(SUB_M68K, subAddr);
        //reset goes to 1 immediately
        Assertions.assertEquals(val | 1, sreg & 0xFFFF);
        Assertions.assertEquals(mreg, mreg2);
    }

    private void testReg6Internal(MegaCdDict.RegSpecMcd regSpec) {
        int mainAddr = MEGA_CD_EXP_START + regSpec.addr;
        int subAddr = START_MCD_SUB_GATE_ARRAY_REGS + regSpec.addr;
        Assertions.assertEquals(regSpec.addr, regSpec.addr);
        int sreg = readWordReg(SUB_M68K, subAddr);

        //MAIN write doesnt change SUB
        int val = 0x7EDC;
        writeAddressSize(M68K, mainAddr, val, Size.WORD);
        int mreg = readWordReg(M68K, mainAddr);
        int sreg2 = readWordReg(SUB_M68K, subAddr);
        Assertions.assertEquals(val & 0xFFFF, mreg);
        Assertions.assertEquals(sreg, sreg2);

        //SUB write doesnt change MAIN
        val = 0x300;
        mreg = readWordReg(M68K, mainAddr);
        writeAddressSize(SUB_M68K, subAddr, val, Size.WORD);
        int mreg2 = readWordReg(M68K, mainAddr);
        //CDC data read, should return 0xFF
        sreg = readWordReg(SUB_M68K, subAddr);
        Assertions.assertEquals(0xFF, sreg & 0xFF);
        Assertions.assertEquals(mreg, mreg2);
    }

    /**
     * clr.l    $00a1200e
     * read long and then write long 0
     */
    @Test
    public void testClearReg_0xE() {
        int commFlags = MEGA_CD_EXP_START | MCD_COMM_FLAGS.addr;
        int comm0 = MEGA_CD_EXP_START | MCD_COMM0.addr;
        //0xA1200F is not writable by MAIN
        mainCpuBus.write(commFlags, 0xAABB, Size.WORD);
        mainCpuBus.write(comm0, 0xCCDD, Size.WORD);

        int res = mainCpuBus.read(commFlags, Size.LONG);
        Assertions.assertEquals(0xBB00_CCDD, res);
        mainCpuBus.write(commFlags, 0, Size.LONG);
        res = mainCpuBus.read(commFlags, Size.LONG);
        Assertions.assertEquals(0, res);
    }

    /**
     * 68M 00ff066e   08f9 0008 00a12000      bset     #$0,$00a12000 [NEW]
     * 68M 00ff0676   0839 0008 00a12000      btst     #$0,$00a12000 [NEW]
     * 68M 00ff067e   6600 fff6               bne.w    $00ff0676 [NEW]
     */
    //TODO fix
//    @Test
    public void testResetReg_IFL2() {
        //bset     #$0,$00a12000
        int val = mainCpuBus.read(MAIN_RESET_REG, Size.BYTE);
        mainCpuBus.write(MAIN_RESET_REG, val | 1, Size.BYTE);
        int startCnt = 1_000;
        int cnt = startCnt;
        boolean trigger = false;
        McdSubInterruptHandler interruptHandler = subCpuBus.getInterruptHandler();
        subCpuBus.write(SUB_INT_MASK_ODD, 0xFF, Size.BYTE);
        do {
            //btst     #$0,$00a12000
            val = mainCpuBus.read(MAIN_RESET_REG, Size.BYTE);
            if (!trigger && cnt < startCnt >> 1) {
                interruptHandler.raiseInterrupt(McdSubInterruptHandler.SubCpuInterrupt.INT_LEVEL2);
                interruptHandler.handleInterrupts();
                trigger = true;
            }
            cnt--;
        } while (cnt > 0 && (val & 1) > 0);
        Assertions.assertNotEquals(0, cnt);
    }

    /**
     * Arslan Senki - The Heroic Legend of Arslan (hangs at copyright screen)
     */
    @Test
    public void testResetReg_IEN2_disable() {
        //enable IEN2
        writeAddressSize(SUB_M68K, SUB_INT_MASK_ODD, 0xFF, Size.BYTE);
        //set IFL2
        int val = readAddressSize(M68K, MAIN_RESET_REG, Size.BYTE);
        writeAddressSize(M68K, MAIN_RESET_REG, val | 1, Size.BYTE);
        //disable IEN2
        writeAddressSize(SUB_M68K, SUB_INT_MASK_ODD, 0, Size.BYTE);
        val = readAddressSize(M68K, MAIN_RESET_REG, Size.BYTE);
        //IFL2 goes to 0
        Assertions.assertEquals(0, val & 1);
    }

}
