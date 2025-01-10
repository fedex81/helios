package omegadrive.cpu.m68k;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.util.SystemTestUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

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
        MdMainBusProvider bus = SystemTestUtil.setupNewMdSystem();
        Optional<M68kProvider> optM = bus.getBusDeviceIfAny(M68kProvider.class);
        Assert.assertTrue(optM.isPresent());
        provider = optM.get();
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
