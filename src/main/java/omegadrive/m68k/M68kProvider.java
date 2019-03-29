package omegadrive.m68k;

import omegadrive.Device;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface M68kProvider extends Device {

    int VBLANK_INTERRUPT_LEVEL = 6;

    int HBLANK_INTERRUPT_LEVEL = 4;

    long getPC();

    boolean isStopped();

    boolean raiseInterrupt(int level);

    int runInstruction();
}
