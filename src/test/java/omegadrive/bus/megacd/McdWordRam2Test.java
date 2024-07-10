package omegadrive.bus.megacd;

import mcd.McdDeviceHelper;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdDict.SharedBitDef;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.START_MCD_WORD_RAM_MODE1;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
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

    protected McdDeviceHelper.McdLaunchContext lc;

    @BeforeEach
    public void setup() {
        lc = McdDeviceHelper.setupDevices();
        ctx = lc.memoryContext;
        mainCpuBus = lc.mainBus;
        subCpuBus = lc.subBus;
        subCpu = lc.subCpu;
    }
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

    //main can't take back access to wram until sub not release it

    /**
     * //main can't take back access to wram until sub not release it
     * mcdWR8(0x8000, 0);
     * val = ga->MEMMOD;
     * ga->MEMMOD &= ~GA_MEMMOD_DMNA; //Main resets DMNA
     * gVsync();
     * if (val != ga->MEMMOD)return 0x04; //<- here
     * mcdWR8(0x8000, 1);
     * if (mcdRD8(0x8000) != 1)return 0x05;
     * ga->MEMMOD |= GA_MEMMOD_DMNA;
     * gVsync();
     * if (val != ga->MEMMOD)return 0x06;
     * mcdWR8(0x8000, 2);
     * if (mcdRD8(0x8000) != 2)return 0x07;
     */
    @Test
    public void testSwitch01() {
        assert ctx.wramSetup.mode == _2M;
        McdWordRamTest.setWramSub2M(lc);
        int mainMemModeAddr = GenesisBusProvider.MEGA_CD_EXP_START +
                MegaCdDict.RegSpecMcd.MCD_MEM_MODE.addr + 1;
        int val = mainCpuBus.read(mainMemModeAddr, Size.BYTE);
        int newVal = val & ~(1 << SharedBitDef.DMNA.getBitPos()); //reset DMNA from MAIN
        mainCpuBus.write(mainMemModeAddr, newVal, Size.BYTE);
        int val2 = mainCpuBus.read(mainMemModeAddr, Size.BYTE);
        Assertions.assertEquals(val, val2);
    }
}