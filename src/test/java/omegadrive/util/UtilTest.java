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

    byte[] input = new byte[0x100];
    int[] signIntInput = new int[0x100];
    int[] unsignIntInput = new int[0x100];

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
