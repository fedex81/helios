package omegadrive.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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

    byte[] input = new byte[SIZE];
    int[] signIntInput = new int[SIZE];
    int[] unsignIntInput = new int[SIZE];

    @Before
    public void setup() {
        int k = 0;
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++, k++) {
//            System.out.println(k + "->" + i);
            input[k] = (byte) i;
            signIntInput[k] = i;
            unsignIntInput[k] = i & 0xFF;
        }
    }

    @Test
    public void testReadNegative() {
        long res = Util.readDataMask(signIntInput, Size.WORD, 0, MASK);
        Assert.assertEquals(0x8081, res);

        res = Util.readDataMask(signIntInput, Size.LONG, 0, MASK);
        Assert.assertEquals(0x80818283, res);
    }

    @Test
    public void testLongReadBoundary() {
        int address = 0xFEFFFE;
        int mask = signIntInput.length - 1;
        long expect = 0x7e7f8081;
        long res = Util.readDataMask(signIntInput, Size.LONG, address, mask);
        Assert.assertEquals(expect, res);
    }

    @Test
    public void testLongWriteBoundary() {
        int address = 0xFEFFFE;
        long value = 0x6e6f7071;
        Util.writeDataMask(signIntInput, Size.LONG, address, value, MASK);
        long res = Util.readDataMask(signIntInput, Size.LONG, address, MASK);
        Assert.assertEquals(value, res);
    }

    @Test
    public void testToByteArray() {
        int[] iin = Util.toSignedIntArray(input);
        byte[] out = Util.signedToByteArray(iin);
        Assert.assertArrayEquals(input, out);

        int[] iin2 = Util.toUnsignedIntArray(input);
        byte[] out2 = Util.unsignedToByteArray(iin2);
        Assert.assertArrayEquals(input, out2);
    }

    @Test
    public void testToByteArray2() {
        byte[] bin = Util.signedToByteArray(signIntInput);
        int[] out = Util.toSignedIntArray(bin);
        Assert.assertArrayEquals(signIntInput, out);

        byte[] binu = Util.unsignedToByteArray(unsignIntInput);
        int[] outu = Util.toUnsignedIntArray(binu);
        Assert.assertArrayEquals(unsignIntInput, outu);
    }

    @Test
    public void testToByteArray3() {
        int[] a = {-130};
        try {
            Util.signedToByteArray(a);
            Assert.fail();
        } catch (Exception e) {
            //expected
            System.out.println(e.getMessage());
        }

        int[] b = {230};
        try {
            Util.signedToByteArray(b);
            Assert.fail();
        } catch (Exception e) {
            //expected
            System.out.println(e.getMessage());
        }
        int[] c = {-1};
        try {
            Util.unsignedToByteArray(a);
            Assert.fail();
        } catch (Exception e) {
            //expected
            System.out.println(e.getMessage());
        }

        int[] d = {260};
        try {
            Util.unsignedToByteArray(d);
            Assert.fail();
        } catch (Exception e) {
            //expected
            System.out.println(e.getMessage());
        }
    }
}
