package omegadrive.memory;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface MemoryProvider {

    int M68K_RAM_SIZE = 0x10000; //64k
    int CHECKSUM_START_ADDRESS = 0x18E;

    static MemoryProvider createInstance(int[] data) {
        GenesisMemoryProvider memory = new GenesisMemoryProvider();
        memory.setCartridge(data);
        return memory;
    }

    long readCartridgeByte(long address);

    long readCartridgeWord(long address);

    int readRamByte(int address);

    void writeRamByte(int address, long data);

    void setCartridge(int[] data);

    void setChecksumRomValue(long value);

    int getRomSize();
}
