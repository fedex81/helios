package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.vdp.MarsVdpImpl;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static s32x.MarsRegTestUtil.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class VdpAutoFillTest {

    private S32XMMREG s32XMMREG;
    private MarsVdpImpl vdp;
    private ByteBuffer buffer;

    @BeforeEach
    public void before() {
        s32XMMREG = createTestInstance().s32XMMREG;
        vdp = (MarsVdpImpl) s32XMMREG.getVdp();
        byte[] b = new byte[0x800];
        Arrays.fill(b, (byte) -1);
        buffer = ByteBuffer.wrap(b);
    }

    @Test
    public void testAutoFill01() {
        testAutoFillInternal(0x20A, 0xE3, 616600809);
    }

    //NOTE: this wraps to 0x200 and overwrites
    @Test
    public void testAutoFill01Wrap() {
        testAutoFillInternal(0x22A, 0xE3, 248042729);
    }

    @Test
    public void testAutoFill02() {
        testAutoFillInternal(0x208, 0xE0, -1043671285);
    }

    //NOTE: this wraps to 0x200 and overwrites
    @Test
    public void testAutoFill02Wrap() {
        testAutoFillInternal(0x280, 0xE0, 1856652043);
    }

    public void testAutoFillInternal(int startAddr, int len, int expectedHash) {
        vdp.runAutoFillInternal(buffer, startAddr, 0xAA, len);
        Assertions.assertEquals(expectedHash, Arrays.hashCode(buffer.array()));
    }


    /**
     * according to docs the len register is not updated
     */
    @Test
    public void testAutoFillRegWrap() {
        int startAddr = 0x1E0;
        int len = 0x30;
        int data = 0xAA;
        s32XMMREG.write(SH2_AFLEN_OFFSET, len, Size.WORD);
        s32XMMREG.write(SH2_AFSAR_OFFSET, startAddr, Size.WORD);
        vdp.runAutoFillInternal(buffer, startAddr, data, len);
        int expSar = (startAddr & 0xFF00) + ((len + startAddr) & 0xFF) + 1;
        int actLen = s32XMMREG.read(SH2_AFLEN_OFFSET, Size.WORD);
        int actSar = s32XMMREG.read(SH2_AFSAR_OFFSET, Size.WORD);
        Assertions.assertEquals(len, actLen);
        Assertions.assertEquals(expSar, actSar);
    }

    @Test
    public void testAutoFillReg() {
        int startAddr = 0x120;
        int len = 0x10;
        int data = 0xAA;
        s32XMMREG.write(SH2_AFLEN_OFFSET, len, Size.WORD);
        s32XMMREG.write(SH2_AFSAR_OFFSET, startAddr, Size.WORD);
        vdp.runAutoFillInternal(buffer, startAddr, data, len);
        int expSar = (startAddr & 0xFF00) + ((len + startAddr) & 0xFF) + 1;
        int actLen = s32XMMREG.read(SH2_AFLEN_OFFSET, Size.WORD);
        int actSar = s32XMMREG.read(SH2_AFSAR_OFFSET, Size.WORD);
        Assertions.assertEquals(len, actLen);
        Assertions.assertEquals(expSar, actSar);
    }
}
