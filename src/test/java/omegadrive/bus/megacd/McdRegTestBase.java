package omegadrive.bus.megacd;

import mcd.MegaCd;
import mcd.bus.MegaCdMainCpuBus;
import mcd.bus.MegaCdSubCpuBus;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.util.BufferUtil;
import org.junit.jupiter.api.BeforeEach;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdRegTestBase {

    MegaCdMemoryContext ctx;
    MegaCdMainCpuBus mainCpuBus;
    MegaCdSubCpuBus subCpuBus;

    MC68000Wrapper subCpu;

    @BeforeEach
    public void setup() {
        ctx = new MegaCdMemoryContext();
        mainCpuBus = new MegaCdMainCpuBus(ctx);
        subCpuBus = new MegaCdSubCpuBus(ctx);
        subCpu = new MC68000Wrapper(BufferUtil.CpuDeviceAccess.SUB_M68K, subCpuBus);
        subCpuBus.attachDevice(subCpu);
        MegaCd.subCpuBusHack = subCpuBus;
    }
}
