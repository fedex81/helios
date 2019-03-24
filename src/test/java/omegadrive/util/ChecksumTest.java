package omegadrive.util;

import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
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
