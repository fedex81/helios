package omegadrive.bus.megacd;

import mcd.dict.MegaCdDict;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._1M;
import static mcd.dict.MegaCdMemoryContext.WordRamMode._2M;
import static mcd.dict.MegaCdMemoryContext.WramSetup.*;
import static omegadrive.bus.model.GenesisBusProvider.MEGA_CD_EXP_START;

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

    Consumer<Integer> mainThrows = val ->
            Assertions.assertThrowsExactly(AssertionError.class,
                    () -> mainCpuBus.write(MAIN_MEM_MODE_REG + 1, val, Size.BYTE));

    Consumer<Integer> mainSetLsb = val -> mainCpuBus.write(MAIN_MEM_MODE_REG + 1, val, Size.BYTE);
    Consumer<Integer> subSetLsb = val -> subCpuBus.write(SUB_MEM_MODE_REG + 1, val, Size.BYTE);
    Supplier<Integer> mainGetLsb = () -> mainCpuBus.read(MAIN_MEM_MODE_REG + 1, Size.BYTE);
    Supplier<Integer> subGetLsb = () -> subCpuBus.read(SUB_MEM_MODE_REG + 1, Size.BYTE);

    //only for RAM writes, do not use for regs
    BiConsumer<Integer, Integer> mainWriteValAtIdx = (idx, val) -> {
        mainCpuBus.write(idx, val, Size.WORD);
        Assertions.assertEquals(val & 0xFFFF, mainCpuBus.read(idx, Size.WORD));
    };

    //only for RAM writes, do not use for regs
    BiConsumer<Integer, Integer> subWriteValAtIdx = (idx, val) -> {
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

    /**
     * Writing zero to the wordram control reg (from either side) does not affect 2M wordram assignment.
     * https://gendev.spritesmind.net/forum/viewtopic.php?f=5&t=3080
     */
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
        mainThrows.accept(reg | 4);
        Assertions.assertEquals(W_2M_MAIN, ctx.wramSetup);

        //SUB sets 1M
        subSetLsb.accept(sreg | 4);
        assert (reg & 1) == 1; //RET=1
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);

        //SUB requests switch, RET = 0
        subSetLsb.accept(subGetLsb.get() & ~1);
        Assertions.assertEquals(W_1M_WR0_MAIN, ctx.wramSetup);

        //MAIN cannot set RET=1 (ie. request switch)
        mainThrows.accept(mainGetLsb.get() | 1);
        Assertions.assertEquals(W_1M_WR0_MAIN, ctx.wramSetup);

        //SUB requests switch, RET = 1
        subSetLsb.accept(subGetLsb.get() | 1);
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);
    }

    @Test
    public void testWRAMDataOnSwitch_2M() {
        setWramMain2M();
        assert ctx.wramSetup == W_2M_MAIN;
        int offsetm = MegaCdDict.START_MCD_WORD_RAM_MODE1;
        int offsets = START_MCD_SUB_WORD_RAM_2M;
        for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
            mainWriteValAtIdx.accept(offsetm + i, i);
        }
        setWramSub2M();
        for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
            //sub can read what main wrote
            Assertions.assertEquals(i & 0xFFFF, subCpuBus.read(offsets + i, Size.WORD));
            //sub modifies
            subWriteValAtIdx.accept(offsets + i, (i + 1) & 0xFFFF);
        }
        setWramMain2M();
        //main read
        for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
            Assertions.assertEquals((i + 1) & 0xFFFF, mainCpuBus.read(offsetm + i, Size.WORD));
        }
    }

    @Test
    public void testWRAMDataOnSwitch_1M() {
        setWram1M_W0Main();
        int offsetm = MegaCdDict.START_MCD_WORD_RAM_MODE1;
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
            Assertions.assertEquals((i + 1) & 0xFFFF, mainCpuBus.read(offsetm + i, Size.WORD));
            //sub can read what main wrote
            Assertions.assertEquals(i & 0xFFFF, subCpuBus.read(offsets + i, Size.WORD));
            //main writes to bank1
            mainWriteValAtIdx.accept(offsetm + i, (i + 2) & 0xFFFF);
            //sub writes to bank0
            subWriteValAtIdx.accept(offsets + i, (i + 3) & 0xFFFF);
        }
        setWram1M_W0Main();
        for (int i = 0; i < MCD_WORD_RAM_1M_SIZE - 1; i += 2) {
            //main can read what sub wrote, bank0
            Assertions.assertEquals((i + 3) & 0xFFFF, mainCpuBus.read(offsetm + i, Size.WORD));
            //sub can read what main wrote, bank1
            Assertions.assertEquals((i + 2) & 0xFFFF, subCpuBus.read(offsets + i, Size.WORD));
        }
    }

    //TODO
    @Test
    public void testWRAMDataOnSwitch_2M_1M() {
        setWramMain2M();
        assert ctx.wramSetup == W_2M_MAIN;
        int offsetm = MegaCdDict.START_MCD_WORD_RAM_MODE1;
        for (int i = 0; i < MCD_WORD_RAM_2M_SIZE - 1; i += 2) {
            mainWriteValAtIdx.accept(offsetm + i, i);
        }
        setWram1M_W0Main();
        for (int i = 0; i < MCD_WORD_RAM_1M_SIZE - 1; i += 2) {

        }
    }

    private void setWram1M_W0Main() {
        if (ctx.wramSetup.mode == _2M) {
            //SUB sets 1M
            subSetLsb.accept(subGetLsb.get() | 4);
            Assertions.assertEquals(_1M, ctx.wramSetup.mode);
        }
        if (ctx.wramSetup == W_1M_WR0_SUB) {
            subSetLsb.accept(subGetLsb.get() & ~1);
        }
        Assertions.assertEquals(W_1M_WR0_MAIN, ctx.wramSetup);
    }

    private void setWram1M_W0Sub() {
        if (ctx.wramSetup.mode == _2M) {
            //SUB sets 1M
            subSetLsb.accept(subGetLsb.get() | 4);
            Assertions.assertEquals(_1M, ctx.wramSetup.mode);
        }
        if (ctx.wramSetup == W_1M_WR0_MAIN) {
            subSetLsb.accept(subGetLsb.get() | 1);
        }
        Assertions.assertEquals(W_1M_WR0_SUB, ctx.wramSetup);
    }

    private void setWramMain2M() {
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

    private void setWramSub2M() {
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