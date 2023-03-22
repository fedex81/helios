package s32x.sh2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class PrTest extends Sh2BaseTest {

    @Test
    public void testPR() {
        testPRInternal(0xFFFF_1111);
        testPRInternal(-1);
        testPRInternal(0x22223333);
        testPRInternal(0xEEEE);
        testPRInternal(0x4444);
        testPRInternal(0xDD);
        testPRInternal(0x55);
    }

    private void testPRInternal(int val) {
        ctx.PR = val;
        sh2.STSPR(0x12A); //sts PR, R1
        sh2.LDSPR(0x412A); //lds R1, PR
        Assertions.assertEquals(val, ctx.registers[1]);
    }
}
