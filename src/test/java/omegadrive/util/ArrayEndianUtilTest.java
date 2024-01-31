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
}
