package omegadrive.bus.megacd;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.START_MCD_WORD_RAM_MODE1;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.dict.MegaCdMemoryContext.WramSetup.W_2M_MAIN;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdWordRam2Test extends McdRegTestBase {
    @Test
    public void testBank1M() {
        Assertions.assertEquals(0, getBank1M(WramSetup.W_1M_WR0_MAIN, M68K));
        Assertions.assertEquals(1, getBank1M(WramSetup.W_1M_WR0_SUB, M68K));
        Assertions.assertEquals(1, getBank1M(WramSetup.W_1M_WR0_MAIN, SUB_M68K));
        Assertions.assertEquals(0, getBank1M(WramSetup.W_1M_WR0_SUB, SUB_M68K));
    }

    @Test
    public void testBank2MWord() {
        int start = START_MCD_WORD_RAM_MODE1;
        int[] addr = {
                start, start + 2, start + MCD_WORD_RAM_1M_SIZE,
                start + MCD_WORD_RAM_1M_SIZE + 2,
                start + MCD_WORD_RAM_2M_SIZE - 2};

        for (int a : addr) {
            System.out.println(th(a));
            Assertions.assertEquals((a & 2) >> 1, getBank(W_2M_MAIN, M68K, a));
        }
    }
}