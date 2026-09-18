package omegadrive.cpu.z80;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.bus.model.MdZ80BusProvider;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static omegadrive.bus.model.MdMainBusProvider.Z80_BUS_REQ_CONTROL_START;
import static omegadrive.bus.model.MdMainBusProvider.Z80_RESET_CONTROL_START;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Z80ResetTest {

    MdZ80BusProvider z80bus;
    MdMainBusProvider mainBus;
    Z80Provider z80;

    static final int RESET_ON = 0, BUSREQ_OFF = 0, RESET_OFF = 1, BUSREQ_ON = 1;

    @BeforeEach
    public void setup() {
        mainBus = SystemTestUtil.setupNewMdSystem();
        Optional<MdZ80BusProvider> optBus = mainBus.getBusDeviceIfAny(MdZ80BusProvider.class);
        assertTrue(optBus.isPresent());
        z80bus = optBus.get();
        Optional<Z80Provider> opt = mainBus.getBusDeviceIfAny(Z80Provider.class);
        assertTrue(opt.isPresent());
        z80 = opt.get();
    }

    @Test
    public void testZ80MultipleUnReset() {
        mainBus.setZ80ResetState(true);
        mainBus.setZ80BusRequested(false);
        Assertions.assertFalse(mainBus.isZ80Running());


        mainBus.setZ80ResetState(false);
        assertTrue(mainBus.isZ80Running());
        assertTrue(z80.getZ80State().getRegPC() == 0);

        //change PC
        z80.executeInstruction();
        assertTrue(z80.getZ80State().getRegPC() > 0);

        //set reset=false again -> no op
        mainBus.setZ80ResetState(false);
        assertTrue(z80.getZ80State().getRegPC() > 0);
    }

    //MegaMan4 SequelWars Demo, make sure z80 is running at the end of the sequence
    @Test
    public void testZ80ResetMegaMan4() {
        mainBus.setZ80ResetState(false);
        mainBus.setZ80BusRequested(true);
        Assertions.assertFalse(mainBus.isZ80Running());
        //change PC
        z80.executeInstruction();
        assertTrue(z80.getZ80State().getRegPC() > 0);

        Assertions.assertFalse(mainBus.isZ80ResetState());
        assertTrue(mainBus.isZ80BusRequested());

        mainBus.write(Z80_BUS_REQ_CONTROL_START, BUSREQ_OFF, Size.BYTE);
        Assertions.assertFalse(mainBus.isZ80BusRequested());
        assertTrue(mainBus.isZ80Running());

        mainBus.write(Z80_RESET_CONTROL_START, RESET_ON, Size.BYTE);
        assertTrue(mainBus.isZ80ResetState());
        Assertions.assertFalse(mainBus.isZ80Running());

        mainBus.write(Z80_BUS_REQ_CONTROL_START, BUSREQ_ON, Size.BYTE);
        assertTrue(mainBus.isZ80BusRequested());
        Assertions.assertFalse(mainBus.isZ80Running());

        mainBus.write(Z80_RESET_CONTROL_START, RESET_ON, Size.BYTE);
        assertTrue(mainBus.isZ80ResetState());
        Assertions.assertFalse(mainBus.isZ80Running());

        mainBus.write(Z80_RESET_CONTROL_START, RESET_OFF, Size.BYTE);
        Assertions.assertFalse(mainBus.isZ80ResetState());
        //z80 should run the reset method now, PC = 0
        assertTrue(z80.getZ80State().getRegPC() == 0);

        mainBus.write(Z80_BUS_REQ_CONTROL_START, BUSREQ_OFF, Size.BYTE);
        Assertions.assertFalse(mainBus.isZ80BusRequested());
        //now Z80 should be running
        assertTrue(mainBus.isZ80Running());

        mainBus.write(Z80_RESET_CONTROL_START, RESET_ON, Size.BYTE);
        assertTrue(mainBus.isZ80ResetState());
        Assertions.assertFalse(mainBus.isZ80Running());
    }
}
