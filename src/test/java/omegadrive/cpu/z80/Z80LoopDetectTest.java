package omegadrive.cpu.z80;

import omegadrive.bus.model.MdMainBusProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import z80core.IMemIoOps;
import z80core.MemIoOps;
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
     * Sorcerer's Kingdom (USA)
     * 00000048            76    halt
     * <p>
     * Shove It! - The Warehouse Game (U) [!].bin.zip
     * Daimakaimura (Japan).zip
     * Space Harrier II (World)
     * 0000009a            00    nop
     * 0000009b      C3 9A 00    jp $009A
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
     *
     * Phantasy Star - Sennenki no Owari ni (Japan).zip
     * 0000004c      3A 39 00    ld a,($0039)
     * 0000004f            B7    or a
     * 00000050      CA 4C 00    jp z,$004C
     * <p>
     *
     * Light Crusader (USA).zip
     * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
     * 0000005d      3A FE 1F    ld a,($1FFE)
     * 00000060            B7    or a
     * 00000061      CA 5D 00    jp z,$005D
     *
     * Phantasy Star IV (USA) (Beta) (1994-05-30).zip
     * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
     * 0000010d      3A 00 1F    ld a,($1F00)
     * 00000110            B7    or a
     * 00000111         28 FA    jr z,$010D
     *
     * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
     * 00000055      3A 01 1F    ld a,($1F01)
     * 00000058            B7    or a
     * 00000059      FA 55 00    jp m,$0055
     *
     * Missed Z80 loop: Z80	Loop len: 3, isBusy: true
     * 00000084            7E    ld a,(hl)
     * 00000085            A2    and d
     * 00000086      CA 84 00    jp z,$0084
     *
     * md_softcheck.bin
     * Missed Z80 loop: Z80	Loop len: 4, isBusy: true
     * 00000100            00    nop
     * 00000101            00    nop
     * 00000102            00    nop
     * 00000103         18 FB    jr $0100
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
     * Populous II - Two Tribes (E) [!].bin.zip
     * Missed Z80 loop: Z80	Loop len: 6, isBusy: true
     * 00000237      3A 06 00    ld a,($0006)
     * 0000023a            A7    and a
     * 0000023b      C2 44 02    jp nz,$0244
     * 0000023e      3A 07 00    ld a,($0007)
     * 00000241            A7    and a
     * 00000242         28 F3    jr z,$0237
     * 00000237      3A 06 00    ld a,($0006)
     *
     */

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

    ;

    static Map<String, Z80LoopTrace> traceMap;

    static {
        List<Z80LoopTrace> l = new ArrayList<>();
        l.add(new Z80LoopTrace("many", "00000000            E9    jp (hl)", 0, new int[]{0xE9}));
        l.add(new Z80LoopTrace("Pac-Mania (USA, Europe)", "00000201      C3 01 02    jp $0201",
                0x201, new int[]{0xC3, 1, 2}));
        l.add(new Z80LoopTrace("Chester Cheetah - Wild Wild Quest (USA)", "00000003         18 FE    jr $0003",
                3, new int[]{0x18, 0xFE}));
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
        l.add(new Z80LoopTrace("Winter Olympics (Europe) (En,Fr,De,Es,It,Pt,Sv,No)", "0000052e            7E    ld a,(hl)\n" +
                "0000052f         E6 03    and $03\n00000531         28 FB    jr z,$052E",
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

        /*
         * FIFA Soccer 96 (USA, Europe) (En,Fr,De,Es,It,Sv)
         * 000001ba      3A BD 00    ld a,($00BD)
         * 000001bd            B7    or a
         * 000001be         20 02    jr nz,$01C2
         * 000001c0         18 F8    jr $01BA
         */
        traceMap = l.stream().collect(Collectors.toMap(Z80LoopTrace::str, Function.identity()));
    }

    private Z80Provider z80;

    @BeforeEach
    public void setup() {
        MdMainBusProvider bus = setupNewMdSystem();
        z80 = bus.getBusDeviceIfAny(Z80Provider.class).get();
        Z80State zs = z80.getZ80State();
        zs.setRegPC(0);
        zs.setRegHL(0);
        z80.loadZ80State(zs);
    }

    @Test
    public void testTraces() {
        IMemIoOps memIoOps = new MemIoOps();
        for (var entry : traceMap.entrySet()) {
            var trace = entry.getValue();
            int pc = trace.pc;
            z80.getZ80().setRegPC(pc);
            for (int i = 0; i < trace.opcodes.length; i++) {
                z80.writeMemory(pc + i, trace.opcodes[i]);
            }
            Z80Helper.LoopType res = Z80Helper.checkLoops(z80.getZ80(), z80.getZ80BusProvider(), memIoOps);
            Assertions.assertTrue(res != Z80Helper.LoopType.NONE, trace.toString());
            //clear memory
            for (int i = 0; i < trace.opcodes.length; i++) {
                z80.writeMemory(pc + i, 0);
            }
            z80.getZ80().setRegPC(0);
        }
    }
}
