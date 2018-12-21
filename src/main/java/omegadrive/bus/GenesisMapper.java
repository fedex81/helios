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

    GenesisMapper NO_MAPPER = new GenesisMapper() {
        @Override
        public long readData(long address, Size size) {
            return 0;
        }

        @Override
        public void writeData(long address, long data, Size size) {

        }
    };

    enum SramMode {DISABLE, READ_ONLY, READ_WRITE}

    long readData(long address, Size size);

    void writeData(long address, long data, Size size);

    default void writeBankData(long addressL, long data) {
        //DO NOTHING
    }

    default void setSramMode(SramMode sramMode) {
        //DO NOTHING
    }

    default void closeRom() {
        //DO NOTHING
    }
}
