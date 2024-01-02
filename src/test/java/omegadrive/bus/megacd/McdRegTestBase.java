package omegadrive.bus.megacd;

import mcd.McdDeviceHelper;
import mcd.bus.MegaCdMainCpuBus;
import mcd.bus.MegaCdSubCpuBus;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.cpu.m68k.MC68000Wrapper;
import org.junit.jupiter.api.BeforeEach;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdRegTestBase {

    protected McdDeviceHelper.McdLaunchContext lc;
    MegaCdMemoryContext ctx;
    MegaCdMainCpuBus mainCpuBus;
    MegaCdSubCpuBus subCpuBus;

    MC68000Wrapper subCpu;

    @BeforeEach
    public void setup() {
        lc = McdDeviceHelper.setupDevices();
        ctx = lc.memoryContext;
        mainCpuBus = lc.mainBus;
        subCpuBus = lc.subBus;
        subCpu = lc.subCpu;
    }
}
