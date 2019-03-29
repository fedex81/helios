package omegadrive.memory;

import omegadrive.Device;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IMemoryRam extends Device {

    int readRamByte(int address);

    void writeRamByte(int address, int data);

    int[] getRamData();

    default int getRamSize() {
        return getRamData().length;
    }
}
