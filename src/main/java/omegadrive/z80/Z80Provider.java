package omegadrive.z80;

import omegadrive.bus.BaseBusProvider;
import z80core.Z80State;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface Z80Provider {

    void initialize();

    int executeInstruction();

    void requestBus();

    void unrequestBus();

    boolean isBusRequested();

    void reset();

    boolean isReset();

    void disableReset();

    boolean isRunning();

    boolean isHalted();

    boolean interrupt(boolean value);

    void triggerNMI();

    int readMemory(int address);

    void writeMemory(int address, int data);

    BaseBusProvider getZ80BusProvider();

    void loadZ80State(Z80State z80State);

    Z80State getZ80State();
}
