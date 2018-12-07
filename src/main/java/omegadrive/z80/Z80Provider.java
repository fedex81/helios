package omegadrive.z80;

import omegadrive.util.Size;
import omegadrive.z80.jsanchezv.Z80State;

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

    void interrupt();

    int readMemory(int address);

    void writeMemory(int address, long data, Size size);

    Z80Memory getZ80Memory();

    void loadZ80State(Z80State z80State);
}
