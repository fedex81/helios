package s32x.util;

import omegadrive.util.Size;

import java.nio.ByteBuffer;

import static s32x.util.RegSpec.BytePosReg.BYTE_0;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class RegSpec {

    public final static RegSpec INVALID_REG = new RegSpec("INVALID", -1, 0, Size.LONG);

    public final String name;
    public final int fullAddr, bufferAddr, addrMask;
    public final Size regSize;

    private final int sizeIndex;
    public final int writableBitMask, preserveBitMask;

    public enum BytePosReg {
        BYTE_0, BYTE_1, BYTE_2, BYTE_3;
    }

    private static final BytePosReg[] bprVals = BytePosReg.values();

    public RegSpec(String name, int fullAddr, int addrMask, Size size) {
        this(name, fullAddr, addrMask, size.getMask(), 0, size);
    }

    public RegSpec(String name, int fullAddr, int addrMask, int writableBitMask, int preserveBitMask, Size size) {
        this.name = name;
        this.fullAddr = fullAddr;
        this.bufferAddr = fullAddr & addrMask;
        this.addrMask = addrMask;
        this.regSize = size;
        this.sizeIndex = size.getByteSize() - 1; //(1,2,4) - 1 -> (0,1,3)
        this.writableBitMask = writableBitMask & size.getMask();
        this.preserveBitMask = preserveBitMask & size.getMask();
    }

    public boolean write(ByteBuffer b, int value, Size size) {
        return write(b, BYTE_0, value, size);
    }

    public boolean write(ByteBuffer b, int regPos, int value, Size size) {
        return write(b, bprVals[regPos - bufferAddr], value, size);
    }

    public boolean write(ByteBuffer b, BytePosReg bytePosReg, int value, Size size) {
        int bytePos = bytePosReg.ordinal();
        assert bytePos <= sizeIndex;
        assert this != INVALID_REG;
        return switch (size) {
            case WORD -> {
                assert regSize != Size.BYTE;
                assert (bytePos & 1) == 0;
                int shift = (sizeIndex - 1 - bytePos) << 3;
                boolean res = writeByteWithinWL(b, bytePos, value << shift);
                res |= writeByteWithinWL(b, bytePos + 1, value << shift);
                yield res;
            }
            case LONG -> {
                assert regSize == Size.LONG;
                assert bytePos == 0;
                boolean res = writeByteWithinWL(b, 0, value);
                res |= writeByteWithinWL(b, 1, value);
                res |= writeByteWithinWL(b, 2, value);
                res |= writeByteWithinWL(b, 3, value);
                yield res;
            }
            case BYTE -> writeByteRaw(b, bytePos & sizeIndex, value);
        };
    }

    private boolean writeByteRaw(ByteBuffer b, int pos, int value) {
        return writeByteWithinWL(b, pos, value << ((sizeIndex - pos) << 3));
    }

    public boolean writeByteWithinWL(ByteBuffer b, int pos, int value) {
        assert pos <= sizeIndex;
        int shift = (sizeIndex - pos) << 3;
        int byteMask = (writableBitMask >> shift) & 0xFF;
        int orMask = (preserveBitMask >> shift) & 0xFF;
        if (byteMask == 0) {
            return false;
        }
        byte v = (byte) (((value >> shift) & byteMask) | orMask);
        if (b.get(bufferAddr + pos) != v) {
            b.put(bufferAddr + pos, v);
            return true;
        }
        return false;
    }
}
