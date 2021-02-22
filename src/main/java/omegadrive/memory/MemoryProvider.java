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

import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

public class MemoryProvider implements IMemoryProvider {

    private final static Logger LOG = LogManager.getLogger(MemoryProvider.class.getSimpleName());

    public static final MemoryProvider NO_MEMORY = new MemoryProvider();

    public static final int M68K_RAM_SIZE = 0x10000;
    public static final int SG1K_Z80_RAM_SIZE = 0x400;
    public static final int MSX_Z80_RAM_SIZE = 0x4000;
    public static final int SMS_Z80_RAM_SIZE = 0x2000;
    public static final int CHECKSUM_START_ADDRESS = 0x18E;

    private int[] rom;
    private int[] ram;

    private long romMask;
    private int romSize;
    private int ramSize = M68K_RAM_SIZE;

    private MemoryProvider() {
    }


    public static IMemoryProvider createGenesisInstance() {
        return createInstance(new int[1], M68K_RAM_SIZE);
    }

    public static IMemoryProvider createSg1000Instance() {
        return createInstance(new int[1], SG1K_Z80_RAM_SIZE);
    }

    public static IMemoryProvider createMsxInstance() {
        return createInstance(new int[1], MSX_Z80_RAM_SIZE);
    }

    public static IMemoryProvider createSmsInstance() {
        return createInstance(new int[1], SMS_Z80_RAM_SIZE);
    }


    public static IMemoryProvider createInstance(int[] rom, int ramSize) {
        MemoryProvider memory = new MemoryProvider();
        memory.setRomData(rom);
        memory.ram = Util.initMemoryRandomBytes(new int[ramSize]);
        memory.ramSize = ramSize;
        return memory;
    }

    @Override
    public int readRomByte(int address) {
        if (address > romSize - 1) {
            address &= romMask;
            address = address > romSize - 1 ? address - (romSize) : address;
        }
        return rom[address];
    }

    @Override
    public int readRamByte(int address) {
        if (address < ramSize) {
            return ram[address];
        }
        LOG.error("Invalid RAM read, address : {}", Integer.toHexString(address));
        return 0;
    }

    @Override
    public void writeRamByte(int address, int data) {
        if (address < ramSize) {
            ram[address] = data;
        } else {
            LOG.error("Invalid RAM write, address : {}, data: {}", Integer.toHexString(address), data);
        }
    }

    @Override
    public void setRomData(int[] data) {
        this.rom = data;
        this.romSize = data.length;
        this.romMask = (long) Math.pow(2, Util.log2(romSize) + 1) - 1;
    }

    @Override
    public void setChecksumRomValue(long value) {
        this.rom[CHECKSUM_START_ADDRESS] = (byte) ((value >> 8) & 0xFF);
        this.rom[CHECKSUM_START_ADDRESS + 1] = (byte) (value & 0xFF);
    }


    @Override
    public int[] getRomData() {
        return rom;
    }

    @Override
    public int[] getRamData() {
        return ram;
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        IntStream.range(0, ram.length).forEach(i -> buffer.put((byte) (ram[i] & 0xFF)));
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        IntStream.range(0, ram.length).forEach(i -> ram[i] = buffer.get() & 0xFF);
    }
}
