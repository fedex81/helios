package omegadrive.memory;

import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * GenesisMemoryProvider
 *
 * @author Federico Berti
 */
public class GenesisMemoryProvider implements MemoryProvider {

    private static Logger LOG = LogManager.getLogger(GenesisMemoryProvider.class.getSimpleName());

    private int[] cartridge;
    private int[] ram = new int[M68K_RAM_SIZE];

    private long romMask;
    private int romSize;

    @Override
    public long readCartridgeByte(long address) {
        if (address > romSize - 1) {
            address &= romMask;
            address = address > romSize - 1 ? address - (romSize) : address;
        }
        return cartridge[(int) address];
    }

    @Override
    public long readCartridgeWord(long address) {
        long data = readCartridgeByte(address) << 8;
        data |= readCartridgeByte(address + 1);
        return data;
    }

    @Override
    public int readRamByte(int address) {
        if (address < M68K_RAM_SIZE) {
            return ram[address];
        }
        LOG.error("Invalid RAM read, address : " + Integer.toHexString((int) address));
        return 0;
    }

    @Override
    public void writeRamByte(int address, long data) {
        if (address < M68K_RAM_SIZE) {
            ram[address] = (int) data;
        } else {
            LOG.error("Invalid RAM write, address : " + Integer.toHexString((int) address) + ", data: " + data);
        }
    }

    @Override
    public void setCartridge(int[] data) {
        this.cartridge = data;
        this.romSize = data.length;
        this.romMask = (long) Math.pow(2, Util.log2(romSize) + 1) - 1;
    }

    @Override
    public int getRomSize() {
        return romSize;
    }
}
