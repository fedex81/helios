package s32x.sh2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static s32x.sh2.Sh2.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class Div1Test extends Sh2BaseTest {

    /**
     * Sequence taken from
     * Mars Sample Program - Gnu Sierra (Unknown) (SDK Build).32x
     */
    @Test
    public void testDiv1() {
        int n = 1, m = 5;
        final int[] R = ctx.registers;

        run(ctx, 0xffffffff, 0x11b4c, 0xFFD0B800);
        run(ctx, 0, 0x11b4c, 0x195000);

        run(ctx, 0, 0xf842, 0xe200);
        run(ctx, 0, 0xf842, 0x195000);
        run(ctx, 0, 0x12c00, 0);
        run(ctx, 0, 0x12c00, 0x3ab800);
        run(ctx, 0xffffffff, 0x103fc, 0xffe20000);
        run(ctx, 0xffffffff, 0x103fc, 0xffe7b000);
        run(ctx, 0, 0x11b6a, 304800);
        run(ctx, 0, 0x11b6a, 195000);
        run(ctx, 0xffffffff, 0x156e6, 0xffe15a00);
        run(ctx, 0, 0x156e6, 0x195000);
        run(ctx, 0, 0x15504, 0x1e0000);
        run(ctx, 0, 0x15504, 0x195000);
        run(ctx, 0xffffffff, 0x15504, 0x195000);
        run(ctx, 0xffffffff, 0x13d96, 0xffd0b800);
        run(ctx, 0, 0x13d96, 0xffe7b000);
        run(ctx, 0xffffffff, 0x1021a, 0x1fa600);
        run(ctx, 0, 0x1021a, 0xffe7b000);
        run(ctx, 0xffffffff, 0x13db4, 0x304800);
        run(ctx, 0, 0x13db4, 0xffe7b000);
        run(ctx, 0xffffffff, 0x160be, 0x1e00);
        run(ctx, 0, 0x160be, 0xffe7b000);
        run(ctx, 0, 0x12c00, 0);
        run(ctx, 0xffffffff, 0x12c00, 0xffc64800);

        int expR1 = 0xffffb400;
        int expR5 = 0x12c00;
        int expR4 = 0xFFC64800;
        int expQ = 1, expM = 0, expT = 0;

        Assertions.assertEquals(expR1, R[1]);
        Assertions.assertEquals(expR5, R[5]);
        Assertions.assertEquals(expR4, R[4]);
        Assertions.assertEquals(expM > 0, (ctx.SR & flagM) > 0);
        Assertions.assertEquals(expQ > 0, (ctx.SR & flagQ) > 0);
        Assertions.assertEquals(expT > 0, (ctx.SR & flagT) > 0);
    }

    private void run(Sh2Context ctx, int val_n, int val_m, int val_r4) {
        run(ctx, -1, -1, val_n, val_m, val_r4);
    }

    private void run(Sh2Context ctx, int m, int n, int val_n, int val_m, int val_r4) {
        n = n < 0 ? 1 : n;
        m = m < 0 ? 5 : m;
        int r4 = 4;
        ctx.registers[n] = val_n;
        ctx.registers[m] = val_m;
        ctx.registers[r4] = val_r4;

        sh2.DIV0S((m << 4) | (n << 8));

        for (int i = 0; i < 32; i++) {
            sh2.ROTL(r4 << 8);
            sh2.DIV1(ctx, n, m);
        }
    }

    //		System.out.printf("####,div1: r[%d]=%x >= r[%d]=%x, %d, %d, %d\n", dvd,
//				ctx.registers[dvd], dvsr, ctx.registers[dvsr], ((ctx.SR & flagM) > 0) ? 1: 0,
//				((ctx.SR & flagQ) > 0) ? 1: 0,
//				((ctx.SR & flagT) > 0) ? 1: 0);
    public final static void DIV1old(Sh2Context ctx, int dvd, int dvsr) {
        long tmp0;
        int old_q;

        old_q = ctx.SR & flagQ;
        if ((0x80000000 & ctx.registers[dvd]) != 0)
            ctx.SR |= flagQ;
        else
            ctx.SR &= ~flagQ;

        long dvdl = ctx.registers[dvd] & 0xFFFF_FFFFL;
        long dvsrl = ctx.registers[dvsr] &= 0xFFFF_FFFFL;

        dvdl <<= 1;
        dvdl |= (ctx.SR & flagT);
        dvdl &= 0xFFFF_FFFFL;
//		System.out.printf("1: %x\n", dvdl);

        tmp0 = dvdl;

        if (old_q == 0) {
            if ((ctx.SR & flagM) == 0) {
                dvdl -= dvsrl;
                dvdl &= 0xFFFF_FFFFL;
//				System.out.printf("2a: %x\n", dvdl);
                if ((ctx.SR & flagQ) == 0) {
                    if (dvdl > tmp0) {
                        ctx.SR |= flagQ;
                    } else {
                        ctx.SR &= ~flagQ;
                    }
                } else if (dvdl > tmp0) {
                    ctx.SR &= ~flagQ;
                } else {
                    ctx.SR |= flagQ;
                }
            } else {
                dvdl += dvsrl;
                dvdl &= 0xFFFF_FFFFL;
//				System.out.printf("2b: %x\n", dvdl);
                if ((ctx.SR & flagQ) == 0) {
                    if (dvdl < tmp0) {
                        ctx.SR &= ~flagQ;
                    } else {
                        ctx.SR |= flagQ;
                    }
                } else {
                    if (dvdl < tmp0) {
                        ctx.SR |= flagQ;
                    } else {
                        ctx.SR &= ~flagQ;
                    }
                }
            }
        } else {
            if ((ctx.SR & flagM) == 0) {
                dvdl += dvsrl;
                dvdl &= 0xFFFF_FFFFL;
//				System.out.printf("2c: %x\n", dvdl);
                if ((ctx.SR & flagQ) == 0) {
                    if (dvdl < tmp0) {
                        ctx.SR |= flagQ;
                    } else {
                        ctx.SR &= ~flagQ;
                    }
                } else {
                    if (dvdl < tmp0) {
                        ctx.SR &= ~flagQ;
                    } else {
                        ctx.SR |= flagQ;
                    }
                }
            } else {
                dvdl -= dvsrl;
                dvdl &= 0xFFFF_FFFFL;
//				System.out.printf("2d: %x\n", dvdl);
                if ((ctx.SR & flagQ) == 0) {
                    if (dvdl > tmp0) {
                        ctx.SR &= ~flagQ;
                    } else {
                        ctx.SR |= flagQ;
                    }
                } else {
                    if (dvdl > tmp0) {
                        ctx.SR |= flagQ;
                    } else {
                        ctx.SR &= ~flagQ;
                    }
                }
            }
        }

        tmp0 = (ctx.SR & (flagQ | flagM));
        if (((tmp0) == 0) || (tmp0 == 0x300)) /* if Q == M set T else clear T */
            ctx.SR |= flagT;
        else
            ctx.SR &= ~flagT;

        ctx.registers[dvd] = (int) dvdl;
//		System.out.printf("####,div1s: r[%d]=%x >= r[%d]=%x, %d, %d, %d\n", dvd,
//				ctx.registers[dvd], dvsr, ctx.registers[dvsr], ((ctx.SR & flagM) > 0) ? 1: 0,
//				((ctx.SR & flagQ) > 0) ? 1: 0,
//				((ctx.SR & flagT) > 0) ? 1: 0);
        ctx.cycles--;
        ctx.PC += 2;
    }
}
