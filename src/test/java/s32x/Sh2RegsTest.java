package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.dict.S32xDict.RegSpecS32x;
import s32x.util.MarsLauncherHelper;
import s32x.util.S32xUtil;

import static s32x.MarsRegTestUtil.*;
import static s32x.dict.S32xDict.START_32X_SYSREG_CACHE;
import static s32x.util.S32xUtil.CpuDeviceAccess.MASTER;
import static s32x.util.S32xUtil.CpuDeviceAccess.SLAVE;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Sh2RegsTest {
    private MarsLauncherHelper.Sh2LaunchContext lc;

    private static final int AND_WORD_NO_MASK = 0xFFFF;

    int[] masterSlaveDiffMask;
    boolean[] testByte;

    RegSpecS32x[] regSpecs = {RegSpecS32x.SH2_INT_MASK, RegSpecS32x.SH2_HCOUNT_REG, RegSpecS32x.SH2_STBY_CHANGE,
            RegSpecS32x.SH2_DREQ_CTRL};

    @BeforeEach
    public void before() {
        lc = createTestInstance();
        masterSlaveDiffMask = new int[regSpecs.length];
        //SH2_INT_MASK first 4 bits are not shared between M/S
        masterSlaveDiffMask[0] = 0xF;
        testByte = new boolean[regSpecs.length];
        testByte[0] = testByte[1] = true;
    }

    @Test
    public void testSh2Regs() {
        for (int k = 0; k < regSpecs.length; k++) {
            System.out.println(regSpecs[k]);
            for (int i = 0; i <= 0xFFFF; i++) {
//                System.out.println(i);
                testWord(MASTER, regSpecs[k], k, i);
                testWord(SLAVE, regSpecs[k], k, i);
                if (i < 0x100) {
                    testByte(MASTER, regSpecs[k], k, i, 0);
                    testByte(SLAVE, regSpecs[k], k, i, 0);
                    testByte(MASTER, regSpecs[k], k, i, 1);
                    testByte(SLAVE, regSpecs[k], k, i, 1);
                }
            }
        }
    }

    private void testByte(S32xUtil.CpuDeviceAccess cpu, RegSpecS32x reg, int k, int val, int bytePos) {
        int andMask = reg.regSpec.writableBitMask;
        int orMask = reg.regSpec.preserveBitMask;
        int ignoreMask = AND_WORD_NO_MASK;
        int regAddr = START_32X_SYSREG_CACHE | reg.addr;
        S32xUtil.CpuDeviceAccess other = cpu == MASTER ? SLAVE : MASTER;
        int valByte = (val >> (8 * (1 - bytePos))) & 0xFF;

        writeBus(lc, MASTER, regAddr + bytePos, 0, Size.BYTE);
        writeBus(lc, MASTER, regAddr + bytePos, valByte, Size.BYTE);
        int res = readBus(lc, MASTER, regAddr, Size.WORD) & ignoreMask;
        int exp = ((val & andMask) | orMask) & ignoreMask;
        Assertions.assertEquals(exp, res);
        res = readBus(lc, SLAVE, regAddr, Size.WORD) & ignoreMask;
        Assertions.assertEquals(exp & masterSlaveDiffMask[k], res & masterSlaveDiffMask[k]);
        //the shared bits have changed after writing to one side
        Assertions.assertEquals(exp & ~masterSlaveDiffMask[k], res & ~masterSlaveDiffMask[k]);
    }

    private void testWord(S32xUtil.CpuDeviceAccess cpu, RegSpecS32x reg, int k, int val) {
        int andMask = reg.regSpec.writableBitMask;
        int orMask = reg.regSpec.preserveBitMask;
        int ignoreMask = 0xFFFF;
        int regAddr = START_32X_SYSREG_CACHE | reg.addr;
        S32xUtil.CpuDeviceAccess other = cpu == MASTER ? SLAVE : MASTER;

        int valOther = readBus(lc, other, regAddr, Size.WORD) & ignoreMask;

        writeBus(lc, cpu, regAddr, val, Size.WORD);
        int res = readBus(lc, cpu, regAddr, Size.WORD) & ignoreMask;
        int exp = ((val & andMask) | orMask) & ignoreMask;
        Assertions.assertEquals(exp, res);
        res = readBus(lc, other, regAddr, Size.WORD) & ignoreMask;
        //the bits not shared have not changed after writing to one side
        Assertions.assertEquals(valOther & masterSlaveDiffMask[k], res & masterSlaveDiffMask[k]);
        //the shared bits have changed after writing to one side
        Assertions.assertEquals(exp & ~masterSlaveDiffMask[k], res & ~masterSlaveDiffMask[k]);
    }
}
