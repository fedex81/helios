package mcd.asic;

import mcd.McdRegTestBase;
import mcd.dict.MegaCdDict.RegSpecMcd;
import mcd.wram.McdWordRamTest;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.asic.AsicTest.compareFileToWram;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdDict.START_MCD_SUB_WORD_RAM_2M;
import static mcd.dict.MegaCdMemoryContext.WramSetup.W_2M_MAIN;
import static mcd.dict.MegaCdMemoryContext.WramSetup.W_2M_SUB;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class AsicWramOwnershipTest extends McdRegTestBase {

    private static final int ADDR = START_MCD_SUB_WORD_RAM_2M + 0x40;
    private static final int VAL = 0x1234;

    @Test
    public void testAsicAccessIgnoresOwnership() {
        McdWordRamTest.setWramMain2M(lc);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);

        ctx.wramHelper.writeWordRamAsic(ADDR, VAL);
        int res = ctx.wramHelper.readWordRamAsic(ADDR) & 0xFFFF;
        Assertions.assertEquals(VAL, res);

        McdWordRamTest.setWramSub2M(lc);
        Assertions.assertEquals(W_2M_SUB, ctx.wramSetup);
        int res2 = ctx.wramHelper.readWordRam(SUB_M68K, ADDR, Size.WORD) & 0xFFFF;
        Assertions.assertEquals(VAL, res2);
    }

    @Test
    public void testCpuGatedPathIsDroppedWhenMainOwns() {
        McdWordRamTest.setWramMain2M(lc);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);

        int gatedRead = ctx.wramHelper.readWordRam(SUB_M68K, ADDR, Size.WORD) & 0xFFFF;
        Assertions.assertEquals(Size.WORD.getMask(), gatedRead);

        ctx.wramHelper.writeWordRamAsic(ADDR, 0);
        ctx.wramHelper.writeWordRam(SUB_M68K, ADDR, VAL, Size.WORD);
        int r = ctx.wramHelper.readWordRamAsic(ADDR) & 0xFFFF;
        Assertions.assertEquals(0, r);
    }

    @Test
    public void testAsicRenderMatchesWhileMainOwnsWordRam() {
        McdWordRamTest.setWramSub2M(lc);
        Assertions.assertEquals(W_2M_SUB, ctx.wramSetup);
        AsicTest.copyFileToWram(lc.memoryContext.wramHelper, AsicTest.testFilePathBefore);

        asicRegWordWrite(MCD_IMG_STAMP_SIZE, (0 << 8) | 7); //GRON=0
        asicRegWordWrite(MCD_IMG_STAMP_MAP_ADDR, 64 << 8);
        asicRegWordWrite(MCD_IMG_VCELL, 13);
        asicRegWordWrite(MCD_IMG_START_ADDR, -64 << 8);
        asicRegWordWrite(MCD_IMG_OFFSET, 0);
        asicRegWordWrite(MCD_IMG_HDOT, 1 << 8);
        asicRegWordWrite(MCD_IMG_VDOT, 112);

        McdWordRamTest.setWramMain2M(lc);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);

        asicRegWordWrite(MCD_IMG_TRACE_VECTOR_ADDR, -128 << 8);
        int guard = 0, linesLeft;
        do {
            lc.asic.step(0);
            linesLeft = lc.asic.read(MCD_IMG_VDOT, MCD_IMG_VDOT.addr, Size.WORD);

        } while (linesLeft > 0 && guard++ < 100_000);

        compareFileToWram(lc.memoryContext.wramHelper, AsicTest.testFilePathAfter);
    }


    private void asicRegWordWrite(RegSpecMcd reg, int word) {
        lc.asic.write(reg, reg.addr, word, Size.WORD);
    }
}
