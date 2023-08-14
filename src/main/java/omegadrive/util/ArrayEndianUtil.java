package omegadrive.util;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class ArrayEndianUtil {

    public static final int MASK_16BIT = 0xFFFF;

    public static int getUInt32LE(byte... bytes) {
        int value = (bytes[0] & 0xFF);
        value = bytes.length > 1 ? value | ((bytes[1] & 0xFF) << 8) : value;
        value = bytes.length > 2 ? value | ((bytes[2] & 0xFF) << 16) : value;
        value = bytes.length > 3 ? value | ((bytes[3] & 0xFF) << 24) : value;
        return value;
    }

    public static int getUInt32LE(int... bytes) {
        int value = (bytes[0] & 0xFF);
        value = bytes.length > 1 ? value | ((bytes[1] & 0xFF) << 8) : value;
        value = bytes.length > 2 ? value | ((bytes[2] & 0xFF) << 16) : value;
        value = bytes.length > 3 ? value | ((bytes[3] & 0xFF) << 24) : value;
        return value;
    }

    /**
     * [0xA0][0x12] -> [0xA012]
     * [0x00][0xBB] -> [0x00BB]
     */
    public static void setWordFromBytesBE(byte[] src, int[] data, int srcIndex, int destIndex) {
        data[destIndex] = (((src[srcIndex] & 0xFF) << 8) | (src[srcIndex + 1] & 0xFF)) & MASK_16BIT;
    }

    public static void setUInt32LE(int value, int[] data, int startIndex) {
        data[startIndex + 3] = (value >> 24) & 0xFF;
        data[startIndex + 2] = (value >> 16) & 0xFF;
        data[startIndex + 1] = (value >> 8) & 0xFF;
        data[startIndex] = (value) & 0xFF;
    }

    public static void setUInt32LE(int value, byte[] data, int startIndex) {
        data[startIndex + 3] = (byte) ((value >> 24) & 0xFF);
        data[startIndex + 2] = (byte) ((value >> 16) & 0xFF);
        data[startIndex + 1] = (byte) ((value >> 8) & 0xFF);
        data[startIndex] = (byte) ((value) & 0xFF);
    }

    public static int[] toUnsignedIntArray(byte[] bytes) {
        int[] data = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = bytes[i] & 0xFF;
        }
        return data;
    }

    public static int[] toSignedIntArray(byte[] bytes) {
        int[] data = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = bytes[i];
        }
        return data;
    }

    /*
     * NOTE: input int[] must contain values representable as bytes
     */
    public static byte[] unsignedToByteArray(int[] bytes) {
        return toByteArray(bytes, false);
    }

    public static byte[] signedToByteArray(int[] bytes) {
        return toByteArray(bytes, true);
    }

    private static byte[] toByteArray(int[] bytes, boolean signed) {
        int min = signed ? Byte.MIN_VALUE : 0;
        int max = signed ? Byte.MAX_VALUE : 0xFF;
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) (bytes[i] & 0xFF);
            if (bytes[i] < min || bytes[i] > max) {
                throw new IllegalArgumentException("Invalid value at pos " + i + ", it doesn't represent a byte: " + bytes[i]);
            }
        }
        return data;
    }

    // addr 0 -> [8bit][8bit] <- addr 1
    public static int getByteInWordBE(int word, int bytePos) {
        return (word >> (((bytePos + 1) & 1) << 3)) & 0xFF;
    }

    // addr 0 -> [8bit][8bit] <- addr 1
    public static int setByteInWordBE(int word, int byteVal, int bytePos) {
        final int shift = ((bytePos + 1) & 1 << 3);
        return (word & ~(0xFF << shift)) | (byteVal << shift);
    }
}
