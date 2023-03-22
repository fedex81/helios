package s32x;

import s32x.sh2.Sh2Context;
import s32x.sh2.device.Sh2DeviceHelper;
import s32x.sh2.drc.Sh2Block;
import s32x.sh2.prefetch.Sh2Prefetch;
import s32x.util.S32xUtil;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class AsmExample implements Runnable {

    private int[] regs;
    private int[] opcodes;
    private int cycle, pc;
    private Sh2MMREG sh2MMREG;
    private Sh2Context sh2Context;

    public AsmExample(int[] registers, int[] prefetchWords, Sh2Context context) {
        this.regs = registers;
        this.opcodes = prefetchWords;
        cycle = pc = 0;
        sh2Context = context;
        sh2MMREG = context.devices.sh2MMREG;
    }

    @Override
    public void run() {
        this.regs[10] &= this.regs[11];
    }

    private void createClassBinary(Sh2Block block, Sh2Prefetch.Sh2DrcContext drcCtx, String blockClass) {
        this.regs = drcCtx.sh2Ctx.registers;
        this.opcodes = block.prefetchWords;
    }

    public static void main(String[] args) {
        Sh2Context sh2Context = new Sh2Context(S32xUtil.CpuDeviceAccess.MASTER);
        Sh2Block block = new Sh2Block(0, S32xUtil.CpuDeviceAccess.MASTER);
        Sh2Context context = new Sh2Context(S32xUtil.CpuDeviceAccess.MASTER);
        context.devices = new Sh2DeviceHelper.Sh2DeviceContext();
        AsmExample b = new AsmExample(sh2Context.registers, block.prefetchWords, context);
//        b.run();
    }
}
