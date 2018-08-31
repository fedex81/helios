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


    @Test
    public void testRomWrapping() {
        GenesisMemoryProvider provider = new GenesisMemoryProvider();
        int size = 4896;
        int address = 32767;
        long expected = 3296;

        int[] data = new int[size];
        IntStream.range(0, size).forEach(i -> data[i] = i);
        provider.setCartridge(data);

        long res = provider.readCartridgeByte(address);
        Assert.assertEquals(expected, res);
    }
}
