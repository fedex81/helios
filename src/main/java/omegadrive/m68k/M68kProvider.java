package omegadrive.m68k;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface M68kProvider {

    int VBLANK_INTERRUPT_LEVEL = 6;

    int HBLANK_INTERRUPT_LEVEL = 4;

    long getPC();

    void setStop(boolean value);

    boolean isStopped();

    int getInterruptMask();

    void raiseInterrupt(int level);

    void reset();

    void initialize();

    int runInstruction();
}
