/*
 * GenesisVdpMemoryInterface
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:37
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.vdp.gen;

import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.stream.IntStream;

import static omegadrive.vdp.model.GenesisVdpProvider.MAX_SPRITES_PER_FRAME_H40;

public class GenesisVdpMemoryInterface implements VdpMemoryInterface {

    public final static boolean verbose = false;
    private final static Logger LOG = LogManager.getLogger(GenesisVdpMemoryInterface.class.getSimpleName());
    private static int EVEN_VALUE_MASK = ~1;

    private int[] vram;
    private int[] cram;
    private int[] vsram;
    private int[] javaPalette;
    private int[] satCache = new int[MAX_SPRITES_PER_FRAME_H40 * 8]; //8 bytes per sprite
    private int satBaseAddress = 0, satEndAddress = satBaseAddress + satCache.length;

    private VdpColorMapper colorMapper;

    protected GenesisVdpMemoryInterface() {
        colorMapper = VdpColorMapper.getInstance();
    }

    public static GenesisVdpMemoryInterface createInstance() {
        GenesisVdpMemoryInterface i = new GenesisVdpMemoryInterface();
        i.init();
        return i;
    }

    public static GenesisVdpMemoryInterface createInstance(int[] vram, int[] cram, int[] vsram) {
        GenesisVdpMemoryInterface i = new GenesisVdpMemoryInterface();
        i.vram = Arrays.copyOf(vram, vram.length);
        i.cram = Arrays.copyOf(cram, cram.length);
        i.vsram = Arrays.copyOf(vsram, vsram.length);
        i.initPalette();
        return i;
    }

    protected void init() {
        vram = new int[GenesisVdpProvider.VDP_VRAM_SIZE];
        cram = new int[GenesisVdpProvider.VDP_CRAM_SIZE];
        vsram = new int[GenesisVdpProvider.VDP_VSRAM_SIZE];
        initPalette();
    }

    private void paletteUpdate(int cramAddress) {
        javaPalette[cramAddress >> 1] = colorMapper.getColor(cram[cramAddress] << 8 | cram[cramAddress + 1]);
    }

    private void initPalette() {
        javaPalette = new int[cram.length / 2];
        IntStream.range(0, javaPalette.length).forEach(i -> paletteUpdate(i << 1));
    }

    //TODO: shouldnt this flip the byte like in readVramWord
    //TODO: DMA is doing it but it should be done here
    @Override
    public int readVramByte(int address) {
        address &= (GenesisVdpProvider.VDP_VRAM_SIZE - 1);
        return vram[address];
    }

    //    The address register wraps past address FFFFh.
    @Override
    public void writeVramByte(int address, int data) {
        address &= (GenesisVdpProvider.VDP_VRAM_SIZE - 1);
        vram[address] = data & 0xFF;
        updateSatCache(address, data & 0xFF);
    }

    @Override
    public void writeVideoRamWord(GenesisVdpProvider.VdpRamType vramType, int data, int address) {
        int data1 = (data >> 8);
        int data2 = data & 0xFF;
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
                LOG.warn("Unexpected videoRam write: {}", vramType);
        }
    }

    private void updateSatCache(int vramAddress, int value) {
        if (vramAddress >= satBaseAddress && vramAddress < satEndAddress) {
            satCache[vramAddress - satBaseAddress] = value;
        }
    }

    @Override
    public void setSatBaseAddress(int satBaseAddress) {
        this.satBaseAddress = satBaseAddress;
        this.satEndAddress = satBaseAddress + satCache.length;
    }

    @Override
    public int[] getSatCache() {
        return satCache;
    }

    @Override
    public int[] getCram() {
        return cram;
    }

    @Override
    public int[] getVram() {
        return vram;
    }

    @Override
    public int[] getVsram() {
        return vsram;
    }

    @Override
    public int[] getJavaColorPalette() {
        return javaPalette;
    }

    //    Even though there are 40 words of VSRAM, the address register will wrap
//    when it passes 7Fh. Writes to the addresses beyond 50h are ignored.
    @Override
    public void writeVsramByte(int address, int data) {
        address &= 0x7F;
        if (address < GenesisVdpProvider.VDP_VSRAM_SIZE) {
            vsram[address] = data & 0xFF;
        } else {
            //Arrow Flash
            LOG.debug("Ignoring vsram write to address: {}", Integer.toHexString(address));
        }
    }

    //    The address register wraps past address 7Fh.
    @Override
    public void writeCramByte(int address, int data) {
        address &= (GenesisVdpProvider.VDP_CRAM_SIZE - 1);
        cram[address] = data & 0xFF;
        paletteUpdate(address & EVEN_VALUE_MASK);
    }


    @Override
    public int readCramByte(int address) {
        address &= (GenesisVdpProvider.VDP_CRAM_SIZE - 1);
        return cram[address];
    }


    @Override
    public int readVsramByte(int address) {
        address &= 0x7F;
        if (address >= GenesisVdpProvider.VDP_VSRAM_SIZE) {
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
