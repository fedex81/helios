package mcd.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class FixedPointUtilTest {
    @Test
    public void test5p11() {
        double expected = 262120.00048828125;
        double total = 0;
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            double res = FixedPointUtil.convert5p11FixedPointSigned(i);
            total += res;
        }
        Assertions.assertEquals(expected, total);
    }

    @Test
    public void test13p3() {
        double expected = 6.7102720125E7;
        double total = 0;
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            double res = FixedPointUtil.convert13p3FixedPointUnsigned(i);
            total += res;
        }
        Assertions.assertEquals(expected, total);
    }
}
