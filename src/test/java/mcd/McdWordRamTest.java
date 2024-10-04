package mcd;

import mcd.McdDeviceHelper.McdLaunchContext;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
import static mcd.dict.MegaCdMemoryContext.WramSetup.*;
import static omegadrive.bus.model.GenesisBusProvider.MEGA_CD_EXP_START;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdWordRamTest extends McdRegTestBase {

    public static final int MAIN_MEM_MODE_REG = MEGA_CD_EXP_START + 2;
    public static final int SUB_MEM_MODE_REG = START_MCD_SUB_GATE_ARRAY_REGS + 2;
    public static final int DMNA_BIT_POS = 1;
    public static final int DMNA_BIT_MASK = 1 << DMNA_BIT_POS;

    public static final int RET_BIT_POS = 0;
    public static final int RET_BIT_MASK = 1 << RET_BIT_POS;

    static BiConsumer<BaseBusProvider, Integer> subSetLsbFn = (bus, val) -> {
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        bus.write(SUB_MEM_MODE_REG + 1, val, Size.BYTE);
    };

    static Function<BaseBusProvider, Integer> subGetLsbFn = bus -> {
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        return bus.read(SUB_MEM_MODE_REG + 1, Size.BYTE);
    };

    static BiConsumer<BaseBusProvider, Integer> mainSetLsbFn = (bus, val) -> {
        MdRuntimeData.setAccessTypeExt(M68K);
        bus.write(MAIN_MEM_MODE_REG + 1, val, Size.BYTE);
    };
    static Function<BaseBusProvider, Integer> mainGetLsbFn = bus -> {
        MdRuntimeData.setAccessTypeExt(M68K);
        return bus.read(MAIN_MEM_MODE_REG + 1, Size.BYTE);
    };
    Consumer<Integer> mainSetLsb = val -> mainSetLsbFn.accept(mainCpuBus, val);
    Consumer<Integer> subSetLsb = val -> subSetLsbFn.accept(subCpuBus, val);
    Supplier<Integer> mainGetLsb = () -> mainGetLsbFn.apply(lc.mainBus);
    Supplier<Integer> subGetLsb = () -> subGetLsbFn.apply(lc.subBus);

    //only for RAM writes, do not use for regs
    BiConsumer<Integer, Integer> mainWriteValAtIdx = (idx, val) -> {
        MdRuntimeData.setAccessTypeExt(M68K);
        mainCpuBus.write(idx, val, Size.WORD);
        Assertions.assertEquals(val & 0xFFFF, mainCpuBus.read(idx, Size.WORD));
    };

    //only for RAM writes, do not use for regs
    BiConsumer<Integer, Integer> subWriteValAtIdx = (idx, val) -> {
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        subCpuBus.write(idx, val, Size.WORD);
        Assertions.assertEquals(val & 0xFFFF, subCpuBus.read(idx, Size.WORD));
    };

    //mainCpu gives 2M WRAM to subCpu
    @Test
    public void test2M_WRAMtoSub() {
        assert ctx.wramSetup.mode == _2M;
        int reg = mainGetLsb.get();
        int sreg = subGetLsb.get();
        Assertions.assertEquals(reg, sreg);
        //DMNA=0, RET=1
        Assertions.assertEquals(RET_BIT_MASK, reg & 3);

        //main sets DMNA=1, RET=0 immediately (inaccurate but should be ok)
        mainSetLsb.accept(DMNA_BIT_MASK);
        reg = mainGetLsb.get();
        sreg = subGetLsb.get();
        Assertions.assertEquals(reg, sreg);
        //subCpu has WRAM
        Assertions.assertEquals(DMNA_BIT_MASK, reg & 3);
    }

    //subCpu gives 2M WRAM to mainCpu
    @Test
    public void test2M_WRAMtoMain() {
        assert ctx.wramSetup.mode == _2M;
        //give WRAM to sub
        test2M_WRAMtoSub();
        int reg = subGetLsb.get();
        int mreg = mainGetLsb.get();
        Assertions.assertEquals(reg, mreg);
        //RET=1, DMNA=0
        Assertions.assertEquals(DMNA_BIT_MASK, reg & 3);

        //sub sets RET=1, DMNA=0 immediately (inaccurate but should be ok)
        subSetLsb.accept(RET_BIT_MASK);
        reg = subGetLsb.get();
        mreg = mainGetLsb.get();
        Assertions.assertEquals(reg, mreg);
        //mainCpu has WRAM
        Assertions.assertEquals(RET_BIT_MASK, reg & 3);
    }

    @Test
    public void test2M_WRAM_Switch() {
        assert ctx.wramSetup.mode == _2M;
        int reg = mainGetLsb.get();
        int sreg = subGetLsb.get();
        Assertions.assertEquals(reg, sreg);
        //DMNA=0, RET=1
        Assertions.assertEquals(RET_BIT_MASK, reg & 3);

        setWramMain2M();
        setWramSub2M();
        setWramMain2M();
        setWramMain2M();
        setWramSub2M();
        setWramSub2M();
    }

    @Test
    public void test1M_WRAM() {
        assert ctx.wramSetup.mode == _2M;
        int reg = mainGetLsb.get();
        int sreg = subGetLsb.get();
        Assertions.assertEquals(reg, sreg);

        //MAIN cannot set mode=1 (1M)
        mainSetLsb.accept(reg | 4);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);

        //SUB sets 1M
        subSetLsb.accept(sreg | 4);
        assert (reg & 1) == 1; //RET=1
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);

        //SUB requests switch, RET = 0
        subSetLsb.accept(subGetLsb.get() & ~1);
        Assertions.assertEquals(W_1M_WR0_MAIN, ctx.wramSetup);

        //MAIN cannot set RET=1 (ie. request switch)
        //set DMNA as well, otherwise it is a SWAP request
        mainSetLsb.accept(mainGetLsb.get() | RET_BIT_MASK | DMNA_BIT_MASK);
        Assertions.assertEquals(W_1M_WR0_MAIN, ctx.wramSetup);

        //SUB requests switch, RET = 1
        subSetLsb.accept(subGetLsb.get() | 1);
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);
    }

    @Test
    public void testWRAMDataOnSwitch_2M() {
        setWramMain2M();
        assert ctx.wramSetup == W_2M_MAIN;
        int offsetm = MegaCdDict.START_MCD_MAIN_WORD_RAM;
        int offsets = START_MCD_SUB_WORD_RAM_2M;
        for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
            mainWriteValAtIdx.accept(offsetm + i, i);
        }
        setWramSub2M();
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
            //sub can read what main wrote
            Assertions.assertEquals(i & 0xFFFF, subCpuBus.read(offsets + i, Size.WORD));
            //sub modifies
            subWriteValAtIdx.accept(offsets + i, (i + 1) & 0xFFFF);
        }
        setWramMain2M();
        //main read
        MdRuntimeData.setAccessTypeExt(M68K);
        for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
            Assertions.assertEquals((i + 1) & 0xFFFF, mainCpuBus.read(offsetm + i, Size.WORD));
        }
    }

    /**
     * TODO it is not this simple anymore
     * TODO how this fits with the MAIN CELL rendering??
     */
    @Test
    public void testWRAMDataOnSwitch_1M() {
        setWram1M_W0Main();
        int offsetm = MegaCdDict.START_MCD_MAIN_WORD_RAM;
        int offsets = START_MCD_SUB_WORD_RAM_1M;

        for (int i = 0; i < MCD_WORD_RAM_1M_SIZE - 1; i += 2) {
            //main writes to bank0
            mainWriteValAtIdx.accept(offsetm + i, i);
            //sub writes to bank1
            subWriteValAtIdx.accept(offsets + i, (i + 1) & 0xFFFF);
        }
        setWram1M_W0Sub();
        for (int i = 0; i < MCD_WORD_RAM_1M_SIZE - 1; i += 2) {
            //main can read what sub wrote
            MdRuntimeData.setAccessTypeExt(M68K);
            Assertions.assertEquals((i + 1) & 0xFFFF, mainCpuBus.read(offsetm + i, Size.WORD));
            //sub can read what main wrote
            MdRuntimeData.setAccessTypeExt(SUB_M68K);
            Assertions.assertEquals(i & 0xFFFF, subCpuBus.read(offsets + i, Size.WORD));
            //main writes to bank1
            mainWriteValAtIdx.accept(offsetm + i, (i + 2) & 0xFFFF);
            //sub writes to bank0
            subWriteValAtIdx.accept(offsets + i, (i + 3) & 0xFFFF);
        }
        setWram1M_W0Main();
        for (int i = 0; i < MCD_WORD_RAM_1M_SIZE - 1; i += 2) {
            //main can read what sub wrote, bank0
            MdRuntimeData.setAccessTypeExt(M68K);
            Assertions.assertEquals((i + 3) & 0xFFFF, mainCpuBus.read(offsetm + i, Size.WORD));
            //sub can read what main wrote, bank1
            MdRuntimeData.setAccessTypeExt(SUB_M68K);
            Assertions.assertEquals((i + 2) & 0xFFFF, subCpuBus.read(offsets + i, Size.WORD));
        }
    }


    /**
     * TODO it is not this simple anymore
     * TODO how this fits with the MAIN CELL rendering??
     * TODO I think current impl is buggy as some FMVs are showing corruption
     */
    /**
     * WORDRAM0         WORDRAM1       WORDRAM_2M
     * 0: 0000          0:AAAA         0:0000
     * 2: 1111          2:BBBB         2:AAAA
     * ...              ...            4:1111
     * 6:BBBB
     */

    @Test
    public void testWRAMDataOnSwitch_2M_1M() {
        setWramMain2M();
        assert ctx.wramSetup == W_2M_MAIN;
        int offsetm = MegaCdDict.START_MCD_MAIN_WORD_RAM;
        int offsets1M = START_MCD_SUB_WORD_RAM_1M;
        int offsets2M = START_MCD_SUB_WORD_RAM_2M;
        ByteBuffer[] wram1MCopy = {ByteBuffer.allocate(MCD_WORD_RAM_1M_SIZE), ByteBuffer.allocate(MCD_WORD_RAM_1M_SIZE)};

        Runnable reset = () -> {
            Arrays.fill(ctx.wordRam01[0], (byte) 0x88);
            Arrays.fill(ctx.wordRam01[1], (byte) 0x88);
            Arrays.fill(wram1MCopy[0].array(), (byte) 0x88);
            Arrays.fill(wram1MCopy[1].array(), (byte) 0x88);
        };

        reset.run();

        int[] vals0 = {0, 0x1111, 0x2222, 0x3333, 0x4444, 0x5555};
        int[] vals1 = {0xAAAA, 0xBBBB, 0xCCCC, 0xDDDD, 0xEEEE, 0xFFFF};
        int[][] vals = {vals0, vals1};
        int[] cnt = {0, 0};

        //write in 2M mode and then check 1M mode sees the same data correctly interleaved
        for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
            int bank = (i & 2) >> 1;
            int val = vals[bank][cnt[bank]];
            cnt[bank] = (cnt[bank] + 1) % vals0.length;
            mainWriteValAtIdx.accept(offsetm + i, val);
            wram1MCopy[bank].putShort((i >> 1) - bank, (short) val);
        }
        setWram1M_W0Main();

        Consumer<WramSetup> checkWram1M = (w) -> {
            int mainWramBank = w == W_1M_WR0_MAIN ? 0 : 1;
            int subWramBank = w == W_1M_WR0_MAIN ? 1 : 0;
            for (int i = 0; i < MCD_WORD_RAM_1M_SIZE - 1; i += 2) {
                //main match wramN
                MdRuntimeData.setAccessTypeExt(M68K);
                int val = mainCpuBus.read(offsetm + i, Size.WORD);
                int expVal = wram1MCopy[mainWramBank].getShort(i) & 0xFFFF;
                Assertions.assertEquals(expVal, val);

                //sub match wramM
                MdRuntimeData.setAccessTypeExt(SUB_M68K);
                val = subCpuBus.read(offsets1M + i, Size.WORD);
                expVal = wram1MCopy[subWramBank].getShort(i) & 0xFFFF;
                Assertions.assertEquals(expVal, val);
            }
        };

        checkWram1M.accept(ctx.wramSetup);
        setWram1M_W0Sub();
        checkWram1M.accept(ctx.wramSetup);

        //write in 1M mode and then check 2M mode sees the same data correctly interleaved
        Consumer<WramSetup> set1MVals = w -> {
            cnt[0] = cnt[1] = 0;
            reset.run();
            for (int i = 0; i < MCD_WORD_RAM_1M_SIZE - 1; i += 2) {
                //main
                int bank = w == W_1M_WR0_MAIN ? 0 : 1;
                int val = vals[bank][cnt[bank]];
                cnt[bank] = (cnt[bank] + 1) % vals0.length;
                mainWriteValAtIdx.accept(offsetm + i, val);
                wram1MCopy[bank].putShort(i, (short) val);

                //sub
                bank = w == W_1M_WR0_MAIN ? 1 : 0;
                val = vals[bank][cnt[bank]];
                cnt[bank] = (cnt[bank] + 1) % vals0.length;
                subWriteValAtIdx.accept(offsets1M + i, val);
                wram1MCopy[bank].putShort(i, (short) val);
            }
        };

        set1MVals.accept(ctx.wramSetup);
        setWramMain2M();

        //check main can see the correct interleaving
        Consumer<WramSetup> check2M = w -> {
            for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
                int bank = (i & 2) >> 1;
                cnt[bank] = (cnt[bank] + 1) % vals0.length;
                MdRuntimeData.setAccessTypeExt(w.cpu);
                int val = w.cpu == M68K ? mainCpuBus.read(offsetm + i, Size.WORD) :
                        subCpuBus.read(offsets2M + i, Size.WORD);
                int expVal = wram1MCopy[bank].getShort((i >> 1) - bank) & 0xFFFF;
                Assertions.assertEquals(expVal, val);
            }
        };
        check2M.accept(ctx.wramSetup);

        //1M mode again
        setWram1M_W0Main();
        set1MVals.accept(ctx.wramSetup);
        setWramSub2M();
        check2M.accept(ctx.wramSetup);
    }

    /**
     * Writing zero to the wordram control reg (from either side) does not affect 2M wordram assignment.
     * https://gendev.spritesmind.net/forum/viewtopic.php?f=5&t=3080
     */
    @Test
    public void testBitsNoChange() {
        setWramSub2M_NoChange();
        setWramMain2M_NoChange();
        setWramMain2M_NoChange();
    }

    public static void setWram1M_W0Main(McdLaunchContext lc) {
        MegaCdMemoryContext ctx = lc.memoryContext;
        if (ctx.wramSetup.mode == _2M) {
            //SUB sets 1M
            subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) | 4);
            Assertions.assertEquals(_1M, ctx.wramSetup.mode);
        }
        if (ctx.wramSetup == W_1M_WR0_SUB) {
            subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) & ~1);
        }
        Assertions.assertEquals(W_1M_WR0_MAIN, ctx.wramSetup);
    }

    public static void setWram1M_W0Sub(McdLaunchContext lc) {
        MegaCdMemoryContext ctx = lc.memoryContext;
        if (ctx.wramSetup.mode == _2M) {
            //SUB sets 1M
            subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) | 4);
            Assertions.assertEquals(_1M, ctx.wramSetup.mode);
        }
        if (ctx.wramSetup == W_1M_WR0_MAIN) {
            subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) | 1);
        }
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);
    }

    private void setWram1M_W0Main() {
        setWram1M_W0Main(lc);
    }

    private void setWram1M_W0Sub() {
        setWram1M_W0Sub(lc);
    }

    private void setWramMain2M() {
        setWramMain2M(lc);
    }

    public static void setWramMain2M(McdLaunchContext lc) {
        MegaCdMemoryContext ctx = lc.memoryContext;
        WramSetup ws = ctx.wramSetup;
        if (ws.mode == _1M) {
            subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) & ~4);
            Assertions.assertEquals(_2M, ctx.wramSetup.mode);
        }
        //assign to main
        subSetLsbFn.accept(lc.subBus, RET_BIT_MASK);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);

        Assertions.assertEquals(RET_BIT_MASK, mainGetLsbFn.apply(lc.mainBus) & 3);
    }

    public static void setWramSub2M(McdLaunchContext lc) {
        MegaCdMemoryContext ctx = lc.memoryContext;
        WramSetup ws = ctx.wramSetup;
        if (ws.mode == _1M) {
            subSetLsbFn.accept(lc.subBus, subGetLsbFn.apply(lc.subBus) & ~4);
            Assertions.assertEquals(_2M, ctx.wramSetup.mode);
        }
        //assign to sub
        mainSetLsbFn.accept(lc.mainBus, DMNA_BIT_MASK);
        Assertions.assertEquals(W_2M_SUB, ctx.wramSetup);

        Assertions.assertEquals(DMNA_BIT_MASK, mainGetLsbFn.apply(lc.mainBus) & 3);
    }

    private void setWramSub2M() {
        setWramSub2M(lc);
    }

    /**
     * Case 1 - assign 2M wordram to MAIN:
     * MAIN $a12003 := 0x00
     * SUB $ff8003 := 0x01
     * assert: MAIN $a12003 == 0x01
     */
    private void setWramMain2M_NoChange() {
        assert ctx.wramSetup.mode == _2M;
        WramSetup ws = ctx.wramSetup;
        //no change
        mainSetLsb.accept(0);
        Assertions.assertEquals(ws, ctx.wramSetup);
        //assign to main
        subSetLsb.accept(RET_BIT_MASK);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);

        Assertions.assertEquals(RET_BIT_MASK, mainGetLsb.get() & 3);
    }

    /**
     * Case 2 - assign 2M wordram to SUB:
     * MAIN $a12003 := 0x02
     * SUB $ff8003 := 0x00
     * assert: MAIN $a12003 == 0x02
     */
    private void setWramSub2M_NoChange() {
        assert ctx.wramSetup.mode == _2M;
        //assign to sub
        mainSetLsb.accept(DMNA_BIT_MASK);
        Assertions.assertEquals(W_2M_SUB, ctx.wramSetup);

        //no change
        subSetLsb.accept(0);
        Assertions.assertEquals(W_2M_SUB, ctx.wramSetup);

        Assertions.assertEquals(DMNA_BIT_MASK, mainGetLsb.get() & 3);
    }
}