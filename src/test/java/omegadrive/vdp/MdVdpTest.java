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

package omegadrive.vdp;

import omegadrive.vdp.md.MdVdpMemoryInterface;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.IntStream;

import static omegadrive.util.Util.th;

public class MdVdpTest {

    VdpMemoryInterface mem = MdVdpMemoryInterface.createInstance();

    int lsb = 0xDD;
    int msb = 0xEE;
    long data = (msb << 8) | lsb;
    long byteSwapData = (lsb << 8) | msb;
    long expected;

    @Before
    public void init() {
        IntStream.range(0, MdVdpProvider.VDP_CRAM_SIZE - 1).forEach(i ->
                mem.writeVideoRamWord(MdVdpProvider.VdpRamType.CRAM, 0, i)
        );
        IntStream.range(0, MdVdpProvider.VDP_VRAM_SIZE - 1).forEach(i ->
                mem.writeVideoRamWord(MdVdpProvider.VdpRamType.VRAM, 0, i)
        );
        IntStream.range(0, MdVdpProvider.VDP_VSRAM_SIZE - 1).forEach(i ->
                mem.writeVideoRamWord(MdVdpProvider.VdpRamType.VSRAM, 0, i)
        );
    }


    @Test
    public void testCram_01() {
        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.CRAM, (int) data, 0);
        long res = mem.readVideoRamWord(MdVdpProvider.VdpRamType.CRAM, 0);
        Assert.assertEquals(data, res);

        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.CRAM, (int) data, 1);
        res = mem.readVideoRamWord(MdVdpProvider.VdpRamType.CRAM, 1);
        Assert.assertEquals(data, res);
    }

    @Test
    public void testCram_02() {
        int baseAddress = 0x48;
        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.CRAM, (int) data, baseAddress);
        long res = mem.readVideoRamWord(MdVdpProvider.VdpRamType.CRAM, baseAddress);
        Assert.assertEquals(data, res);

        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.CRAM, 0x1122, 0);
        res = mem.readVideoRamWord(MdVdpProvider.VdpRamType.CRAM, 0);
        Assert.assertEquals(0x1122, res);

        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.CRAM, (int) data, MdVdpProvider.VDP_CRAM_SIZE);
        res = mem.readVideoRamWord(MdVdpProvider.VdpRamType.CRAM, MdVdpProvider.VDP_CRAM_SIZE);
        Assert.assertEquals(data, res);
    }

    @Test
    public void testVsram_01() {
        int baseAddress = 0x48;
        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.VSRAM, (int) data, baseAddress);
        long res = mem.readVideoRamWord(MdVdpProvider.VdpRamType.VSRAM, baseAddress);
        Assert.assertEquals(data, res);

        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.VSRAM, 0x1122, 0);
        res = mem.readVideoRamWord(MdVdpProvider.VdpRamType.VSRAM, 0);
        Assert.assertEquals(0x1122, res);

        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.VSRAM, (int) data, MdVdpProvider.VDP_VSRAM_SIZE);
        res = mem.readVideoRamWord(MdVdpProvider.VdpRamType.VSRAM, MdVdpProvider.VDP_VSRAM_SIZE);
        Assert.assertEquals(th(0x1111), th(res));

        mem.writeVideoRamWord(MdVdpProvider.VdpRamType.VSRAM, 0x3344, MdVdpProvider.VDP_VSRAM_SIZE - 2);
        res = readVideoRamAddressLong(MdVdpProvider.VdpRamType.VSRAM, MdVdpProvider.VDP_VSRAM_SIZE - 2);
        Assert.assertEquals(th(0x33441111), th(res));
    }

    @Test
    public void testVram_Even() {
        testVdpRam_EvenOdd(MdVdpProvider.VdpRamType.VRAM, 8);
    }

    @Test
    public void testVram_Odd() {
        testVdpRam_EvenOdd(MdVdpProvider.VdpRamType.VRAM, 9);
    }

    @Test
    public void testVsram_Even() {
        testVdpRam_EvenOdd(MdVdpProvider.VdpRamType.VSRAM, 8);
    }

    @Test
    public void testVsram_Odd() {
        testVdpRam_EvenOdd(MdVdpProvider.VdpRamType.VSRAM, 9);
    }

    @Test
    public void testCram_Even() {
        testVdpRam_EvenOdd(MdVdpProvider.VdpRamType.CRAM, 8);
    }

    @Test
    public void testCram_Odd() {
        testVdpRam_EvenOdd(MdVdpProvider.VdpRamType.CRAM, 9);
    }


    private void testVdpRam_EvenOdd(MdVdpProvider.VdpRamType vdpRamType, int address) {
        boolean even = address % 2 == 0;
        boolean byteSwap = vdpRamType == MdVdpProvider.VdpRamType.VRAM && !even;
        mem.writeVideoRamWord(vdpRamType, (int) data, address);

        long readData = byteSwap ? byteSwapData : data;

        long res = mem.readVideoRamWord(vdpRamType, address);
        Assert.assertEquals(th(readData), th(res));

        res = mem.readVideoRamWord(vdpRamType, address - 1);
        expected = even ? 0 : readData;
        Assert.assertEquals(th(expected), th(res));

        res = mem.readVideoRamWord(vdpRamType, address - 2);
        Assert.assertEquals(th(0), th(res));

        res = mem.readVideoRamWord(vdpRamType, address + 1);
        expected = even ? readData : 0;
        Assert.assertEquals(th(expected), th(res));

        res = mem.readVideoRamWord(vdpRamType, address + 2);
        Assert.assertEquals(th(0), th(res));

        res = readVideoRamAddressLong(vdpRamType, address);
        expected = readData << 16;
        Assert.assertEquals(th(expected), th(res));

        res = readVideoRamAddressLong(vdpRamType, address - 1);
        expected = even ? readData : readData << 16;
        Assert.assertEquals(th(expected), th(res));

        res = readVideoRamAddressLong(vdpRamType, address - 2);
        Assert.assertEquals(th(readData), th(res));

        res = readVideoRamAddressLong(vdpRamType, address + 1);
        expected = even ? readData << 16 : 0;
        Assert.assertEquals(th(expected), th(res));

        res = readVideoRamAddressLong(vdpRamType, address + 2);
        Assert.assertEquals(th(0), th(res));
    }

    private long readVideoRamAddressLong(MdVdpProvider.VdpRamType vdpRamType, int address) {
        long data = mem.readVideoRamWord(vdpRamType, address);
        return data << 16 | mem.readVideoRamWord(vdpRamType, address + 2);
    }
}
