package omegadrive.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static omegadrive.util.ArrayEndianUtil.*;

/**
 * UtilTest
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class UtilTest {

    private static final int SIZE = 0x100;
    private static final int MASK = SIZE - 1;

    private static final String sentinelPath = "src/test/resources/misc/";

    public static final boolean RUNNING_IN_GITHUB;

    static {
        System.out.println(new File(".").getAbsolutePath());
        Path p = Paths.get(".", sentinelPath);
        RUNNING_IN_GITHUB = !p.toFile().exists();
        System.err.println("Ignore tests failing in GitHub: " + RUNNING_IN_GITHUB);
    }

    byte[] input = new byte[SIZE];

    @Before
    public void setup() {
        int k = 0;
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++, k++) {
//            System.out.println(k + "->" + i);
            input[k] = (byte) i;
        }
    }

    @Test
    public void testReadNegative() {
        long res = Util.readDataMask(input, Size.WORD, 0, MASK);
        Assert.assertEquals(0x8081, res);

        res = Util.readDataMask(input, Size.LONG, 0, MASK);
        Assert.assertEquals(0x80818283, res);
    }

    @Test
    public void testLongReadBoundary() {
        int address = 0xFEFFFE;
        int mask = input.length - 1;
        long expect = 0x7e7f8081;
        try {
            long res = Util.readDataMask(input, Size.LONG, address, mask);
            Assert.fail();
        } catch (Exception e) {
        } //expected
    }

    /**
     * Chaotix 1994-12-07
     * <p>
     * 68k: LONG write to RAM 0xff_fffe, triggers an ArrayIndexOBE
     * Hw is using two word writes: 0xFF_FFFE, and 0xFF_FFFE + 2 = 0x1_0000 = 0
     * ie. the 2nd write goes to ROM area and it is ignored
     * <p>
     * works on hw
     */
    @Test
    public void testLongWriteBoundary_NotSupported() {
        int address = 0xFEFFFE;
        int value = 0x6e6f7071;
        try {
            Util.writeDataMask(input, Size.LONG, address, value, MASK);
            Assert.fail();
        } catch (Exception e) {
        } //expected
    }

    @Test
    public void testToByteArray() {
        int[] iin = toSignedIntArray(input);
        byte[] out = signedToByteArray(iin);
        Assert.assertArrayEquals(input, out);

        int[] iin2 = toUnsignedIntArray(input);
        byte[] out2 = unsignedToByteArray(iin2);
        Assert.assertArrayEquals(input, out2);
    }

    @Test
    public void testToByteArray3() {
        int[] a = {-130};
        try {
            signedToByteArray(a);
            Assert.fail();
        } catch (Exception e) {
            //expected
            System.out.println(e.getMessage());
        }

        int[] b = {230};
        try {
            signedToByteArray(b);
            Assert.fail();
        } catch (Exception e) {
            //expected
            System.out.println(e.getMessage());
        }
        int[] c = {-1};
        try {
            unsignedToByteArray(c);
            Assert.fail();
        } catch (Exception e) {
            //expected
            System.out.println(e.getMessage());
        }

        int[] d = {260};
        try {
            unsignedToByteArray(d);
            Assert.fail();
        } catch (Exception e) {
            //expected
            System.out.println(e.getMessage());
        }
    }
}
