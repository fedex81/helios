package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;

/**
 * Federico Berti
 * <p>
 * Both 68k and Sh2s are big endian
 * <p>
 * Copyright 2021
 */
public class EndiannessTest {

    static int numBytes = 4;

    private ByteBuffer reset(int numEl) {
        return ByteBuffer.allocate(numEl);
    }

    @Test
    public void testLong() {
        ByteBuffer b = reset(numBytes);
        byte valB, actB;
        int valW, valL, actW, actL;

        //positive, pos 0
        b = reset(numBytes);
        valL = 0x11223344;
        S32xUtil.writeBufferRaw(b, 0, valL, Size.LONG);
        actL = S32xUtil.readBuffer(b, 0, Size.LONG);
        Assertions.assertEquals(valL, actL);

        actW = S32xUtil.readBuffer(b, 0, Size.WORD);
        valW = valL >> 16;
        Assertions.assertEquals(valW, actW);

        actW = S32xUtil.readBuffer(b, 2, Size.WORD);
        valW = valL & 0xFFFF;
        Assertions.assertEquals(valW, actW);

        actB = (byte) S32xUtil.readBuffer(b, 0, Size.BYTE);
        Assertions.assertEquals(valL >> 24, actB);

        actB = (byte) S32xUtil.readBuffer(b, 1, Size.BYTE);
        Assertions.assertEquals((valL >> 16) & 0xFF, actB);

        actB = (byte) S32xUtil.readBuffer(b, 2, Size.BYTE);
        Assertions.assertEquals((valL >> 8) & 0xFF, actB);

        actB = (byte) S32xUtil.readBuffer(b, 3, Size.BYTE);
        Assertions.assertEquals((valL >> 0) & 0xFF, actB);

        //negative, pos 0
        b = reset(numBytes);
        valL = -0x55667788;
        S32xUtil.writeBufferRaw(b, 0, valL, Size.LONG);
        actL = S32xUtil.readBuffer(b, 0, Size.LONG);
        Assertions.assertEquals(valL, actL);

        actW = S32xUtil.readBuffer(b, 0, Size.WORD);
        valW = (valL >> 16) & 0xFFFF;
        Assertions.assertEquals((short) valW, (short) actW);

        actW = S32xUtil.readBuffer(b, 2, Size.WORD);
        valW = valL & 0xFFFF;
        Assertions.assertEquals((short) valW, (short) actW);

        actB = (byte) S32xUtil.readBuffer(b, 0, Size.BYTE);
        byte expB = (byte) ((valL >> 24) & 0xFF);
        Assertions.assertEquals(expB, actB);

        actB = (byte) S32xUtil.readBuffer(b, 1, Size.BYTE);
        expB = (byte) ((valL >> 16) & 0xFF);
        Assertions.assertEquals(expB, actB);

        actB = (byte) S32xUtil.readBuffer(b, 2, Size.BYTE);
        expB = (byte) ((valL >> 8) & 0xFF);
        Assertions.assertEquals(expB, actB);

        actB = (byte) S32xUtil.readBuffer(b, 3, Size.BYTE);
        expB = (byte) ((valL >> 0) & 0xFF);
        Assertions.assertEquals(expB, actB);
    }

    @Test
    public void testByte() {
        int numByte = 4;
        ByteBuffer b = ByteBuffer.allocate(numByte);
        byte valB, actB;
        int valW, valL, actW, actL;

        //positive, pos 0
        b = ByteBuffer.allocate(numByte);
        valB = 0x11;
        S32xUtil.writeBufferRaw(b, 0, valB, Size.BYTE);
        actB = (byte) S32xUtil.readBuffer(b, 0, Size.BYTE);
        Assertions.assertEquals(valB, actB);

        actB = (byte) S32xUtil.readBuffer(b, 1, Size.BYTE);
        Assertions.assertEquals(valB >> 8, actB);

        actW = S32xUtil.readBuffer(b, 0, Size.WORD);
        valW = valB << 8;
        Assertions.assertEquals(valW, actW);

        actL = S32xUtil.readBuffer(b, 0, Size.LONG);
        valL = valB << 24;
        Assertions.assertEquals(valL, actL);

        //negative, pos 0
        b = ByteBuffer.allocate(numByte);
        valB = -0x11;
        S32xUtil.writeBufferRaw(b, 0, valB, Size.BYTE);
        actB = (byte) S32xUtil.readBuffer(b, 0, Size.BYTE);
        Assertions.assertEquals(valB, actB);

        actB = (byte) S32xUtil.readBuffer(b, 1, Size.BYTE);
        Assertions.assertEquals((valB << 8) & 0xFF, actB);

        actW = S32xUtil.readBuffer(b, 0, Size.WORD);
        valW = (valB << 8) & 0xFFFF;
        Assertions.assertEquals((short) valW, (short) actW);

        actL = S32xUtil.readBuffer(b, 0, Size.LONG);
        valL = valB << 24;
        Assertions.assertEquals(valL, actL);

        //positive, pos 1
        b = ByteBuffer.allocate(numByte);
        valB = 0x22;
        S32xUtil.writeBufferRaw(b, 1, valB, Size.BYTE);
        actB = (byte) S32xUtil.readBuffer(b, 1, Size.BYTE);
        Assertions.assertEquals(valB, actB);

        actB = (byte) S32xUtil.readBuffer(b, 0, Size.BYTE);
        Assertions.assertEquals((valB << 8) & 0xFF, actB);

        actW = S32xUtil.readBuffer(b, 0, Size.WORD);
        valW = valB;
        Assertions.assertEquals(valW, actW);

        actL = S32xUtil.readBuffer(b, 0, Size.LONG);
        valL = valB << 16;
        Assertions.assertEquals(valL, actL);

        //negative, pos 1
        b = ByteBuffer.allocate(numByte);
        valB = -0x22;
        S32xUtil.writeBufferRaw(b, 1, valB, Size.BYTE);
        actB = (byte) S32xUtil.readBuffer(b, 1, Size.BYTE);
        Assertions.assertEquals(valB, actB);

        actB = (byte) S32xUtil.readBuffer(b, 0, Size.BYTE);
        Assertions.assertEquals((valB << 8) & 0xFF, actB);

        actW = S32xUtil.readBuffer(b, 0, Size.WORD);
        valW = valB & 0xFF;
        Assertions.assertEquals(valW, actW);

        actL = S32xUtil.readBuffer(b, 0, Size.LONG);
        valL = (valB & 0xFF) << 16;
        Assertions.assertEquals(valL, actL);
    }

    @Test
    public void testWord() {
        int numByte = 4;
        ByteBuffer b = ByteBuffer.allocate(numByte);
        byte valB, actB;
        int valW, valL, actW, actL;

        //positive, pos 0
        b = ByteBuffer.allocate(numByte);
        valW = 0x1122;
        S32xUtil.writeBufferRaw(b, 0, valW, Size.WORD);
        actW = S32xUtil.readBuffer(b, 0, Size.WORD);
        Assertions.assertEquals(valW, actW);

        actB = (byte) S32xUtil.readBuffer(b, 0, Size.BYTE);
        Assertions.assertEquals((valW & 0xFFFF) >> 8, actB);

        actB = (byte) S32xUtil.readBuffer(b, 1, Size.BYTE);
        Assertions.assertEquals((valW & 0xFF), actB);

        actL = S32xUtil.readBuffer(b, 0, Size.LONG);
        valL = valW << 16;
        Assertions.assertEquals(valL, actL);

        //negative, pos 0
        b = ByteBuffer.allocate(numByte);
        valW = -0x3344;
        S32xUtil.writeBufferRaw(b, 0, valW, Size.WORD);
        actW = S32xUtil.readBuffer(b, 0, Size.WORD);
        Assertions.assertEquals((short) valW, (short) actW);

        actB = (byte) S32xUtil.readBuffer(b, 0, Size.BYTE);
        Assertions.assertEquals((valW & 0xFFFF) >> 8, actB & 0xFF);

        actB = (byte) S32xUtil.readBuffer(b, 1, Size.BYTE);
        Assertions.assertEquals((valW & 0xFF), actB & 0xFF);

        actL = S32xUtil.readBuffer(b, 0, Size.LONG);
        valL = valW << 16;
        Assertions.assertEquals(valL, actL);
    }
}
