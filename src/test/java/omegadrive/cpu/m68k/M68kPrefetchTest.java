package omegadrive.cpu.m68k;

import m68k.cpu.Cpu;
import m68k.memory.AddressSpace;
import omegadrive.bus.md.MdBus;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static omegadrive.bus.model.MdMainBusProvider.Z80_ADDRESS_SPACE_START;
import static omegadrive.bus.model.MdMainBusProvider.Z80_BUS_REQ_CONTROL_START;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class M68kPrefetchTest {

    final static int nopOpcode = 0x4E71;

    final static short[] tk_opcodes = {
            //68M 00E0_0000 3239 00a11100           move.w   $00a11100,d1
            0x3239, 0x00A1, 0x1100,
            //68M 00E0_0006 0801 0000               btst     #$0,d1
            0x0801, 0,
            //68M 00E0_000A 67f4                    beq.s    $00E0_0000
            0x67f4,
            //68M 00E0_0008 4e71 NOP
            0x4E71
    };

    final static short[] sb_opcodes = {
            //68M 00E0_0000 4a39 00a11100           tst.b    $00a11100 [NEW]
            0x4a39, 0x00A1, 0x1100,
            //68M 00E0_000A 67f8                    beq.s    $00E0_0000
            0x67f9,
            //68M 00E0_000C 4e71 NOP
            0x4E71
    };

    final static short[] uwol_opcodes = {
            //68M 00E0_0000 1039 00a04000           move.b   $00a04000,d0
            0x4a39, 0x00A1, 0x1100,
            //68M 00E0_000A 6df8                    blt.s    $00E0_0000
            0x6df8,
            //68M 00E0_000C 4e71 NOP
            0x4E71
    };

    /**
     * 68M 00000656   41f9 00ff022c           lea      $00ff022c,a0
     * 68M 0000065c   213c 72014e75           move.l   #$72014e75,-(a0)
     * 68M 00000662   213c 30bc7200           move.l   #$30bc7200,-(a0)
     * 68M 00000668   213c 41fa0006           move.l   #$41fa0006,-(a0)
     * 68M 0000066e   4e90                    jsr      (a0)
     * 68M 00000670   4e71                    nop
     * [
     * 68M 00ff0220   41fa 0006               lea      $0006(pc),a0
     * 68M 00ff0224   30bc 7200               move.w   #$7200,(a0)
     * 68M 00ff0228   7200                    moveq    #$00,d1
     * 68M 00ff022a   4e75                    rts
     * ]
     */
    final static short[] mt_opcodes = {
            0x41f9, 0x00ff, 0x022c,
            0x213c, 0x7201, 0x4e75,
            0x213c, 0x30bc, 0x7200,
            0x213c, 0x41fa, 0x0006,
            0x4e90,
            0x4e71
    };
    private MdMainBusProvider bus;
    private MC68000Wrapper w;


    @BeforeEach
    public void init() {
        bus = SystemTestUtil.setupNewMdSystem();
        w = bus.getBusDeviceIfAny(MC68000Wrapper.class).get();
        Assertions.assertNotNull(w);
        Assertions.assertNotNull(w.m68k);
        Assertions.assertNotNull(w.addressSpace);
    }

    /**
     * //bitVal =0
     * 68M 0002e95c   3239 00a11100           move.w   $00a11100,d1
     * 68M 0002e962   0801 0000               btst     #$0,d1
     * 68M 0002e966   67f4                    beq.s    $0002e95c
     */
    @Test
    public void testTimeKillers() {
        Cpu m68k = w.m68k;
        writeCodeToRam(w.addressSpace, tk_opcodes);

        int nopIdx = MdBus.ADDRESS_RAM_MAP_START + ((tk_opcodes.length - 1) << 1);

        //0xA11100 returns 0
        bus.setZ80BusRequested(true);
        bus.setZ80ResetState(false);

        checkZ80BusControlValue(0);

        m68k.setPC(MdBus.ADDRESS_RAM_MAP_START);
//        MC68000Helper.printCpuState(m68k, "");
        m68k.execute();
//        MC68000Helper.printCpuState(m68k, "");

        checkZ80BusControlValue(0);

        //code checks the wrong bit, on a word-wide read it should check bit#8
        int res = bus.read(Z80_BUS_REQ_CONTROL_START, Size.WORD);
        Assertions.assertEquals(1, res & 1);

//        MC68000Helper.printCpuState(m68k, "");
        m68k.execute();
//        MC68000Helper.printCpuState(m68k, "");
        m68k.execute();
//        MC68000Helper.printCpuState(m68k, "");
        m68k.execute();
//        MC68000Helper.printCpuState(m68k, "");
        Assertions.assertEquals(nopIdx + 2, m68k.getPC());
        Assertions.assertEquals(nopOpcode, m68k.getOpcode());

        checkZ80BusControlValue(0);
    }

    /**
     * 68M 0001eb2a   4a39 00a11100           tst.b    $00a11100
     * 68M 0001eb30   67f8                    beq.s    $0001eb2a
     */
    @Test
    public void testShadowOfTheBeast() {
        MC68000Wrapper w = bus.getBusDeviceIfAny(MC68000Wrapper.class).get();
        Assertions.assertNotNull(w);
        Cpu m68k = w.m68k;

        writeCodeToRam(w.addressSpace, sb_opcodes);

        int nopIdx = MdBus.ADDRESS_RAM_MAP_START + ((sb_opcodes.length - 1) << 1);

        //0xA11100 returns 0
        bus.setZ80BusRequested(true);
        bus.setZ80ResetState(false);

        checkZ80BusControlValue(0);

        m68k.setPC(MdBus.ADDRESS_RAM_MAP_START);
        m68k.execute();

        //code expects the entire byte to be != 0, instead of checking the correct bit#0
        int res = bus.read(Z80_BUS_REQ_CONTROL_START, Size.BYTE);
        Assertions.assertNotEquals(0, res);

        checkZ80BusControlValue(0);

        m68k.execute();
        m68k.execute();

        Assertions.assertEquals(nopIdx + 2, m68k.getPC());
        Assertions.assertEquals(nopOpcode, m68k.getOpcode());

        checkZ80BusControlValue(0);
    }

    /**
     * 68M 000004e0	1039 00a04000           move.b   $00a04000,d0
     * 68M 000004e6	6df8                    blt.s    $000004e0
     */
    @Test
    public void testUwol() {
        MC68000Wrapper w = bus.getBusDeviceIfAny(MC68000Wrapper.class).get();
        Z80Provider z = bus.getBusDeviceIfAny(Z80Provider.class).get();
        Assertions.assertNotNull(w);
        Cpu m68k = w.m68k;
        int memAddress = Z80_ADDRESS_SPACE_START | 0x4000;
        int z80MemAddr = memAddress & MdMainBusProvider.M68K_TO_Z80_MEMORY_MASK;

        writeCodeToRam(w.addressSpace, uwol_opcodes);
        z.writeMemory(z80MemAddr, 0xDD);
        z.writeMemory(z80MemAddr + 1, 0xEE);

        int nopIdx = MdBus.ADDRESS_RAM_MAP_START + ((uwol_opcodes.length - 1) << 1);

        //68k cannot access Z80 RAM
        bus.setZ80BusRequested(false);
        bus.setZ80ResetState(false);

        checkZ80BusControlValue(1);

        m68k.setPC(MdBus.ADDRESS_RAM_MAP_START);
        m68k.execute();

        int prefetch = uwol_opcodes[3]; //blt

        int res = bus.read(memAddress, Size.BYTE);
        Assertions.assertEquals(prefetch >>> 8, res);

        res = bus.read(memAddress, Size.WORD);
        Assertions.assertEquals(prefetch, res);

        checkZ80BusControlValue(1);

        m68k.execute();
        m68k.execute();

        Assertions.assertEquals(nopIdx + 2, m68k.getPC());
        Assertions.assertEquals(nopOpcode, m68k.getOpcode());

        checkZ80BusControlValue(1);
    }

    /**
     * 68M 00000656   41f9 00ff002c           lea      $00ff002c,a0
     * 68M 0000065c   213c 72014e75           move.l   #$72014e75,-(a0)
     * 68M 00000662   213c 30bc7200           move.l   #$30bc7200,-(a0)
     * 68M 00000668   213c 41fa0006           move.l   #$41fa0006,-(a0)
     * 68M 0000066e   4e90                    jsr      (a0)
     * 68M 00ff0020   41fa 0006               lea      $0006(pc),a0
     * 68M 00ff0024   30bc 7200               move.w   #$7200,(a0)
     * 68M 00ff0028   7200                    moveq    #$00,d1
     * 68M 00ff002a   4e75                    rts
     * 68M 00000670   0a3c 0004               ori.w    #$0004,sr
     * 68M 00000674   4e75                    rts
     * <p>
     * Writes some code to RAM, note #$72014e75
     * 00ff0028 7201 moveq    #$01,d1
     * 00ff002a 4e75 rts
     * <p>
     * this bit is effectively rewriting @00ff0028 with 0x7200 (moveq    #$00,d1)
     * 68M 00ff0020   41fa 0006               lea      $0006(pc),a0
     * 68M 00ff0024   30bc 7200               move.w   #$7200,(a0)
     * <p>
     * but at this point 7201 has already been prefetched and the next opcode should still be 7201.
     * d1 stores the test result, 0 -> fail, 1 - success
     *
     * see (sha1)
     * 29c0624649ff6fc78350535b74651453047f65e6  misc_test.bin
     */
    @Test
    public void testMiscTestBin() {
        MC68000Wrapper w = bus.getBusDeviceIfAny(MC68000Wrapper.class).get();
        Assertions.assertNotNull(w);
        Cpu m68k = w.m68k;

        writeCodeToRam(w.addressSpace, mt_opcodes);
        m68k.setPC(MdBus.ADDRESS_RAM_MAP_START);

        int nopIdx = MdBus.ADDRESS_RAM_MAP_START + ((mt_opcodes.length - 1) << 1);

        int cnt = 0, limit = 50;
//        MC68000Helper.printCpuState(m68k, "Start");
        do {
//            MC68000Helper.printCpuState(m68k, "Before");
            m68k.execute();
            cnt++;
        } while (m68k.getPC() != nopIdx && cnt < limit);
//        MC68000Helper.printCpuState(m68k, "End");
        Assertions.assertNotEquals(limit, cnt);
        int expected = 1;
        Assertions.assertEquals(expected, m68k.getDataRegisterByte(1));
    }

    private void checkZ80BusControlValue(int exp) {
        int res = bus.read(Z80_BUS_REQ_CONTROL_START, Size.BYTE);
        Assertions.assertEquals(exp, res & 1);
        res = bus.read(Z80_BUS_REQ_CONTROL_START, Size.WORD);
        Assertions.assertEquals(exp, (res >> 8) & 1);
    }

    private void writeCodeToRam(AddressSpace memory, short[] opcodes) {
        ByteBuffer bb = ByteBuffer.allocate(opcodes.length << 1);
        bb.asShortBuffer().put(opcodes);

        //add code to RAM
        for (int i = 0; i < opcodes.length << 1; i++) {
            memory.writeByte(MdBus.ADDRESS_RAM_MAP_START + i, bb.get(i));
        }
    }
}
