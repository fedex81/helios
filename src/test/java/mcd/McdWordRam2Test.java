package mcd;

import mcd.dict.MegaCdDict;
import omegadrive.bus.md.GenesisBus;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.util.Md32xRuntimeData;

import static mcd.McdWordRamTest.*;
import static mcd.bus.McdWordRamHelper.getBank;
import static mcd.bus.McdWordRamHelper.getBank1M;
import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.SharedBitDef.MODE;
import static mcd.dict.MegaCdDict.SharedBitDef.RET;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
import static mcd.dict.MegaCdMemoryContext.WramSetup.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdWordRam2Test extends McdRegTestBase {

    @BeforeEach
    public void setup() {
        setupBase();
    }
    @Test
    public void testBank1M() {
        Assertions.assertEquals(0, getBank1M(W_1M_WR0_MAIN, M68K));
        Assertions.assertEquals(1, getBank1M(WramSetup.W_1M_WR0_SUB, M68K));
        Assertions.assertEquals(1, getBank1M(W_1M_WR0_MAIN, SUB_M68K));
        Assertions.assertEquals(0, getBank1M(WramSetup.W_1M_WR0_SUB, SUB_M68K));
    }

    @Test
    public void testBank2MWord() {
        int start = START_MCD_MAIN_WORD_RAM_MODE1;
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

    private void write1mString(CpuDeviceAccess cpu, String s, Size size) {
        assert lc.memoryContext.wramSetup.mode == _1M;
        int bank = getBank1M(lc.memoryContext.wramSetup, cpu);
        GenesisBus bus = cpu == M68K ? mainCpuBus : subCpuBus;
        int baseAddr = START_MCD_SUB_WORD_RAM_1M;
        if (cpu == M68K) {
            baseAddr = bank == 0 ? START_MCD_MAIN_WORD_RAM : START_MCD_MAIN_WORD_RAM + MCD_WORD_RAM_1M_SIZE;
        }
        for (int i = 0; i < s.length(); i += size.getByteSize()) {
            int val = switch (size) {
                case BYTE -> s.charAt(i);
                case WORD -> (s.charAt(i) << 8) | s.charAt(i + 1);
                case LONG -> (s.charAt(i) << 24) | (s.charAt(i + 1) << 16) | (s.charAt(i + 2) << 8) | s.charAt(i + 3);
            };
            bus.write(baseAddr + i, val, size);
        }
    }


    private String read1mString(CpuDeviceAccess cpu, int len, Size size) {
        assert lc.memoryContext.wramSetup.mode == _1M;
        GenesisBus bus = cpu == M68K ? mainCpuBus : subCpuBus;
        int baseAddr = cpu == M68K ? START_MCD_MAIN_WORD_RAM : START_MCD_SUB_WORD_RAM_1M;
        String res = "";
        for (int i = 0; i < len; i += size.getByteSize()) {
            int val = bus.read(baseAddr + i, size);
            res += switch (size) {
                case BYTE -> (char) val;
                case WORD -> ("" + (char) (byte) (val >> 8)) + (char) (byte) val;
                case LONG -> (((("" + (char) (byte) (val >> 24)) + (char) (byte) (val >> 16))
                        + (char) (byte) (val >> 8)) + (char) (byte) val);
            };
        }
        return res;
    }

    @Test
    public void testWram1M_RW() {
        for (Size size : Size.values()) {
            System.out.println(size);
            String str = "MOD Player24" + size.name();
            final int len = str.length();
            Assertions.assertTrue(len % Size.LONG.getByteSize() == 0);

            //reset WRAM
            int wlen = lc.memoryContext.wordRam01[0].length;
            for (int i = 0; i < wlen; i++) {
                lc.memoryContext.wordRam01[0][i] = 0;
                lc.memoryContext.wordRam01[1][i] = 0;
            }

            //sub writes to 1M, bank0
            McdWordRamTest.setWram1M_W0Sub(lc);
            write1mString(SUB_M68K, str, size);

            //main reads back from bank0
            McdWordRamTest.setWram1M_W0Main(lc);
            String res = read1mString(M68K, str.length(), size);
            Assertions.assertEquals(str, res);

            //main writes to 1M, bank0
            assertMode(W_1M_WR0_MAIN);
            str = "TESTING_MAIN" + size.name();
            assert len == str.length();
            write1mString(M68K, str, size);

            //sub reads back, bank0
            McdWordRamTest.setWram1M_W0Sub(lc);
            res = read1mString(SUB_M68K, str.length(), size);
            Assertions.assertEquals(str, res);

            //main writes to bank1
            assertMode(W_1M_WR0_SUB);
            str = "writes to ba" + size.name();
            assert len == str.length();
            write1mString(M68K, str, size);

            //sub reads back, bank1
            McdWordRamTest.setWram1M_W0Main(lc);
            res = read1mString(SUB_M68K, str.length(), size);
            Assertions.assertEquals(str, res);

            //sub writes to 1M, bank1
            assertMode(W_1M_WR0_MAIN);
            str = "str.length()" + size.name();
            assert len == str.length();
            write1mString(SUB_M68K, str, size);

            //main reads back from bank1
            McdWordRamTest.setWram1M_W0Sub(lc);
            res = read1mString(M68K, str.length(), size);
            Assertions.assertEquals(str, res);
        }
    }

    private void assertMode(WramSetup ws) {
        Assertions.assertEquals(lc.memoryContext.wramSetup, ws);
    }

    @Test
    public void testWramDotMappedSubReads() {
        assert ctx.wramSetup.mode == _2M;
        //priority mode off = 0, PM0 = PM1 = 0
        assert (subCpuBus.read(SUB_MEM_MODE_REG + 1, Size.BYTE) & 0x18) == 0;
        McdWordRamTest.setWramMain2M(lc);
        mainCpuBus.write(START_MCD_MAIN_WORD_RAM, 0x1234, Size.WORD);
        mainCpuBus.write(START_MCD_MAIN_WORD_RAM + 2, 0xABCD, Size.WORD);

        //WR0        WR1                2M
        //1234       ABCD               1234
        //0000       0000               ABCD
        //                              0000
        //                              0000

        McdWordRamTest.setWram1M_W0Main(lc);

        //main reads the start of WR0 -> 1234
        int res = mainCpuBus.read(START_MCD_MAIN_WORD_RAM, Size.WORD);
        Assertions.assertEquals(0x1234, res);

        //sub reads the start of WR1 -> ABCD
        res = subCpuBus.read(START_MCD_SUB_WORD_RAM_1M, Size.WORD);
        Assertions.assertEquals(0xABCD, res);

        //sub reads the start of 2M window -> 0x0A0B
        Md32xRuntimeData.setAccessTypeExt(SUB_M68K);
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

    //ROTD sets DMNA=1 in 1M and expects SUB to have WordRAM after the switch to 2M.
    //TODO fix
//    @Test
    @Ignore
    public void testRiseOfTheDragon() {
        McdWordRamTest.setWram1M_W0Main(lc);
        int v = mainGetLsbFn.apply(mainCpuBus);
        //1M Mode, main sets DMNA
        mainSetLsbFn.accept(mainCpuBus, v | DMNA_BIT_MASK);
        //switch to 2M
        subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) & ~4);
        Assertions.assertEquals(_2M, ctx.wramSetup.mode);
        Assertions.assertEquals(SUB_M68K, ctx.wramSetup.cpu);
    }

    //UP sets bank in 1M (so, RET=1) and expects MAIN to have WordRAM after the switch to 2M.
    @Test
    public void testUltraverse() {
        McdWordRamTest.setWram1M_W0Main(lc);
        McdWordRamTest.setWram1M_W0Sub(lc); //RET = 1
        //switch to 2M
        subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) & ~4);
        Assertions.assertEquals(_2M, ctx.wramSetup.mode);
        Assertions.assertEquals(M68K, ctx.wramSetup.cpu);
    }

    /**
     * [ff8003]=00 (switch to 2M)
     * [ff8003]=01 (give WordRAM to MAIN)
     * [ff8003]=04 (switch to 1M, set bank=0)
     * <p>
     * versus
     * <p>
     * [ff8003]=00 (switch to 2M)
     * [ff8003]=05 (switch to 1M, set bank=1)
     * <p>
     * As you'd expect, the 1M WordRAM banks are assigned differently, based on last write to bit 0.
     * However, [ff8003]=05 has the unexpected result of setting RET=1 despite the mode bit.
     * In switching back to 2M (say, by setting [ff8003]=00), WordRAM will have already been
     * assigned to MAIN, and the register value will reflect this: [ff8003]==01.
     */
    //TODO fix
    @Test
    public void testSequence1() {
        /**
         *  [ff8003]=00 (switch to 2M)
         *  [ff8003]=01 (give WordRAM to MAIN)
         *  [ff8003]=04 (switch to 1M, set bank=0)
         */
        McdWordRamTest.setWram1M_W0Main(lc);
        //switch to 2M
        subSetLsbFn.accept(lc.subBus, 0);
        Assertions.assertEquals(_2M, ctx.wramSetup.mode);

        //give WordRAM to MAIN
        subSetLsbFn.accept(lc.subBus, RET_BIT_MASK);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);

        //switch to 1M
        subSetLsbFn.accept(lc.subBus, MODE.getBitMask());
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);

        //switch to 2M
        subSetLsbFn.accept(lc.subBus, 0);
        Assertions.assertEquals(W_2M_SUB, ctx.wramSetup);

        /**
         * [ff8003]=00 (switch to 2M)
         * [ff8003]=05 (switch to 1M, set bank=1)
         */
        McdWordRamTest.setWram1M_W0Main(lc);
        //switch to 2M
        subSetLsbFn.accept(lc.subBus, 0);
        Assertions.assertEquals(_2M, ctx.wramSetup.mode);

        //switch to 1M, bank=1
        subSetLsbFn.accept(lc.subBus, MODE.getBitMask() | RET.getBitMask());
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);

        //switch to 2M
        subSetLsbFn.accept(lc.subBus, 0);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);
        Assertions.assertEquals(1, subGetLsbFn.apply(lc.subBus));
    }

    /**
     * RET bit does not matter, it can not be written by main-CPU so it keeps its previous value but yes,
     * in 1M mode (but not in 2M mode), writing 1 to bit 0 has no effect on the read value and writing 0
     * set DMNA bit value to 1.
     * Writing 1 to DMNA bit in 1M mode although have some side-effect when switching back to 2M mode,
     * as mentioned earlier and as explained by TascoDlx above.
     */

    /**
     * The DMNA bit must be set to 0 (not 1) to request word-ram bank switching
     * (it is read as 1 after that, until the switch has been performed by sub-cpu).
     * I actually found this to be correctly explained in only one
     * place of the available documentation (main-cpu section of "rex sabio" hardware manual),
     * in other places it is either wrong ("writing 1 to dmna bit") or imprecise ("on read, dmna bit is set to 1"),
     * which probably did not help during manuals translation.
     */
    @Test
    public void test1M_MainSwitch() {
        McdWordRamTest.setWram1M_W0Main(lc);

        //switch bank, set DMNA=0, main = bank1
        mainSetLsbFn.accept(lc.mainBus, mainGetLsbFn.apply(lc.mainBus) & ~1);
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);
        //DMNA then goes to 1, (TODO->) and then 0
        Assertions.assertEquals(DMNA_BIT_MASK, mainGetLsbFn.apply(lc.mainBus) & DMNA_BIT_MASK);
    }

    @Test
    public void test2M_SubRetBit() {
        McdWordRamTest.setWramSub2M(lc);
        //set RET=1, WRAM goes to MAIN
        subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) | RET.getBitMask());
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);
        //RET=1 means switch done
        Assertions.assertEquals(RET.getBitMask(), subGetLsbFn.apply(lc.subBus) & RET.getBitMask());
    }

    /**
     * //W_1M_WR0_MAIN then MODE = 1 -> remain 1M, swap request to SUB
     * //MODE=1,
     * //MAIN set DMNA=0 swap request
     * //RET goes to 1, DMNA gets set to 1 as well
     * 68M 00ff0498   13fc 0004 00a12003      move.b   #$0004,$00a12003 [NEW]
     * 68M 00ff04a0   0839 0001 00a12003      btst     #$1,$00a12003 [NEW]
     * 68M 00ff04a8   67ee                    beq.s    $00ff0498 [NEW]
     */
    @Test
    public void test1M_terminator() {
        McdWordRamTest.setWram1M_W0Main(lc);
        //switch bank, set DMNA=0, main = bank1
        mainSetLsbFn.accept(lc.mainBus, MODE.getBitMask());
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);
        Assertions.assertEquals(7, mainGetLsbFn.apply(lc.mainBus));
    }


    /**
     * Battlecorps E demo, BC racers E Demo
     * 68S 00025af0   0879 0000 00ff8003      bchg     #$0,$00ff8003 [NEW]
     * 68S 00025af8   0839 0001 00ff8003      btst     #$1,$00ff8003 [NEW]
     * 68S 00025b00   66f6                    bne.s    $00025af8 [NEW]
     */
    @Test
    public void test1M_bcracers() {
        McdWordRamTest.setWram1M_W0Main(lc);
        //switch bank, set DMNA=0, main = bank1
        int val = subGetLsbFn.apply(lc.subBus);
        int retFlip = ~val & 1;
        val &= ~1; //unset RET
        val |= retFlip;
        subSetLsbFn.accept(lc.subBus, val);
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);
        Assertions.assertEquals(5, mainGetLsbFn.apply(lc.mainBus));
    }
}