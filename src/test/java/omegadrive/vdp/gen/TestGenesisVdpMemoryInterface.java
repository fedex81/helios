package omegadrive.vdp.gen;

import omegadrive.vdp.model.GenesisVdpProvider;

/**
 * TestGenesisVdpMemoryInterface
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class TestGenesisVdpMemoryInterface extends GenesisVdpMemoryInterface {
    int vramWrites, vramReads;
    int cramReads, cramWrites;
    int vsramReads, vsramWrites;

    public TestGenesisVdpMemoryInterface() {
        super();
        init();
    }

    @Override
    public int readVramByte(int address) {
        vramReads++;
        return super.readVramByte(address);
    }

    @Override
    public int readCramByte(int address) {
        cramReads++;
        return super.readCramByte(address);
    }

    @Override
    public int readVsramByte(int address) {
        vsramReads++;
        return super.readVsramByte(address);
    }

    @Override
    public void writeVramByte(int address, int data) {
        super.writeVramByte(address, data);
        vramWrites++;
    }

    @Override
    public void writeCramByte(int address, int data) {
        super.writeCramByte(address, data);
        cramWrites++;
    }

    @Override
    public void writeVsramByte(int address, int data) {
        super.writeVsramByte(address, data);
        vsramWrites++;
    }

    public int getMemoryReads(GenesisVdpProvider.VdpRamType ramType) {
        switch (ramType) {
            case VRAM:
                return vramReads;
            case CRAM:
                return cramReads;
            case VSRAM:
                return vsramReads;
        }
        return -1;
    }

    public int getMemoryWrites(GenesisVdpProvider.VdpRamType ramType) {
        switch (ramType) {
            case VRAM:
                return vramWrites;
            case CRAM:
                return cramWrites;
            case VSRAM:
                return vsramWrites;
        }
        return -1;
    }

    public void resetStats() {
        vramWrites = vramReads = cramReads = cramWrites = vsramWrites = vsramReads = 0;
    }
}
