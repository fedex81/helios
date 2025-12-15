package s32x.sh2;

import omegadrive.Device;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.Util;
import s32x.savestate.Gs32xStateHandler;
import s32x.sh2.Sh2Helper.FetchResult;
import s32x.sh2.device.Sh2DeviceHelper.Sh2DeviceContext;
import s32x.sh2.drc.Sh2Block;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.StringJoiner;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class Sh2Context implements Device, Serializable {

    @Serial
    private static final long serialVersionUID = 2187364810718100709L;

    final static int NUM_REG = 16;

    /* System Registers */
    public final int[] registers;

    public int GBR, VBR, SR;
    public int MACH, MACL, PR;
    public int PC;

    public int opcode, delayPC;

    /*
     * defines the number of cycles we can run before stopping the interpreter
     */
    public int cycles;
    public int cycles_ran;

    public transient CpuDeviceAccess cpuAccess;
    public boolean delaySlot;
    public final boolean debug;
    public int burstCycles;
    public transient FetchResult fetchResult;

    public transient Sh2DeviceContext devices;

    public final String sh2ShortCode;

    public boolean checkInterrupt;

    public Sh2Context(CpuDeviceAccess cpuAccess) {
        this(cpuAccess, Sh2Helper.Sh2Config.DEFAULT_SH2_CYCLES, false);
    }

    public Sh2Context(CpuDeviceAccess cpuAccess, int burstCycles) {
        this(cpuAccess, burstCycles, false);
    }

    public Sh2Context(CpuDeviceAccess cpuAccess, int burstCycles, boolean debug) {
        this.registers = new int[NUM_REG];
        this.cpuAccess = cpuAccess;
        this.sh2ShortCode = cpuAccess.cpuShortCode;
        this.fetchResult = new FetchResult();
        this.fetchResult.block = Sh2Block.INVALID_BLOCK;
        this.debug = debug;
        this.burstCycles = burstCycles;
        Gs32xStateHandler.addDevice(this);
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        Device.super.saveContext(buffer);
        buffer.put(Util.serializeObject(this));
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        Device.super.loadContext(buffer);
        Serializable s = Util.deserializeObject(buffer);
        assert s instanceof Sh2Context;
        loadContext((Sh2Context) s);
    }

    public void loadContext(Sh2Context ctx) {
        assert ctx.sh2ShortCode.equals(sh2ShortCode);
        cpuAccess = CpuDeviceAccess.fromCpuCode(ctx.sh2ShortCode);
        System.arraycopy(ctx.registers, 0, registers, 0, registers.length);
        PC = ctx.PC;
        opcode = ctx.opcode;
        delaySlot = ctx.delaySlot;
        delayPC = ctx.delayPC;
        GBR = ctx.GBR;
        VBR = ctx.VBR;
        SR = ctx.SR;
        MACH = ctx.MACH;
        MACL = ctx.MACL;
        PR = ctx.PR;
        cycles = ctx.cycles;
        cycles_ran = ctx.cycles_ran;
        checkInterrupt = ctx.checkInterrupt;
        burstCycles = ctx.burstCycles;
        //invalidate on load
        fetchResult.block = Sh2Block.INVALID_BLOCK;
        fetchResult.pc = 0;
        fetchResult.opcode = 0;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Sh2Context.class.getSimpleName() + "[", "]")
                .add("registers=" + Arrays.toString(registers))
                .add("GBR=" + GBR)
                .add("VBR=" + VBR)
                .add("SR=" + SR)
                .add("MACH=" + MACH)
                .add("MACL=" + MACL)
                .add("PR=" + PR)
                .add("PC=" + PC)
                .add("opcode=" + opcode)
                .add("delayPC=" + delayPC)
                .add("cycles=" + cycles)
                .add("cycles_ran=" + cycles_ran)
                .add("cpuAccess=" + cpuAccess)
                .add("delaySlot=" + delaySlot)
                .add("debug=" + debug)
                .add("burstCycles=" + burstCycles)
                .add("fetchResult=" + fetchResult)
                .add("sh2ShortCode='" + sh2ShortCode + "'")
                .add("checkInterrupt=" + checkInterrupt)
                .toString();
    }
}
