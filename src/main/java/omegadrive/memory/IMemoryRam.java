package omegadrive.memory;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IMemoryRam {
//    int MEMORY_SIZE = 0x2000;

    int readRamByte(int address);

    void writeRamByte(int address, int data);

//    default void writeRamByte(int address, int data){
//        writeRamByte(address, (byte)(data & 0xFF));
//    }

    int[] getRamData();

    default int getRamSize() {
        return getRamData().length;
    }
}
