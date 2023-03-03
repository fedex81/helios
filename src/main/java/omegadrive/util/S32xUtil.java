package omegadrive.util;

import com.google.common.math.IntMath;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static omegadrive.util.Util.th;
import static omegadrive.vdp.model.BaseVdpProvider.H40;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
//TODO remove
public class S32xUtil {

    private static final Logger LOG = LogHelper.getLogger(S32xUtil.class.getSimpleName());

    public static final int[] EMPTY_INT_ARRAY = {};

    public static final boolean assertionsEnabled;

    static {
        boolean res = false;
        assert res = true;
        assertionsEnabled = res;
    }

    public static void writeBuffers(ByteBuffer b1, ByteBuffer b2, int pos, int value, Size size) {
        writeBuffer(b1, pos, value, size);
        writeBuffer(b2, pos, value, size);
    }

    public static final VarHandle INT_BYTEBUF_HANDLE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle SHORT_BYTEBUF_HANDLE = MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    public static boolean writeBuffer(ByteBuffer b, int pos, int value, Size size) {
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
        switch (size) {
            case WORD:
                assert (pos & 1) == 0;
                return (int) SHORT_BYTEBUF_HANDLE.get(b, pos);
            case LONG:
                assert (pos & 1) == 0;
                return (int) INT_BYTEBUF_HANDLE.get(b, pos);
            case BYTE:
                return b.get(pos);
            default:
                System.err.println("Unsupported size: " + size);
                return 0xFF;
        }
    }

    public static int readBufferByte(ByteBuffer b, int pos) {
        return b.get(pos);
    }

    public static int readBufferWord(ByteBuffer b, int pos) {
        assert (pos & 1) == 0;
        return (int) SHORT_BYTEBUF_HANDLE.get(b, pos);
    }

    public static int readBufferLong(ByteBuffer b, int pos) {
        assert (pos & 1) == 0;
        return (int) INT_BYTEBUF_HANDLE.get(b, pos);
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
            writeBuffer(b, pos, newVal, size);
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
            writeBuffer(b, pos, newVal, size);
            return newVal;
        }
        return val;
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
}