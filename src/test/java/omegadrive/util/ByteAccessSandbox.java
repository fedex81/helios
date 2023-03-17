package omegadrive.util;


import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static omegadrive.util.Util.*;

/**
 * Sandbox to verify perf: byte[] access vs varHandles vs unsafe
 * As of jdk 17 byte[] perf is similar or better than the alternatives.
 * <p>
 * I consider write access to be not worth optimizing (verify?)
 * <p>
 * TODO add foreign memory api
 */
public class ByteAccessSandbox {

    private static final Unsafe unsafe;

    static {
        Field f = null;
        Unsafe u = null;
        try {
            f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (Unsafe) f.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        unsafe = u;
    }

    private static final long BYTE_ARRAY_BASE_OFFSET = unsafe.arrayBaseOffset(byte[].class);

    public static int readDataUnsafe(byte[] src, Size size, int address) {
        int res = switch (size) {
            case WORD -> ByteAccessSandbox.getShort(src, address) & 0xFFFF;
            case LONG -> ByteAccessSandbox.getInt(src, address);
            case BYTE -> ByteAccessSandbox.getByte(src, address);
        };
        assert res == readData(src, size, address) : th(res) + "," + th(readData(src, size, address));
        return res;
    }

    public static int readData(byte[] src, Size size, int address) {
        return switch (size) {
            case WORD -> ((src[address] & 0xFF) << 8) | (src[address + 1] & 0xFF);
            case LONG -> ((src[address] & 0xFF) << 24) | (src[address + 1] & 0xFF) << 16 |
                    (src[address + 2] & 0xFF) << 8 | (src[address + 3] & 0xFF);
            case BYTE -> src[address];
        };
    }

    /**
     * NOTE: jdk17, this seems equivalent (tiny bit slower) to the array unpacking below
     */
    public static int readDataVarHandle(byte[] src, Size size, int address) {
        return switch (size) {
            //perf: needs to cast to short (not int)
            case WORD -> (short) SHORT_BYTEARR_HANDLE.get(src, address);
            case LONG -> (int) INT_BYTEARR_HANDLE.get(src, address);
            case BYTE -> src[address];
        };
    }

    public static int readBufferWord(ByteBuffer b, int pos) {
        assert (pos & 1) == 0;
        //perf: needs to cast to short (not int)
        return ByteAccessSandbox.getShort(b, pos);
    }

    public static byte getByte(byte[] b, int pos) {
        return unsafe.getByte(b, BYTE_ARRAY_BASE_OFFSET + pos);
    }

    public static short getShort(byte[] b, int pos) {
        short s = unsafe.getShort(b, BYTE_ARRAY_BASE_OFFSET + pos);
        return Short.reverseBytes(s);
    }

    public static int getInt(byte[] b, int pos) {
        int i = unsafe.getInt(b, BYTE_ARRAY_BASE_OFFSET + pos);
        return Integer.reverseBytes(i);
    }

    public static byte getByte(ByteBuffer b, int pos) {
        return getByte(b.array(), pos);
    }

    public static short getShort(ByteBuffer b, int pos) {
        return getShort(b.array(), pos);
    }

    public static int getInt(ByteBuffer b, int pos) {
        return getInt(b.array(), pos);
    }
}