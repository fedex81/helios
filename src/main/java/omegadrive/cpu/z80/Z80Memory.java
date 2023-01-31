/*
 * Z80Memory
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/06/19 13:57
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

package omegadrive.cpu.z80;

import omegadrive.memory.IMemoryRam;

public class Z80Memory implements IMemoryRam {

    private final byte[] memory;

    public Z80Memory(int size) {
        memory = new byte[size];
    }

    @Override
    public byte readRamByte(int address) {
        return memory[address];
    }

    @Override
    public void writeRamByte(int address, byte data) {
        memory[address] = data;
    }

    @Override
    public byte[] getRamData() {
        return memory;
    }
}
