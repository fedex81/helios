package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.util.MarsLauncherHelper;
import s32x.util.S32xUtil;

import java.util.function.Consumer;

import static s32x.MarsRegTestUtil.*;
import static s32x.dict.S32xDict.*;
import static s32x.util.S32xUtil.CpuDeviceAccess.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class FrameBufferAccessTest {

    private MarsLauncherHelper.Sh2LaunchContext lc;

    private static final int MD_ACCESS = 0;
    private static final int SH2_ACCESS = 1;

    private static final int sh2HCount = START_32X_SYSREG + RegSpecS32x.SH2_HCOUNT_REG.regSpec.fullAddr;
    private static final int mdTv = M68K_START_32X_SYSREG + RegSpecS32x.MD_SEGA_TV.regSpec.fullAddr;
    private static final int sh2sscr = START_32X_VDPREG + RegSpecS32x.SSCR.regSpec.fullAddr;
    private static final int mdSscr = M68K_START_32X_VDPREG + RegSpecS32x.SSCR.regSpec.fullAddr;

    @BeforeEach
    public void before() {
        lc = MarsRegTestUtil.createTestInstance();
        lc.s32XMMREG.aden = 1;
    }

    @Test
    public void testMdAccess() {
        setFmAccess(lc, MD_ACCESS);

        //sh2 can modify sysRegs
        modifyAddress(MASTER, sh2HCount, true);
        modifyAddress(SLAVE, sh2HCount, true);
        //sh2 cannot modify vdpRegs, frame buffer, overwrite image, palette
        modifyAddress(MASTER, sh2sscr, false);
        modifyAddress(SLAVE, sh2sscr, false);
        modifyAddress(MASTER, START_32X_COLPAL, false);
        modifyAddress(SLAVE, START_32X_COLPAL, false);
        modifyAddress(MASTER, START_DRAM, false);
        modifyAddress(SLAVE, START_DRAM, false);
        modifyAddress(MASTER, START_OVER_IMAGE, false);
        modifyAddress(SLAVE, START_OVER_IMAGE, false);

        //md can modify anything
//        modifyAddress(M68K, mdTv, true);
        modifyAddress(M68K, mdSscr, true);
        modifyAddress(M68K, M68K_START_32X_COLPAL, true);
        modifyAddress(M68K, M68K_START_FRAME_BUFFER, true);
        modifyAddress(M68K, M68K_START_OVERWRITE_IMAGE, true);
    }

    @Test
    public void testSh2Access() {
        setFmAccess(lc, SH2_ACCESS);

        //sh2 can modify anything
        modifyAddress(MASTER, sh2HCount, true);
        modifyAddress(SLAVE, sh2HCount, true);
        modifyAddress(MASTER, sh2sscr, true);
        modifyAddress(SLAVE, sh2sscr, true);
        modifyAddress(MASTER, START_32X_COLPAL, true);
        modifyAddress(SLAVE, START_32X_COLPAL, true);
        modifyAddress(MASTER, START_DRAM, true);
        modifyAddress(SLAVE, START_DRAM, true);
        modifyAddress(MASTER, START_OVER_IMAGE, true);
        modifyAddress(SLAVE, START_OVER_IMAGE, true);

        //md can only modify sysRegs
//        modifyAddress(M68K, mdTv, false);
        modifyAddress(M68K, mdSscr, false);
        modifyAddress(M68K, M68K_START_32X_COLPAL, false);
        modifyAddress(M68K, M68K_START_FRAME_BUFFER, false);
        modifyAddress(M68K, M68K_START_OVERWRITE_IMAGE, false);
    }

    //soulstar
    @Test
    public void testSh2FrameBufferMirrors() {
        setFmAccess(lc, SH2_ACCESS);

        int fbAddr = START_DRAM + 0x100;
        int fbAddr2 = fbAddr | 0x10_000;
        int baseVal = 0xABCD;

        writeBus(lc, MASTER, fbAddr, 0, Size.WORD);
        writeBus(lc, MASTER, fbAddr2, 0, Size.WORD);

        Consumer<Integer> checker = addr -> {
            int addrMirror = addr | 0x40_000;
            int res;
            int val = baseVal;
            //FB write non-mirror
            writeBus(lc, MASTER, addr, val, Size.WORD);
            res = readBus(lc, MASTER, addr, Size.WORD);
            Assertions.assertEquals(val, res);
            res = readBus(lc, MASTER, addrMirror, Size.WORD);
            Assertions.assertEquals(val, res);

            //FB write mirror
            val++;
            writeBus(lc, MASTER, addrMirror, val, Size.WORD);
            res = readBus(lc, MASTER, addr, Size.WORD);
            Assertions.assertEquals(val, res);
            res = readBus(lc, MASTER, addrMirror, Size.WORD);
            Assertions.assertEquals(val, res);
        };

        checker.accept(fbAddr);
        checker.accept(fbAddr2);
    }

    @Test
    public void testSwitch() {
        testMdAccess();
        testSh2Access();
        testMdAccess();
        testSh2Access();
    }

    private int modifyAddress(S32xUtil.CpuDeviceAccess access, int address, boolean matchNewVal) {
        int res = readBus(lc, access, address, Size.WORD);
        int newVal = res + 1;
        int exp = matchNewVal ? newVal : res;
        writeBus(lc, access, address, newVal, Size.WORD);
        int act = readBus(lc, access, address, Size.WORD);
        Assertions.assertEquals(exp, act);
        return act;
    }
}
