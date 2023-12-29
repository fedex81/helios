package s32x.sh2.poll;

import omegadrive.util.BufferUtil;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.Size;
import org.junit.jupiter.api.*;
import s32x.event.PollSysEventManager;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Helper;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.sh2.device.IntControl;
import s32x.sh2.drc.Sh2DrcBlockOptimizer.PollType;
import s32x.util.MarsLauncherHelper;
import s32x.util.Md32xRuntimeData;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static omegadrive.util.Util.th;
import static s32x.MarsRegTestUtil.createTestInstance;
import static s32x.dict.S32xDict.SH2_START_ROM;
import static s32x.dict.S32xDict.SH2_START_SDRAM;
import static s32x.sh2.Sh2Disassembler.NOP;
import static s32x.sh2.device.IntControl.Sh2Interrupt.PWM_06;
import static s32x.sh2.drc.DrcUtil.RUNNING_IN_GITHUB;
import static s32x.sh2.drc.DrcUtil.loopUntilDrc;
import static s32x.sh2.drc.Sh2DrcBlockOptimizer.POLLER_ACTIVATE_LIMIT;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Sh2PollerTest implements PollSysEventManager.SysEventListener {
    private static MarsLauncherHelper.Sh2LaunchContext lc;
    protected static Sh2Config configDrcEn = new Sh2Config(true, true, true, true);

    private CpuDeviceAccess lastCpuEvent;
    private PollSysEventManager.SysEvent lastEvent;

    static class LocalTestCtx {
        public int[] regs, opcodes;
        public int cyclesPerBlock, start, memLoadAddress, matchVal, noMatchVal;
        public boolean isPoll, matchOrNot;
        public Size memLoadSize;
    }

    @BeforeAll
    public static void beforeAll() {
        Sh2Config.reset(configDrcEn);
        lc = createTestInstance();
    }

    @BeforeEach
    public void before() {
        lc = createTestInstance();
        lastCpuEvent = null;
        lastEvent = null;
        lc.sh2.reset(lc.masterCtx);
        Md32xRuntimeData.setAccessTypeExt(CpuDeviceAccess.MASTER);
        PollSysEventManager.instance.reset();
        PollSysEventManager.instance.addSysEventListener(CpuDeviceAccess.MASTER, "Sh2PollerTest", this);
        Sh2Helper.clear();
        Assumptions.assumeFalse(RUNNING_IN_GITHUB);
    }

    /**
     * M 400	841a	mov.b @(10, R1), R0 //MOVBL4
     * M 402	2028	tst R2, R0
     * M 404	89fc	bt H'400
     * M 406	0009	nop
     * M 408	0009	bra H'408
     * M 40A	0009	nop
     */
    @Test
    public void testMemLoad01() {
        LocalTestCtx c = new LocalTestCtx();
        c.regs = getEmptyRegs();
        c.memLoadAddress = 0x8a;
        c.memLoadSize = Size.BYTE;
        c.matchVal = 2;
        c.noMatchVal = 4;
        c.matchOrNot = false;
        c.regs[2] = c.matchVal;
        c.regs[1] = SH2_START_SDRAM | (c.memLoadAddress - 10);
        c.cyclesPerBlock = 10 + 11; //exec + fetch
        c.start = 0x400;
        //BRA 0x408
        c.opcodes = new int[]{0x841a, 0x2028, 0x89fc, NOP, 0xaffe, NOP}; //BRA 0x408
        c.isPoll = true;
        testMemLoadInternal(c);
    }

    /**
     * 6007656 = 1, spinCount < 3
     * <p>
     * SLAVE Poll detected at PC 6000ba0: 6007656 SDRAM
     * 06000ba0	c539	mov.w @(57, GBR), R0  //spinCount = 1
     * 00000ba2	8800	cmp/eq H'00, R0
     * 00000ba4	8bfc	bf H'00000ba0 //no branch
     * [...]
     * 06000ba0	c539	mov.w @(57, GBR), R0 //spinCount = 2
     * 00000ba2	8800	cmp/eq H'00, R0
     * 00000ba4	8bfc	bf H'00000ba0 //no branch
     * [...]
     * 06000ba0	c539	mov.w @(57, GBR), R0 //spinCount = 3, this is detected as POLLING!!!
     * 00000ba2	8800	cmp/eq H'00, R0
     * 00000ba4	8bfc	bf H'00000ba0
     */
    @Test
    public void testMemLoad02() {
        LocalTestCtx c = new LocalTestCtx();
        c.regs = getEmptyRegs();
        c.memLoadAddress = 0x8c;
        c.memLoadSize = Size.WORD;
        c.matchVal = 0;
        c.noMatchVal = 4;
        c.matchOrNot = true;
        c.regs[2] = c.matchVal;
        c.regs[1] = SH2_START_SDRAM | (c.memLoadAddress - 10);
        c.cyclesPerBlock = 10 + 11; //exec + fetch
        c.start = 0x600;
        c.opcodes = new int[]{0xc539, 0x8800, 0x8bfc, 0xaffb, NOP, 0xaffa, NOP}; //BRA 0x400,  BRA 0x400
        c.isPoll = true;

        int gbrVal = 0x1a;
        lc.masterCtx.GBR = SH2_START_SDRAM | gbrVal;
        testMemLoad02Internal(c);
    }

    /**
     * Check that if the value matches the exit condition we don't begin polling.
     * R0 = 0 and we enter this code:
     * <p>
     * 06000ba0	c539	mov.w @(57, GBR), R0
     * 00000ba2	8800	cmp/eq H'00, R0
     * 00000ba4	8bfc	bf H'00000ba0
     */
    @Test
    public void testMemLoad03() {
        LocalTestCtx c = new LocalTestCtx();
        c.regs = getEmptyRegs();
        c.memLoadAddress = 0x8c;
        c.memLoadSize = Size.WORD;
        c.matchVal = 0;
        c.noMatchVal = 4;
        c.matchOrNot = true;
        c.regs[2] = c.matchVal;
        c.regs[1] = SH2_START_SDRAM | (c.memLoadAddress - 10);
        c.cyclesPerBlock = 10 + 11; //exec + fetch
        c.start = 0x600;
        c.opcodes = new int[]{0xc539, 0x8800, 0x8bfc, 0xaffb, NOP, 0xaffa, NOP}; //BRA 0x400,  BRA 0x400
        c.isPoll = true;

        int gbrVal = 0x1a;
        lc.masterCtx.GBR = SH2_START_SDRAM | gbrVal;
        testMemLoad03Internal(c);
    }

    @Test
    public void testMemLoadChangeMemoryLocation() {
        LocalTestCtx c = new LocalTestCtx();
        c.memLoadSize = Size.BYTE;
        c.matchVal = 2;
        c.noMatchVal = 4;
        c.matchOrNot = false;
        c.cyclesPerBlock = 10 + 11; //exec + fetch
        c.start = 0x400;
        //BRA 0x408
        c.opcodes = new int[]{0x841a, 0x2028, 0x89fc, NOP, 0xaffe, NOP}; //BRA 0x408
        c.isPoll = true;
        for (int i = 0; i < 0x200; i += 0x10) {
            c.regs = getEmptyRegs();
            c.memLoadAddress = i + 10;
            System.out.println(th(c.memLoadAddress));
            c.regs[2] = c.matchVal;
            c.regs[1] = SH2_START_SDRAM | (c.memLoadAddress - 10);
            testMemLoadInternal(c);
            Sh2Helper.clear();
        }
    }

    /**
     * soulstar x
     * <p>
     * M 060009f4	6211	mov.w @R1, R2  //MOVWL
     * M 060009f6	3200	cmp/eq R0, R2
     * M 060009f8	89fc	bt H'060009f4
     */
    @Test
    public void testInterruptStopPoller() {
        LocalTestCtx c = new LocalTestCtx();
        c.regs = getEmptyRegs();
        c.memLoadAddress = 0x5a;
        c.memLoadSize = Size.WORD;
        c.matchVal = 1;
        c.noMatchVal = 5;
        c.matchOrNot = true;
        c.regs[0] = c.matchVal;
        c.regs[1] = SH2_START_SDRAM | c.memLoadAddress;
        c.cyclesPerBlock = 4;
        c.start = 0x600;
        //BRA 0x408
        c.opcodes = new int[]{0x6211, 0x3200, 0x89fc, NOP, 0xaffe, NOP};
        c.isPoll = true;

        testInterruptInternal(c);
    }

    /**
     * M 060009f6	affe	bra here
     * M 060009f8	0009	nop
     */
    @Test
    public void testInterruptStopBusyLoop() {
        LocalTestCtx c = new LocalTestCtx();
        c.regs = getEmptyRegs();
        c.cyclesPerBlock = 1;
        c.start = 0x600;
        c.opcodes = new int[]{0xaffe, NOP};
        c.isPoll = false;
        c.memLoadSize = Size.WORD;
        testInterruptInternal(c);
    }

    private void testInterruptInternal(LocalTestCtx c) {
        int startRom = SH2_START_ROM | c.start;
        Sh2Context sh2Context = lc.masterCtx;
        setupMemSh2(c);
        //runs the block
        runBlock(sh2Context, c.cyclesPerBlock);
        loopUntilDrcAndDetect(startRom, sh2Context, c.isPoll);
        setInterrupt(PWM_06);

        //disables polling or busyLoop
        runBlock(sh2Context, c.cyclesPerBlock);
        Assertions.assertEquals(CpuDeviceAccess.MASTER, lastCpuEvent);
        Assertions.assertEquals(PollSysEventManager.SysEvent.INT, lastEvent);
        Assertions.assertFalse(isPollerActive());

        //re-enter polling
        loopUntilPollingActive(sh2Context);

        clearInterrupt(PWM_06);
    }

    private void testMemLoadInternal(LocalTestCtx c) {
        setupMemSh2(c);

        int startRom = SH2_START_ROM | c.start;
        Sh2Context sh2Context = lc.masterCtx;
        //runs the block
        runBlock(sh2Context, c.cyclesPerBlock);
        Assertions.assertEquals(c.noMatchVal, sh2Context.registers[0]);
        loopUntilDrcAndDetect(startRom, sh2Context, true);

        Sh2Helper.Sh2PcInfoWrapper wrapper = Sh2Helper.get(startRom, CpuDeviceAccess.MASTER);
        Assertions.assertEquals(c.memLoadAddress, wrapper.block.poller.blockPollData.memLoadTarget & 0xFFFF);

        //write to sdram should NOT trigger an event
        lc.memory.write8(SH2_START_SDRAM | c.memLoadAddress + 4, (byte) c.noMatchVal);
        Assertions.assertEquals(PollSysEventManager.SysEvent.START_POLLING, lastEvent);
        Assertions.assertTrue(isPollerActive());

        //write to sdram should trigger an event
        lc.memory.write8(SH2_START_SDRAM | c.memLoadAddress, (byte) c.noMatchVal);
        Assertions.assertEquals(CpuDeviceAccess.MASTER, lastCpuEvent);
        Assertions.assertEquals(PollSysEventManager.SysEvent.SDRAM, lastEvent);
        Assertions.assertFalse(isPollerActive());

        //check condition
        runBlock(sh2Context, c.cyclesPerBlock);
        Assertions.assertTrue(isPollerActive());
        //resume polling
        runBlock(sh2Context, c.cyclesPerBlock);
        Assertions.assertTrue(isPollerActive());
        Assertions.assertEquals(CpuDeviceAccess.MASTER, lastCpuEvent);
        Assertions.assertEquals(PollSysEventManager.SysEvent.START_POLLING, lastEvent);

        //write to sdram should trigger an event
        lc.memory.write8(SH2_START_SDRAM | c.memLoadAddress, (byte) (c.noMatchVal | c.matchVal));
        Assertions.assertEquals(CpuDeviceAccess.MASTER, lastCpuEvent);
        Assertions.assertEquals(PollSysEventManager.SysEvent.SDRAM, lastEvent);
        Assertions.assertFalse(isPollerActive());

        //check condition, exit loop
        runBlock(sh2Context, c.cyclesPerBlock);
        Assertions.assertFalse(isPollerActive());
        Assertions.assertEquals(startRom + 8, sh2Context.PC);
    }

    public void testMemLoad02Internal(LocalTestCtx c) {
        int startRom = SH2_START_ROM | c.start;
        Sh2Context sh2Context = lc.masterCtx;
        int cycles = c.cyclesPerBlock;

        setupMemSh2(c);

        runBlock(sh2Context, cycles);
        //activate drc
        Sh2Helper.Sh2PcInfoWrapper wrapper = Sh2Helper.get(startRom, CpuDeviceAccess.MASTER);
        Assertions.assertNotNull(wrapper);
        Assertions.assertNotEquals(Sh2Helper.SH2_NOT_VISITED, wrapper);
        loopUntilDrc(lc.sh2, sh2Context, wrapper);
        Assertions.assertEquals(PollType.SDRAM, wrapper.block.pollType);
        System.out.println(th(wrapper.block.poller.blockPollData.memLoadTarget));

        Assertions.assertFalse(isPollerActive());

        int cnt = 0;
        do {
            runBlock(sh2Context, cycles);
            Assertions.assertFalse(isPollerActive());
            cnt++;
        } while (cnt < POLLER_ACTIVATE_LIMIT << 1);

        ByteBuffer sdram = lc.memory.getMemoryDataCtx().sdram;
        //now we start looping
        sdram.putShort(c.memLoadAddress, (short) c.noMatchVal);
        loopUntilPollingActive(sh2Context);
        Assertions.assertTrue(wrapper.block.poller.isPollingActive());

        //stop looping
        sdram.putShort(c.memLoadAddress, (short) c.matchVal);
        runBlock(sh2Context, cycles);
        Assertions.assertFalse(isPollerActive());
    }

    public void testMemLoad03Internal(LocalTestCtx c) {
        int startRom = SH2_START_ROM | c.start;
        Sh2Context sh2Context = lc.masterCtx;
        int cycles = c.cyclesPerBlock;

        setupMemSh2(c);

        runBlock(sh2Context, cycles);
        //activate drc
        Sh2Helper.Sh2PcInfoWrapper wrapper = Sh2Helper.get(startRom, CpuDeviceAccess.MASTER);
        Assertions.assertNotNull(wrapper);
        Assertions.assertNotEquals(Sh2Helper.SH2_NOT_VISITED, wrapper);
        loopUntilDrc(lc.sh2, sh2Context, wrapper);
        Assertions.assertEquals(PollType.SDRAM, wrapper.block.pollType);
        System.out.println(th(wrapper.block.poller.blockPollData.memLoadTarget));

        Assertions.assertFalse(isPollerActive());

        //we shouldn't start looping, as the value matches the exit condition
        ByteBuffer sdram = lc.memory.getMemoryDataCtx().sdram;
        sdram.putShort(c.memLoadAddress, (short) c.matchVal);

        int cnt = 0;
        do {
            runBlock(sh2Context, cycles);
            Assertions.assertFalse(isPollerActive());
            cnt++;
        } while (cnt < POLLER_ACTIVATE_LIMIT << 1);

        Assertions.assertFalse(wrapper.block.poller.isPollingActive());
        Assertions.assertFalse(isPollerActive());
    }


    private void setupMemSh2(LocalTestCtx c) {
        int startRom = SH2_START_ROM | c.start;
        ByteBuffer sdram = lc.memory.getMemoryDataCtx().sdram;
        BufferUtil.writeBufferRaw(sdram, c.memLoadAddress, (short) (c.matchOrNot ? c.matchVal : c.noMatchVal), c.memLoadSize);

        ByteBuffer rom = lc.memory.getMemoryDataCtx().rom;
        for (int i = 0; i < c.opcodes.length; i++) {
            rom.putShort(c.start + (i << 1), (short) c.opcodes[i]);
        }

        Sh2Context sh2Context = lc.masterCtx;
        System.arraycopy(c.regs, 0, sh2Context.registers, 0, c.regs.length);
        sh2Context.PC = startRom;
    }


    private void setInterrupt(IntControl.Sh2Interrupt interrupt) {
        IntControl intc = lc.mDevCtx.intC;
        intc.setIntsMasked(0xF); //unmask
        intc.setIntPending(interrupt, true);
    }

    private void clearInterrupt(IntControl.Sh2Interrupt interrupt) {
        IntControl intc = lc.mDevCtx.intC;
        intc.clearExternalInterrupt(interrupt);
    }

    private void loopUntilDrcAndBusyLoopDetect(int start, Sh2Context sh2Context) {
        loopUntilDrcAndDetect(start, sh2Context, false);
    }

    private void loopUntilDrcAndDetect(int start, Sh2Context sh2Context, boolean isPoll) {
        Sh2Helper.Sh2PcInfoWrapper wrapper = Sh2Helper.get(start, CpuDeviceAccess.MASTER);
        Assertions.assertNotNull(wrapper);
        Assertions.assertNotEquals(Sh2Helper.SH2_NOT_VISITED, wrapper);
        loopUntilDrc(lc.sh2, sh2Context, wrapper);
        Assertions.assertEquals(isPoll ? PollType.SDRAM : PollType.BUSY_LOOP, wrapper.block.pollType);
        loopUntilPollingActive(sh2Context);
        Assertions.assertTrue(wrapper.block.poller.isPollingActive());
    }

    private void runBlock(Sh2Context sh2Context, int cycles) {
        Sh2Context.burstCycles = cycles;
        sh2Context.cycles = cycles;
        lc.sh2.run(sh2Context);
        //TODO initial loop does nothing, see run()
        Assertions.assertTrue(sh2Context.cycles < 0 || sh2Context.cycles == cycles);
    }

    public boolean isPollerActive() {
        return PollSysEventManager.currentPollers[CpuDeviceAccess.MASTER.ordinal()].isPollingActive();
    }

    private void loopUntilPollingActive(Sh2Context sh2Context) {
        boolean pollActive = false;
        int cnt = 0;
        do {
            lc.sh2.run(sh2Context);
            pollActive = isPollerActive();
            cnt++;
        } while (!pollActive && cnt < POLLER_ACTIVATE_LIMIT * 20);
        Assertions.assertNotEquals(100, cnt);
        Assertions.assertEquals(CpuDeviceAccess.MASTER, lastCpuEvent);
        Assertions.assertEquals(PollSysEventManager.SysEvent.START_POLLING, lastEvent);
        Assertions.assertTrue(isPollerActive());
    }

    private int[] getEmptyRegs() {
        int[] regs = lc.masterCtx.registers.clone();
        Arrays.fill(regs, 0);
        return regs;
    }

    @Override
    public void onSysEvent(CpuDeviceAccess cpu, PollSysEventManager.SysEvent event) {
        lastCpuEvent = cpu;
        lastEvent = event;
        System.out.println(cpu + "," + event);
        if (lastEvent == PollSysEventManager.SysEvent.SDRAM || lastEvent == PollSysEventManager.SysEvent.INT) {
            PollSysEventManager.instance.resetPoller(cpu);
        }
    }
}
