package s32x.util;

import omegadrive.util.BufferUtil;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class RegSpecTest {

    private ByteBuffer b;
    private RegSpec regSpecWord, regSpecByte, regSpecLong;

    private enum BYTE_POS_REG {
        BYTE_0, BYTE_1, BYTE_2, BYTE_3;
    }

    @BeforeEach
    public void before() {
        b = ByteBuffer.allocate(0x16);
        regSpecWord = new RegSpec("TEST1", 2, 0xFF, Size.WORD);
        regSpecByte = new RegSpec("TEST2", 4, 0xFF, Size.BYTE);
        regSpecLong = new RegSpec("TEST3", 6, 0xFF, Size.LONG);
    }

    @Test
    public void testByteEven() {
        int val = 0x80;
        BYTE_POS_REG bytePos = BYTE_POS_REG.BYTE_0;
        testInternal(regSpecByte, val, bytePos, Size.BYTE);
        testInternal(regSpecWord, val, bytePos, Size.BYTE);
        testInternal(regSpecLong, val, bytePos, Size.BYTE);
        testInternal(regSpecByte, val + 1, bytePos, Size.BYTE);
        testInternal(regSpecWord, val + 1, bytePos, Size.BYTE);
        testInternal(regSpecLong, val + 1, bytePos, Size.BYTE);

        bytePos = BYTE_POS_REG.BYTE_2;
        testInternal(regSpecLong, val, bytePos, Size.BYTE);
        testInternal(regSpecLong, val + 1, bytePos, Size.BYTE);
    }

    @Test
    public void testByteNotChanging() {
        BYTE_POS_REG bytePos = BYTE_POS_REG.BYTE_0;
        Arrays.fill(b.array(), (byte) 0);
        testNotChangingInternal(regSpecByte, 0x100, bytePos, Size.BYTE);
        testNotChangingInternal(regSpecWord, 0x100, bytePos, Size.BYTE);
        testNotChangingInternal(regSpecLong, 0x100, bytePos, Size.BYTE);

        bytePos = BYTE_POS_REG.BYTE_1;
        testNotChangingInternal(regSpecWord, 0x100, bytePos, Size.BYTE);
        testNotChangingInternal(regSpecLong, 0x100, bytePos, Size.BYTE);

        bytePos = BYTE_POS_REG.BYTE_2;
        testNotChangingInternal(regSpecLong, 0x100, bytePos, Size.BYTE);

        bytePos = BYTE_POS_REG.BYTE_3;
        testNotChangingInternal(regSpecLong, 0x100, bytePos, Size.BYTE);
    }

    @Test
    public void testByteOdd() {
        int val = 1;
        BYTE_POS_REG bytePos = BYTE_POS_REG.BYTE_1;
        testInternal(regSpecWord, val, bytePos, Size.BYTE);
        testInternal(regSpecLong, val, bytePos, Size.BYTE);
        testInternal(regSpecWord, val + 1, bytePos, Size.BYTE);
        testInternal(regSpecLong, val + 1, bytePos, Size.BYTE);

        bytePos = BYTE_POS_REG.BYTE_3;
        testInternal(regSpecLong, val, bytePos, Size.BYTE);
        testInternal(regSpecLong, val + 1, bytePos, Size.BYTE);
    }

    @Test
    public void testWordEven() {
        int val = 0x8070;
        testInternal(regSpecWord, val, BYTE_POS_REG.BYTE_0, Size.WORD);
        testInternal(regSpecLong, val, BYTE_POS_REG.BYTE_0, Size.WORD);
        testInternal(regSpecLong, val + 0x100, BYTE_POS_REG.BYTE_2, Size.WORD);

        testInternal(regSpecWord, val + 1, BYTE_POS_REG.BYTE_0, Size.WORD);
        testInternal(regSpecLong, val + 1, BYTE_POS_REG.BYTE_0, Size.WORD);
        testInternal(regSpecLong, val + 0x0000_0100, BYTE_POS_REG.BYTE_2, Size.WORD);
    }

    @Test
    public void testWordNotChanging() {
        BYTE_POS_REG bytePos = BYTE_POS_REG.BYTE_0;
        Arrays.fill(b.array(), (byte) 0);
        testNotChangingInternal(regSpecWord, 0x1_0000, bytePos, Size.WORD);
        testNotChangingInternal(regSpecLong, 0x1_0000, bytePos, Size.WORD);

        bytePos = BYTE_POS_REG.BYTE_2;
        testNotChangingInternal(regSpecLong, 0x1_0000, bytePos, Size.BYTE);
    }

    @Test
    public void testLong() {
        int val = 0xAABBCCDD;
        testInternal(regSpecLong, val, BYTE_POS_REG.BYTE_0, Size.LONG);
        testInternal(regSpecLong, val + 1, BYTE_POS_REG.BYTE_0, Size.LONG);
        testInternal(regSpecLong, (val & 0xFFFF_00FF) | 0x0000EE00, BYTE_POS_REG.BYTE_0, Size.LONG);
        testInternal(regSpecLong, (val & 0xFF00_FFFF) | 0x00110000, BYTE_POS_REG.BYTE_0, Size.LONG);
        testInternal(regSpecLong, (val & 0x00FF_FFFF) | 0x22000000, BYTE_POS_REG.BYTE_0, Size.LONG);
    }

    private void testInternal(RegSpec r, int val, BYTE_POS_REG bytePos, Size size, boolean shouldChange) {
        Arrays.fill(b.array(), (byte) 0);
        boolean change = r.write(b, r.bufferAddr + bytePos.ordinal(), val, size);
        Assertions.assertEquals(shouldChange, change);
        if (shouldChange) {
            int res = BufferUtil.readBuffer(b, r.bufferAddr + bytePos.ordinal(), size) & size.getMask();
            Assertions.assertEquals(val, res);
        }
    }

    private void testNotChangingInternal(RegSpec r, int val, BYTE_POS_REG bytePos, Size size) {
        boolean change = r.write(b, r.bufferAddr + bytePos.ordinal(), val, size);
        Assertions.assertEquals(false, change);
    }

    private void testInternal(RegSpec r, int val, BYTE_POS_REG bytePos, Size size) {
        testInternal(r, val, bytePos, size, true);
    }
}
