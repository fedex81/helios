package s32x.sh2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class SubTest extends Sh2BaseTest {

    int baseCode = 0x300A;
    int NO_OVF = 0, NO_CARRY = 0;
    int OVF = 1, CARRY = 1;

    @Test
    public void testSUBC() {
        //r0 - r1 - T
        testSUBCInternal(0, 0, 0, NO_CARRY);
        testSUBCInternal(0, 0, 1, CARRY);

        testSUBCInternal(0, 1, 0, CARRY);
        testSUBCInternal(0, 1, 1, CARRY);

        testSUBCInternal(0, -1, 0, CARRY);
        testSUBCInternal(0, -1, 1, CARRY);

        testSUBCInternal(0, -53, 0, CARRY);
        testSUBCInternal(0, -53, 1, CARRY);

        testSUBCInternal(-1, 0, 0, NO_CARRY);
        testSUBCInternal(-1, 0, 1, NO_CARRY);

        testSUBCInternal(1, 1, 0, NO_CARRY);
        testSUBCInternal(1, 1, 1, CARRY);

        testSUBCInternal(-1, 1, 0, NO_CARRY);
        testSUBCInternal(-1, 1, 1, NO_CARRY);

        testSUBCInternal(-1, -1, 0, NO_CARRY);
        testSUBCInternal(-1, -1, 1, CARRY);

        testSUBCInternal(1, -1, 0, CARRY);
        testSUBCInternal(1, -1, 1, CARRY);
    }

    private void testSUBCInternal(int r0, int r1, int sr, int expT) {
        ctx.SR = sr;
        ctx.registers[0] = r0;
        ctx.registers[1] = r1;
        sh2.SUBC(baseCode + 0x10); //SUBC r1,r0
        Assertions.assertEquals(expT, ctx.SR & Sh2.flagT);
    }
}
