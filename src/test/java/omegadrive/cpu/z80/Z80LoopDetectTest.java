package omegadrive.cpu.z80;

import omegadrive.SystemLoader;
import omegadrive.bus.model.MdMainBusProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import z80core.Z80State;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static omegadrive.util.SystemTestUtil.setupNewMdSystem;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class Z80LoopDetectTest {
    record Z80LoopTrace(String ex, String str, int pc, int[] opcodes) {
        @Override
        public String toString() {
            return new StringJoiner(", ", Z80LoopTrace.class.getSimpleName() + "[", "]")
                    .add("\n" + ex)
                    .add("\n" + str)
                    .add("\npc=" + pc)
                    .add("opcodes=" + Arrays.toString(opcodes))
                    .toString();
        }
    }

    static Map<String, Z80LoopTrace> traceMap;

    static {
        List<Z80LoopTrace> l = new ArrayList<>();
        l.add(new Z80LoopTrace("many", "00000000            E9    jp (hl)", 0, new int[]{0xE9}));
        l.add(new Z80LoopTrace("Pac-Mania (USA, Europe)", "00000201      C3 01 02    jp $0201",
                0x201, new int[]{0xC3, 1, 2}));
        l.add(new Z80LoopTrace("Batman (Japan).rom", "00000252      C3 52 02    jp $0252",
                0x252, new int[]{0xC3, 0x52, 0x02}));
        l.add(new Z80LoopTrace("u1", "0000000f      C3 0F 00    jp $000F",
                0xf, new int[]{0xC3, 0x0f, 0}));
        l.add(new Z80LoopTrace("u2", "000000af            00    nop\n000000b0      C3 AF 00    jp $00AF",
                0xaf, new int[]{0, 0xC3, 0xAf, 0}));
        l.add(new Z80LoopTrace("Chester Cheetah - Wild Wild Quest (USA)", "00000003         18 FE    jr $0003",
                3, new int[]{0x18, 0xFE}));
        l.add(new Z80LoopTrace("Home Basic (Japan) (SC-3000).sc", "000000c9         20 FE    jr nz,$00C9",
                0xc9, new int[]{0x20, 0xFE}));
        l.add(new Z80LoopTrace("Sorcerer's Kingdom (USA)", "00000048            76    halt",
                0x48, new int[]{0x76}));
        l.add(new Z80LoopTrace("Space Harrier II (World)", "0000009a            00    nop\n" +
                "0000009b      C3 9A 00    jp $009A", 0x9a, new int[]{0, 0xC3, 0x9A, 0}));
        l.add(new Z80LoopTrace("World Championship Soccer II (U)", "00000998      3A 6F 01    ld a,($016F)\n" +
                "0000099b            A7    and a\n0000099c      C2 98 09    jp nz,$0998",
                0x998, new int[]{0x3A, 0x6F, 1, 0xA7, 0xC2, 0x98, 9}));
        l.add(new Z80LoopTrace("Addams Family Values (Europe) (En,Fr,De)", "0000002a            7E    ld a,(hl)\n" +
                "0000002b            B7    or a\n0000002c         28 FC    jr z,$002A",
                0x2A, new int[]{0x7E, 0xB7, 0x28, 0xFC}));
        l.add(new Z80LoopTrace("Winter Olympics (Europe) (En,Fr,De,Es,It,Pt,Sv,No)",
                "0000052e            7E    ld a,(hl)\n0000052f         E6 03    and $03\n" +
                        "00000531         28 FB    jr z,$052E",
                0x52e, new int[]{0x7E, 0xE6, 3, 0x28, 0xFB}));
        l.add(new Z80LoopTrace("Tick, The (USA)", "00000a0a            1A    ld a,(de)\n" +
                "00000a0b         E6 02    and $02\n00000a0d         28 FB    jr z,$0A0A",
                0xa0a, new int[]{0x1A, 0xE6, 2, 0x28, 0xFB}));
        l.add(new Z80LoopTrace("Hellfire (Europe)", "00000094      3A 00 40    ld a,($4000)\n" +
                "00000097         E6 02    and $02\n00000099         28 F9    jr z,$0094",
                0x94, new int[]{0x3A, 0, 0x40, 0xE6, 2, 0x28, 0xF9}));
        l.add(new Z80LoopTrace("Phantasy Star - Sennenki no Owari ni (Japan)", "0000004c      3A 39 00    ld a,($0039)\n" +
                "0000004f            B7    or a\n00000050      CA 4C 00    jp z,$004C",
                0x4c, new int[]{0x3A, 0x39, 0, 0xB7, 0xCA, 0x4c, 0}));
        l.add(new Z80LoopTrace("Light Crusader (USA)", "0000005d      3A FE 1F    ld a,($1FFE)\n" +
                "00000060            B7    or a\n00000061      CA 5D 00    jp z,$005D",
                0x5d, new int[]{0x3A, 0xFE, 0x1f, 0xB7, 0xCA, 0x5D, 0}));
        l.add(new Z80LoopTrace("Phantasy Star IV (USA) (Beta) (1994-05-30)", "0000010d      3A 00 1F    ld a,($1F00)\n" +
                "00000110            B7    or a\n00000111         28 FA    jr z,$010D",
                0x10d, new int[]{0x3A, 0x00, 0x1f, 0xB7, 0x28, 0xFA}));
        l.add(new Z80LoopTrace("Unknown", "00000055      3A 01 1F    ld a,($1F01)\n" +
                "00000058            B7    or a\n00000059      FA 55 00    jp m,$0055",
                0x55, new int[]{0x3A, 0x01, 0x1f, 0xB7, 0xFA, 0x55, 0}));
        l.add(new Z80LoopTrace("Unknown", "00000084            7E    ld a,(hl)\n" +
                "00000085            A2    and d\n00000086      CA 84 00    jp z,$0084",
                0x84, new int[]{0x7E, 0xA2, 0xCA, 0x84, 0}));
        l.add(new Z80LoopTrace("md_softcheck", "00000100            00    nop\n" +
                "00000101            00    nop\n00000102            00    nop\n00000103         18 FB    jr $0100",
                0x100, new int[]{0, 0, 0, 0x18, 0xFB}));
        l.add(new Z80LoopTrace("titan-overdrive2", "0000011c            B6    or (hl)\n" +
                "0000011d      E2 1C 01    jp po,$011C",
                0x11c, new int[]{0xB6, 0xE2, 0x1C, 1}));
        l.add(new Z80LoopTrace("FIFA Soccer 96 (USA, Europe)", "000001ba      3A BD 00    ld a,($00BD)\n" +
                "000001bd            B7    or a\n000001be         20 02    jr nz,$01C2\n000001c0         18 F8    jr $01BA",
                0x1ba, new int[]{0x3A, 0xBD, 0, 0xB7, 0x20, 2, 0x18, 0xF8}));
        l.add(new Z80LoopTrace("megaman2_0.10", "0000001e            A6    and (hl)\n" +
                "0000001f      C2 1E 00    jp nz,$001E",
                0x1e, new int[]{0xA6, 0xC2, 0x1E, 0}));
        l.add(new Z80LoopTrace("Madou Monogatari III - Kyuukyoku Joou-sama (Japan) (Rev 1)",
                "00001d62      3A 31 C0    ld a,($C031)\n00001d65         E6 F0    and $F0\n" +
                        "00001d67         20 F9    jr nz,$1D62",
                0x1d62, new int[]{0x3A, 0x31, 0xC0, 0xE6, 0xF0, 0x20, 0xF9}));
        l.add(new Z80LoopTrace("Puyo Puyo (Japan)", "00001382      3A 16 CA    ld a,($CA16)\n" +
                "00001385            A7    and a\n00001386         28 FA    jr z,$1382",
                0x1382, new int[]{0x3A, 0x16, 0xCA, 0xA7, 0x28, 0xFA}));
        l.add(new Z80LoopTrace("Megami Tensei Gaiden - Last Bible (Japan)", "000000bd            7E    ld a,(hl)\n" +
                "000000be            B7    or a\n000000bf            00    nop\n000000c0         20 FB    jr nz,$00BD",
                0xBD, new int[]{0x7E, 0xB7, 0, 0x20, 0xFB}));
        l.add(new Z80LoopTrace("Galaga 2 (Europe).gg", "0000019d      21 51 C0    ld hl,$C051\n" +
                "000001a0      3A 4E C0    ld a,($C04E)\n000001a3            B7    or a\n000001a4         28 F7    jr z,$019D",
                0x19d, new int[]{0x21, 0x51, 0xC0, 0x3A, 0x4E, 0xC0, 0xB7, 0x28, 0xF7}));
        l.add(new Z80LoopTrace("unknown", "000000c4      21 F2 C0    ld hl,$C0F2\n" +
                "000000c7            7E    ld a,(hl)\n000000c8            B7    or a\n000000c9      CA C4 00    jp z,$00C4",
                0xc4, new int[]{0x21, 0xF2, 0xC0, 0x7E, 0xB7, 0xCA, 0xC4, 0}));
        l.add(new Z80LoopTrace("Pastfinder (Japan).rom", "000014c8      3A 33 E0    ld a,($E033)\n" +
                "000014cb            B7    or a\n000014cc      CA C8 14    jp z,$24C8",
                0x14c8, new int[]{0x3A, 0x33, 0xE0, 0xB7, 0xCA, 0xC8, 0x14}));
        l.add(new Z80LoopTrace("unknown", "0000033a      2A 40 C0    ld hl,($C040)\n" +
                        "0000033d            7D    ld a,l\n0000033e            B7    or a\n0000033f         20 F9    jr nz,$033A",
                0x33a, new int[]{0x2A, 0x40, 0xC0, 0x7d, 0xB7, 0x20, 0xF9}));
        l.add(new Z80LoopTrace("Buster Ball (Japan).gg", "000004b6      2A 12 C0    ld hl,($C012)\n" +
                        "000004b9            7C    ld a,h\n000004ba            B5    or l\n000004bb         20 F9    jr nz,$04B6",
                0x33a, new int[]{0x2A, 0x12, 0xC0, 0x7c, 0xB5, 0x20, 0xF9}));
        l.add(new Z80LoopTrace("Bee & Flower (Japan) (Alt 1).rom", "000007c7      21 0C E1    ld hl,$E10C\n" +
                "000007ca            7E    ld a,(hl)\n000007cb            B7    or a\n000007cc      CA C7 40    jp z,$07C7",
                0x7c7, new int[]{0x21, 0x0C, 0xE1, 0x7E, 0xB7, 0xCA, 0xC7, 7}));
        l.add(new Z80LoopTrace("Mr. Do's Wild Ride (Japan).rom",
                "000000f8      21 01 E0    ld hl,$E001\n000000fb            7E    ld a,(hl)\n" +
                        "000000fc            A7    and a\n000000fd         28 F9    jr z,$00F8",
                0xf8, new int[]{0x21, 0x01, 0xE0, 0x7E, 0xA7, 0x28, 0xF9}));
        l.add(new Z80LoopTrace("Predator 2 (Brazil).sms", "00007944      3A 02 C4    ld a,($C402)\n" +
                "00007947            BA    cp d\n00007948         28 FA    jr z,$7944",
                0x94, new int[]{0x3A, 2, 0xC4, 0xBA, 0x28, 0xFA}));
        l.add(new Z80LoopTrace("Ryuukyuu (Japan).gg",
                "0000002f      21 A7 C0    ld hl,$C0A7\n00000032            BE    cp (hl)\n" +
                        "00000033         28 FA    jr z,$002F",
                0x2f, new int[]{0x21, 0xA7, 0xC0, 0xBE, 0x28, 0xFA}));
        l.add(new Z80LoopTrace("H.E.R.O. (Japan).rom",
                "000000fb         3E 27    ld a,$27\n000000fd      32 3F C0    ld ($C03F),a\n" +
                        "00000100         18 F9    jr $00FB",
                0xfb, new int[]{0x3E, 0x27, 0x32, 0x3F, 0xC0, 0x18, 0xF9}));
        l.add(new Z80LoopTrace("Star Trek - The Next Generation - The Advanced Holodeck Tutorial (USA).gg",
                "000006a5      3A 61 CA    ld a,($CA61)\n000006a8      21 62 CA    ld hl,$CA62\n" +
                        "000006ab            BE    cp (hl)\n000006ac         20 F7    jr nz,$06A5",
                0x6a5, new int[]{0x3A, 0x61, 0xCA, 0x21, 0x62, 0xCA, 0xBE, 0x20, 0xF7}));
        l.add(new Z80LoopTrace("Chicago Syndicate (USA, Europe).gg",
                "00000a7f      3A C1 C1    ld a,($C1C1)\n00000a82            B7    or a\n" +
                        "00000a83      FA 97 0A    jp m,$0A97\n00000a86      C2 7F 0A    jp nz,$0A7F",
                0xa7f, new int[]{0x3A, 0xC1, 0xC1, 0xB7, 0xFA, 0x97, 0xA, 0xC2, 0x7F, 0xA}));
        l.add(new Z80LoopTrace("u3", "000002bb         CB 46    bit 0,(hl)\n000002bd      CA BB 02    jp z,$02BB",
                0x2bb, new int[]{0xCB, 0x46, 0xCA, 0xBB, 2}));
        //TODO 1st jump is to a non-consecutive PC
//        l.add(new Z80LoopTrace("Pitfall II - The Lost Caverns (Japan).sg", "00000013            7E    ld a,(hl)\n" +
//                "00000014            B7    or a\n00000015         18 04    jr $001B\n0000001b         28 F6    jr z,$0013",
//                0x13, new int[]{0x7E, 0xB7, 0x18, 4, 0x28, 0xF6}));
        /*
Missed Z80 loop: Z80	Loop len: 4, isBusy: true
00000068      3A FF 1F    ld a,($1FFF)
0000006b         CB 7F    bit 7,a
0000006d      CA 96 00    jp z,$0096
00000096      C3 68 00    jp $0068

         */
        traceMap = l.stream().collect(Collectors.toMap(Z80LoopTrace::str, Function.identity()));
    }

    private Z80CoreWrapper z80;

    @BeforeEach
    public void setup() {
        MdMainBusProvider bus = setupNewMdSystem();
        z80 = bus.getBusDeviceIfAny(Z80CoreWrapper.class).get();
        Z80State zs = z80.getZ80State();
        zs.setRegPC(0);
        zs.setRegHL(0);
        z80.loadZ80State(zs);
    }

    @Test
    public void testTraces() {
        for (var entry : traceMap.entrySet()) {
            Z80LoopHelper loopHelper = new Z80LoopHelper(SystemLoader.SystemType.SMS, z80.getZ80(), z80.getZ80BusProvider());
            var trace = entry.getValue();
            int pc = trace.pc;
            z80.getZ80().setRegPC(pc);
            for (int i = 0; i < trace.opcodes.length; i++) {
                z80.writeMemory(pc + i, trace.opcodes[i]);
            }

            Z80LoopHelper.LoopType res = loopHelper.checkLoops().loopType;
            Assertions.assertTrue(res != Z80LoopHelper.LoopType.NONE, trace.toString());
            //clear memory
            for (int i = 0; i < trace.opcodes.length; i++) {
                z80.writeMemory(pc + i, 0);
            }
            z80.getZ80().setRegPC(0);
        }
    }
}

/**
 * //infinite loops
 * 00000000            E9    jp (hl)
 * <p>
 * Pac-Mania (USA, Europe).zip
 * 00000201      C3 01 02    jp $0201
 * <p>
 * Chester Cheetah - Wild Wild Quest (USA).zip
 * 00000003         18 FE    jr $0003
 * <p>
 * Home Basic (Japan) (SC-3000).sc
 * 000000c9         20 FE    jr nz,$00C9
 *
 * Sorcerer's Kingdom (USA)
 * 00000048            76    halt
 * <p>
 * Shove It! - The Warehouse Game (U) [!].bin.zip
 * Daimakaimura (Japan).zip
 * Space Harrier II (World)
 * 0000009a            00    nop
 * 0000009b      C3 9A 00    jp $009A
 * <p>
 * megaman2_0.10.sms
 * Missed Z80 loop: Z80	Loop len: 2, isBusy: true
 * 0000001e            A6    and (hl)
 * 0000001f      C2 1E 00    jp nz,$001E
 * <p>
 * Sonic the Hedgehog 2 (World) (Beta) (September, 1992)
 * 00000174            7A    ld a,d
 * 00000175            B3    or e
 * 00000176         28 FC    jr z,$0174
 * <p>
 * //busy loops
 * titan-overdrive2.zip
 * remute_redeyes.zip
 * 0000011c            B6    or (hl) //hl = 0x4080 ??
 * 0000011d      E2 1C 01    jp po,$011C
 *
 * <p>
 * World Championship Soccer II (U)
 * 00000998      3A 6F 01    ld a,($016F) //RAM
 * 0000099b            A7    and a
 * 0000099c      C2 98 09    jp nz,$0998
 * <p>
 * Addams Family Values (Europe) (En,Fr,De).zip
 * 0000002a            7E    ld a,(hl) //RAM
 * 0000002b            B7    or a
 * 0000002c         28 FC    jr z,$002A
 * <p>
 * Winter Olympics (Europe) (En,Fr,De,Es,It,Pt,Sv,No).zip
 * 0000052e            7E    ld a,(hl) //HL = 0x4000
 * 0000052f         E6 03    and $03
 * 00000531         28 FB    jr z,$052E
 * <p>
 * Tick, The (USA).zip
 * 00000a0a            1A    ld a,(de) //DE=0x4000
 * 00000a0b         E6 02    and $02
 * 00000a0d         28 FB    jr z,$0A0A
 * <p>
 * Hellfire (Europe)
 * 00000094      3A 00 40    ld a,($4000) //YM2612 status port
 * 00000097         E6 02    and $02   //E6 01    and $01, E6 03    and $03
 * 00000099         28 F9    jr z,$0094
 * <p>
 * Phantasy Star - Sennenki no Owari ni (Japan).zip
 * 0000004c      3A 39 00    ld a,($0039)
 * 0000004f            B7    or a
 * 00000050      CA 4C 00    jp z,$004C
 * <p>
 * <p>
 * Light Crusader (USA).zip
 * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
 * 0000005d      3A FE 1F    ld a,($1FFE)
 * 00000060            B7    or a
 * 00000061      CA 5D 00    jp z,$005D
 * <p>
 * Phantasy Star IV (USA) (Beta) (1994-05-30).zip
 * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
 * 0000010d      3A 00 1F    ld a,($1F00)
 * 00000110            B7    or a
 * 00000111         28 FA    jr z,$010D
 * <p>
 * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
 * 00000055      3A 01 1F    ld a,($1F01)
 * 00000058            B7    or a
 * 00000059      FA 55 00    jp m,$0055
 * <p>
 * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
 * 00000084            7E    ld a,(hl)
 * 00000085            A2    and d
 * 00000086      CA 84 00    jp z,$0084
 * <p>
 * 124: Puyo Puyo (Japan).gg
 * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
 * 00003382      3A 16 CA    ld a,($CA16)
 * 00003385            A7    and a
 * 00003386         28 FA    jr z,$3382
 * <p>
 * Pastfinder (Japan).rom
 * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
 * 000084c8      3A 33 E0    ld a,($E033)
 * 000084cb            B7    or a
 * 000084cc      CA C8 84    jp z,$84C8
 *
 * Ryuukyuu (Japan).gg
 * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
 * 0000002f      21 A7 C0    ld hl,$C0A7
 * 00000032            BE    cp (hl)
 * 00000033         28 FA    jr z,$002F
 *
 * md_softcheck.bin
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 00000100            00    nop
 * 00000101            00    nop
 * 00000102            00    nop
 * 00000103         18 FB    jr $0100
 * <p>
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 0000033a      2A 40 C0    ld hl,($C040)
 * 0000033d            7D    ld a,l
 * 0000033e            B7    or a
 * 0000033f         20 F9    jr nz,$033A
 *
 * Megami Tensei Gaiden - Last Bible (Japan).gg
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 000000bd            7E    ld a,(hl)
 * 000000be            B7    or a
 * 000000bf            00    nop
 * 000000c0         20 FB    jr nz,$00BD
 *
 * 227: Buster Ball (Japan).gg
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 000004b6      2A 12 C0    ld hl,($C012)
 * 000004b9            7C    ld a,h
 * 000004ba            B5    or l
 * 000004bb         20 F9    jr nz,$04B6
 *
 * 57: Bee & Flower (Japan) (Alt 1).rom
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 000047c7      21 0C E1    ld hl,$E10C
 * 000047ca            7E    ld a,(hl)
 * 000047cb            B7    or a
 * 000047cc      CA C7 47    jp z,$47C7
 *
 * 152: Mr. Do's Wild Ride (Japan).rom
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 000000f8      21 01 E0    ld hl,$E001
 * 000000fb            7E    ld a,(hl)
 * 000000fc            A7    and a
 * 000000fd         28 F9    jr z,$00F8
 *
 * Pitfall II - The Lost Caverns (Japan).sg
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 00000013            7E    ld a,(hl)
 * 00000014            B7    or a
 * 00000015         18 04    jr $001B
 * 0000001b         28 F6    jr z,$0013
 *
 *  //not doing two jumps or two loads (from mem) loops
 *
 * FIFA Soccer 96 (USA, Europe) (En,Fr,De,Es,It,Sv)
 * 000001ba      3A BD 00    ld a,($00BD)
 * 000001bd            B7    or a
 * 000001be         20 02    jr nz,$01C2
 * 000001c0         18 F8    jr $01BA
 * <p>
 * Du Shen Zhi Meng Huan Poker (Taiwan) (Unl)
 * 00000100      3A 03 02    ld a,($0203)
 * 00000103            B7    or a
 * 00000104      CA DD 01    jp z,$01DD
 * 000001dd      C3 00 01    jp $0100
 *
 * Pop Breaker (Japan).gg
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 00000232            B7    or a
 * 00000233         20 05    jr nz,$023A
 * 00000235      3A 85 C0    ld a,($C085)
 * 00000238         18 F8    jr $0232
 * <p>
 * Chicago Syndicate (USA, Europe).gg
 * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
 * 00000a7f      3A C1 C1    ld a,($C1C1)
 * 00000a82            B7    or a
 * 00000a83      FA 97 0A    jp m,$0A97
 * 00000a86      C2 7F 0A    jp nz,$0A7F
 *
 * Populous II - Two Tribes (E) [!].bin.zip
 * Missed Z80 loop: Z80	Loop len: 6, isBusy: true
 * 00000237      3A 06 00    ld a,($0006)
 * 0000023a            A7    and a
 * 0000023b      C2 44 02    jp nz,$0244
 * 0000023e      3A 07 00    ld a,($0007)
 * 00000241            A7    and a
 * 00000242         28 F3    jr z,$0237
 * 00000237      3A 06 00    ld a,($0006)
 */
