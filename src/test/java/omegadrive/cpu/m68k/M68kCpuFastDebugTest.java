package omegadrive.cpu.m68k;

import omegadrive.cpu.CpuFastDebug;
import omegadrive.cpu.m68k.debug.MC68000WrapperFastDebug;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class M68kCpuFastDebugTest {

    static int[] test01 = {1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            2, 3, 3, 2, 3, 3, 2, 3, 3};

    static int[] test02 = {1, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 4, 5, 3,
            2, 4, 5, 2, 4, 5, 2, 4, 5};

    static int[] test03 = {1, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 6, 7, 8, 3, 3,
            2, 3, 3, 2, 3, 3, 2, 3, 3};

    @Test
    public void testLoopRepetitionDetection() {
        CpuFastDebug.CpuDebugContext ctx = MC68000WrapperFastDebug.createContext();
        CpuFastDebug cfd = new CpuFastDebug(null, ctx);
        for (int i = 0; i < test01.length; i++) {
            cfd.isBusyLoop(test01[i], 1);
        }
        for (int i = 0; i < test02.length; i++) {
            cfd.isBusyLoop(test02[i], 1);
        }
        for (int i = 0; i < test03.length; i++) {
            cfd.isBusyLoop(test02[i], 1);
        }
    }

    @Test
    public void testBusyLoopDetection() {
        int[] opcodes;
        /*
         * sor2
         * 00001a4e   4a38 fc10               tst.b    $fc10
         * 00001a52   66fa                    bne.s    $00001a4e
         *
         * s1
         * 000029ac   4a38 f62a               tst.b    $f62a
         * 000029b0   66fa                    bne.s    $000029ac
         *
         */
        opcodes = new int[]{0x4a38, 0x66fa};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * s1
         * 0000128e   3215                     move.w   (a5),d1
         * 00001290   0801 0001                btst     #$1,d1
         * 00001294   66f8                    bne.s    $0000128e
         */
        opcodes = new int[]{0x3215, 0x0801, 0x66f8};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * sor2
         *00001734   3814                     move.w   (a4),d4
         * 00001736   0804 0001                btst     #$1,d4
         * 0000173a   66f8                    bne.s    $00001734
         */
        opcodes = new int[]{0x3814, 0x0804, 0x66f8};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * jim power
         * 00004dd2   4a10                    tst.b    (a0)
         * 00004dd4   67fc                    beq.s    $00004dd2
         *
         * 0000465e   4a78 eb8c               tst.w    $eb8c
         * 00004662   6afa                    bpl.s    $0000465e
         */
        opcodes = new int[]{0x4a10, 0x67fc};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
        opcodes = new int[]{0x4a78, 0x6afa};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * outrun
         * 0000044a   3e14                     move.w   (a4),d7
         * 0000044c   0247 0002                andi.w   #$0002,d7
         * 00000450   66f8                    bne.s    $0000044a
         *
         * 00000664   4a38 f014               tst.b    $f014
         * 00000668   67fa                    beq.s    $00000664
         */
        opcodes = new int[]{0x4a38, 0x67fa};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
        opcodes = new int[]{0x3e14, 0x0247, 0x66f8};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * s2
         * 000011d2   3215                     move.w   (a5),d1
         * 000011d4   0801 0001                btst     #$1,d1
         * 000011d8   66f8                    bne.s    $000011d2
         *
         * 0000414   3039 00c00004            move.w   $00c00004,d0
         * 0000041a   0240 0008                andi.w   #$0008,d0
         * 0000041e   67f4                    beq.s    $00000414
         *
         * 0016a7a   4a78 f644               tst.w    $f644
         * 00016a7e   66fa                    bne.s    $00016a7a
         */
        opcodes = new int[]{0x4a78, 0x66fa};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
        opcodes = new int[]{0x3215, 0x0801, 0x66f8};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
        opcodes = new int[]{0x3039, 0x0240, 0x67f4};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
          titan
          003a4ca8   0839 0003 00c00005       btst     #$3,$00c00005
          003a4cb0   66f6                    bne.s    $003a4ca8

          003a4eb0   0c39 0002 00c00008       cmpi.b   #$02,$00c00008
          003a4eb8   66f6                    bne.s    $003a4eb0

          003a4fca   0839 0003 00c00005       btst     #$3,$00c00005
          003a4fd2   67f6                    beq.s    $003a4fca
         */
        opcodes = new int[]{0x0839, 0x66f6};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
        opcodes = new int[]{0x0c39, 0x66f6};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
        opcodes = new int[]{0x0839, 0x67f6};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 0006eb4e   b038 fcf2                cmp.b    $fcf2,d0
         * 0006eb52   67fa                    beq.s    $0006eb4e
         *
         * 000f4d2c   b079 00ff0106            cmp.w    $00ff0106,d0
         * 000f4d32   67f8                    beq.s    $000f4d2c
         */
        opcodes = new int[]{0xb038, 0x67fa};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
        opcodes = new int[]{0xb079, 0x67f8};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 0004698e   0cad 000451fc 0014       cmpi.l   #$000451fc,$0014(a5)
         * 00046996   66f6                    bne.s    $0004698e
         */
        opcodes = new int[]{0x0cad, 0x66f6};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 00000e88   b06d ffff83f0                cmp.w    $83f0(a5),d0
         * 00000e8c   67fa                    beq.s    $00000e88
         */
        opcodes = new int[]{0xb06d, 0x67fa};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 00003a8e   c050                     and.w    (a0),d0
         * 00003a90   66fc                    bne.s    $00003a8e
         */
        opcodes = new int[]{0xc050, 0x66fc};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 00004128   c079 00c00004            and.w    $00c00004,d0
         * 0000412e   66f8                    bne.s    $00004128
         */
        opcodes = new int[]{0xc079, 0x66f8};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 00000334   b050                     cmp.w    (a0),d0
         * 00000336   67fc                    beq.s    $00000334
         */
        opcodes = new int[]{0xb050, 0x67fc};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 0002f44e   0510                     btst     d2,(a0)
         * 0002f450   67fc                    beq.s    $0002f44e
         */
        opcodes = new int[]{0x0510, 0x67fc};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 000006a6   102d 002b                move.b   $002b(a5),d0
         * 000006aa   66fa                    bne.s    $000006a6
         */
        opcodes = new int[]{0x102d, 0x66fa};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /*
         * 0002e784   b3ed ffffd47a                cmpa.l   $d47a(a5),a1
         * 0002e788   67fa                    beq.s    $0002e784
         *
         * 000077f0   08b8 0007 f010           bclr     #$7,$f010
         * 000077f6   67f8                    beq.s    $000077f0
         */
    }

    @Test
    public void testNoLoop() {
        int[] opcodes;
        /*
         * 0001451e   0810 0004                btst     #$4,(a0)
         * 00014522   57c8 fffffffa                dbeq     d0,$0001451e
         *
         * modifies d0
         */
        opcodes = new int[]{0x0810, 0x57c8};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertTrue(isIgnored(opcodes));

        /*
         * 000007bc   08d5 0001                bset     #$1,(a5)
         * 000007c0   67fa                    beq.s    $000007bc
         *
         * modifies (a5)
         */
        opcodes = new int[]{0x08d5, 0x67fa};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
    }

    @Test
    public void test32xDetect() {
        int[] opcodes;
        /**
         * 008807c2   3429 0028                move.w   $0028(a1),d2
         * 008807c6   0c42 0000                cmpi.w   #$0000,d2
         * 008807ca   67f6                     beq.s    $008807c2
         */
        opcodes = new int[]{0x3429, 0x0c42, 0x67f6};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 008823dc   3016                     move.w   (a6),d0
         * 008823de   0c40 464e                cmpi.w   #$464e,d0
         * 008823e2   6600 fffffff8            bne.w    $008823dc
         */
        opcodes = new int[]{0x3016, 0x0c40, 0x6600};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 0088096c   4efa fffffffe               jmp      $fffe(pc)
         */
        opcodes = new int[]{0x4efa};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 0088081a   0c90 4d5f4f4b            cmpi.l   #$4d5f4f4b,(a0)
         * 00880820   66f8                    bne.s    $0088081a
         */
        opcodes = new int[]{0x0c90, 0x66f8};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
    }

    @Test
    public void testIgnore() {
        int[] opcodes;
        /*
         * 00006634   5340                     subq.w   #1,d0
         * 00006636   66fc                    bne.s    $00006634
         */
        opcodes = new int[]{0x5340, 0x66fc};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertTrue(isIgnored(opcodes));
    }

    private boolean isBusyLoop(int[] opcodes) {
        return CpuFastDebug.isBusyLoop(MC68000WrapperFastDebug.isLoopOpcode, opcodes);
    }

    private boolean isIgnored(int[] opcodes) {
        return CpuFastDebug.isIgnore(MC68000WrapperFastDebug.isIgnoreOpcode, opcodes);
    }
}
