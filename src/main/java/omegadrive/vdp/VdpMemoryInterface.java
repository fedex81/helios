package omegadrive.vdp;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface VdpMemoryInterface {

    void writeVramByte(int address, int data);

    int readVramByte(int address);

    void writeVideoRamWord(VdpProvider.VdpRamType vramType, int data, int address);

    int readVideoRamWord(VdpProvider.VdpRamType vramType, int address);

}
