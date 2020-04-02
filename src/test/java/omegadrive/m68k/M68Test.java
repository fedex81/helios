package omegadrive.m68k;

import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
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
        memoryProvider.setRomData(new int[1024]);
        memoryProvider.getRomData()[0x3c] = 1;
        bus.attachDevice(memoryProvider);
        provider = MC68000Wrapper.createInstance(bus, false);
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
