/*
 * Copyright (c) 2018-2019 Federico Berti
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

import org.junit.Assert;
import org.junit.Test;

import java.util.stream.IntStream;

/**
 * MdMemoryProviderTest
 *
 * @author Federico Berti
 */
public class MdMemoryProviderTest {

    IMemoryProvider provider = MemoryProvider.createMdInstance();

    @Test
    public void testRomWrapping01() {
        int size = 0x1320;
        int address = 0x7FFF;
        byte expected = (byte) 0xFF;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping02() {
        int size = 1048576;
        int address = 1048576;
        byte expected = 0;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping03() {
        int size = 1048576;
        int address = 1048576 * 2;
        byte expected = 0;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping04() {
        int size = 1048576;
        int address = 1048576 * 2 + 1;
        byte expected = 1;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping05() {
        int size = 1048576;
        int address = 1048576 - 1;
        byte expected = (byte) address;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping06() {
        int size = 1048576;
        int address = 1048576 * 2 - 1;
        int expected = size - 1;
        testRowWrappingInternal(size, address, (byte) (expected & 0xFF));
    }

    private void testRowWrappingInternal(int size, int address, byte expected) {
        byte[] data = new byte[size];
        IntStream.range(0, size).forEach(i -> data[i] = (byte) i);
        provider.setRomData(data);

        long res = provider.readRomByte(address);
        Assert.assertEquals(expected, res);
    }
}
