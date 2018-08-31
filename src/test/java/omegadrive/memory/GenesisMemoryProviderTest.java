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

    GenesisMemoryProvider provider = new GenesisMemoryProvider();

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
        provider.setCartridge(data);

        long res = provider.readCartridgeByte(address);
        Assert.assertEquals(expected, res);
    }
}
