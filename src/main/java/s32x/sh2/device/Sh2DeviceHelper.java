package s32x.sh2.device;

import omegadrive.util.BufferUtil;
import s32x.Sh2MMREG;
import s32x.bus.Sh2Bus;
import s32x.sh2.Sh2Context;
import s32x.util.MarsLauncherHelper.Sh2LaunchContext;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class Sh2DeviceHelper {

    public enum Sh2DeviceType {NONE, UBC, FRT, BSC, DMA, INTC, DIV, SCI, WDT}

    public static class Sh2DeviceContext {
        public BufferUtil.CpuDeviceAccess cpu;
        public IntControl intC;
        public DmaC dmaC;
        public SerialCommInterface sci;
        public DivUnit divUnit;
        public WatchdogTimer wdt;
        public FreeRunningTimer frt;
        public Sh2MMREG sh2MMREG;
    }

    public static Sh2DeviceContext createDevices(Sh2Context sh2Context, Sh2LaunchContext ctx) {
        return createDevices(sh2Context, ctx.memory, ctx.memory.getSh2MMREGS(sh2Context.cpuAccess));
    }

    public static Sh2DeviceContext createDevices(Sh2Context sh2Context, Sh2Bus memory, Sh2MMREG sh2Regs) {
        Sh2DeviceContext ctx = new Sh2DeviceContext();
        ctx.cpu = sh2Context.cpuAccess;
        ctx.sh2MMREG = sh2Regs;
        ctx.intC = IntControlImpl.createInstance(sh2Context, sh2Regs.getRegs());
        ctx.dmaC = new DmaC(ctx.cpu, ctx.intC, memory, sh2Regs.getRegs());
        ctx.sci = new SerialCommInterface(ctx.cpu, ctx.intC, sh2Regs.getRegs());
        ctx.divUnit = new DivUnit(ctx.cpu, ctx.intC, sh2Regs.getRegs());
        ctx.wdt = new WatchdogTimer(ctx.cpu, ctx.intC, sh2Regs.getRegs());
        ctx.frt = new FreeRunningTimer(ctx.cpu, ctx.intC, sh2Regs.getRegs());
        return ctx;
    }
}
