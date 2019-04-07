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
 * GenesisMemoryProviderTest
 *
 * @author Federico Berti
 */
public class GenesisMemoryProviderTest {

    IMemoryProvider provider = MemoryProvider.createGenesisInstance();

    @Test
    public void testRomWrapping01() {
        int size = 4896;
        int address = 32767;
        int expected = 3295;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping02() {
        int size = 1048576;
        int address = 1048576;
        int expected = 0;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping03() {
        int size = 1048576;
        int address = 1048576 * 2;
        int expected = 0;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping04() {
        int size = 1048576;
        int address = 1048576 * 2 + 1;
        int expected = 1;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping05() {
        int size = 1048576;
        int address = 1048576 - 1;
        int expected = address;
        testRowWrappingInternal(size, address, expected);
    }

    @Test
    public void testRomWrapping06() {
        int size = 1048576;
        int address = 1048576 * 2 - 1;
        int expected = size - 1;
        testRowWrappingInternal(size, address, expected);
    }

    private void testRowWrappingInternal(int size, int address, int expected) {
        int[] data = new int[size];
        IntStream.range(0, size).forEach(i -> data[i] = i);
        provider.setRomData(data);

        long res = provider.readRomByte(address);
        Assert.assertEquals(expected, res);
    }
}
