package omegadrive.vdp;

import omegadrive.vdp.model.VdpMemoryInterface;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.IntStream;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class GenesisVdpTest {

    VdpMemoryInterface mem = GenesisVdpMemoryInterface.createInstance();

    int lsb = 0xDD;
    int msb = 0xEE;
    long data = (msb << 8) | lsb;
    long byteSwapData = (lsb << 8) | msb;
    long expected;

    @Before
    public void init() {
        IntStream.range(0, VdpProvider.VDP_CRAM_SIZE - 1).forEach(i ->
                mem.writeVideoRamWord(VdpProvider.VdpRamType.CRAM, 0, i)
        );
        IntStream.range(0, VdpProvider.VDP_VRAM_SIZE - 1).forEach(i ->
                mem.writeVideoRamWord(VdpProvider.VdpRamType.VRAM, 0, i)
        );
        IntStream.range(0, VdpProvider.VDP_VSRAM_SIZE - 1).forEach(i ->
                mem.writeVideoRamWord(VdpProvider.VdpRamType.VSRAM, 0, i)
        );
    }


    @Test
    public void testCram_01() {
        mem.writeVideoRamWord(VdpProvider.VdpRamType.CRAM, (int) data, 0);
        long res = mem.readVideoRamWord(VdpProvider.VdpRamType.CRAM, 0);
        Assert.assertEquals(data, res);

        mem.writeVideoRamWord(VdpProvider.VdpRamType.CRAM, (int) data, 1);
        res = mem.readVideoRamWord(VdpProvider.VdpRamType.CRAM, 1);
        Assert.assertEquals(data, res);
    }

    @Test
    public void testCram_02() {
        int baseAddress = 0x48;
        mem.writeVideoRamWord(VdpProvider.VdpRamType.CRAM, (int) data, baseAddress);
        long res = mem.readVideoRamWord(VdpProvider.VdpRamType.CRAM, baseAddress);
        Assert.assertEquals(data, res);

        mem.writeVideoRamWord(VdpProvider.VdpRamType.CRAM, 0x1122, 0);
        res = mem.readVideoRamWord(VdpProvider.VdpRamType.CRAM, 0);
        Assert.assertEquals(0x1122, res);

        mem.writeVideoRamWord(VdpProvider.VdpRamType.CRAM, (int) data, VdpProvider.VDP_CRAM_SIZE);
        res = mem.readVideoRamWord(VdpProvider.VdpRamType.CRAM, VdpProvider.VDP_CRAM_SIZE);
        Assert.assertEquals(data, res);
    }

    @Test
    public void testVsram_01() {
        int baseAddress = 0x48;
        mem.writeVideoRamWord(VdpProvider.VdpRamType.VSRAM, (int) data, baseAddress);
        long res = mem.readVideoRamWord(VdpProvider.VdpRamType.VSRAM, baseAddress);
        Assert.assertEquals(data, res);

        mem.writeVideoRamWord(VdpProvider.VdpRamType.VSRAM, 0x1122, 0);
        res = mem.readVideoRamWord(VdpProvider.VdpRamType.VSRAM, 0);
        Assert.assertEquals(0x1122, res);

        mem.writeVideoRamWord(VdpProvider.VdpRamType.VSRAM, (int) data, VdpProvider.VDP_VSRAM_SIZE);
        res = mem.readVideoRamWord(VdpProvider.VdpRamType.VSRAM, VdpProvider.VDP_VSRAM_SIZE);
        Assert.assertEquals(Long.toHexString(0x1111), Long.toHexString(res));

        mem.writeVideoRamWord(VdpProvider.VdpRamType.VSRAM, 0x3344, VdpProvider.VDP_VSRAM_SIZE - 2);
        res = readVideoRamAddressLong(VdpProvider.VdpRamType.VSRAM, VdpProvider.VDP_VSRAM_SIZE - 2);
        Assert.assertEquals(Long.toHexString(0x33441111), Long.toHexString(res));
    }

    @Test
    public void testVram_Even() {
        testVdpRam_EvenOdd(VdpProvider.VdpRamType.VRAM, 8);
    }

    @Test
    public void testVram_Odd() {
        testVdpRam_EvenOdd(VdpProvider.VdpRamType.VRAM, 9);
    }

    @Test
    public void testVsram_Even() {
        testVdpRam_EvenOdd(VdpProvider.VdpRamType.VSRAM, 8);
    }

    @Test
    public void testVsram_Odd() {
        testVdpRam_EvenOdd(VdpProvider.VdpRamType.VSRAM, 9);
    }

    @Test
    public void testCram_Even() {
        testVdpRam_EvenOdd(VdpProvider.VdpRamType.CRAM, 8);
    }

    @Test
    public void testCram_Odd() {
        testVdpRam_EvenOdd(VdpProvider.VdpRamType.CRAM, 9);
    }


    private void testVdpRam_EvenOdd(VdpProvider.VdpRamType vdpRamType, int address) {
        boolean even = address % 2 == 0;
        boolean byteSwap = vdpRamType == VdpProvider.VdpRamType.VRAM && !even;
        mem.writeVideoRamWord(vdpRamType, (int) data, address);

        long readData = byteSwap ? byteSwapData : data;

        long res = mem.readVideoRamWord(vdpRamType, address);
        Assert.assertEquals(Long.toHexString(readData), Long.toHexString(res));

        res = mem.readVideoRamWord(vdpRamType, address - 1);
        expected = even ? 0 : readData;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = mem.readVideoRamWord(vdpRamType, address - 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));

        res = mem.readVideoRamWord(vdpRamType, address + 1);
        expected = even ? readData : 0;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = mem.readVideoRamWord(vdpRamType, address + 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));

        res = readVideoRamAddressLong(vdpRamType, address);
        expected = readData << 16;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(vdpRamType, address - 1);
        expected = even ? readData : readData << 16;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(vdpRamType, address - 2);
        Assert.assertEquals(Long.toHexString(readData), Long.toHexString(res));

        res = readVideoRamAddressLong(vdpRamType, address + 1);
        expected = even ? readData << 16 : 0;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(vdpRamType, address + 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));
    }

    private long readVideoRamAddressLong(VdpProvider.VdpRamType vdpRamType, int address) {
        long data = mem.readVideoRamWord(vdpRamType, address);
        return data << 16 | mem.readVideoRamWord(vdpRamType, address + 2);
    }
}
