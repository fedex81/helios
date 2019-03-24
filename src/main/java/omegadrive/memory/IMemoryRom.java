package omegadrive.memory;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface IMemoryRom {

    int readRomByte(int address);

    int[] getRomData();

    default int getRomSize() {
        return getRomData().length;
    }
}
