package omegadrive.memory;

import omegadrive.util.Size;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public interface ReadableByteMemory {

    default int readRamByte(int address) {
        return read(address, Size.BYTE);
    }

    int read(int a, Size size);
}
