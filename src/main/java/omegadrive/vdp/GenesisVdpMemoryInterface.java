package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class GenesisVdpMemoryInterface implements VdpMemoryInterface {

    private static Logger LOG = LogManager.getLogger(GenesisVdpMemoryInterface.class.getSimpleName());

    public static boolean verbose = false || Genesis.verbose;

    private int[] vram = new int[VdpProvider.VDP_VRAM_SIZE];
    private int[] cram = new int[VdpProvider.VDP_CRAM_SIZE];
    private int[] vsram = new int[VdpProvider.VDP_VSRAM_SIZE];

    @Override
    public int readVideoRamWord(VdpProvider.VdpRamType vramType, int address) {
        int data = 0;
        //ignore A0, always use an even address
        address &= ~1;
        switch (vramType) {
            case VRAM:
                data = readVramWord(address);
                break;
            case VSRAM:
                data = readVsramWord(address);
                break;
            case CRAM:
                data = readCramWord(address);
                break;
            default:
                LOG.warn("Unexpected videoRam read: " + vramType);
        }
        return data;
    }

    @Override
    public int readVramByte(int address) {
        address &= (VdpProvider.VDP_VRAM_SIZE - 1);
        return vram[address];
    }

    //    The address register wraps past address FFFFh.
    @Override
    public void writeVramByte(int address, int data) {
        address &= (VdpProvider.VDP_VRAM_SIZE - 1);
        vram[address] = data & 0xFF;
    }

    @Override
    public void writeVideoRamWord(VdpProvider.VdpRamType vramType, int data, int address) {
        int word = data;
        int data1 = (word >> 8);
        int data2 = word & 0xFF;
        //ignore A0
        int index = address & ~1;

        switch (vramType) {
            case VRAM:
                boolean byteSwap = (address & 1) == 1;
                writeVramByte(index, byteSwap ? data2 : data1);
                writeVramByte(index + 1, byteSwap ? data1 : data2);
                break;
            case VSRAM:
                writeVsramByte(index, data1);
                writeVsramByte(index + 1, data2);
                break;
            case CRAM:
                writeCramByte(index, data1);
                writeCramByte(index + 1, data2);
                break;
            default:
                LOG.warn("Unexpected videoRam write: " + vramType);
        }
    }


    //    Even though there are 40 words of VSRAM, the address register will wrap
//    when it passes 7Fh. Writes to the addresses beyond 50h are ignored.
    private void writeVsramByte(int address, int data) {
        address &= 0x7F;
        if (address < VdpProvider.VDP_VSRAM_SIZE) {
            vsram[address] = data & 0xFF;
        } else {
            //Arrow Flash
            LOG.debug("Ignoring vsram write to address: {}", Integer.toHexString(address));
        }
    }

    //    The address register wraps past address 7Fh.
    private void writeCramByte(int address, int data) {
        address &= (VdpProvider.VDP_CRAM_SIZE - 1);
        cram[address] = data & 0xFF;
    }

    private int readVramWord(int address) {
        return readVramByte(address) << 8 | readVramByte(address + 1);
    }

    private int readCramByte(int address) {
        address &= (VdpProvider.VDP_CRAM_SIZE - 1);
        return cram[address];
    }

    private int readCramWord(int address) {
        return readCramByte(address) << 8 | readCramByte(address + 1);
    }

    private int readVsramByte(int address) {
        address &= 0x7F;
        if (address >= VdpProvider.VDP_VSRAM_SIZE) {
            address = 0;
        }
        return vsram[address];
    }

    private int readVsramWord(int address) {
        return readVsramByte(address) << 8 | readVsramByte(address + 1);
    }
}
