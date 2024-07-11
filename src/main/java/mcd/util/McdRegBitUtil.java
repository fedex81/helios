package mcd.util;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdDict.SharedBitDef;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.Size;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.getBitFromByte;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdRegBitUtil {

    public static void setSharedBits(MegaCdMemoryContext ctx, CpuDeviceAccess cpu, int byteVal, SharedBitDef... bitDef) {
        Arrays.stream(bitDef).forEach(bd -> setSharedBit(ctx, cpu, byteVal, bd));
    }

    /**
     * Given the byte value, only set the bit at bitPos as defined in SharedBit
     */
    public static void setSharedBit(MegaCdMemoryContext ctx, CpuDeviceAccess cpu, int byteVal, SharedBitDef bitDef) {
        assert cpu == M68K || cpu == SUB_M68K;
        CpuDeviceAccess other = cpu == M68K ? SUB_M68K : M68K;
        ByteBuffer regs = ctx.getGateSysRegs(other);
        setBit(regs, bitDef.getRegBytePos(), bitDef.getBitPos(), (byteVal >> bitDef.getBitPos()) & 1, Size.BYTE);
    }

    public static void setBitInternal(ByteBuffer buff, int regAddr, int bitPos, int data) {
        assert bitPos < 8;
        setBitVal(buff, regAddr, bitPos, (data >> bitPos) & 1, Size.BYTE);
    }

    public static void setSharedBitInternal(MegaCdMemoryContext ctx, CpuDeviceAccess cpu, SharedBitDef bitDef, int data) {
        setBitInternal(ctx.getGateSysRegs(cpu), bitDef.getRegBytePos(), bitDef.getBitPos(), data);
        setSharedBit(ctx, cpu, data, bitDef);
    }

    public static void setBitDefInternal(MegaCdMemoryContext ctx, CpuDeviceAccess cpu,
                                         MegaCdDict.BitRegisterDef def, int data) {
        assert !(def instanceof SharedBitDef);
        assert def.getCpu() == cpu;
        setBitInternal(ctx.getGateSysRegs(cpu), def.getRegBytePos(), def.getBitPos(), data);
    }

    public static int getInvertedBitFromByte(byte b, int bitPos) {
        return ~getBitFromByte(b, bitPos) & 1;
    }
}
