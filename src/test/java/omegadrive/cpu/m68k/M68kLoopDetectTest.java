package omegadrive.cpu.m68k;

import m68k.cpu.MC68000;
import omegadrive.SystemLoader;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cpu.m68k.drc.M68kLoopHelper;
import omegadrive.cpu.m68k.drc.M68kLoopHelper.LoopType;
import omegadrive.cpu.m68k.drc.M68kLoopHelperImpl;
import omegadrive.cpu.m68k.drc.M68kOpcodeSpecHelper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static omegadrive.bus.model.MdMainBusProvider.Z80_ADDRESS_SPACE_START;
import static omegadrive.cpu.m68k.drc.M68kOpcodeSpecHelper.generateOnce;
import static omegadrive.cpu.m68k.drc.M68kOpcodeSpecHelper.isValidOpcodeForLoopingGenerate;
import static omegadrive.util.SystemTestUtil.setupNewMdSystem;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class M68kLoopDetectTest {
    record M68kLoopTrace(String ex, String str, int pc, int[] opcodes, LoopType loopType) {
        @Override
        public String toString() {
            return new StringJoiner(", ", M68kLoopTrace.class.getSimpleName() + "[", "]")
                    .add("\n" + ex)
                    .add("\n" + str)
                    .add("\npc=" + pc)
                    .add("opcodes=" + Arrays.toString(opcodes))
                    .toString();
        }
    }

    static Map<String, M68kLoopTrace> traceMap;

    static {
        List<M68kLoopTrace> l = new ArrayList<>();
        l.add(new M68kLoopTrace("Story of Thor, The (Europe)",
                "0000313a\t6000 fffe               bra.w    $0000313a",
                0x313a, new int[]{0x6000, 0xfffe}, LoopType.INFINITE_LOOP));
        l.add(new M68kLoopTrace("007 Shitou - The Duel (Japan)",
                "00004bec\t4a39 00ffc404           tst.b    $00ffc404\n00004bf2\t67f8                    beq.s    $00004bec",
                0x4bec, new int[]{0x4a39, 0x00ff, 0xc404, 0x67f8}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Super League (Europe)",
                "00009452\t4a38 e200               tst.b    $e200\n00009456\t66fa                    bne.s    $00009452",
                0x9452, new int[]{0x4a38, 0xe200, 0x66fa}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("F1 Circus MD (Japan)",
                "00000a24\tb038 803d               cmp.b    $803d,d0\n00000a28\t67fa                    beq.s    $00000a24",
                0xa24, new int[]{0xb038, 0x803d, 0x67fa}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Mazin Wars (Europe)",
                "00001346\t0c2e 000a 001a          cmpi.b   #$0a,$001a(a6)\n0000134c\t66f8                    bne.s    $00001346",
                0x1346, new int[]{0xc2e, 0xa, 0x1a, 0x66f8}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Story of Thor, The - A Successor of The Light (J)",
                "0000b11e\t0839 0001 00ff164e      btst     #$1,$00ff164e\n0000b126\t6600 fff6               bne.w    $0003b11e",
                0xb11e, new int[]{0x839, 0x1, 0xff, 0x164e, 0x6600, 0xfff6}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Pirates! Gold (U)",
                "0000eb52\tb050                    cmp.w    (a0),d0\n0000eb54\t67fc                    beq.s    $0000eb52",
                0x0eb52, new int[]{0xb050, 0x67fc}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Judge Dredd - The Movie",
                "0000c75a\tc056                    and.w    (a6),d0\n0000c75c\t66fc                    bne.s    $0000c75a",
                0xc75a, new int[]{0xc056, 0x66fc}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("dma_speed_test.bin",
                "00000884\t4a03                    tst.b    d3\n00000886\t6afc                    bpl.s    $00000884",
                0x884, new int[]{0x4a03, 0x6afc}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Snake260 by Sonic 65 09-2009 (PD)",
                "0000113c\t0cb9 000d3c04 00ffecae  cmpi.l   #$000d3c04,$00ffecae\n" +
                        "00001146\t66f4                    bne.s    $0000113c",
                0x113c, new int[]{0x0cb9, 0xd, 0x3c04, 0xff, 0xecae, 0x66f4}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Arrow Flash (USA)",
                "00000486\t4e71                    nop\n00000488\t4e71                    nop\n0000048a\t4e71                    nop\n" +
                        "0000048c\t60f8                    bra.s    $00000486",
                0x486, new int[]{0x4e71, 0x4e71, 0x4e71, 0x60f8}, LoopType.INFINITE_LOOP));
        l.add(new M68kLoopTrace("Golden Axe (World)",
                "00000cc6\t1038 c183               move.b   $c183,d0\n00000cca\t66fa                    bne.s    $00000cc6",
                0xcc6, new int[]{0x1038, 0xc183, 0x66fa}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Marsupilami (USA) ",
                "00009138\t4a39 00ff0446           tst.b    $00ff0446\n0000913e\t66f8                    bne.s    $00009138",
                0x9138, new int[]{0x4a39, 0xff, 0x0446, 0x66f8}, LoopType.BUSY_LOOP));
        l.add(new M68kLoopTrace("Monster World IV (USA, Europe)",
                "00000564\t2000                    move.l   d0,d0\n00000566\t60fc                    bra.s    $00000564",
                0x564, new int[]{0x2000, 0x60fc}, LoopType.BUSY_LOOP)); //TODO fix, should be INFINITE_LOOP
        l.add(new M68kLoopTrace("Pokemon Stadium (Taiwan) (En) (Unl)",
                "00000128\t4ef9 00000128           jmp      $00000128",
                0x128, new int[]{0x4ef9, 0, 0x128}, LoopType.INFINITE_LOOP));
        l.add(new M68kLoopTrace("Double Dribble - The Playoff Edition (USA)",
                "00000434\t60fe                    bra.s    $00000434",
                0x434, new int[]{0x60fe}, LoopType.INFINITE_LOOP));
        l.add(new M68kLoopTrace("VS Puyo Puyo Sun (USA, Europe)",
                "0000039a\t6000 fffe               bra.w    $0000039a",
                0x39a, new int[]{0x6000, 0xfffe}, LoopType.INFINITE_LOOP));
        l.add(new M68kLoopTrace("none",
                "0000045e\t3014                    move.w   (a4),d0\n00000460\t7000                    moveq    #$00,d0\n" +
                        "00000462\t23fc c0000000 00c00004  move.l   #$c0000000,$00c00004",
                0x45e, new int[]{0x3014, 0x7000, 0x23fc, 0xc000, 0, 0xc0, 4}, LoopType.NONE));
        l.add(new M68kLoopTrace("none",
                "00006942\t6700 00fe               beq.w    $00006a42",
                0x6942, new int[]{0x6700, 0xfe}, LoopType.NONE));
        l.add(new M68kLoopTrace("none",
                "000017a4\t6000 0482               bra.w    $00001c28",
                0x17a4, new int[]{0x6000, 0x482}, LoopType.NONE));
        l.add(new M68kLoopTrace("none",
                "00001c7c\t6000 ecba               bra.w    $00000938",
                0x1c7c, new int[]{0x6000, 0xecba}, LoopType.NONE));
        l.add(new M68kLoopTrace("none",
                "0000c4c8\t6100 59fe               bsr.w    $00011ec8",
                0xc4c8, new int[]{0x6100, 0x59fe}, LoopType.NONE));
        l.add(new M68kLoopTrace("none",
                "00005362\t4eba c5a4               jsr      $c5a4(pc)",
                0x5362, new int[]{0x4eba, 0xc5a4}, LoopType.NONE));
        l.add(new M68kLoopTrace("none",
                "000040c0\t4eb8 5f22               jsr      $5f22",
                0x40c0, new int[]{0x4eb8, 0x5f22}, LoopType.NONE));
        //JSR does not qualify as it modifies the stack
        l.add(new M68kLoopTrace("none",
                "0000198a\t4eba f0ea               jsr      $f0ea(pc) -> a76",
                0x198a, new int[]{0x4eba, 0xf0ea}, LoopType.NONE));
        l.add(new M68kLoopTrace("none",
                "00006066\t4efa fffe               jmp      $fffe(pc)",
                0x6066, new int[]{0x4efa, 0xfffe}, LoopType.INFINITE_LOOP));


        traceMap = l.stream().collect(Collectors.toMap(M68kLoopTrace::str, Function.identity()));
    }

    private MC68000Wrapper m68k;
    private MdMainBusProvider bus;

    private IMemoryProvider memoryProvider;

    private M68kLoopHelper loopHelper;

    int romSize = 0x10_000;

    private byte[] mdRom = new byte[romSize];

    @BeforeEach
    public void setup() {
        Arrays.fill(mdRom, (byte) -1);
        memoryProvider = MemoryProvider.createMdInstance();
        memoryProvider.setRomData(mdRom);
        mdRom = memoryProvider.getRomData();
        bus = setupNewMdSystem(memoryProvider);
        m68k = bus.getBusDeviceIfAny(MC68000Wrapper.class).get();
        loopHelper = new M68kLoopHelperImpl(SystemLoader.SystemType.MD, m68k.getM68k(), bus, romSize);
    }

    private void writeData(int idx, int data) {
        if (idx < Z80_ADDRESS_SPACE_START) {
            Util.writeDataMask(mdRom, idx, data, romSize - 1, Size.WORD);
        } else {
            bus.write(idx, data, Size.WORD);
        }
    }
    @Test
    public void testTraces() {
        generateOnce(m68k.getM68k());
        for (var entry : traceMap.entrySet()) {
            var trace = entry.getValue();
            int pc = trace.pc;
            m68k.getM68k().setPC(pc);
            int memIdx = 0;
            for (int i = 0; i < trace.opcodes.length; i++) {
                memIdx = pc + (i << 1);
                writeData(memIdx, trace.opcodes[i]);
            }
            int rtsOpcode = 0x4e75;
            writeData(memIdx + 2, rtsOpcode);
            M68kLoopHelper.M68kBlock block = null;
            try {
                block = loopHelper.checkLoops(pc);
            } catch (Exception | Error e) {
                Assertions.fail(trace.toString(), e);
            }
            Assertions.assertEquals(trace.loopType, block.loopType, trace.toString());
            Assertions.assertEquals(trace.pc, block.pc, trace.toString());
            if (trace.loopType == LoopType.NONE) {
                Assertions.assertEquals(trace.opcodes[0], block.opcodes[0], trace.toString());
            } else {
                Assertions.assertArrayEquals(trace.opcodes, block.opcodes, trace.toString());
            }
            //clear memory
            for (int i = 0; i < trace.opcodes.length; i++) {
                writeData(pc + (i << 1), -1);
            }
            m68k.getM68k().setPC(0);
        }
    }

//    @Test
//    public void testTracesLoop() {
//        do {
//            testTraces();
//            loopHelper.reset();
//            Util.sleep(5);
//        } while (true);
//    }

    @Test
    public void testRomSize() {
        for (var entry : traceMap.entrySet()) {
            var trace = entry.getValue();
            Assertions.assertTrue(trace.pc + trace.opcodes.length < romSize);
        }
    }

    //TODO needs to understand instruction boundaries
//    @Test
    public void testOpcodeGen2() {
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        for (var entry : traceMap.entrySet()) {
            ok &= Arrays.stream(entry.getValue().opcodes).allMatch(M68kOpcodeSpecHelper::isValidOpcodeForLooping);
            if (!ok) {
                sb.append(entry).append("\n");
            }

        }
        Assertions.assertTrue(ok, sb.toString());
    }

    @Test
    public void testOpcodeGen() {
        int[] opcodes = new int[0xFFFF + 1];
        MC68000 cpu = m68k.getM68k();
        generateOnce(m68k.getM68k());
        for (int i = 0; i < opcodes.length; i++) {
//            System.err.print(th(i));
            boolean valid;
            String inst = "NONE";
            try {
                var di = cpu.getInstructionFor(i).disassemble(0, i);
                inst = di.getVeryShortFormat();
                valid = isValidOpcodeForLoopingGenerate(di);
            } catch (Exception e) {
                valid = false;
            }
            opcodes[i] = valid ? 1 : 0;
//            System.err.println("," + inst + "," + valid);
        }
    }
}

/**
 * //infinite loops
 * 19: Story of Thor, The (Europe)
 * Missed 68k loop: 68M	Loop len: 1, isBusy: true
 * 0000313a	6000 fffe               bra.w    $0000313a
 * <p>
 * Rocket Knight Adventures (J)
 * Missed 68k loop: 68M	Loop len: 1, isBusy: true
 * 000003c6	60fe                    bra.s    $000003c6
 * <p>
 * Pokemon Stadium (Taiwan) (En) (Unl)
 * Missed 68k loop: 68M	Loop len: 1, isBusy: true
 * 00000128	4ef9 00000128           jmp      $00000128
 *
 * Double Dribble - The Playoff Edition (USA)
 * Missed 68k loop: 68M	Loop len: 1, isBusy: true
 * 00000434	60fe                    bra.s    $00000434
 *
 * 32: VS Puyo Puyo Sun (USA, Europe) (Mega Drive Mini 2, Genesis Mini 2).zip
 * Missed 68k loop: 68M	Loop len: 1, isBusy: true
 * 0000039a	6000 fffe               bra.w    $0000039a
 *
 * //busy loops
 * 007 Shitou - The Duel (Japan).md
 * 68M	Loop len: 2, isBusy: true
 * 00004bec	4a39 00ffc404           tst.b    $00ffc404
 * 00004bf2	67f8                    beq.s    $00004bec
 * <p>
 * Super League (Europe).zip
 * 68M	Loop len: 2, isBusy: true
 * 00009452	4a38 e200               tst.b    $e200
 * 00009456	66fa                    bne.s    $00009452
 * <p>
 * F1 Circus MD (Japan).zip
 * M68kCycle table loaded in: 25 ms68M	Loop len: 2, isBusy: true
 * 00020a24	b038 803d               cmp.b    $803d,d0
 * 00020a28	67fa                    beq.s    $00020a24
 * <p>
 * F1 Circus MD (Japan).zip
 * 68M	Loop len: 2, isBusy: true
 * 00000874	0c38 0001 803d          cmpi.b   #$01,$803d
 * 0000087a	65f8                    bcs.s    $00000874
 * <p>
 * 11: Mazin Wars (Europe).zip
 * 68M	Loop len: 2, isBusy: true
 * 00001346	0c2e 000a 001a          cmpi.b   #$0a,$001a(a6)
 * 0000134c	66f8                    bne.s    $00001346
 * <p>
 * Story of Thor, The - A Successor of The Light (J)
 * 68M	Loop len: 2, isBusy: true
 * 0003b11e	0839 0001 00ff164e      btst     #$1,$00ff164e
 * 0003b126	6600 fff6               bne.w    $0003b11e
 * <p>
 * 2: Judge Dredd - The Movie
 * 68M	Loop len: 2, isBusy: true
 * 0000c75a	c056                    and.w    (a6),d0
 * 0000c75c	66fc                    bne.s    $0000c75a
 * <p>
 * Golden Axe (World).zip
 * 68M	Loop len: 2, isBusy: true
 * 00000cc6	1038 c183               move.b   $c183,d0
 * 00000cca	66fa                    bne.s    $00000cc6
 * <p>
 * FIFA Soccer 97 - Gopher Mod (Hack)
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 000ab1fc	202d d42a               move.l   $d42a(a5),d0
 * 000ab200	66fa                    bne.s    $000ab1fc
 * <p>
 * F1 Grand Prix - Nakajima Satoru (J)
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 0000127a	3038 8884               move.w   $8884,d0
 * 0000127e	66fa                    bne.s    $0000127a
 * <p>
 * dma_speed_test.bin
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 00000884	4a03                    tst.b    d3
 * 00000886	6afc                    bpl.s    $00000884
 * <p>
 * 30: Verytex (J)
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 00000b30	4a39 00ff0454           tst.b    $00ff0454
 * 00000b36	66f8                    bne.s    $00000b30
 * <p>
 * Snake260 by Sonic 65 09-2009 (PD)
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 000e113c	0cb9 000d3c04 00ffecae  cmpi.l   #$000d3c04,$00ffecae
 * 000e1146	66f4                    bne.s    $000e113c
 * <p>
 * Pirates! Gold (U)
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 0001eb52	b050                    cmp.w    (a0),d0
 * 0001eb54	67fc                    beq.s    $0001eb52
 *
 * Marsupilami (USA) (En,Fr,De,Es,It)
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 00009138	4a39 00ff0446           tst.b    $00ff0446
 * 0000913e	66f8                    bne.s    $00009138
 *
 * Crying - Aseimei Sensou (Japan)
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 00007950	08b8 0007 f1fb          bclr     #$7,$f1fb
 * 00007956	67f8                    beq.s    $00007950
 *
 * Monster World IV (USA, Europe)
 * Missed 68k loop: 68M	Loop len: 2, isBusy: true
 * 00000564	2000                    move.l   d0,d0
 * 00000566	60fc                    bra.s    $00000564
 *
 * <p>
 * Arrow Flash (USA)
 * 68M	Loop len: 4, isBusy: true
 * 00000486	4e71                    nop
 * 00000488	4e71                    nop
 * 0000048a	4e71                    nop
 * 0000048c	60f8                    bra.s    $00000486
 */
