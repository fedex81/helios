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

package omegadrive.util;

import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ChecksumTest {


    IMemoryProvider mp = MemoryProvider.createGenesisInstance();
    int[] data = new int[0];

    @Test
    public void testEvenSizeChecksum01() {
        data = new int[0x204];
        Arrays.fill(data, 0);
        data[0x200] = 0;
        data[0x201] = 1;
        data[0x202] = 0;
        data[0x203] = 1;
        mp.setRomData(data);

        int expected = 2;
        long actual = Util.computeChecksum(mp);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testEvenSizeChecksum02() {
        data = new int[0x204];
        Arrays.fill(data, 0);
        data[0x200] = 0xFF;
        data[0x201] = 0xFF;
        data[0x202] = 0xFF;
        data[0x203] = 0xFF;
        mp.setRomData(data);

        int expected = 65534;
        long actual = Util.computeChecksum(mp);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testOddSizeChecksum01() {
        data = new int[0x205];
        Arrays.fill(data, 0);
        data[0x200] = 0;
        data[0x201] = 1;
        data[0x202] = 0;
        data[0x203] = 1;
        data[0x204] = 1;
        mp.setRomData(data);

        int expected = 3;
        long actual = Util.computeChecksum(mp);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testOddSizeChecksum02() {
        data = new int[0x205];
        Arrays.fill(data, 0);
        data[0x200] = 0xFF;
        data[0x201] = 0xFF;
        data[0x202] = 0xFF;
        data[0x203] = 0xFF;
        data[0x204] = 0xFF;
        mp.setRomData(data);

        int expected = 253;
        long actual = Util.computeChecksum(mp);
        Assert.assertEquals(expected, actual);
    }
}
