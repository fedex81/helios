/*
 * MdVdpMemoryInterface
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

package omegadrive.vdp.md;

import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.IntStream;

import static omegadrive.util.Util.readBufferWord;
import static omegadrive.util.Util.th;
import static omegadrive.vdp.model.MdVdpProvider.*;

public class MdVdpMemoryInterface implements VdpMemoryInterface {

    public final static boolean verbose = false;
    private final static Logger LOG = LogHelper.getLogger(MdVdpMemoryInterface.class.getSimpleName());
    private static final int EVEN_VALUE_MASK = ~1;

    private ByteBuffer vram;
    private ByteBuffer cram;
    private ByteBuffer vsram;
    private int[] javaPalette;
    private final int[] satCache = new int[MAX_SPRITES_PER_FRAME_H40 * 8]; //8 bytes per sprite
    private int satBaseAddress = 0, satEndAddress = satBaseAddress + satCache.length;

    private final VdpColorMapper colorMapper;

    protected MdVdpMemoryInterface() {
        colorMapper = VdpColorMapper.getInstance();
    }

    public static MdVdpMemoryInterface createInstance() {
        MdVdpMemoryInterface i = new MdVdpMemoryInterface();
        i.init();
        return i;
    }

    protected void init() {
        vram = Util.initMemoryRandomBytes(ByteBuffer.allocate(VDP_VRAM_SIZE).order(ByteOrder.BIG_ENDIAN));
        cram = Util.initMemoryRandomBytes(ByteBuffer.allocate(VDP_CRAM_SIZE).order(ByteOrder.BIG_ENDIAN));
        vsram = Util.initMemoryRandomBytes(ByteBuffer.allocate(VDP_VSRAM_SIZE).order(ByteOrder.BIG_ENDIAN));
        initPalette();
    }

    private void paletteUpdate(int cramAddress) {
        javaPalette[cramAddress >> 1] = colorMapper.getColor(readBufferWord(cram, cramAddress));
    }

    private void initPalette() {
        javaPalette = new int[cram.capacity() / 2];
        IntStream.range(0, javaPalette.length).forEach(i -> paletteUpdate(i << 1));
    }

    //TODO: shouldnt this flip the byte like in readVramWord
    //TODO: DMA is doing it but it should be done here
    protected byte readVramByte(int address) {
        return vram.get(address & VDP_VRAM_MASK);
    }

    //    The address register wraps past address FFFFh.
    protected void writeVramByte(int address, byte data) {
        vram.put(address & VDP_VRAM_MASK, data);
        updateSatCache(address, data);
    }

    @Override
    public int readVideoRamWord(MdVdpProvider.VdpRamType vramType, int address) {
        switch (vramType) {
            case VRAM:
                return readVramWord(address);
            case VSRAM:
                return readVsramWord(address);
            case CRAM:
                return readCramWord(address);
            default:
                LOG.warn("Unexpected videoRam read: {}", vramType);
        }
        return 0;
    }

    @Override
    public void writeVideoRamWord(MdVdpProvider.VdpRamType vramType, int data, int address) {
        byte data1 = (byte) (data >> 8);
        byte data2 = (byte) data;
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

    @Override
    public void writeVideoRamByte(VdpRamType vramType, int address, byte data) {
        switch (vramType) {
            case VRAM -> writeVramByte(address, data);
            case CRAM -> writeCramByte(address, data);
            case VSRAM -> writeVsramByte(address, data);
        }
    }

    @Override
    public byte readVideoRamByte(VdpRamType vramType, int address) {
        return switch (vramType) {
            case VRAM -> readVramByte(address);
            case CRAM -> readCramByte(address);
            case VSRAM -> readVsramByte(address);
        };
    }

    private void updateSatCache(int vramAddress, byte value) {
        if (vramAddress >= satBaseAddress && vramAddress < satEndAddress) {
            satCache[vramAddress - satBaseAddress] = value & 0xFF;
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
    public ByteBuffer getCram() {
        return cram;
    }

    @Override
    public ByteBuffer getVram() {
        return vram;
    }

    @Override
    public ByteBuffer getVsram() {
        return vsram;
    }

    @Override
    public int[] getJavaColorPalette() {
        return javaPalette;
    }

    //    Even though there are 40 words of VSRAM, the address register will wrap
//    when it passes 7Fh. Writes to the addresses beyond 50h are ignored.
    protected void writeVsramByte(int address, byte data) {
        address &= 0x7F;
        if (address < MdVdpProvider.VDP_VSRAM_SIZE) {
            vsram.put(address, data);
        } else {
            //Arrow Flash
            if (verbose) LOG.debug("Ignoring vsram byte-write to address: {}, val: {}", th(address), th(data));
        }
    }

    //    The address register wraps past address 7Fh.
    protected void writeCramByte(int address, byte data) {
        cram.put(address & VDP_CRAM_MASK, data);
        paletteUpdate(address & VDP_CRAM_MASK & EVEN_VALUE_MASK);
    }

    protected byte readCramByte(int address) {
        return cram.get(address & VDP_CRAM_MASK);
    }

    protected byte readVsramByte(int address) {
        address &= 0x7F;
        if (address >= MdVdpProvider.VDP_VSRAM_SIZE) {
            address = 0;
        }
        return vsram.get(address);
    }

    protected int readVramWord(int address) {
        //ignore A0, always use an even address
        address &= EVEN_VALUE_MASK;
        return (readVramByte(address) & 0xFF) << 8 | (readVramByte(address + 1) & 0xFF);
    }

    protected int readVsramWord(int address) {
        //ignore A0, always use an even address
        address &= EVEN_VALUE_MASK;
        return (readVsramByte(address) & 0xFF) << 8 | (readVsramByte(address + 1) & 0xFF);
    }

    protected int readCramWord(int address) {
        //ignore A0, always use an even address
        address &= EVEN_VALUE_MASK;
        return (readCramByte(address) & 0xFF) << 8 | (readCramByte(address + 1) & 0xFF);
    }
}
