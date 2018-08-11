package omegadrive.z80;


/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class Z80SimpleMemory implements IMemory {

    public static final int MEMORY_SIZE = 0x10000;

    private final int[] memory = new int[MEMORY_SIZE];

    @Override
    public int readByte(int address) {
        return memory[address];
    }

    @Override
    public int readWord(int address) {
        return readByte(address) + readByte(address + 1) * 0xFF;
    }

    @Override
    public void writeByte(int address, int data) {
        if (data < 0) {
            data += 0x80;
        }
        memory[address] = data;
    }

    @Override
    public void writeWord(int address, int data) {
        writeByte(address, (data & 0x00FF));
        address = (address + 1) & 65535;
        data = (data >>> 8);
        writeByte(address, data);
    }

    public int[] getMemory() {
        return memory;
    }
}
