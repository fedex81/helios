package s32x;

import omegadrive.util.BufferUtil;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.dict.S32xDict.RegSpecS32x;
import s32x.util.MarsLauncherHelper;

import java.util.function.Consumer;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static s32x.MarsRegTestUtil.*;
import static s32x.dict.S32xDict.M68K_START_32X_SYSREG;
import static s32x.dict.S32xDict.RegSpecS32x.PWM_CTRL;
import static s32x.dict.S32xDict.RegSpecS32x.PWM_CYCLE;
import static s32x.dict.S32xDict.START_32X_SYSREG;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * <p>
 * TODO test PWM fifo
 */
public class PwmRegTest {

    private MarsLauncherHelper.Sh2LaunchContext lc;

    @BeforeEach
    public void before() {
        lc = createTestInstance();
        lc.s32XMMREG.aden = 1;
    }

    static int MD_PWM_CTRL_REG = M68K_START_32X_SYSREG + PWM_CTRL.regSpec.fullAddr;
    static int SH2_PWM_CTRL_REG = START_32X_SYSREG + PWM_CTRL.regSpec.fullAddr;

    @Test
    public void testPwmCtrlMasking() {
        int[] tmVals = {0, 0x100, 0xF00};
        RegSpecS32x[] regSpecs = {PWM_CTRL};
        PwmRegTestCtx masterCtx = PwmRegTestCtx.create(MASTER, regSpecs, 0, 0xF8F, this::checkPwmCtrl);
        PwmRegTestCtx slaveCtx = PwmRegTestCtx.create(SLAVE, regSpecs, 0, 0xF8F, this::checkPwmCtrl);
        for (int tm : tmVals) {
//            System.out.println("TM: " + tm);
            writeBus(lc, MASTER, SH2_PWM_CTRL_REG, tm, Size.WORD);
            PwmRegTestCtx m68kCtx = PwmRegTestCtx.create(M68K, regSpecs, tm, 0xF, this::checkPwmCtrl);
            PwmRegTestCtx z80Ctx = PwmRegTestCtx.create(Z80, regSpecs, tm, 0xF, this::checkPwmCtrl);
            testPwmMasking(m68kCtx);
            testPwmMasking(z80Ctx);
            testPwmMasking(masterCtx);
            testPwmMasking(slaveCtx);
        }
    }

    @Test
    public void testPwmCycleMasking() {
        RegSpecS32x[] regSpecs = {PWM_CYCLE};
        PwmRegTestCtx m68kCtx = PwmRegTestCtx.create(M68K, regSpecs, 0, 0xFFF);
        PwmRegTestCtx z80Ctx = PwmRegTestCtx.create(Z80, regSpecs, 0, 0xFFF);
        PwmRegTestCtx masterCtx = PwmRegTestCtx.create(MASTER, regSpecs, 0, 0xFFF);
        PwmRegTestCtx slaveCtx = PwmRegTestCtx.create(SLAVE, regSpecs, 0, 0xFFF);
        testPwmMasking(m68kCtx);
        testPwmMasking(z80Ctx);
        testPwmMasking(masterCtx);
        testPwmMasking(slaveCtx);
    }

    private void testPwmMasking(PwmRegTestCtx ctx) {
        int andMask = ctx.andMask;
        int orMask = ctx.orMask;
        int regBase = ctx.regBase;
        RegSpecS32x[] regSpecs = ctx.regSpecs;
        CpuDeviceAccess cpu = ctx.cpu;
        boolean isMd = cpu.regSide == BufferUtil.S32xRegSide.MD;
        for (int k = 0; k < regSpecs.length; k++) {
            int ignoreMask = 0xFFFF;
            int regAddr = regBase | regSpecs[k].addr;
//            System.out.println(cpu + "," + regSpecs[k]);
            writeBus(lc, cpu, regAddr, 0, Size.WORD);
            for (int i = 0; i <= 0xFFFF; i++) {
//                System.out.println(i);
                int res = 0, exp = 0;
                //word
                if (cpu == M68K) {
                    exp = ((i & andMask) | orMask) & ignoreMask;
                    writeBus(lc, cpu, regAddr, i, Size.WORD);
                    res = readBus(lc, cpu, regAddr, Size.WORD) & ignoreMask;
                    Assertions.assertEquals(exp & ignoreMask, res & ignoreMask);
                    ctx.checker.accept(exp & ignoreMask);
                }
                writeBus(lc, cpu, regAddr, 0, Size.WORD);
                if (i < 0x100 && isMd) {
                    exp = (((i >> 8) & andMask) | orMask) & ignoreMask;
                    //byte #0
                    writeBus(lc, cpu, regAddr, 0, Size.BYTE);
                    writeBus(lc, cpu, regAddr, i >> 8, Size.BYTE);
                    res = readBus(lc, M68K, regAddr, Size.WORD) & ignoreMask;
                    Assertions.assertEquals(exp, res);
                    ctx.checker.accept(exp & ignoreMask);

                    //byte #1
                    exp = (((i & 0xFF) & andMask) | orMask) & ignoreMask;
                    writeBus(lc, cpu, regAddr + 1, 0, Size.BYTE);
                    writeBus(lc, cpu, regAddr + 1, i & 0xFF, Size.BYTE);
                    res = readBus(lc, M68K, regAddr, Size.WORD) & ignoreMask;
                    Assertions.assertEquals(exp, res);
                    ctx.checker.accept(exp & ignoreMask);
                }
            }
        }
    }

    private void checkPwmCtrl(int exp) {
        int evenByte = exp >> 8;
        int oddByte = exp & 0xFF;
        Assertions.assertEquals(evenByte, readBus(lc, Z80, MD_PWM_CTRL_REG, Size.BYTE));
        Assertions.assertEquals(oddByte, readBus(lc, Z80, MD_PWM_CTRL_REG + 1, Size.BYTE));
        Assertions.assertEquals(evenByte, readBus(lc, M68K, MD_PWM_CTRL_REG, Size.BYTE));
        Assertions.assertEquals(oddByte, readBus(lc, M68K, MD_PWM_CTRL_REG + 1, Size.BYTE));
        Assertions.assertEquals(evenByte, readBus(lc, MASTER, SH2_PWM_CTRL_REG, Size.BYTE));
        Assertions.assertEquals(oddByte, readBus(lc, MASTER, SH2_PWM_CTRL_REG + 1, Size.BYTE));
        Assertions.assertEquals(evenByte, readBus(lc, SLAVE, SH2_PWM_CTRL_REG, Size.BYTE));
        Assertions.assertEquals(oddByte, readBus(lc, SLAVE, SH2_PWM_CTRL_REG + 1, Size.BYTE));
        Assertions.assertEquals(exp, readBus(lc, M68K, MD_PWM_CTRL_REG, Size.WORD));
        Assertions.assertEquals(exp, readBus(lc, MASTER, SH2_PWM_CTRL_REG, Size.WORD));
        Assertions.assertEquals(exp, readBus(lc, SLAVE, SH2_PWM_CTRL_REG, Size.WORD));
    }

    static class PwmRegTestCtx {
        CpuDeviceAccess cpu;
        RegSpecS32x[] regSpecs;
        int orMask, andMask, regBase;

        Consumer<Integer> checker;

        public static PwmRegTestCtx create(CpuDeviceAccess cpu, RegSpecS32x[] regSpecs, int orMask, int andMask) {
            return create(cpu, regSpecs, orMask, andMask, t -> {
            });
        }

        public static PwmRegTestCtx create(CpuDeviceAccess cpu, RegSpecS32x[] regSpecs, int orMask, int andMask,
                                           Consumer<Integer> checker) {
            PwmRegTestCtx c = new PwmRegTestCtx();
            c.regBase = cpu.regSide == BufferUtil.S32xRegSide.MD ? M68K_START_32X_SYSREG : START_32X_SYSREG;
            c.cpu = cpu;
            c.regSpecs = regSpecs;
            c.orMask = orMask;
            c.andMask = andMask;
            c.checker = checker;
            return c;
        }
    }
}