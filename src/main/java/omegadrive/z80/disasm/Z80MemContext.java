/*
 * Z80MemContext
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

package omegadrive.z80.disasm;

import emulib.plugins.memory.AbstractMemoryContext;
import omegadrive.bus.BaseBusProvider;
import omegadrive.util.Size;

public class Z80MemContext extends AbstractMemoryContext<Integer> {

    private BaseBusProvider busProvider;

    public static Z80MemContext createInstance(BaseBusProvider busProvider) {
        Z80MemContext c = new Z80MemContext();
        c.busProvider = busProvider;
        return c;
    }

    @Override
    public Integer read(int memoryPosition) {
        return (int) busProvider.read(memoryPosition, Size.BYTE);
    }

    @Override
    public Integer[] readWord(int memoryPosition) {
        return new Integer[]{read(memoryPosition), read(memoryPosition + 1)};
    }

    @Override
    public void write(int memoryPosition, Integer value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeWord(int memoryPosition, Integer[] value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?> getDataType() {
        return Integer.class;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSize() {
        return -1;
    }
}
