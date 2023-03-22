package s32x.sh2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class Tsti extends Sh2BaseTest {

    //11001000iiiiiiii
    private static int baseCode = 0xC800;

    @Test
    public void tsti() {
        tstiInt(baseCode | 2, 2, 0, 0);
        tstiInt(baseCode | 1, 2, 0, 1);
        tstiInt(baseCode | 1, 2, 1, 1);
        tstiInt(baseCode | 1, 0, 0, 1);
        tstiInt(baseCode | 1, 0, 1, 1);
        tstiInt(baseCode | 1, 20, 1, 1);
        tstiInt(baseCode | 8, 0xffff8, 0, 0);
    }

    private void tstiInt(int opcode, int r0, int prevT, int expT) {
        ctx.SR = prevT == 0 ? ctx.SR & (~Sh2.flagT) : ctx.SR | Sh2.flagT;
        ctx.registers[0] = r0;
        sh2.TSTI(opcode);
        Assertions.assertEquals(expT, ctx.SR & Sh2.flagT);
    }
}
