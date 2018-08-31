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

    boolean isStopped();

    void raiseInterrupt(int level);

    void reset();

    void initialize();

    int runInstruction();

    void startMonitor();
}
