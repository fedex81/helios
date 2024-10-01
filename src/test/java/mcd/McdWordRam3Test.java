package mcd;

import omegadrive.util.BufferUtil;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    public void testYumemiCellByteRead() {
        MdRuntimeData.setAccessTypeExt(BufferUtil.CpuDeviceAccess.M68K);
        McdWordRamTest.setWramMain2M(lc);
        lc.mainBus.write(0x7F_FFFA, 0x1122, Size.WORD);
        lc.mainBus.write(0x7F_FFFC, 0x3344, Size.WORD);
        lc.mainBus.write(0x7F_FFFE, 0x5566, Size.WORD);
        McdWordRamTest.setWram1M_W0Sub(lc);
        Assertions.assertEquals(0x33, lc.mainBus.read(0x7F_FFFE, Size.BYTE));
        Assertions.assertEquals(0x44, lc.mainBus.read(0x7F_FFFF, Size.BYTE));
    }

    @Test
    public void testYumemiCellLongWrite() {
        MdRuntimeData.setAccessTypeExt(BufferUtil.CpuDeviceAccess.M68K);
        McdWordRamTest.setWram1M_W0Main(lc);
        lc.mainBus.write(0x20_0000, 0x3130_3136, Size.LONG);
    }

    @Test
    public void testStarbladeCellByteWrite() {
        MdRuntimeData.setAccessTypeExt(BufferUtil.CpuDeviceAccess.M68K);
        McdWordRamTest.setWram1M_W0Sub(lc);

        lc.mainBus.write(0x20_6c10, 0x1234_5678, Size.LONG);
        lc.mainBus.write(0x20_6c11, 0xAB, Size.BYTE);
        int res = lc.mainBus.read(0x20_6c10, Size.LONG);
        Assertions.assertEquals(0x12ab5678, res);
    }
}