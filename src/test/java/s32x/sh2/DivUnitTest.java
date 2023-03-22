package s32x.sh2;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.MarsRegTestUtil;
import s32x.sh2.device.DivUnit;
import s32x.util.MarsLauncherHelper;

import static omegadrive.util.Util.th;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * Some tests taken from yabause, see: void div_operation_test(void)
 */
public class DivUnitTest {

    private DivUnit divUnit;

    @BeforeEach
    public void before() {
        MarsLauncherHelper.Sh2LaunchContext lc = MarsRegTestUtil.createTestInstance();
        lc.s32XMMREG.aden = 1;
        divUnit = lc.mDevCtx.divUnit;
    }

    @Test
    public void testDivs() {
        //64/32
        long quot = 0x10L << 24;
        long rem = 9;
        divUnit.write(DIV_DVSR, 0x10, Size.LONG);
        divUnit.write(DIV_DVDNTH, 0x1, Size.LONG);
        divUnit.write(DIV_DVDNTL, 0x9, Size.LONG);

        Assertions.assertEquals(quot, divUnit.read(DIV_DVDNTL, Size.LONG));
        Assertions.assertEquals(quot, divUnit.read(DIV_DVDNT, Size.LONG));
        Assertions.assertEquals(quot, divUnit.read(DIV_DVDNTUL, Size.LONG));

        Assertions.assertEquals(rem, divUnit.read(DIV_DVDNTH, Size.LONG));
        Assertions.assertEquals(rem, divUnit.read(DIV_DVDNTUH, Size.LONG));

        //32/32
        quot = 0x1000000;
        rem = 9;
        divUnit.write(DIV_DVSR, 0x10, Size.LONG);
        divUnit.write(DIV_DVDNT, 0x10000009, Size.LONG);

        Assertions.assertEquals(quot, divUnit.read(DIV_DVDNTL, Size.LONG));
        Assertions.assertEquals(quot, divUnit.read(DIV_DVDNT, Size.LONG));
        Assertions.assertEquals(quot, divUnit.read(DIV_DVDNTUL, Size.LONG));

        Assertions.assertEquals(rem, divUnit.read(DIV_DVDNTH, Size.LONG));
        Assertions.assertEquals(rem, divUnit.read(DIV_DVDNTUH, Size.LONG));
    }

    @Test
    public void testDiv64() {
        divUnit.write(DIV_DVCR, 0x0, Size.LONG);

        divUnit.write(DIV_DVSR, 0xFFFFFE50, Size.LONG);
        divUnit.write(DIV_DVDNTH, 0x256, Size.LONG);
        divUnit.write(DIV_DVDNTL, 0x0, Size.LONG);

        Assertions.assertEquals(1, divUnit.read(DIV_DVCR, Size.LONG));

        divUnit.write(DIV_DVCR, 0x0, Size.LONG);

        divUnit.write(DIV_DVSR, 0x2c7, Size.LONG);
        divUnit.write(DIV_DVDNTH, 0x256, Size.LONG);
        divUnit.write(DIV_DVDNTL, 0x0, Size.LONG);

        Assertions.assertEquals(1, divUnit.read(DIV_DVCR, Size.LONG));
    }

    @Test
    public void testDiv64Overflow() {
        //64 bit overflow positive
        divUnit.write(DIV_DVSR, 0x1, Size.LONG);
        divUnit.write(DIV_DVCR, 0, Size.LONG);
        divUnit.write(DIV_DVDNTH, 0x1, Size.LONG);
        divUnit.write(DIV_DVDNTL, 0, Size.LONG);

        Assertions.assertEquals(Integer.MAX_VALUE, divUnit.read(DIV_DVDNTL, Size.LONG));
        //TODO according to yabause it should be 0xFFFFFFFE
//        Assertions.assertEquals(quot, divUnit.read(DIV_DVDNTH, Size.LONG));
        Assertions.assertEquals(1, divUnit.read(DIV_DVCR, Size.LONG));

        //64 bit overflow negative
        divUnit.write(DIV_DVSR, -0x1, Size.LONG);
        divUnit.write(DIV_DVCR, 0, Size.LONG);
        divUnit.write(DIV_DVDNTH, 0x1, Size.LONG);
        divUnit.write(DIV_DVDNTL, 0, Size.LONG);

        Assertions.assertEquals(Integer.MIN_VALUE, divUnit.read(DIV_DVDNTL, Size.LONG));
        Assertions.assertEquals(1, divUnit.read(DIV_DVCR, Size.LONG));
    }

    @Test
    public void testDiv64Overflow2() {
        for (long i = -2; i < 3; i++) {
            //values around 0x7fffffff
            long v = i + Integer.MAX_VALUE;
            divUnit.write(DIV_DVSR, 0x1, Size.LONG);
            divUnit.write(DIV_DVCR, 0, Size.LONG);
            divUnit.write(DIV_DVDNTH, (int) ((v >> 32) & 0xFFFF_FFFFL), Size.LONG);
            divUnit.write(DIV_DVDNTL, (int) (v & 0xFFFF_FFFFL), Size.LONG);

            int expected = (int) Math.min(v, Integer.MAX_VALUE);
            Assertions.assertEquals(expected, divUnit.read(DIV_DVDNTL, Size.LONG));
            System.out.println(th(v) + "," + th(expected));

            //values around 0x80000000
            v = i + Integer.MIN_VALUE;
            divUnit.write(DIV_DVSR, 0x1, Size.LONG);
            divUnit.write(DIV_DVCR, 0, Size.LONG);
            divUnit.write(DIV_DVDNTH, (int) ((v >> 32) & 0xFFFF_FFFFL), Size.LONG);
            divUnit.write(DIV_DVDNTL, (int) (v & 0xFFFF_FFFFL), Size.LONG);

            expected = (int) Math.max(v, Integer.MIN_VALUE);
            Assertions.assertEquals(expected, divUnit.read(DIV_DVDNTL, Size.LONG));
            System.out.println(th(v) + "," + th(expected));
        }
    }

    @Test
    public void testDiv32ByZero() {
        divUnit.write(DIV_DVSR, 0, Size.LONG);
        divUnit.write(DIV_DVCR, 0, Size.LONG);
        divUnit.write(DIV_DVDNT, 0, Size.LONG);

        Assertions.assertEquals(Integer.MAX_VALUE, divUnit.read(DIV_DVDNTL, Size.LONG));
        Assertions.assertEquals(0, divUnit.read(DIV_DVDNTH, Size.LONG));
        Assertions.assertEquals(1, divUnit.read(DIV_DVCR, Size.LONG));

        divUnit.write(DIV_DVSR, 0, Size.LONG);
        divUnit.write(DIV_DVCR, 0, Size.LONG);
        divUnit.write(DIV_DVDNT, 0xD0000000, Size.LONG);

        Assertions.assertEquals(Integer.MAX_VALUE, divUnit.read(DIV_DVDNTL, Size.LONG));
//        Assertions.assertEquals(0xFFFFFFFE, divUnit.read(DIV_DVDNTH, Size.LONG));
        Assertions.assertEquals(1, divUnit.read(DIV_DVCR, Size.LONG));

    }

    @Test
    public void testDiv64ByZero() {
        divUnit.write(DIV_DVSR, 0, Size.LONG);
        divUnit.write(DIV_DVCR, 0, Size.LONG);
        divUnit.write(DIV_DVDNTL, 0, Size.LONG);

        Assertions.assertEquals(Integer.MAX_VALUE, divUnit.read(DIV_DVDNTL, Size.LONG));
        Assertions.assertEquals(0, divUnit.read(DIV_DVDNTH, Size.LONG));
        Assertions.assertEquals(1, divUnit.read(DIV_DVCR, Size.LONG));

        divUnit.write(DIV_DVSR, 0, Size.LONG);
        divUnit.write(DIV_DVCR, 0, Size.LONG);
        divUnit.write(DIV_DVDNTL, 0xD0000000, Size.LONG);

        Assertions.assertEquals(Integer.MAX_VALUE, divUnit.read(DIV_DVDNTL, Size.LONG));
//        Assertions.assertEquals(0xFFFFFFFE, divUnit.read(DIV_DVDNTH, Size.LONG));
        Assertions.assertEquals(1, divUnit.read(DIV_DVCR, Size.LONG));

    }
}
