package omegadrive.z80;

import omegadrive.memory.IMemoryRam;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class Z80Memory implements IMemoryRam {

    public static int Z80_RAM_MEMORY_SIZE = 0x2000;

    private final int[] memory;

    public Z80Memory(int size) {
        memory = new int[size];
    }

    public Z80Memory() {
        this(Z80_RAM_MEMORY_SIZE);
    }

    @Override
    public int readRamByte(int address) {
        return memory[address] & 0xFF;
    }

    @Override
    public void writeRamByte(int address, int data) {
        memory[address] = data & 0xFF;
    }

    @Override
    public int[] getRamData() {
        return memory;
    }
}
