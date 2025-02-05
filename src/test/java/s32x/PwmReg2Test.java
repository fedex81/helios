package s32x;

import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.util.MarsLauncherHelper;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.MASTER;
import static s32x.MarsRegTestUtil.*;
import static s32x.dict.S32xDict.M68K_START_32X_SYSREG;
import static s32x.dict.S32xDict.RegSpecS32x.PWM_CTRL;
import static s32x.dict.S32xDict.START_32X_SYSREG;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * <p>
 */
public class PwmReg2Test {

    private MarsLauncherHelper.Sh2LaunchContext lc;

    @BeforeEach
    public void before() {
        lc = createTestInstance();
        lc.s32XMMREG.aden = 1;
    }

    static int MD_PWM_CTRL_REG = M68K_START_32X_SYSREG + PWM_CTRL.regSpec.fullAddr;
    static int SH2_PWM_CTRL_REG = START_32X_SYSREG + PWM_CTRL.regSpec.fullAddr;

    //for A15130, TM3,2,1,0 and RTP are read only, according to docs
    @Test
    public void testPwmCtrlMaskingMd_Word() {
        for (int i = 0; i < 0x10000; i++) {
            int val = i;
            int exp = (readBus(lc, M68K, MD_PWM_CTRL_REG, Size.WORD) & 0xFFF0) | (val & 0xf);
            assertWriteAndCheck(M68K, MD_PWM_CTRL_REG, Size.WORD, val, exp);

            //set TMn and RTP from the SH2 side
            val = 0xF80 | (val & 0xF000);
            assertWriteAndCheck(MASTER, SH2_PWM_CTRL_REG, Size.WORD, val, val & 0xFFF);

            //MD can't change them
            val = i & 0xF;
            exp = (readBus(lc, M68K, MD_PWM_CTRL_REG, Size.WORD) & 0xFFF0) | (val & 0xf);
            assertWriteAndCheck(M68K, MD_PWM_CTRL_REG, Size.WORD, val, exp);
        }
    }

    @Test
    public void testPwmCtrlMaskingMd_Byte() {
        for (int i = 0; i < 0x100; i++) {
            //byte0 = A15130
            assertWriteAndCheck(M68K, MD_PWM_CTRL_REG, Size.BYTE, i, readBus(lc, M68K, MD_PWM_CTRL_REG, Size.BYTE));
            //byte1 = A15131
            int exp = (readBus(lc, M68K, MD_PWM_CTRL_REG + 1, Size.BYTE) & 0xF0) | (i & 0xf);
            assertWriteAndCheck(M68K, MD_PWM_CTRL_REG + 1, Size.BYTE, i, exp);
        }
    }

    private void assertWriteAndCheck(CpuDeviceAccess cpu, int regNum, Size size, int value, int expected) {
        writeBus(lc, cpu, regNum, value, size);
        int res = readBus(lc, cpu, regNum, size);
        Assertions.assertEquals(expected, res);
    }
}