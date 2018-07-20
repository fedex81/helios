package omegadrive.bus;

import omegadrive.util.Size;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface GenesisMapper {

    long readData(long address, Size size);

    void writeData(long address, long data, Size size);

    default void writeBankData(long addressL, long data) {
        //DO NOTHING
    }
}
