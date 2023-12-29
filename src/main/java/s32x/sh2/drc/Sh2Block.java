package s32x.sh2.drc;

import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import s32x.Sh2MMREG;
import s32x.sh2.Sh2;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Helper;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.sh2.prefetch.Sh2Prefetch;
import s32x.sh2.prefetch.Sh2Prefetcher;
import s32x.util.Md32xRuntimeData;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.StringJoiner;

import static omegadrive.util.Util.th;
import static s32x.sh2.drc.Sh2DrcBlockOptimizer.*;
import static s32x.sh2.drc.Sh2DrcBlockOptimizer.PollType.NONE;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Sh2Block {
    private static final Logger LOG = LogHelper.getLogger(Sh2Block.class.getSimpleName());

    //needs to be (powerOf2 - 1)
    private static final int OPT_THRESHOLD2 = Integer.parseInt(System.getProperty("helios.32x.sh2.drc.stage2.hits", "31"));

    public static final Sh2Block INVALID_BLOCK = new Sh2Block(-1, BufferUtil.CpuDeviceAccess.MASTER);
    public static final int SH2_DRC_MAX_BLOCK_LEN_BYTES =
            Integer.parseInt(System.getProperty("helios.32x.sh2.drc.maxBlockLen", "32"));
    public static final int MAX_INST_LEN = SH2_DRC_MAX_BLOCK_LEN_BYTES >> 1;

    //0 - Master, 1 - Slave
    public static final int CPU_FLAG = 1 << 0;
    public static final int CACHE_FETCH_FLAG = 1 << 1;
    public static final int NO_JUMP_FLAG = 1 << 2;

    public static final int VALID_FLAG = 1 << 3;

    public Sh2Prefetcher.Sh2BlockUnit[] inst;
    public Sh2Prefetcher.Sh2BlockUnit curr;
    public int[] prefetchWords;
    public int prefetchPc, hits, start, end, pcMasked, prefetchLenWords, fetchMemAccessDelay, cyclesConsumed;
    public ByteBuffer fetchBuffer;
    public Sh2Block nextBlock = INVALID_BLOCK;
    public Sh2Prefetch.Sh2DrcContext drcContext;
    public PollerCtx poller = UNKNOWN_POLLER;
    public int blockFlags;
    public PollType pollType = PollType.UNKNOWN;
    public Runnable stage2Drc;
    public int hashCodeWords;
    private static final boolean verbose = false;

    static {
        BufferUtil.assertPowerOf2Minus1("OPT_THRESHOLD2", OPT_THRESHOLD2);
        INVALID_BLOCK.setFlag(VALID_FLAG, false);
        assert POLLER_ACTIVATE_LIMIT >= 3;
    }

    public Sh2Block(int pc, BufferUtil.CpuDeviceAccess cpu) {
        prefetchPc = pc;
        blockFlags = (cpu.ordinal() | VALID_FLAG);
    }

    public final void runBlock(Sh2 sh2, Sh2MMREG sm) {
        assert prefetchPc != -1;
        assert (blockFlags & VALID_FLAG) > 0;
        if (stage2Drc != null) {
            stage2Drc.run();
            return;
        }
        runInterpreter(sh2, sm, drcContext.sh2Ctx);
    }

    protected final void runInterpreter(Sh2 sh2, Sh2MMREG sm, Sh2Context ctx) {
        Sh2Prefetcher.Sh2BlockUnit prev = curr;
        addHit();
        int startCycle = ctx.cycles;
        do {
            sh2.printDebugMaybe(curr.opcode);
            curr.runnable.run();
            if (curr.inst.isBranchDelaySlot || curr.next == null) {
                break;
            }
            curr = curr.next;
        } while (true);
        cyclesConsumed = (startCycle - ctx.cycles) + Md32xRuntimeData.getCpuDelayExt();
        curr = prev;
    }

    public void addHit() {
        hits++;
        if (stage2Drc == null && ((hits + 1) & OPT_THRESHOLD2) == 0) {
            assert inst != null;
            if (verbose) LOG.info("{} HRC2 count: {}\n{}", "", th(hits), Sh2Helper.toListOfInst(this));
            stage2();
            if (Sh2Config.get().pollDetectEn) {
                Sh2DrcBlockOptimizer.pollDetector(this);
            }
        }
    }

    public void stage1(Sh2Prefetcher.Sh2BlockUnit[] ic) {
        assert inst == null;
        inst = ic;
        inst[0].next = inst.length > 1 ? inst[1] : null;
        inst[0].pc = prefetchPc;
        int lastIdx = ic.length - 1;
        for (int i = 1; i < inst.length - 1; i++) {
            inst[i].next = inst[i + 1];
            inst[i].pc = prefetchPc + (i << 1);
            assert !inst[i].inst.isBranch || inst[i].inst.isBranchDelaySlot;
        }
        Sh2Prefetcher.Sh2BlockUnit sbu = inst[lastIdx];
        sbu.pc = prefetchPc + (lastIdx << 1);
        curr = inst[0];
        assert sbu.pc != 0;
        //TODO fix prefetch
        assert inst.length >= (MAX_INST_LEN - 1) ||
                (sbu.inst.isBranch || (inst[Math.max(0, lastIdx - 1)].inst.isBranchDelaySlot && !sbu.inst.isBranch))
                || sbu.inst.isIllegal :
                th(sbu.pc) + "," + inst.length + "\n" + this;
    }

    public void stage2() {
        if (Sh2Config.get().drcEn) {
            assert drcContext != null;
            stage2Drc = Sh2BlockRecompiler.getInstance().createDrcClass(this, drcContext);
        }
    }

    public boolean isPollingBlock() {
        return pollType.ordinal() > NONE.ordinal();
    }

    public void setCacheFetch(boolean val) {
        setFlag(CACHE_FETCH_FLAG, val);
    }

    public void setNoJump(boolean val) {
        setFlag(NO_JUMP_FLAG, val);
    }

    public boolean isValid() {
        return (blockFlags & VALID_FLAG) > 0;
    }

    private void setFlag(int flag, boolean val) {
        blockFlags &= ~flag;
        blockFlags |= val ? flag : 0;
    }

    public boolean isNoJump() {
        return (blockFlags & NO_JUMP_FLAG) > 0;
    }

    public boolean isCacheFetch() {
        return (blockFlags & CACHE_FETCH_FLAG) > 0;
    }

    public void invalidate() {
        blockFlags &= ~VALID_FLAG;
        prefetchPc |= 1;
    }

    public void setValid() {
        blockFlags |= VALID_FLAG;
        prefetchPc &= ~1;
    }

    public BufferUtil.CpuDeviceAccess getCpu() {
        return BufferUtil.CpuDeviceAccess.cdaValues[blockFlags & CPU_FLAG];
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Sh2Block.class.getSimpleName() + "[", "]")
                .add("inst=" + Arrays.toString(inst))
                .add("curr=" + curr)
                .add("prefetchWords=" + Arrays.toString(prefetchWords))
                .add("prefetchPc=" + th(prefetchPc))
                .add("hits=" + hits)
                .add("start=" + th(start))
                .add("end=" + th(end))
                .add("pcMasked=" + th(pcMasked))
                .add("prefetchLenWords=" + prefetchLenWords)
                .add("fetchMemAccessDelay=" + fetchMemAccessDelay)
                .add("cyclesConsumed=" + cyclesConsumed)
                .add("fetchBuffer=" + fetchBuffer)
                .add("drcContext=" + drcContext)
                .add("blockFlags=" + blockFlags)
                .add("pollType=" + pollType)
                .add("stage2Drc=" + stage2Drc)
                .toString();
    }
}