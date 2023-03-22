package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static s32x.MarsRegTestUtil.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class VdpRegTest {

    private S32XMMREG s32XMMREG;

    @BeforeEach
    public void before() {
        s32XMMREG = createTestInstance().s32XMMREG;
    }

    //palette enable bit#13
    @Test
    public void testPEN() {
        s32XMMREG.write(SH2_FBCR_OFFSET, 0, Size.WORD);
        //startup, vblankOn, pen= true
        assertPEN(s32XMMREG, true);
        s32XMMREG.setVBlank(true);

        s32XMMREG.setHBlank(true);
        assertPEN(s32XMMREG, true);

        s32XMMREG.setHBlank(false); //vblank on
        assertPEN(s32XMMREG, true);

        s32XMMREG.setVBlank(true);
        s32XMMREG.setHBlank(true);
        assertPEN(s32XMMREG, true);

        s32XMMREG.setVBlank(true);
        s32XMMREG.setHBlank(false);
        assertPEN(s32XMMREG, true);

        s32XMMREG.setVBlank(false);
        s32XMMREG.setHBlank(true);
        assertPEN(s32XMMREG, true);

        s32XMMREG.setVBlank(false);
        s32XMMREG.setHBlank(false);
        assertPEN(s32XMMREG, false);
    }

    /**
     * Frame Buffer Authorization, bit#1
     * 0: Access approved
     * 1: Access denied
     * <p>
     * When having performed FILL, be sure to access the Frame Buffer after
     * confirming that FEN is equal to 0.
     * <p>
     * ie. when the vdp is accessing the FB, FEN = 1
     * dram refresh, FEN=1
     */
    @Test
    public void testFEN() {
        int res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD);
        assertFEN(s32XMMREG, 0);

        s32XMMREG.write(SH2_FBCR_OFFSET, 2, Size.WORD);
        assertFEN(s32XMMREG, 1);

        s32XMMREG.write(SH2_FBCR_OFFSET, 0, Size.WORD);
        assertFEN(s32XMMREG, 0);
    }

    @Test
    public void testFEN2() {
        int res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD);
        assertFEN(s32XMMREG, 0);

        //set FEN=1, access denied
        s32XMMREG.write(SH2_FBCR_OFFSET, 2, Size.WORD);
        assertFEN(s32XMMREG, 1);

        //toggle hblank
        s32XMMREG.setHBlank(true);
        s32XMMREG.setHBlank(false);

        //fen should go back to 0
        assertFEN(s32XMMREG, 0);
    }

    @Test
    public void testFBCR_ReadOnly() {
        s32XMMREG.write(SH2_FBCR_OFFSET, 0, Size.WORD);

        s32XMMREG.setVBlank(true);
        s32XMMREG.setHBlank(true);
        s32XMMREG.write(SH2_FBCR_OFFSET, 0, Size.WORD);
        assertVBlank(s32XMMREG, true);
        assertHBlank(s32XMMREG, true);
        assertPEN(s32XMMREG, true);
    }

    @Test
    public void testBitmapMode_Priority() {
        s32XMMREG.write(SH2_BITMAP_MODE_OFFSET, 0, Size.WORD);
        assertPRIO(s32XMMREG, false);

        s32XMMREG.setVBlank(true);
        s32XMMREG.write(SH2_BITMAP_MODE_OFFSET, 0x80, Size.WORD);
        assertPRIO(s32XMMREG, true);

        s32XMMREG.write(SH2_BITMAP_MODE_OFFSET, 0, Size.WORD);
        assertPRIO(s32XMMREG, false);

        s32XMMREG.setVBlank(false);

        //TODO only allowed during vblank
//        s32XMMREG.write(SH2_BITMAP_MODE_OFFSET, 0x80, Size.WORD);
//        assertPRIO(s32XMMREG, false);
//
//        s32XMMREG.setVBlank(false);
//
////        s32XMMREG.write(SH2_BITMAP_MODE_OFFSET, 0, Size.WORD);
//        assertPRIO(s32XMMREG, false);
    }

    @Test
    public void testVdpBitmapMode() {
        testByteWriteRegIgnoresEvenByte(SH2_BITMAP_MODE_OFFSET, 0x83, 0x7FFF);
    }

    @Test
    public void testAFLR() {
        testByteWriteRegIgnoresEvenByte(SH2_AFLEN_OFFSET, 0xFF, 0xFFFF);
    }

    @Test
    public void testSSCR() {
        testByteWriteRegIgnoresEvenByte(SH2_SSCR_OFFSET, 0x1, 0xFFFF);
    }

    private void testByteWriteRegIgnoresEvenByte(int regOffset, int oddByteMask, int readWordMask) {
        for (int i = 0; i < 0x100; i++) {
            System.out.println(i);
            s32XMMREG.write(regOffset + 1, i, Size.BYTE);
            int res1 = s32XMMREG.read(regOffset, Size.WORD);
            Assertions.assertEquals(i & oddByteMask, res1 & readWordMask);

            //ignored
            s32XMMREG.write(regOffset, i, Size.BYTE);
            int res2 = s32XMMREG.read(regOffset, Size.WORD);
            Assertions.assertEquals(res1, res2);
        }
    }

    @Test
    public void testFBCR() {
        s32XMMREG.write(SH2_FBCR_OFFSET, 0, Size.WORD);
        int res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD);
        Assertions.assertEquals(0, res & 0x1FFF);

        s32XMMREG.write(SH2_FBCR_OFFSET, 0b111 << 12, Size.WORD);
        res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD);
        Assertions.assertEquals(0, res & 0x1FFF);

        s32XMMREG.write(SH2_FBCR_OFFSET, 0b111 << 4, Size.BYTE);
        res = s32XMMREG.read(SH2_FBCR_OFFSET, Size.WORD);
        Assertions.assertEquals(0, res & 0x1FFF);

        testByteWriteRegIgnoresEvenByte(SH2_FBCR_OFFSET, 3, 0x1FFF);
    }
}
