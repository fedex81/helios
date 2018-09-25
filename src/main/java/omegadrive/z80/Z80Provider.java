package omegadrive.z80;

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

    void writeByte(int addr, long data);

    void writeWord(int addr, long data);
}
