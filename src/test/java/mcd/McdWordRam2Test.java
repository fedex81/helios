package mcd;

import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdDict.*;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.*;
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
    @Test
    public void testSwitch01() {
        assert ctx.wramSetup.mode == _2M;
        McdWordRamTest.setWramSub2M(lc);
        int mainMemModeAddr = GenesisBusProvider.MEGA_CD_EXP_START +
                MegaCdDict.RegSpecMcd.MCD_MEM_MODE.addr + 1;
        int val = mainCpuBus.read(mainMemModeAddr, Size.BYTE);
        //reset DMNA from MAIN, ignored
        int newVal = val & ~(SharedBitDef.DMNA.getBitMask());
        mainCpuBus.write(mainMemModeAddr, newVal, Size.BYTE);
        int val2 = mainCpuBus.read(mainMemModeAddr, Size.BYTE);
        Assertions.assertEquals(val, val2);
    }

    @Test
    public void testWramSubReads() {
        assert ctx.wramSetup.mode == _2M;
        McdWordRamTest.setWramMain2M(lc);
        mainCpuBus.write(START_MCD_WORD_RAM, 0x1234, Size.WORD);
        mainCpuBus.write(START_MCD_WORD_RAM + 2, 0xABCD, Size.WORD);

        //WR0        WR1                2M
        //1234       ABCD               1234
        //0000       0000               ABCD
        //                              0000
        //                              0000

        McdWordRamTest.setWram1M_W0Main(lc);

        //main reads the start of WR0 -> 1234
        int res = mainCpuBus.read(START_MCD_WORD_RAM, Size.WORD);
        Assertions.assertEquals(0x1234, res);

        //sub reads the start of WR1 -> ABCD
        res = subCpuBus.read(START_MCD_SUB_WORD_RAM_1M, Size.WORD);
        Assertions.assertEquals(0xABCD, res);

        //sub reads the start of 2M window -> 0x0A0B
        res = subCpuBus.read(START_MCD_SUB_WORD_RAM_2M, Size.WORD);
        Assertions.assertEquals(0x0A0B, res);

        //sub reads the start+2 of 2M window -> 0x0C0D
        res = subCpuBus.read(START_MCD_SUB_WORD_RAM_2M + 2, Size.WORD);
        Assertions.assertEquals(0x0C0D, res);

        for (int i = 0; i < 4; i++) {
            subCpuBus.write(START_MCD_SUB_WORD_RAM_2M + i, 0xF0 + i, Size.BYTE);
        }

        for (int i = 0; i < 4; i++) {
            res = subCpuBus.read(START_MCD_SUB_WORD_RAM_2M + i, Size.BYTE);
            Assertions.assertEquals(i, res);
        }

        res = subCpuBus.read(START_MCD_SUB_WORD_RAM_1M, Size.WORD);
        Assertions.assertEquals(0x0123, res);

        for (int i = 0; i < 256; i++) {
            subCpuBus.write(START_MCD_SUB_WORD_RAM_2M + i * 2 + 0, 0xF0 + (i >> 4), Size.BYTE);
            subCpuBus.write(START_MCD_SUB_WORD_RAM_2M + i * 2 + 1, 0xF0 + i, Size.BYTE);
        }

        for (int i = 0; i < 256; i++) {
            res = subCpuBus.read(START_MCD_SUB_WORD_RAM_1M + i, Size.BYTE);
            Assertions.assertEquals(i, res);
        }
    }
}