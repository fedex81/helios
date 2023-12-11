package omegadrive.bus.megacd;

import java.io.Serial;
import java.io.Serializable;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MegaCdMemoryContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 9209612516906245680L;

    public static final MegaCdMemoryContext NO_CONTEXT = new MegaCdMemoryContext();
    public static final int MCD_WORD_RAM_SIZE = 0x40_000;
    public static final int MCD_PRG_RAM_SIZE = 0x80_000;
    public static final int MCD_GATE_REGS_SIZE = 0x40;

    public static final int MCD_GATE_REGS_MASK = MCD_GATE_REGS_SIZE - 1;
    public static final int MCD_WORD_RAM_MASK = MCD_WORD_RAM_SIZE - 1;
    public static final int MCD_PRG_RAM_MASK = MCD_PRG_RAM_SIZE - 1;

    public byte[] prgRam, mainGateRegs, subGateRegs, wordRam;

    public MegaCdMemoryContext() {
        prgRam = new byte[MCD_PRG_RAM_SIZE];
        wordRam = new byte[MCD_WORD_RAM_SIZE];
        mainGateRegs = new byte[MCD_GATE_REGS_SIZE];
        subGateRegs = new byte[MCD_GATE_REGS_SIZE];
    }
}
