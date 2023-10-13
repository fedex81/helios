package omegadrive.cart.mapper.md.eeprom;

import omegadrive.util.Size;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public interface EepromBase {

    EepromBase NO_OP = new EepromBase() {

        public int readEeprom(int address, Size size) {
            return 0;
        }

        public void writeEeprom(int address, int data, Size size) {
        }

        @Override
        public void setSram(byte[] sram) {
        }
    };

    int readEeprom(int address, Size size);

    void writeEeprom(int address, int data, Size size);

    void setSram(byte[] sram);
}
