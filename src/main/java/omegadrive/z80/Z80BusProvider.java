package omegadrive.z80;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public interface Z80BusProvider {

    int readMemory(int address);

    void writeMemory(int address, int data);

    void setRomBank68kSerial(int romBank68kSerial);

    int getRomBank68kSerial();

}
