package mcd.wram;

import mcd.McdRegTestBase;
import mcd.dict.MegaCdMemoryContext.WramSetup;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mcd.bus.McdWordRamHelper.getAddress;
import static mcd.bus.McdWordRamHelper.getBank;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdWordRam3Test extends McdRegTestBase {

    @BeforeEach
    public void setup() {
        setupBase();
    }

    @Test
    public void testInterleaved2MWrites() {
        McdWordRamTest.setWramMain2M(lc);
        writeAddressSize(M68K, 0x7F_FFF8, 0x1122, Size.WORD);
        writeAddressSize(M68K, 0x7F_FFFA, 0x3344, Size.WORD);
        writeAddressSize(M68K, 0x7F_FFFC, 0x5566, Size.WORD);
        writeAddressSize(M68K, 0x7F_FFFE, 0x7788, Size.WORD);
        McdWordRamTest.setWram1M_W0Sub(lc);
        Assertions.assertEquals(0x3344, readAddressSize(M68K, 0x7F_FFFC, Size.WORD));
        Assertions.assertEquals(0x77, readAddressSize(M68K, 0x7F_FFFE, Size.BYTE));
        Assertions.assertEquals(0x88, readAddressSize(M68K, 0x7F_FFFF, Size.BYTE));
        Assertions.assertEquals(0x3344_7788, readAddressSize(M68K, 0x7F_FFFC, Size.LONG));
        McdWordRamTest.setWram1M_W0Main(lc);
        Assertions.assertEquals(0x1122, readAddressSize(M68K, 0x7F_FFFC, Size.WORD));
        Assertions.assertEquals(0x55, readAddressSize(M68K, 0x7F_FFFE, Size.BYTE));
        Assertions.assertEquals(0x66, readAddressSize(M68K, 0x7F_FFFF, Size.BYTE));
        Assertions.assertEquals(0x1122_5566, readAddressSize(M68K, 0x7F_FFFC, Size.LONG));
    }

    @Test
    public void testYumemiCellLongWrite() {
        McdWordRamTest.setWram1M_W0Main(lc);
        writeAddressSize(M68K, 0x20_0000, 0x3130_3136, Size.LONG);
    }

    @Test
    public void testStarbladeCellByteWrite() {
        McdWordRamTest.setWram1M_W0Sub(lc);
        writeAddressSize(M68K, 0x20_6c10, 0x1234_5678, Size.LONG);
        writeAddressSize(M68K, 0x20_6c11, 0xAB, Size.BYTE);
        int res = readAddressSize(M68K, 0x20_6c10, Size.LONG);
        Assertions.assertEquals(0x12ab5678, res);
    }

    @Test
    public void test2MInterleave() {
        WramSetup ws = WramSetup.W_2M_MAIN;

        //word 0 -> bank0 @ 0, word 1 -> bank 1 @ 0
        Assertions.assertEquals(0, getBank(ws, M68K, 0));
        Assertions.assertEquals(0, getAddress(ws, 0));
        Assertions.assertEquals(1, getBank(ws, M68K, 2));
        Assertions.assertEquals(0, getAddress(ws, 2));

        //word 2 -> bank0 @ 2, word 3 -> bank 1 @ 2
        Assertions.assertEquals(0, getBank(ws, M68K, 4));
        Assertions.assertEquals(2, getAddress(ws, 4));
        Assertions.assertEquals(1, getBank(ws, M68K, 6));
        Assertions.assertEquals(2, getAddress(ws, 6));

    }
}