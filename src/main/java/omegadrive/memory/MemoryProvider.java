/*
 * MemoryProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 17:41
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

package omegadrive.memory;

import omegadrive.util.LogHelper;
import omegadrive.util.RomHolder;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

import static omegadrive.util.Util.th;

public class MemoryProvider implements IMemoryProvider {

    private final static Logger LOG = LogHelper.getLogger(MemoryProvider.class.getSimpleName());

    public static final IMemoryProvider NO_MEMORY = createInstance(RomHolder.EMPTY_ROM, 0);

    public static final int M68K_RAM_SIZE = 0x10000;
    public static final int SG1K_Z80_RAM_SIZE = 0x400;
    public static final int MSX_Z80_RAM_SIZE = 0x4000;
    public static final int SMS_Z80_RAM_SIZE = 0x2000;
    public static final int CHECKSUM_START_ADDRESS = 0x18E;

    private byte[] ram;
    private int ramSize = M68K_RAM_SIZE;

    private RomHolder romHolder;

    private MemoryProvider() {
    }

    public static IMemoryProvider createGenesisInstance() {
        return createInstance(RomHolder.EMPTY_ROM, M68K_RAM_SIZE);
    }

    public static IMemoryProvider createSg1000Instance() {
        return createInstance(RomHolder.EMPTY_ROM, SG1K_Z80_RAM_SIZE);
    }

    public static IMemoryProvider createMsxInstance() {
        return createInstance(RomHolder.EMPTY_ROM, MSX_Z80_RAM_SIZE);
    }

    public static IMemoryProvider createSmsInstance() {
        return createInstance(RomHolder.EMPTY_ROM, SMS_Z80_RAM_SIZE);
    }

    public static IMemoryProvider createInstance(RomHolder romHolder, int ramSize) {
        MemoryProvider m = new MemoryProvider();
        m.romHolder = romHolder;
        m.ram = Util.initMemoryRandomBytes(new byte[ramSize]);
        m.ramSize = ramSize;
        return m;
    }

    public static IMemoryProvider createInstance(byte[] rom, int ramSize) {
        return createInstance(new RomHolder(rom), ramSize);
    }

    @Override
    public int readRomByte(int address) {
        return Util.readDataMask(romHolder.data, address, romHolder.romMask, Size.BYTE);
    }

    @Override
    public byte readRamByte(int address) {
        if (address < ramSize) {
            return ram[address];
        }
        LOG.error("Invalid RAM read, address : {}", th(address));
        return 0;
    }

    @Override
    public void writeRamByte(int address, byte data) {
        if (address < ramSize) {
            ram[address] = data;
        } else {
            LOG.error("Invalid RAM write, address : {}, data: {}", th(address), data);
        }
    }

    @Override
    public void setRomData(byte[] data) {
        this.romHolder = new RomHolder(data);
    }

    @Override
    public void setChecksumRomValue(long value) {
        Util.writeDataMask(romHolder.data, CHECKSUM_START_ADDRESS, (byte) ((value >> 8) & 0xFF), romHolder.romMask, Size.BYTE);
        Util.writeDataMask(romHolder.data, CHECKSUM_START_ADDRESS + 1, (byte) (value & 0xFF), romHolder.romMask, Size.BYTE);
    }

    @Override
    public byte[] getRamData() {
        return ram;
    }

    @Override
    public RomHolder getRomHolder() {
        return romHolder;
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        IntStream.range(0, ram.length).forEach(i -> buffer.put((byte) (ram[i] & 0xFF)));
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        IntStream.range(0, ram.length).forEach(i -> ram[i] = buffer.get());
    }
}
