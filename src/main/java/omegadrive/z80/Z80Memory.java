package omegadrive.z80;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class Z80Memory implements IMemory {

    private final byte[] memory;

    public Z80Memory(int size) {
        memory = new byte[size];
    }

    public Z80Memory() {
        this(MEMORY_SIZE);
    }

    @Override
    public int readByte(int address) {
        return memory[address] & 0xFF;
    }

    @Override
    public void writeByte(int address, int data) {
        memory[address] = (byte) (data & 0xFF);
    }

    @Override
    public byte[] getData() {
        return memory;
    }
}
