package s32x.sh2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class AddTest extends Sh2BaseTest {

    int baseCode = 0x300E;
    int baseCodeV = 0x300F;

    int NO_OVF = 0, NO_CARRY = 0;
    int OVF = 1, CARRY = 1;

    @Test
    public void testADDC() {
        testADDCInternal(0, 0xFFFF_FFFE, 0, NO_CARRY);
        testADDCInternal(0, 0xFFFF_FFFE, 1, NO_CARRY);
        testADDCInternal(1, 0xFFFF_FFFE, 0, NO_CARRY);
        testADDCInternal(1, 0xFFFF_FFFE, 1, CARRY);

        testADDCInternal(1, 0xFFFF_FFFF, 0, CARRY);
        testADDCInternal(0, 0xFFFF_FFFF, 1, CARRY);
        testADDCInternal(0, 0xFFFF_FFFF, 0, NO_CARRY);
        testADDCInternal(1, 0xFFFF_FFFF, 1, CARRY);

        testADDCInternal(0, 0x7FFF_FFFF, 0, NO_CARRY);
        testADDCInternal(0, 0x7FFF_FFFF, 1, NO_CARRY);
        testADDCInternal(1, 0x7FFF_FFFF, 0, NO_CARRY);
        testADDCInternal(1, 0x7FFF_FFFF, 1, NO_CARRY);
    }

    @Test
    public void testADDV() {
        testADDVInternal(1, 0x7FFF_FFFE, 0, NO_OVF);
        testADDVInternal(1, 0x7FFF_FFFE, 1, NO_OVF);

        testADDVInternal(1, 0x7FFF_FFFF, 0, OVF);
        testADDVInternal(1, 0x7FFF_FFFF, 1, OVF);

        testADDVInternal(-1, 0x8000_0001, 0, NO_OVF);
        testADDVInternal(-1, 0x8000_0001, 1, NO_OVF);

        testADDVInternal(-1, 0x8000_0000, 0, OVF);
        testADDVInternal(-1, 0x8000_0000, 1, OVF);
    }

    private void testADDCInternal(int r0, int r1, int sr, int expT) {
        ctx.SR = sr;
        ctx.registers[0] = r0;
        ctx.registers[1] = r1;
        sh2.ADDC(baseCode + 0x10); //ADDC r1,r0
        Assertions.assertEquals(expT, ctx.SR & Sh2.flagT);
    }

    private void testADDVInternal(int r0, int r1, int sr, int expT) {
        ctx.SR = sr;
        ctx.registers[0] = r0;
        ctx.registers[1] = r1;
        sh2.ADDV(baseCodeV + 0x10); //ADDV r1,r0
        Assertions.assertEquals(expT, ctx.SR & Sh2.flagT);
    }
}
