package omegadrive.z80;

import omegadrive.Device;
import omegadrive.bus.BaseBusProvider;
import z80core.Z80State;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface Z80Provider extends Device {

    int executeInstruction();

    boolean interrupt(boolean value);

    void triggerNMI();

    boolean isHalted();

    int readMemory(int address);

    void writeMemory(int address, int data);

    BaseBusProvider getZ80BusProvider();

    void loadZ80State(Z80State z80State);

    Z80State getZ80State();
}
