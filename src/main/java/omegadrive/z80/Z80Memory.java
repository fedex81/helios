/*
 * Z80Memory
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

package omegadrive.z80;

import omegadrive.memory.IMemoryRam;

public class Z80Memory implements IMemoryRam {

    public static int Z80_RAM_MEMORY_SIZE = 0x2000;

    private final int[] memory;

    public Z80Memory(int size) {
        memory = new int[size];
    }

    public Z80Memory() {
        this(Z80_RAM_MEMORY_SIZE);
    }

    @Override
    public int readRamByte(int address) {
        return memory[address] & 0xFF;
    }

    @Override
    public void writeRamByte(int address, int data) {
        memory[address] = data & 0xFF;
    }

    @Override
    public int[] getRamData() {
        return memory;
    }
}
