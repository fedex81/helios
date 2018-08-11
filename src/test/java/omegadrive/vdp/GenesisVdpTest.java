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
    long expected;

    @Before
    public void init() {
        IntStream.range(0, VdpProvider.VDP_CRAM_SIZE - 1).forEach(i ->
                vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, 0, i)
        );
    }


    @Test
    public void testCram_01() {
        vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, (int) data, 0);
        long res = vdp.readVideoRamWord(VdpProvider.VramMode.cramRead, 0);
        Assert.assertEquals(data, res);
    }

    @Test
    public void testCram_02() {
        vdp.writeVideoRamWord(VdpProvider.VramMode.cramWrite, (int) data, 1);
        long res = vdp.readVideoRamWord(VdpProvider.VramMode.cramRead, 1);
        Assert.assertEquals(data, res);
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
        vdp.writeVideoRamWord(writeMode, (int) data, address);
        long res = vdp.readVideoRamWord(readMode, address);
        Assert.assertEquals(Long.toHexString(data), Long.toHexString(res));

        res = vdp.readVideoRamWord(readMode, address - 1);
        expected = msb;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = vdp.readVideoRamWord(readMode, address - 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));

        res = vdp.readVideoRamWord(readMode, address + 1);
        expected = lsb << 8;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = vdp.readVideoRamWord(readMode, address + 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address);
        expected = data << 16;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address - 1);
        expected = data << 8;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address - 2);
        Assert.assertEquals(Long.toHexString(data), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address + 1);
        expected = ((long) lsb) << 24;
        Assert.assertEquals(Long.toHexString(expected), Long.toHexString(res));

        res = readVideoRamAddressLong(readMode, address + 2);
        Assert.assertEquals(Long.toHexString(0), Long.toHexString(res));
    }

    private long readVideoRamAddressLong(VdpProvider.VramMode mode, int address) {
        long data = vdp.readVideoRamWord(mode, address);
        return data << 16 | vdp.readVideoRamWord(mode, address + 2);
    }


}
