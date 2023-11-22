package s32x.sh2.drc;

import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import s32x.Sh2MMREG;
import s32x.bus.Sh2MemoryParallel;
import s32x.sh2.Sh2;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Helper;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.util.Md32xRuntimeData;
import s32x.util.S32xUtil;

import java.util.Arrays;

import static s32x.sh2.drc.Sh2DrcBlockOptimizer.handlePoll;

/**
 * Test only
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Sh2BlockParallel extends Sh2Block {
    private static final Logger LOG = LogHelper.getLogger(Sh2BlockParallel.class.getSimpleName());
    private Sh2Context[] cloneCtxs;
    private static final boolean verbose = false;

    public Sh2BlockParallel(int pc, S32xUtil.CpuDeviceAccess cpu) {
        super(pc, cpu);
    }

    //NOTE uncomment for testing
//    @Override
//    public void runBlock(Sh2 sh2, Sh2MMREG sm) {
//        runBlockParallel(sh2, sm);
//    }

    private void runBlockParallel(Sh2 sh2, Sh2MMREG sm) {
        assert prefetchPc != -1;
        assert (blockFlags & VALID_FLAG) > 0;
        if (stage2Drc != null) {
            if (Sh2Config.get().pollDetectEn) {
                handlePoll(this);
            }
            prepareInterpreterParallel();
            stage2Drc.run();
            runInterpreterParallel(sh2, sm);
            return;
        }
        ((Sh2MemoryParallel) drcContext.memory).setActive(false);
        runInterpreter(sh2, sm, drcContext.sh2Ctx);
    }

    private void prepareInterpreterParallel() {
        if (cloneCtxs == null) {
            cloneCtxs = new Sh2Context[]{new Sh2Context(S32xUtil.CpuDeviceAccess.MASTER, false), new Sh2Context(S32xUtil.CpuDeviceAccess.SLAVE, false)};
        }
        Sh2Context ctx = this.drcContext.sh2Ctx;
        Sh2Context cloneCtx = cloneCtxs[ctx.cpuAccess.ordinal()];
        cloneCtx.PC = ctx.PC;
        cloneCtx.delayPC = ctx.delayPC;
        cloneCtx.GBR = ctx.GBR;
        cloneCtx.MACL = ctx.MACL;
        cloneCtx.SR = ctx.SR;
        cloneCtx.MACH = ctx.MACH;
        cloneCtx.VBR = ctx.VBR;
        cloneCtx.PR = ctx.PR;
        cloneCtx.opcode = ctx.opcode;
        cloneCtx.fetchResult = ctx.fetchResult;
        cloneCtx.cycles = ctx.cycles;
        cloneCtx.cycles_ran = ctx.cycles_ran;
        System.arraycopy(ctx.registers, 0, cloneCtx.registers, 0, ctx.registers.length);
        Sh2MemoryParallel sp = ((Sh2MemoryParallel) drcContext.memory);
        sp.setActive(true);
    }

    private void runInterpreterParallel(Sh2 sh2, Sh2MMREG sm) {
        int delay = Md32xRuntimeData.getCpuDelayExt();
        Sh2MemoryParallel sp = ((Sh2MemoryParallel) drcContext.memory);
        sp.setReplayMode(true);
        Sh2Context ctx = this.drcContext.sh2Ctx;
        Sh2Context intCtx = cloneCtxs[ctx.cpuAccess.ordinal()];
        sh2.setCtx(intCtx);
        boolean log = false;
        try {
            runInterpreter(sh2, sm, intCtx);
        } catch (Exception | Error e) {
            LOG.error("", e);
            log = true;
        }
        sh2.setCtx(ctx);

        if (ctx.equals(intCtx)) {
            log |= !Arrays.equals(ctx.registers, intCtx.registers);
        } else {
            log = true;
        }
        if (log) {
            LOG.info("\ndrc: {}\nint: {}\n{}", ctx, intCtx, Sh2Helper.toListOfInst(this));
            throw new RuntimeException();
        }
        sp.setReplayMode(false);
        sp.clear();
        sp.setActive(false);
        Md32xRuntimeData.resetCpuDelayExt(delay);
    }
}