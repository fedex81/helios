package s32x.sh2;

import com.google.common.base.Objects;
import omegadrive.Device;
import omegadrive.util.Util;
import s32x.savestate.Gs32xStateHandler;
import s32x.sh2.Sh2.FetchResult;
import s32x.sh2.device.Sh2DeviceHelper.Sh2DeviceContext;
import s32x.sh2.drc.Sh2Block;
import s32x.util.S32xUtil;

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

    final static int NUM_REG = 16;
    public static int burstCycles = 1;

    /* System Registers */
    public final int registers[];

    public int GBR, VBR, SR;
    public int MACH, MACL, PR;
    public int PC;

    public int opcode, delayPC;

    /*
     * defines the number of cycles we can ran before stopping the interpreter
     */
    public int cycles;
    public int cycles_ran;

    public final S32xUtil.CpuDeviceAccess cpuAccess;
    public final String sh2TypeCode;
    public boolean delaySlot;
    public final boolean debug;
    public FetchResult fetchResult;

    public transient Sh2DeviceContext devices;

    public Sh2Context(S32xUtil.CpuDeviceAccess cpuAccess) {
        this(cpuAccess, false);
    }

    public Sh2Context(S32xUtil.CpuDeviceAccess cpuAccess, boolean debug) {
        this.registers = new int[NUM_REG];
        this.cpuAccess = cpuAccess;
        this.sh2TypeCode = cpuAccess.name().substring(0, 1);
        this.fetchResult = new FetchResult();
        this.fetchResult.block = Sh2Block.INVALID_BLOCK;
        this.debug = debug;
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
        Serializable s = Util.deserializeObject(buffer.array(), 0, buffer.capacity());
        assert s instanceof Sh2Context;
        loadContext((Sh2Context) s);
    }

    public void loadContext(Sh2Context ctx) {
        assert ctx.cpuAccess == cpuAccess;
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
        fetchResult.block = Sh2Block.INVALID_BLOCK;
        fetchResult.pc = 0;
        fetchResult.opcode = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sh2Context that = (Sh2Context) o;
        return GBR == that.GBR && VBR == that.VBR && SR == that.SR && MACH == that.MACH && MACL == that.MACL &&
                PR == that.PR && PC == that.PC &&
                delaySlot == that.delaySlot && cpuAccess == that.cpuAccess &&
                Objects.equal(sh2TypeCode, that.sh2TypeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(GBR, VBR, SR, MACH, MACL, PR, PC, cpuAccess, sh2TypeCode, delaySlot);
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
                .add("sh2TypeCode='" + sh2TypeCode + "'")
                .add("delaySlot=" + delaySlot)
                .add("debug=" + debug)
                .add("fetchResult=" + fetchResult)
                .toString();
    }
}
