package omegadrive.vdp.md;

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
    public byte readVramByte(int address) {
        vramReads++;
        return super.readVramByte(address);
    }

    @Override
    public byte readCramByte(int address) {
        cramReads++;
        return super.readCramByte(address);
    }

    @Override
    public byte readVsramByte(int address) {
        vsramReads++;
        return super.readVsramByte(address);
    }

    @Override
    public void writeVideoRamByte(GenesisVdpProvider.VdpRamType vramType, int address, byte data) {
        super.writeVideoRamByte(vramType, address, data);
    }

    @Override
    public void writeVramByte(int address, byte data) {
        super.writeVramByte(address, data);
        vramWrites++;
    }

    @Override
    public void writeCramByte(int address, byte data) {
        super.writeCramByte(address, data);
        cramWrites++;
    }

    @Override
    public void writeVsramByte(int address, byte data) {
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
