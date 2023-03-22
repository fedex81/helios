package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.dict.Sh2Dict;
import s32x.util.MarsLauncherHelper;
import s32x.util.S32xUtil.CpuDeviceAccess;

import static s32x.MarsRegTestUtil.createTestInstance;
import static s32x.dict.Sh2Dict.BSC_LONG_WRITE_MASK;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;
import static s32x.util.S32xUtil.CpuDeviceAccess.MASTER;
import static s32x.util.S32xUtil.CpuDeviceAccess.SLAVE;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Sh2MiscRegTest {

    private MarsLauncherHelper.Sh2LaunchContext lc;

    @BeforeEach
    public void before() {
        lc = createTestInstance();
    }

    @Test
    public void testBsc() {
        testBscInternal(MASTER);
        testBscInternal(SLAVE);
    }

    @Test
    public void testCcr() {
        testCcrInternal(MASTER);
        testCcrInternal(SLAVE);
    }

    private void testBscInternal(CpuDeviceAccess cpu) {
        System.out.println(cpu);
        Sh2MMREG s = cpu == MASTER ? lc.mDevCtx.sh2MMREG : lc.sDevCtx.sh2MMREG;
        int bcr1Mask = cpu.ordinal() << 15;
        int res;
        res = readLong(s, BSC_BCR1);
        Assertions.assertEquals(bcr1Mask | 0x3f0, res);
        res = readLong(s, BSC_BCR2);
        Assertions.assertEquals(0xfc, res);

        res = readLowWord(s, BSC_BCR1);
        Assertions.assertEquals(bcr1Mask | 0x3f0, res);
        res = readLowWord(s, BSC_BCR2);
        Assertions.assertEquals(0xfc, res);

        res = readLowWord(s, BSC_BCR1);
        s.write(BSC_BCR1.addr, 1, Size.LONG); //ignored
        Assertions.assertEquals(res, readLowWord(s, BSC_BCR1));

        s.write(BSC_BCR1.addr, 1, Size.BYTE); //ignored
        Assertions.assertEquals(res, readLowWord(s, BSC_BCR1));

        s.write(BSC_BCR1.addr, 1, Size.WORD); //ignored
        Assertions.assertEquals(res, readLowWord(s, BSC_BCR1));

        s.write(BSC_BCR1.addr, BSC_LONG_WRITE_MASK | 1, Size.LONG);
        res = readLowWord(s, BSC_BCR1);
        Assertions.assertEquals(bcr1Mask | 1, res);

        res = readLong(s, BSC_BCR1);
        Assertions.assertEquals(bcr1Mask | 1, res);
    }

    private void testCcrInternal(CpuDeviceAccess cpu) {
        System.out.println(cpu);
        int cachePurgeMask = 0x10;
        int enableMask = 1;
        Sh2MMREG s = cpu == MASTER ? lc.mDevCtx.sh2MMREG : lc.sDevCtx.sh2MMREG;
        int res;
        res = readByte(s, NONE_CCR);
        Assertions.assertEquals(0, res);

        int val = cachePurgeMask | enableMask;

        //xmen
        s.write(NONE_CCR.addr, val, Size.WORD);
        res = readByte(s, NONE_CCR);
        Assertions.assertEquals(0, res);

        //cache purge always reads 0
        s.write(NONE_CCR.addr, val, Size.BYTE);
        res = readByte(s, NONE_CCR);
        Assertions.assertEquals(val & (~cachePurgeMask), res);

        s.write(NONE_CCR.addr, 0, Size.BYTE);
        res = readByte(s, NONE_CCR);
        Assertions.assertEquals(0, res);
    }

    private int readLong(Sh2MMREG s, Sh2Dict.RegSpecSh2 r) {
        return s.read(r.addr, Size.LONG);
    }

    private int readByte(Sh2MMREG s, Sh2Dict.RegSpecSh2 r) {
        return s.read(r.addr, Size.BYTE);
    }

    private int readLowWord(Sh2MMREG s, Sh2Dict.RegSpecSh2 r) {
        assert r.regSpec.regSize == Size.LONG;
        return s.read(r.addr + 2, Size.WORD) & 0xFFFF;
    }
}
