package omegadrive.z80;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IMemory {
    int readByte(int address);

    int readWord(int address);

    void writeByte(int address, int data);

    void writeWord(int address, int data);
}
