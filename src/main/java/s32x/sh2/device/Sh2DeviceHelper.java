package s32x.sh2.device;

import omegadrive.util.BufferUtil;
import s32x.Sh2MMREG;
import s32x.bus.Sh2Bus;
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

    public static Sh2DeviceContext createDevices(BufferUtil.CpuDeviceAccess cpu, Sh2LaunchContext ctx) {
        return createDevices(cpu, ctx.memory, ctx.memory.getSh2MMREGS(cpu));
    }

    public static Sh2DeviceContext createDevices(BufferUtil.CpuDeviceAccess cpu, Sh2Bus memory, Sh2MMREG sh2Regs) {
        Sh2DeviceContext ctx = new Sh2DeviceContext();
        ctx.cpu = cpu;
        ctx.sh2MMREG = sh2Regs;
        ctx.intC = IntControlImpl.createInstance(cpu, sh2Regs.getRegs());
        ctx.dmaC = new DmaC(cpu, ctx.intC, memory, sh2Regs.getRegs());
        ctx.sci = new SerialCommInterface(cpu, ctx.intC, sh2Regs.getRegs());
        ctx.divUnit = new DivUnit(cpu, ctx.intC, sh2Regs.getRegs());
        ctx.wdt = new WatchdogTimer(cpu, ctx.intC, sh2Regs.getRegs());
        ctx.frt = new FreeRunningTimer(cpu, ctx.intC, sh2Regs.getRegs());
        return ctx;
    }
}
