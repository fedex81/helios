package omegadrive.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static omegadrive.util.ArrayEndianUtil.setByteInWordBE;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class ArrayEndianUtilTest {

    @Test
    public void testSetByteInWordBE() {
        int res = 0x2211;
        for (int i = 0; i < 0x10000; i += 100) {
            int t = i;
            t = setByteInWordBE(t, 0xBB11, 1);
            t = setByteInWordBE(t, 0xAA22, 0);
            Assertions.assertEquals(res, t);
        }
    }

    @Test
    public void testSetNibbleInByteBE() {
        int[][] test = {
                {1, 376, -128, -120},
                {0, 240, 35, 3},
                {1, 259, 16, 19},
                {1, 266, 16, 26},
                {1, 469, -32, -27}
        };
        for (int i = 0; i < test.length; i++) {
            int[] testVals = test[i];
            int res = ArrayEndianUtil.setNibbleInByteBE(testVals[2], testVals[1], testVals[0]);
            Assertions.assertEquals(testVals[3], res);
        }

    }
}
