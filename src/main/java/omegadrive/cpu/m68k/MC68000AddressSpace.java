/*
 * MC68000AddressSpace
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive.cpu.m68k;

import m68k.memory.AddressSpace;
import omegadrive.bus.model.MdBusProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.Size;

public class MC68000AddressSpace implements AddressSpace {

    private MdBusProvider busProvider;

    public static AddressSpace createInstance(MdBusProvider busProvider) {
        MC68000AddressSpace m = new MC68000AddressSpace();
        m.busProvider = busProvider;
        return m;
    }

    @Override
    public void reset() {
        //NOT USED - DO NOTHING
    }

    @Override
    public int getStartAddress() {
        return 0;
    }

    @Override
    public int getEndAddress() {
        return MemoryProvider.M68K_RAM_SIZE / 1024;
    }

    @Override
    public int readByte(int addr) {
        return busProvider.read(addr, Size.BYTE);
    }

    @Override
    public int readWord(int addr) {
        return busProvider.read(addr, Size.WORD);
    }

    @Override
    public int readLong(int addr) {
        return busProvider.read(addr, Size.LONG);
    }

    @Override
    public void writeByte(int addr, int value) {
        busProvider.write(addr, value, Size.BYTE);
    }

    @Override
    public void writeWord(int addr, int value) {
        busProvider.write(addr, value, Size.WORD);
    }

    @Override
    public void writeLong(int addr, int value) {
        busProvider.write(addr, value, Size.LONG);
    }

    @Override
    public int internalReadByte(int addr) {
        return readByte(addr);
    }

    @Override
    public int internalReadWord(int addr) {
        return readWord(addr);
    }

    @Override
    public int internalReadLong(int addr) {
        return readLong(addr);
    }

    @Override
    public void internalWriteByte(int addr, int value) {
        writeByte(addr, value);
    }

    @Override
    public void internalWriteWord(int addr, int value) {
        writeWord(addr, value);
    }

    @Override
    public void internalWriteLong(int addr, int value) {
        writeLong(addr, value);
    }

    @Override
    public int size() {
        //NOTE: used for debugging
        return MdBusProvider.ADDRESS_UPPER_LIMIT + 1;
    }
}
