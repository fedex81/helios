package omegadrive.cpu.z80;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * TODO add support for 0xCB, 0xDD, 0xED, 0xFD
 */
public class Z80CpuFastDebugTest {

    @Test
    public void testBusyLoopDetection() {
        int[] opcodes;
        /*
         * 00000000            E9    jp (hl)
         */
        opcodes = new int[]{0xE9};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 0000009d            00    nop
         */
        opcodes = new int[]{0};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 0000009d            00    nop
         * 0000009e      C3 9D 00    jp $009D
         */
        opcodes = new int[]{0, 0xC3};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));


        /**
         * 00000035            7E    ld a,(hl)
         * 00000036            B7    or a
         * 00000037      F2 35 00    jp p,$0035
         */
        opcodes = new int[]{0x7E, 0xB7, 0xF2};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 00000711      3A 00 40    ld a,($4000)
         * 00000714         E6 01    and $01
         * 00000716         28 F9    jr z,$0711
         */
        opcodes = new int[]{0x3A, 0xE6, 0x28};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 00000059      DD CB 4E    bit 1,(ix+$00)
         * 0000005d         28 FA    jr z,$0059
         */
//        opcodes = new int[]{0xDD, 0xCB, 0x28};
//        Assertions.assertTrue(isBusyLoop(opcodes));
//        Assertions.assertFalse(isIgnored(opcodes));

        /* 00000338      3A 00 40    ld a,($4000)
         * 0000033b         CB 4F    bit 1,a
         * 0000033d      CA 38 03    jp z,$0338
         */
//        opcodes = new int[]{0x3A, 0xCB, 0x4F, 0xCA};
//        Assertions.assertTrue(isBusyLoop(opcodes));
//        Assertions.assertFalse(isIgnored(opcodes));

        /* 00000d1a         CB 46    bit 0,(hl)
         * 00000d1c         28 FC    jr z,$0D1A
         */
//        opcodes = new int[]{0xCB, 0x46, 0x28};
//        Assertions.assertTrue(isBusyLoop(opcodes));
//        Assertions.assertFalse(isIgnored(opcodes));

        /* 00000711      3A 00 40    ld a,($4000)
         * 00000714         E6 01    and $01
         * 00000716         28 F9    jr z,$0711
         */
        opcodes = new int[]{0x3A, 0xE6, 0x28};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 00000003         18 FE    jr $0003
         */
        opcodes = new int[]{0x18};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 00000095      3A 8E 1F    ld a,($1F8E)
         * 00000098            B7    or a
         * 00000099      CA 95 00    jp z,$0095
         */
        opcodes = new int[]{0x3A, 0xB7, 0xCA};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * 00000112      3A FA 0C    ld a,($0CFA)
         * 00000115            A7    and a
         * 00000116         20 FA    jr nz,$0112
         */
        opcodes = new int[]{0x3A, 0xA7, 0x20};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));
        /*
         * 000002bb         CB 46    bit 0,(hl)
         * 000002bd      CA BB 02    jp z,$02BB
         */
//        opcodes = new int[]{0xCB, 0x46, 0xCA};
//        Assertions.assertTrue(isBusyLoop(opcodes));
//        Assertions.assertFalse(isIgnored(opcodes));

        /*
         *  00000174            7A    ld a,d
         * 00000175            B3    or e
         * 00000176         28 FC    jr z,$0174
         */
        opcodes = new int[]{0x7A, 0xB3, 0x28};
        Assertions.assertTrue(isBusyLoop(opcodes));
        Assertions.assertFalse(isIgnored(opcodes));

        /**
         * ??
         * 00000028   7E FB 1F 00 00    ld a,(hl)
         * 00000029   FE 81 1F 00 00    cp $81
         * 0000002b   38 FB 1F 00 00    jr c,$0028
         */
    }

    @Test
    public void testIgnore() {
        int[] opcodes;

        //0000000c         ED B0    ldir
        opcodes = new int[]{0xED, 0xB0};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertTrue(isIgnored(opcodes));

        //0000008c         10 FE    djnz $008C
        opcodes = new int[]{0x10};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertTrue(isIgnored(opcodes));

        //00000137            2B    dec hl
        opcodes = new int[]{0x2B};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertTrue(isIgnored(opcodes));

        /**
         * 00000136            77    ld (hl),a
         * 00000137            23    inc hl
         * 00000138            05    dec b
         * 00000139         20 FB    jr nz,$0136
         */
        opcodes = new int[]{0x77, 0x23, 0x05, 0x20};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertTrue(isIgnored(opcodes));

        /**
         * ??
         * 0000005b            FB    ei
         * 0000005c      3A FF 1F    ld a,($1FFF)
         * 0000005f            B7    or a
         * 00000060      C2 5B 00    jp nz,$005B
         *
         * ??
         * 00000000            F3    di
         * 00000001            F3    di
         * 00000002      C3 00 00    jp $0000
         *
         * ??
         * 00000018   00 00 04 1F 00    nop
         * 00000019   77 00 04 1F 00    ld (hl),a
         * 0000001a   DF 00 04 1F 00    rst $18
         */
        opcodes = new int[]{0xFB, 0x3A, 0xB7, 0xC2};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertTrue(isIgnored(opcodes));

        opcodes = new int[]{0xF3, 0xF3, 0xC3};
        Assertions.assertFalse(isBusyLoop(opcodes));
        Assertions.assertTrue(isIgnored(opcodes));
    }

    private boolean isBusyLoop(int[] opcodes) {
        for (int i = 0; i < opcodes.length; i++) {
            boolean res = Z80Helper.isBusyLoop(opcodes[i], 0);
            if (!res) {
                return false;
            }
        }
        return true;
    }

    private boolean isIgnored(int[] opcodes) {
        return !isBusyLoop(opcodes);
    }
}
