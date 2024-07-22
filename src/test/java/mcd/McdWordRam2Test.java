package mcd;

import mcd.dict.MegaCdDict;
import omegadrive.bus.md.GenesisBus;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mcd.McdWordRamTest.SUB_MEM_MODE_REG;
import static mcd.dict.MegaCdDict.*;
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

    private void write1mString(CpuDeviceAccess cpu, String s, Size size) {
        assert lc.memoryContext.wramSetup.mode == _1M;
        int bank = getBank1M(lc.memoryContext.wramSetup, cpu);
        GenesisBus bus = cpu == M68K ? mainCpuBus : subCpuBus;
        int baseAddr = START_MCD_SUB_WORD_RAM_1M;
        if (cpu == M68K) {
            baseAddr = bank == 0 ? START_MCD_WORD_RAM : START_MCD_WORD_RAM + MCD_WORD_RAM_1M_SIZE;
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
        int bank = getBank1M(lc.memoryContext.wramSetup, cpu);
        GenesisBus bus = cpu == M68K ? mainCpuBus : subCpuBus;
        int baseAddr = START_MCD_SUB_WORD_RAM_1M;
        if (cpu == M68K) {
            baseAddr = bank == 0 ? START_MCD_WORD_RAM : START_MCD_WORD_RAM + MCD_WORD_RAM_1M_SIZE;
        }
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