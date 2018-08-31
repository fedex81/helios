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

    static MemoryProvider createInstance(int[] data) {
        GenesisMemoryProvider memory = new GenesisMemoryProvider();
        memory.setCartridge(data);
        return memory;
    }

    long readCartridgeByte(long address);

    long readCartridgeWord(long address);

    long readRam(long address);

    void writeRam(long address, long data);

    void setCartridge(int[] data);

    int getRomSize();
}
