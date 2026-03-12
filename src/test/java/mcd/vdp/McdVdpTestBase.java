package mcd.vdp;

import mcd.McdRegTestBase;
import mcd.util.McdTestUtil;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.system.SystemProvider;
import omegadrive.vdp.md.MdVdp;
import omegadrive.vdp.model.MdVdpProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdVdpTestBase extends McdRegTestBase {

    protected MdVdpProvider vdpProvider;
    protected M68kProvider cpu;

    @BeforeEach
    public void setupBase() {
        SystemProvider sp = McdTestUtil.createTestMcdProvider();
        super.setupBase();
        lc.mainBus.attachDevice(sp);
        vdpProvider = MdVdp.createInstance(lc.mainBus);
        cpu = MC68000Wrapper.createInstance(lc.mainBus);
        lc.mainBus.attachDevice(cpu);
        Assertions.assertNotNull(vdpProvider);
    }
}
