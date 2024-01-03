package s32x.util.blipbuffer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static s32x.util.blipbuffer.BlipBufferHelper.clampToByte;
import static s32x.util.blipbuffer.BlipBufferHelper.clampToShort;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class BlipBufferHelperTest {

    @Test
    public void testClampToByteNearZero() {
        Assertions.assertEquals(-1, clampToByte(-1));
        Assertions.assertEquals(0, clampToByte(0));
        Assertions.assertEquals(1, clampToByte(1));
    }

    @Test
    public void testClampToByte() {
        boolean wentPos = false;
        for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i += 50) {
            if ((byte) i != i) {
                int s = clampToByte(i);
                Assertions.assertEquals(s, i < 0 ? Byte.MIN_VALUE : Byte.MAX_VALUE);
            }
            //detect overflow
            wentPos |= i > 0;
            if (wentPos && i < 0) {
                break;
            }

        }
    }

    @Test
    public void testClampToShortNearZero() {
        Assertions.assertEquals(-1, clampToShort(-1));
        Assertions.assertEquals(0, clampToShort(0));
        Assertions.assertEquals(1, clampToShort(1));
    }

    @Test
    public void testClampToShort() {
        boolean wentPos = false;
        for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i += 50) {
            if ((short) i != i) {
                int s = clampToShort(i);
                Assertions.assertEquals(s, i < 0 ? Short.MIN_VALUE : Short.MAX_VALUE);
            }
            //detect overflow
            wentPos |= i > 0;
            if (wentPos && i < 0) {
                break;
            }
        }
    }
}
