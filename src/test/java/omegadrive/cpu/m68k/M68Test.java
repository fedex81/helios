package omegadrive.cpu.m68k;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.SystemTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M68Test
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class M68Test {

    private M68kProvider provider;

    @BeforeEach
    public void setup() {
        IMemoryProvider memoryProvider = MemoryProvider.createMdInstance();
        //fill the vector area with non-zero
        IntStream.range(0, 0x200).forEach(i -> memoryProvider.getRomData()[i] = 0x11);

        MdMainBusProvider bus = SystemTestUtil.setupNewMdSystem(memoryProvider);
        Optional<M68kProvider> optM = bus.getBusDeviceIfAny(M68kProvider.class);
        assertTrue(optM.isPresent());
        provider = optM.get();
    }

    @Test
    public void testRaiseInterrupt() {
        assertTrue(provider.raiseInterrupt(4));
        assertFalse(provider.raiseInterrupt(3));
        //Lemmings
        assertFalse(provider.raiseInterrupt(4));
        assertTrue(provider.raiseInterrupt(6));
        assertFalse(provider.raiseInterrupt(4));
    }
}
