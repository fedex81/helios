package omegadrive.vdp;

import omegadrive.bus.BusProvider;
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

    VdpProvider vdp = new GenesisVdp(BusProvider.createBus());

    int lsb = 0xDD;
    int msb = 0xEE;
    long data = (msb << 8) | lsb;
    long byteSwapData = (lsb << 8) | msb;
    long expected;

    @Before
    public void init() {
        IntStream.range(0, VdpProvider.VDP_CRAM_SIZE - 1).forEach(i ->
                vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, 0, i)
        );
        IntStream.range(0, VdpProvider.VDP_VRAM_SIZE - 1).forEach(i ->
                vdp.writeVideoRamWord(VdpProvider.VramMode.vramWrite, 0, i)
        );
        IntStream.range(0, VdpProvider.VDP_VSRAM_SIZE - 1).forEach(i ->
                vdp.writeVideoRamWord(VdpProvider.VramMode.vsramWrite, 0, i)
        );
    }


    @Test
    public void testCram_01() {
        vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, (int) data, 0);
        long res = vdp.readVideoRamWord(VdpProvider.VramMode.cramRead, 0);
        Assert.assertEquals(data, res);

        vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, (int) data, 1);
        res = vdp.readVideoRamWord(VdpProvider.VramMode.cramRead, 1);
        Assert.assertEquals(data, res);
    }

    @Test
    public void testCram_02() {
        int baseAddress = 0x48;
        vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, (int) data, baseAddress);
        long res = vdp.readVideoRamWord(VdpProvider.VramMode.cramRead, baseAddress);
        Assert.assertEquals(data, res);

        vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, 0x1122, 0);
        res = vdp.readVideoRamWord(VdpProvider.VramMode.cramRead, 0);
        Assert.assertEquals(0x1122, res);

        vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, (int) data, VdpProvider.VDP_CRAM_SIZE);
        res = vdp.readVideoRamWord(VdpProvider.VramMode.cramRead, VdpProvider.VDP_CRAM_SIZE);
        Assert.assertEquals(data, res);
    }

    @Test
    public void testVsram_01() {
        int baseAddress = 0x48;
        vdp.writeVideoRamWord(VdpProvider.VramMode.vsramWrite, (int) data, baseAddress);
        long res = vdp.readVideoRamWord(VdpProvider.VramMode.vsramRead, baseAddress);
        Assert.assertEquals(data, res);

        vdp.writeVideoRamWord(VdpProvider.VramMode.vsramWrite, 0x1122, 0);
        res = vdp.readVideoRamWord(VdpProvider.VramMode.vsramRead, 0);
        Assert.assertEquals(0x1122, res);

        vdp.writeVideoRamWord(VdpProvider.VramMode.vsramWrite, (int) data, VdpProvider.VDP_VSRAM_SIZE);
        res = vdp.readVideoRamWord(VdpProvider.VramMode.vsramRead, VdpProvider.VDP_VSRAM_SIZE);
        Assert.assertEquals(Long.toHexString(0x1111), Long.toHexString(res));

        vdp.writeVideoRamWord(VdpProvider.VramMode.vsramWrite, 0x3344, VdpProvider.VDP_VSRAM_SIZE - 2);
        res = readVideoRamAddressLong(VdpProvider.VramMode.vsramRead, VdpProvider.VDP_VSRAM_SIZE - 2);
        Assert.assertEquals(Long.toHexString(0x33441111), Long.toHexString(res));
    }

    @Test
    public void testVram_Even() {
        testVdpRam_EvenOdd(VdpProvider.VramMode.vramRead, VdpProvider.VramMode.vramWrite, 8);
    }

    @Test
    public void testVram_Odd() {
        testVdpRam_EvenOdd(VdpProvider.VramMode.vramRead, VdpProvider.VramMode.vramWrite, 9);
    }

    @Test
    public void testVsram_Even() {
        testVdpRam_EvenOdd(VdpProvider.VramMode.vsramRead, VdpProvider.VramMode.vsramWrite, 8);
    }

    @Test
    public void testVsram_Odd() {
        testVdpRam_EvenOdd(VdpProvider.VramMode.vsramRead, VdpProvider.VramMode.vsramWrite, 9);
    }

    @Test
    public void testCram_Even() {
        testVdpRam_EvenOdd(VdpProvider.VramMode.cramRead, VdpProvider.VramMode.cramWrite, 8);
    }

    @Test
    public void testCram_Odd() {
        testVdpRam_EvenOdd(VdpProvider.VramMode.cramRead, VdpProvider.VramMode.cramWrite, 9);
    }


    private void testVdpRam_EvenOdd(VdpProvider.VramMode readMode, VdpProvider.VramMode writeMode, int address) {
        boolean even = address % 2 == 0;
        boolean byteSwap = writeMode == VdpProvider.VramMode.vramWrite && !even;
        vdp.writeVideoRamWord(writeMode, (int) data, address);

        long readData = byteSwap ? byteSwapData : data;

        long res = vdp.readVideoRamWord(readMode, address);
        Assert.assertEquals(Long.toHexString(readData), Long.toHexString(res));

        res = vdp.readVideoRamWord(readMode, address - 1);
        expected = even ? 0 : readData;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = vdp.readVideoRamWord(readMode, address - 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));

        res = vdp.readVideoRamWord(readMode, address + 1);
        expected = even ? readData : 0;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = vdp.readVideoRamWord(readMode, address + 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address);
        expected = readData << 16;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address - 1);
        expected = even ? readData : readData << 16;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address - 2);
        Assert.assertEquals(Long.toHexString(readData), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address + 1);
        expected = even ? readData << 16 : 0;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address + 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));
    }

    private long readVideoRamAddressLong(VdpProvider.VramMode mode, int address) {
        long data = vdp.readVideoRamWord(mode, address);
        return data << 16 | vdp.readVideoRamWord(mode, address + 2);
    }
}
