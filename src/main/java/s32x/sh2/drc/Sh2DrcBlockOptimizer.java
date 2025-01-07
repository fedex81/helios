package s32x.sh2.drc;

import com.google.common.collect.ImmutableMap;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import s32x.dict.S32xDict;
import s32x.event.PollSysEventManager;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Debug;
import s32x.sh2.Sh2Disassembler;
import s32x.sh2.Sh2Helper;

import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;

import static omegadrive.util.Util.th;
import static s32x.sh2.Sh2Impl.RM;
import static s32x.sh2.Sh2Impl.RN;
import static s32x.sh2.drc.Sh2DrcBlockOptimizer.PollType.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */

/**
 * SRB2 32XN
 * 2024-12-24 23:36:34.012 ERROR [X_v0.1.32x] Sh2DrcBlockOptimizer: MASTER Poll ignored at PC 20190e8: 603bf3a NONE
 * 020190e8	6031	mov.w @R3, R0
 * 000190ea	6121	mov.w @R2, R1
 * 000190ec	611d	extu.w R1, R1
 * 000190ee	c901	and H'01, R0
 * 000190f0	3010	cmp/eq R1, R0
 * 000190f2	8bf9	bf H'000190e8
 * <p>
 * 2024-12-24 23:36:34.488 ERROR [X_v0.1.32x] Sh2DrcBlockOptimizer: SLAVE Poll ignored at PC 600654e: 20004038 PWM
 * 0600654e	6b41	mov.w @R4, R11
 * 00006550	4b11	cmp/pz R11
 * 00006552	8bfc	bf H'0000654e
 * <p>
 * 2024-12-24 23:36:34.488 ERROR [X_v0.1.32x] Sh2DrcBlockOptimizer: SLAVE Poll ignored at PC 600654e: 20004038 PWM
 * 0600654e	6b41	mov.w @R4, R11
 * 00006550	4b11	cmp/pz R11
 * 00006552	8bfc	bf H'0000654e
 * <p>
 * 2024-12-24 23:39:33.693 ERROR [X_v0.1.32x] Sh2DrcBlockOptimizer: MASTER Poll ignored at PC 2016e40: 2603bf2c NONE
 * 02016e40	6642	mov.l @R4, R6
 * 00016e42	6163	mov R6, R1
 * 00016e44	3178	sub R7, R1
 * 00016e46	31a3	cmp/ge R10, R1
 * 00016e48	8ffa	bf/s H'00016e40
 * 00016e4a	e363	mov H'63, R3
 */
public class Sh2DrcBlockOptimizer {
    private final static Logger LOG = LogHelper.getLogger(Sh2DrcBlockOptimizer.class.getSimpleName());

    //toggle poll detection but keep busyLoop detection enabled
    public final static boolean ENABLE_POLL_DETECT = true;
    private final static boolean LOG_POLL_DETECT = false;

    private final static boolean LOG_POLL_DETECT_UNSUPPORTED = false;

    public static final int POLLER_ACTIVATE_LIMIT = 3;

    private static final boolean verbose = false;
    public static final Map<S32xDict.S32xRegType, PollType> ptMap = ImmutableMap.of(
            S32xDict.S32xRegType.DMA, DMA,
            S32xDict.S32xRegType.PWM, PWM,
            S32xDict.S32xRegType.VDP, VDP,
            S32xDict.S32xRegType.SYS, SYS,
            S32xDict.S32xRegType.NONE, NONE,
            S32xDict.S32xRegType.COMM, COMM
    );

    public enum PollState {NO_POLL, ACTIVE_POLL}

    public enum PollType {
        UNKNOWN,
        NONE,
        BUSY_LOOP(true),
        SDRAM(true),
        FRAMEBUFFER, //TODO DoomRes, is it worth it?
        COMM(true),
        DMA,
        PWM,
        SYS(true),
        VDP(true);

        public final boolean supported;

        PollType() {
            this(false);
        }

        PollType(boolean supported) {
            this.supported = supported;
        }
    }

    public static final Predicate<Integer> isTasOpcode = op -> (op & 0xF0FF) == 0x401b;
    public static final Predicate<Integer> isMovtOpcode = op -> (op & 0xF0FF) == 0x0029;
    public static final Predicate<Integer> isMemLoadOpcode = op -> Sh2Debug.isMovOpcode.or(isTasOpcode).test(op);
    private static final Predicate<Integer> isMoviOpcode = w -> (w & 0xF000) == 0xE000;

    //shlr8
    private static final Predicate<Integer> isShiftOpcode = w -> (w & 0xF0FF) == 0x4019;

    //opcode that modifies the T flag, ie. shlr
    private static final Predicate<Integer> isFlagOpcode = op -> (op & 0xF0FF) == 0x4001;

    private static final Predicate<Integer> isCmpTstOpcode = Sh2Debug.isTstOpcode.or(Sh2Debug.isCmpOpcode);

    public static final Predicate<Integer> isCmpTstFlagOpcode = isCmpTstOpcode.or(isFlagOpcode);

    private static final Predicate<Integer> isSwapOpcode = w -> ((w & 0xF00F) == 0x6008 || (w & 0xF00F) == 0x6009);
    private static final Predicate<Integer> isXorOpcode = w -> (w & 0xF00F) == 0x200A;

    private static final Predicate<Integer> isExtOpcode = w ->
            (w & 0xF00F) == 0x600C || (w & 0xF00F) == 0x600D || (w & 0xF00F) == 0x600E || (w & 0xF00F) == 0x600F;

    private static final Predicate<Integer> isAndOrOpcode = w -> ((w & 0xF00F) == 0x2009 || (w & 0xFF00) == 0xC900 ||
            (w & 0xFF00) == 0xCB00);

    public static final Predicate<Integer> isMovR2ROpcode = w -> (w & 0xF00F) == 0x6003; //mov Rm, Rn
    public static final Predicate<Integer> isRegOnlyOpcode = isAndOrOpcode.or(isExtOpcode).or(isSwapOpcode).
            or(isMovR2ROpcode).or(isMoviOpcode).or(isMovtOpcode).or(isShiftOpcode).or(isXorOpcode);

    private static final Predicate<Integer> isDtOpcode = op -> (op & 0xF0FF) == 0x4010;

    public static class BlockPollData {
        public int memLoadPos = -1, memLoadOpcode, cmpPos = -1, cmpOpcode, branchPos = -1,
                branchOpcode, branchPc, branchDestPc;
        public int pc;
        public int memLoadTarget = 0, memLoadTargetEnd;
        public Size memLoadTargetSize;
        public final int[] words;
        public final Sh2Context ctx;
        public final Sh2Block block;
        public boolean isPoller, isBusyLoop;

        public BlockPollData(Sh2Block block, Sh2Context ctx, int pc, int[] prefetchWords) {
            this.words = prefetchWords;
            this.pc = pc;
            this.ctx = ctx;
            this.block = block;
        }

        public void init() {
            detect();
        }

        private void detect() {
            int memLoads = 0, numNops = 0, numRegOnly = 0, pollInstLen = 0;
            for (int i = 0; i < words.length; i++) {
                final int opcode = words[i];
                numNops += opcode == Sh2Disassembler.NOP ? 1 : 0;
                numRegOnly += isRegOnlyOpcode.test(opcode) ? 1 : 0;
                if (isMemLoadOpcode.test(opcode)) {
                    memLoadPos = i;
                    memLoadOpcode = opcode;
                    pollInstLen++;
                    memLoads++;
                } else if (isCmpTstFlagOpcode.test(opcode)) {
                    cmpPos = i;
                    cmpOpcode = opcode;
                    pollInstLen++;
                } else if (Sh2Debug.isBranchNearOpcode.test(opcode)) {
                    branchPos = i;
                    branchOpcode = opcode;
                    branchPc = pc + (branchPos << 1);
                    pollInstLen++;
                }
            }
            int nonNopsLen = words.length - numNops;
            if (memLoadPos >= 0) {
                parseMemLoad(this, ctx, memLoadOpcode);
            }
            if (branchPos >= 0) {
                branchDestPc = getBranchDestination(branchOpcode, branchPc);
            }
            if (nonNopsLen == 2) {
                if (cmpPos >= 0 && branchPos >= 0 && branchDestPc == pc) {
                    parseMemLoad(this, ctx, cmpOpcode);
                    memLoadPos = cmpPos;
                    memLoads++;
                }
            } else if (nonNopsLen == 1) {
                int endPc = pc + ((words.length - 1) << 1);
                if (branchPos >= 0 && branchDestPc >= pc && branchDestPc <= endPc) {
                    isBusyLoop = true;
                } else {
//                    LOG.info("{} NOT a BusyLoop: {}\n{}", block.drcContext.cpu, th(block.prefetchPc),
//                            Sh2Helper.toListOfInst(block));
                }
            }
            if (memLoads == 1) {
                isPollerRecalc(pollInstLen, numNops + numRegOnly);
            }
            /**
             *  06002406	0009	nop
             *  00002408	0009	nop
             *  0000240a	88ff	cmp/eq H'ffffffff, R0
             *  0000240c	8bfb	bf H'00002406
             */
            if (isPoller && memLoadTarget == 0) {
                LOG.warn("Busy Loop?\n{}\n{}\n{}", Sh2Helper.toListOfInst(block), block, this);
                isPoller = false;
                isBusyLoop = true;
            }
        }

        private boolean isPollerRecalc(int pollInstLen, int numNopsAndRegOnly) {
            int len = words.length;
            boolean okLen = pollInstLen + numNopsAndRegOnly == len;
            isPoller = (pollInstLen > 2 ? cmpPos >= 0 : true) && (pollInstLen > 1 ? memLoadPos >= 0 : true)
                    && branchPos >= 0 && branchDestPc == pc && okLen;
            return isPoller;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", BlockPollData.class.getSimpleName() + "[", "]")
                    .add("memLoadPos=" + th(memLoadPos))
                    .add("memLoadOpcode=" + th(memLoadOpcode))
                    .add("cmpPos=" + th(cmpPos))
                    .add("cmpOpcode=" + th(cmpOpcode))
                    .add("branchPos=" + th(branchPos))
                    .add("branchOpcode=" + th(branchOpcode))
                    .add("branchPc=" + th(branchPc))
                    .add("branchDestPc=" + th(branchDestPc))
                    .add("pc=" + th(pc))
                    .add("memLoadTarget=" + th(memLoadTarget))
                    .add("memLoadTargetSize=" + memLoadTargetSize)
                    .add("isPoller=" + isPoller)
                    .toString();
        }
    }

    public static class PollerCtx {
        public BufferUtil.CpuDeviceAccess cpu;
        public int pc;
        public PollSysEventManager.SysEvent event;
        public PollState pollState = PollState.NO_POLL;
        public int spinCount = 0;
        public int pollValue = 0;
        public BlockPollData blockPollData;
        public Sh2Helper.Sh2PcInfoWrapper piw;

        public static PollerCtx create(Sh2Helper.Sh2PcInfoWrapper piw) {
            PollerCtx ctx = new PollerCtx();
            Sh2Block block = piw.block;
            ctx.piw = piw;
            ctx.pc = block.prefetchPc;
            assert block.drcContext != null : piw;
            ctx.cpu = block.drcContext.cpu;
            ctx.event = PollSysEventManager.SysEvent.NONE;
            ctx.blockPollData = new BlockPollData(block, block.drcContext.sh2Ctx, ctx.pc, block.prefetchWords);
            return ctx;
        }

        public boolean isPollingActive() {
            return pollState != PollState.NO_POLL;
        }

        public boolean isPollingBusyLoop() {
            return piw.block.pollType == BUSY_LOOP;
        }

        public void stopPolling() {
            pollState = PollState.NO_POLL;
            spinCount = 0;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", PollerCtx.class.getSimpleName() + "[", "]")
                    .add("cpu=" + cpu)
                    .add("pc=" + th(pc))
                    .add("event=" + event)
                    .add("pollState=" + pollState)
                    .add("spinCount=" + spinCount)
                    .add("blockPollData=" + blockPollData)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PollerCtx pollerCtx = (PollerCtx) o;
            return pc == pollerCtx.pc && spinCount == pollerCtx.spinCount && cpu == pollerCtx.cpu
                    && event == pollerCtx.event && pollState == pollerCtx.pollState && com.google.common.base.Objects.equal(blockPollData, pollerCtx.blockPollData);
        }

        @Override
        public int hashCode() {
            return com.google.common.base.Objects.hashCode(cpu, pc, event, pollState, spinCount, blockPollData);
        }

        public void invalidate() {
            event = PollSysEventManager.SysEvent.NONE;
        }
    }

    public static final PollerCtx NO_POLLER = new PollerCtx();
    public static final PollerCtx UNKNOWN_POLLER = new PollerCtx();


    public static void pollDetector(Sh2Block block) {
        Sh2Helper.Sh2PcInfoWrapper piw = Sh2Helper.get(block.prefetchPc, block.drcContext.cpu);
        if (piw.block.poller != UNKNOWN_POLLER) {
            PollerCtx ctx = piw.block.poller;
            Sh2Block prevBlock = piw.block;
            assert block.isValid() && ctx != UNKNOWN_POLLER &&
                    (prevBlock != Sh2Block.INVALID_BLOCK ? prevBlock == block : true)
                    && ctx.cpu == block.drcContext.cpu :
                    "Poller: " + ctx + "\nPiwBlock: " + piw.block + "\nPrevPoll: " + prevBlock + "\nNewPoll : " + block;
            return;
        }
        assert piw.block == block : "PiwBlock: " + piw.block + "\nBlock: " + block + "\n" +
                block.drcContext.cpu + "," + th(block.prefetchPc) + "," + th(block.hashCodeWords);
        if (block.pollType == UNKNOWN) {
            PollerCtx ctx = PollerCtx.create(piw);
            ctx.blockPollData.init();
            piw.block.poller = addPollMaybe(ctx, block);
        }
        //mark this block as processed
        if (block.pollType == UNKNOWN) {
            block.pollType = NONE;
        }
    }

    private static int getBranchDestination(int jmpOpcode, int jmpPc) {
        int branchDest = 0;
        if ((jmpOpcode & 0xF000) == 0x8000) { //BT, BF, BTS, BFS
            int d = (byte) (jmpOpcode & 0xFF) << 1;
            branchDest = jmpPc + d + 4;
        } else if ((jmpOpcode & 0xF000) == 0xA000) { //BRA
            int disp = ((jmpOpcode & 0x800) == 0) ? 0x00000FFF & jmpOpcode : 0xFFFFF000 | jmpOpcode;
            branchDest = jmpPc + 4 + (disp << 1);
        }
        return branchDest;
    }

    private static void parseMemLoad(BlockPollData bpd) {
        parseMemLoad(bpd, bpd.ctx, bpd.memLoadOpcode);
    }

    private static void parseMemLoad(BlockPollData bpd, Sh2Context sh2Context, int memReadOpcode) {
        final int[] r = sh2Context.registers;
        if ((memReadOpcode & 0xF000) == 0x5000) {
//            MOVLL4 MOV.L@(disp,Rm),  Rn(disp × 4 + Rm) → Rn    0101nnnnmmmmdddd
            bpd.memLoadTarget = ((memReadOpcode & 0xF) << 2) + r[RM(memReadOpcode)];
            bpd.memLoadTargetSize = Size.LONG;
        } else if (((memReadOpcode & 0xF000) == 0xC000) && ((((memReadOpcode >> 8) & 0xF) == 4) || (((memReadOpcode >> 8) & 0xF) == 5) || (((memReadOpcode >> 8) & 0xF) == 6))) {
            //MOVBLG MOV.B@(disp,GBR),     R0(disp + GBR) → sign extension → R0    11000100dddddddd
            bpd.memLoadTargetSize = Size.vals[(memReadOpcode >> 8) & 0x3];
            bpd.memLoadTarget = ((memReadOpcode & 0xFF) << bpd.memLoadTargetSize.ordinal()) + sh2Context.GBR;
        } else if (((memReadOpcode & 0xF000) == 0x6000) && ((memReadOpcode & 0xF) < 3)) {
            //MOVXL, MOV.X @Rm,Rn
            bpd.memLoadTarget = r[RM(memReadOpcode)];
            bpd.memLoadTargetSize = Size.vals[memReadOpcode & 0xF];
        } else if (((memReadOpcode & 0xF000) == 0x8000) && (((memReadOpcode & 0xF00) == 0x400) || ((memReadOpcode & 0xF00) == 0x500))) {
            //MOVBL4, MOV.B @(disp,Rm),R0
            //MOVWL4, MOV.W @(disp,Rm),R0
            bpd.memLoadTargetSize = Size.vals[(memReadOpcode >> 8) & 1];
            bpd.memLoadTarget = r[RM(memReadOpcode)] + ((memReadOpcode & 0xF) << bpd.memLoadTargetSize.ordinal());
        } else if (((memReadOpcode & 0xFF00) == 0xCC00)) {
            //TSTM TST.B #imm,@(R0,GBR) 11001100iiiiiiii     (R0 + GBR) & imm;if the result is 0, 1→T
            bpd.memLoadTarget = r[0] + sh2Context.GBR;
            bpd.memLoadTargetSize = Size.BYTE;
        } else if ((memReadOpcode & 0xF0FF) == 0x401b) { //TAS.B @Rn
            bpd.memLoadTarget = r[RN(memReadOpcode)];
            bpd.memLoadTargetSize = Size.BYTE;
        }
        if (bpd.memLoadTargetSize != null) {
            bpd.memLoadTargetEnd = bpd.memLoadTarget + (bpd.memLoadTargetSize.getByteSize() - 1);
        }
    }

    private static PollerCtx addPollMaybe(PollerCtx pctx, Sh2Block block) {
        boolean supported = false, log = false;
        BlockPollData bpd = pctx.blockPollData;
        PollerCtx toSet = NO_POLLER;
        if (bpd.isPoller) {
            block.pollType = getAccessType(bpd.memLoadTarget);
            log |= bpd.branchDestPc == bpd.pc;
            if (block.pollType != UNKNOWN) {
                pctx.event = PollSysEventManager.SysEvent.valueOf(block.pollType.name());
                log = true;
                if (ENABLE_POLL_DETECT && block.pollType.supported) {
                    supported = true;
                    toSet = pctx;
//                    log = false;
                }
            }
            log |= bpd.memLoadTargetSize == null && block.pollType != UNKNOWN;
            if (LOG_POLL_DETECT && log) {
                logPollBlock(block, bpd, supported);
            }
        } else if (bpd.isBusyLoop) {
            if (verbose) LOG.info("{} BusyLoop detected: {}\n{}", block.drcContext.cpu, th(block.prefetchPc),
                    Sh2Helper.toListOfInst(block));
            block.pollType = BUSY_LOOP;
            pctx.event = PollSysEventManager.SysEvent.INT;
            toSet = pctx;
        }
        if (block.pollType == UNKNOWN) {
            block.pollType = NONE;
        }
        if (LOG_POLL_DETECT_UNSUPPORTED && !block.pollType.supported && bpd.branchDestPc == bpd.pc) {
            if (bpd.words.length < 10) {
                logPollBlock(block, bpd, false);
            }
        }
        assert block.pollType != UNKNOWN;
        assert toSet != null;
        return toSet;
    }

    private static void logPollBlock(Sh2Block block, BlockPollData bpd, boolean supported) {
        LOG.makeLoggingEventBuilder(supported ? Level.INFO : Level.ERROR).log(
                "{} Poll {} at PC {}: {} {}\n{}", block.drcContext.cpu,
                supported ? "detected" : "ignored", th(block.prefetchPc),
                th(bpd.memLoadTarget), block.pollType,
                Sh2Helper.toListOfInst(block));
    }

    //TODO poll on cached address??? tas poll is allowed even on cached addresses
    private static PollType getAccessType(int address) {
        switch (address >>> S32xDict.SH2_PC_AREA_SHIFT) {
            case 0x6:
            case 0x26:
                return SDRAM;
            case 0x24:
            case 0x4:
                return FRAMEBUFFER;
            case 0:
            case 0x20: {
                PollType pt = ptMap.get(S32xDict.getRegSpec(BufferUtil.CpuDeviceAccess.MASTER, address).deviceType);
//                assert pt != null : th(address);
                return pt == null ? NONE : pt;
            }
            default:
                LOG.error("{} Unexpected {} access type for polling: {}", MdRuntimeData.getAccessTypeExt(), NONE
                        , th(address));
                return NONE;
        }
    }

    public final static void handlePoll(Sh2Block block) {
        final BufferUtil.CpuDeviceAccess cpu = block.getCpu();
        if (!block.isPollingBlock()) {
            final PollerCtx current = PollSysEventManager.instance.getPoller(cpu);
            assert current != UNKNOWN_POLLER;
            if (current != NO_POLLER && block.pollType == BUSY_LOOP) { //TODO check
                PollSysEventManager.instance.resetPoller(current.cpu);
            }
            return;
        }
        final PollerCtx currentPoller = PollSysEventManager.instance.getPoller(cpu);
        final PollerCtx blockPoller = block.poller;
        assert blockPoller == Sh2Helper.get(block.prefetchPc, cpu).block.poller;
        if (currentPoller == NO_POLLER) {
            assert blockPoller != UNKNOWN_POLLER;
            if (blockPoller != NO_POLLER) {
                PollSysEventManager.instance.setPoller(cpu, blockPoller);
            } else {
                //tempo
                assert ENABLE_POLL_DETECT ? !block.pollType.supported : true : block + "\n" + blockPoller;
                if (verbose) LOG.info("{} ignoring {} poll at PC {}", cpu, block.pollType, th(block.prefetchPc));
                block.pollType = PollType.NONE;
            }
        } else if (!currentPoller.isPollingActive()) {
            if (blockPoller != currentPoller) {
                PollSysEventManager.instance.resetPoller(cpu);
                return;
            }
            startPollingMaybe(blockPoller, block.pollType);
        } else if (currentPoller.isPollingActive()) {
            if (verbose) LOG.info("Polling active: {}", currentPoller);
            assert blockPoller == currentPoller;
        } else {
            throw new RuntimeException("Unexpected, poller: " + currentPoller);
        }
    }

    private static void startPollingMaybe(PollerCtx blockPoller, PollType pollType) {
        if (blockPoller.spinCount < POLLER_ACTIVATE_LIMIT) {
            if (verbose)
                LOG.info("{} avoid re-entering {} poll at PC {}, on address: {}", blockPoller.cpu, pollType,
                        th(blockPoller.pc), th(blockPoller.blockPollData.memLoadTarget));
            if (blockPoller.spinCount == POLLER_ACTIVATE_LIMIT - 1) {
                Sh2DrcBlockOptimizer.parseMemLoad(blockPoller.blockPollData);
                blockPoller.pollValue = PollSysEventManager.readPollValue(blockPoller);
            }
            return;
        }
        //chaotix, star trek, needs this
        if (!checkPollValueStable(blockPoller, pollType)) {
            return;
        }
        blockPoller.pollState = PollState.ACTIVE_POLL;
        if (verbose)
            LOG.info("{} entering {} poll at PC {}, on address: {}, currentVal: {}", blockPoller.cpu, pollType,
                    th(blockPoller.pc), th(blockPoller.blockPollData.memLoadTarget), th(blockPoller.pollValue));
        PollSysEventManager.instance.fireSysEvent(blockPoller.cpu, PollSysEventManager.SysEvent.START_POLLING);
    }

    private static boolean checkPollValueStable(PollerCtx blockPoller, PollType pollType) {
        int pollValue = PollSysEventManager.readPollValue(blockPoller);
        boolean res = pollValue == blockPoller.pollValue;
        if (!res) {
            if (verbose)
                LOG.info("{} skipping {} poll at PC {} as poll value has changed, on address: {}, val: {} -> {}",
                        blockPoller.cpu, pollType, th(blockPoller.pc), th(blockPoller.blockPollData.memLoadTarget),
                        th(blockPoller.pollValue), th(pollValue));
            blockPoller.pollValue = blockPoller.spinCount = 0;
        }
        return res;
    }
}
