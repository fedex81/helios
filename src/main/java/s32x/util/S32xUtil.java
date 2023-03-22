package s32x.util;

import com.google.common.math.IntMath;
import omegadrive.Device;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.slf4j.Logger;
import s32x.S32XMMREG.RegContext;
import s32x.dict.S32xDict;
import s32x.dict.S32xDict.RegSpecS32x;
import s32x.util.RegSpec.BytePosReg;

import java.nio.ByteBuffer;

import static omegadrive.util.Util.*;
import static omegadrive.vdp.model.BaseVdpProvider.H40;
import static s32x.dict.Sh2Dict.RegSpecSh2;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class S32xUtil {

    private static final Logger LOG = LogHelper.getLogger(S32xUtil.class.getSimpleName());

    public static final int[] EMPTY_INT_ARRAY = {};

    public static final boolean assertionsEnabled;

    static {
        boolean res = false;
        assert res = true;
        assertionsEnabled = res;
    }

    public static final boolean ENFORCE_FM_BIT_ON_READS = assertionsEnabled;

    public interface StepDevice extends Device {
        default void step(int cycles) {
        } //DO NOTHING
    }

    public interface Sh2Device extends StepDevice {
        void write(RegSpecSh2 regSpec, int pos, int value, Size size);

        int read(RegSpecSh2 regSpec, int reg, Size size);

        default void write(RegSpecSh2 regSpec, int value, Size size) {
            write(regSpec, regSpec.addr, value, size);
        }

        default int read(RegSpecSh2 regSpec, Size size) {
            return read(regSpec, regSpec.addr, size);
        }
    }

    public static void writeBuffers(ByteBuffer b1, ByteBuffer b2, int pos, int value, Size size) {
        writeBufferRaw(b1, pos, value, size);
        writeBufferRaw(b2, pos, value, size);
    }

    public static int readBufferRegLong(ByteBuffer b, RegSpecSh2 r) {
        return Util.readBufferLong(b, r.addr & RegSpecSh2.REG_MASK);
    }

    public static void writeBufferLong(ByteBuffer b, RegSpecSh2 r, int value) {
        r.regSpec.write(b, value, Size.LONG);
    }

    public static void writeBufferLong(ByteBuffer b, int pos, int value) {
        INT_BYTEBUF_HANDLE.set(b, pos, value);
    }

    public static void writeBuffersLong(ByteBuffer b, RegSpecSh2 r, RegSpecSh2 r1, RegSpecSh2 r2, int value) {
        r.regSpec.write(b, value, Size.LONG);
        r1.regSpec.write(b, value, Size.LONG);
        r2.regSpec.write(b, value, Size.LONG);
    }

    public static void writeBuffersLong(ByteBuffer b, RegSpecSh2 r, RegSpecSh2 r1, int value) {
        r.regSpec.write(b, value, Size.LONG);
        r1.regSpec.write(b, value, Size.LONG);
    }

    public static void writeRegBuffer(RegSpec r, ByteBuffer b, int value, Size size) {
        r.write(b, BytePosReg.BYTE_0, value, size);
    }

    public static void writeRegBuffer(RegSpec r, ByteBuffer b, int addr, int value, Size size) {
        r.write(b, addr, value, size);
    }

    public static void writeRegBuffer(RegSpecSh2 r, ByteBuffer b, int value, Size size) {
        writeRegBuffer(r.regSpec, b, value, size);
    }

    public static boolean writeBufferRaw(ByteBuffer b, int pos, int value, Size size) {
        boolean changed = false;
        if (size == Size.WORD) {
            if ((short) SHORT_BYTEBUF_HANDLE.get(b, pos) != value) {
                SHORT_BYTEBUF_HANDLE.set(b, pos, (short) value);
                changed = true;
            }
        } else if (size == Size.LONG) {
            if ((int) INT_BYTEBUF_HANDLE.get(b, pos) != value) {
                INT_BYTEBUF_HANDLE.set(b, pos, value);
                changed = true;
            }
        } else if (size == Size.BYTE) {
            if (b.get(pos) != value) {
                b.put(pos, (byte) value);
                changed = true;
            }
        }
        return changed;
    }

    public static int readBuffer(ByteBuffer b, int pos, Size size) {
        return switch (size) {
            case WORD -> readBufferWord(b, pos);
            case LONG -> readBufferLong(b, pos);
            case BYTE -> readBufferByte(b, pos);
            default -> {
                LOG.error("Unexpected size: " + size);
                yield size.getMask();
            }
        };
    }

    public static void setBit(ByteBuffer b1, ByteBuffer b2, int pos, int bitPos, int bitValue, Size size) {
        setBit(b1, pos, bitPos, bitValue, size);
        setBit(b2, pos, bitPos, bitValue, size);
    }

    public static int getBitFromByte(byte b, int bitPos) {
        return (b >> bitPos) & 1;
    }

    /**
     * @return true - has changed, false - otherwise
     */
    public static boolean setBit(ByteBuffer b, int pos, int bitPos, int bitValue, Size size) {
        int val = readBuffer(b, pos, size);
        //clear bit and then set it
        int newVal = (val & ~(1 << bitPos)) | (bitValue << bitPos);
        if (val != newVal) {
            writeBufferRaw(b, pos, newVal, size);
            return true;
        }
        return false;
    }

    /**
     * @return the new value
     */
    public static int setBitVal(ByteBuffer b, int pos, int bitPos, int bitValue, Size size) {
        int val = readBuffer(b, pos, size);
        //clear bit and then set it
        int newVal = (val & ~(1 << bitPos)) | (bitValue << bitPos);
        if (val != newVal) {
            writeBufferRaw(b, pos, newVal, size);
            return newVal;
        }
        return val;
    }

    public static int readWordFromBuffer(RegContext ctx, RegSpecS32x reg) {
        return readBufferReg(ctx, reg, reg.addr, Size.WORD);
    }

    public static int readBufferReg(RegContext ctx, RegSpecS32x reg, int address, Size size) {
        address &= reg.getAddrMask();
        if (reg.deviceType == S32xDict.S32xRegType.VDP) {
            return readBuffer(ctx.vdpRegs, address, size);
        }
        switch (reg.regCpuType) {
            case REG_BOTH:
                assert readBuffer(ctx.sysRegsMd, address, size) == readBuffer(ctx.sysRegsSh2, address, size);
                //fall-through
            case REG_MD:
                return readBuffer(ctx.sysRegsMd, address, size);
            case REG_SH2:
                return readBuffer(ctx.sysRegsSh2, address, size);
        }
        LOG.error("Unable to read buffer: {}, addr: {} {}", reg.getName(), th(address), size);
        return size.getMask();
    }

    public static boolean setBitRegFromWord(ByteBuffer bb, RegSpecS32x reg, int pos, int value) {
        return S32xUtil.setBit(bb, reg.addr, pos, value, Size.WORD);
    }

    public static void setBitReg(RegContext rc, RegSpecS32x reg, int address, int pos, int value, Size size) {
        address &= reg.getAddrMask();
        if (reg.deviceType == S32xDict.S32xRegType.VDP) {
            S32xUtil.setBit(rc.vdpRegs, address, pos, value, size);
            return;
        }
        switch (reg.regCpuType) {
            case REG_BOTH -> S32xUtil.setBit(rc.sysRegsMd, rc.sysRegsSh2, address, pos, value, size);
            case REG_MD -> S32xUtil.setBit(rc.sysRegsMd, address, pos, value, size);
            case REG_SH2 -> S32xUtil.setBit(rc.sysRegsSh2, address, pos, value, size);
            default ->
                    LOG.error("Unable to setBit: {}, addr: {}, value: {} {}", reg.getName(), th(address), th(value), size);
        }
    }

    public static void writeBufferReg(RegContext rc, RegSpecS32x reg, int address, int value, Size size) {
        address &= reg.getAddrMask();
        if (reg.deviceType == S32xDict.S32xRegType.VDP) {
            reg.regSpec.write(rc.vdpRegs, address, value, size);
            return;
        }
        switch (reg.regCpuType) {
            case REG_BOTH -> {
                writeRegBuffer(reg.regSpec, rc.sysRegsMd, address, value, size);
                writeRegBuffer(reg.regSpec, rc.sysRegsSh2, address, value, size);
            }
            case REG_MD -> writeRegBuffer(reg.regSpec, rc.sysRegsMd, address, value, size);
            case REG_SH2 -> writeRegBuffer(reg.regSpec, rc.sysRegsSh2, address, value, size);
            default ->
                    LOG.error("Unable to write buffer: {}, addr: {}, value: {} {}", reg.getName(), th(address), th(value), size);
        }
    }

    @Deprecated
    public static void writeBufferRegOld(RegContext rc, RegSpecS32x reg, int address, int value, Size size) {
        address &= reg.getAddrMask();
        if (reg.deviceType == S32xDict.S32xRegType.VDP) {
            writeBufferRaw(rc.vdpRegs, address, value, size);
            return;
        }
        switch (reg.regCpuType) {
            case REG_BOTH -> writeBuffers(rc.sysRegsMd, rc.sysRegsSh2, address, value, size);
            case REG_MD -> writeBufferRaw(rc.sysRegsMd, address, value, size);
            case REG_SH2 -> writeBufferRaw(rc.sysRegsSh2, address, value, size);
            default ->
                    LOG.error("Unable to write buffer: {}, addr: {}, value: {} {}", reg.getName(), th(address), th(value), size);
        }
    }

    public static String toHexString(ByteBuffer b, int pos, Size size) {
        return Integer.toHexString(readBuffer(b, pos, size));
    }

    public static void assertPowerOf2Minus1(String name, int value) {
        if (!IntMath.isPowerOfTwo(value + 1)) {
            LOG.error(name + " should be a (powerOf2 - 1), ie. 0xFF, actual: " + th(value - 1));
        }
        assert IntMath.isPowerOfTwo(value + 1) :
                name + " should be a (powerOf2 - 1), ie. 0xFF, actual: " + th(value - 1);
    }

    //duplicate every 4th pixel, 5*256/4 = 320
    public static void vidH32StretchToH40(VideoMode srcv, int[] src, int[] dest) {
        assert dest.length > src.length;
        assert srcv.getDimension().height * srcv.getDimension().width == src.length;
        assert srcv.getDimension().height * H40 == dest.length;
        int k = 0;
        final int h = srcv.getDimension().height;
        final int w = srcv.getDimension().width;
        for (int i = 0; i < h; i++) {
            int base = i * w;
            for (int j = 0; j < w; j += 4) {
                dest[k++] = src[base + j];
                dest[k++] = src[base + j + 1];
                dest[k++] = src[base + j + 2];
                dest[k++] = src[base + j + 3];
                dest[k++] = src[base + j + 3];
            }
        }
    }

    public static int hashCode(int a[], int len) {
        if (a == null)
            return 0;

        int result = 1;
        for (int i = 0; i < len; i++) {
            result = 31 * result + a[i];
        }

        return result;
    }

    public enum S32xRegSide {MD, SH2}

    public enum CpuDeviceAccess {
        MASTER, SLAVE, M68K, Z80;

        public final S32xRegSide regSide;

        CpuDeviceAccess() {
            this.regSide = this.ordinal() < 2 ? S32xRegSide.SH2 : S32xRegSide.MD;
        }

        public static final CpuDeviceAccess[] cdaValues = CpuDeviceAccess.values();
    }
}