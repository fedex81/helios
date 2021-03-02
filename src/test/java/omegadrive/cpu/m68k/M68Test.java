package omegadrive.cpu.m68k;

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.SystemProvider;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.model.GenesisVdpProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * M68Test
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class M68Test {

    private M68kProvider provider;

    @Before
    public void setup() {
        GenesisBusProvider bus = GenesisBusProvider.createBus();
        IMemoryProvider memoryProvider = MemoryProvider.createGenesisInstance();
        GenesisVdpProvider vdpProvider = GenesisVdpProvider.createVdp(bus);
        memoryProvider.setRomData(new int[1024]);
        memoryProvider.getRomData()[0x3c] = 1;
        provider = MC68000Wrapper.createInstance(bus);
        SystemProvider systemProvider = MdVdpTestUtil.createTestGenesisProvider();
        bus.attachDevice(memoryProvider).attachDevice(provider).attachDevice(systemProvider).attachDevice(vdpProvider);
        bus.init();
    }

    @Test
    public void testRaiseInterrupt() {
        Assert.assertTrue(provider.raiseInterrupt(4));
        Assert.assertFalse(provider.raiseInterrupt(3));
        //Lemmings
        Assert.assertFalse(provider.raiseInterrupt(4));
        Assert.assertTrue(provider.raiseInterrupt(6));
        Assert.assertFalse(provider.raiseInterrupt(4));
    }
}
