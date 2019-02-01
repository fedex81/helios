package omegadrive.z80;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IMemory {
    int MEMORY_SIZE = 0x2000;

    int readByte(int address);

    void writeByte(int address, int data);

    byte[] getData();
}
