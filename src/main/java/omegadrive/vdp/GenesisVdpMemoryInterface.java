package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.util.CramViewer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

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
    private static int EVEN_VALUE_MASK = ~1;

    private int[] vram;
    private int[] cram;
    private int[] vsram;

    private CramViewer cramViewer;

    private GenesisVdpMemoryInterface() {
//        cramViewer = CramViewer.createInstance(this);
    }

    public static GenesisVdpMemoryInterface createInstance() {
        GenesisVdpMemoryInterface i = new GenesisVdpMemoryInterface();
        i.vram = new int[VdpProvider.VDP_VRAM_SIZE];
        i.cram = new int[VdpProvider.VDP_CRAM_SIZE];
        i.vsram = new int[VdpProvider.VDP_VSRAM_SIZE];
        return i;
    }

    public static GenesisVdpMemoryInterface createInstance(int[] vram, int[] cram, int[] vsram) {
        GenesisVdpMemoryInterface i = new GenesisVdpMemoryInterface();
        i.vram = Arrays.copyOf(vram, vram.length);
        i.cram = Arrays.copyOf(cram, cram.length);
        i.vsram = Arrays.copyOf(vsram, vsram.length);
        return i;
    }

    //TODO: shouldnt this flip the byte like in readVramWord
    //TODO: DMA is doing it but it should be done here
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
        int index = address & EVEN_VALUE_MASK;

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
    @Override
    public void writeVsramByte(int address, int data) {
        address &= 0x7F;
        if (address < VdpProvider.VDP_VSRAM_SIZE) {
            vsram[address] = data & 0xFF;
        } else {
            //Arrow Flash
            LOG.debug("Ignoring vsram write to address: {}", Integer.toHexString(address));
        }
    }

    //    The address register wraps past address 7Fh.
    @Override
    public void writeCramByte(int address, int data) {
        address &= (VdpProvider.VDP_CRAM_SIZE - 1);
        cram[address] = data & 0xFF;
//        cramViewer.update();
    }



    @Override
    public int readCramByte(int address) {
        address &= (VdpProvider.VDP_CRAM_SIZE - 1);
        return cram[address];
    }


    @Override
    public int readVsramByte(int address) {
        address &= 0x7F;
        if (address >= VdpProvider.VDP_VSRAM_SIZE) {
            address = 0;
        }
        return vsram[address];
    }

    @Override
    public int readVramWord(int address) {
        //ignore A0, always use an even address
        address &= EVEN_VALUE_MASK;
        return readVramByte(address) << 8 | readVramByte(address + 1);
    }

    @Override
    public int readVsramWord(int address) {
        //ignore A0, always use an even address
        address &= EVEN_VALUE_MASK;
        return readVsramByte(address) << 8 | readVsramByte(address + 1);
    }

    @Override
    public int readCramWord(int address) {
        //ignore A0, always use an even address
        address &= EVEN_VALUE_MASK;
        return readCramByte(address) << 8 | readCramByte(address + 1);
    }
}
