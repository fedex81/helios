package omegadrive.memory;

import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MemoryProvider
 *
 * @author Federico Berti
 */
public class MemoryProvider implements IMemoryProvider {

    private static Logger LOG = LogManager.getLogger(MemoryProvider.class.getSimpleName());

    public static final int M68K_RAM_SIZE = 0x10000;
    public static final int SG1K_Z80_RAM_SIZE = 0x400;
    public static final int CHECKSUM_START_ADDRESS = 0x18E;

    private int[] rom;
    private int[] ram;

    private long romMask;
    private int romSize;
    private int ramSize = M68K_RAM_SIZE;

    private MemoryProvider() {
    }


    public static IMemoryProvider createGenesisInstance() {
        return createInstance(new int[1], M68K_RAM_SIZE);
    }

    public static IMemoryProvider createSg1000Instance() {
        return createInstance(new int[1], SG1K_Z80_RAM_SIZE);
    }


    public static IMemoryProvider createInstance(int[] rom, int ramSize) {
        MemoryProvider memory = new MemoryProvider();
        memory.setRomData(rom);
        memory.ram = new int[ramSize];
        return memory;
    }

    @Override
    public int readRomByte(int address) {
        if (address > romSize - 1) {
            address &= romMask;
            address = address > romSize - 1 ? address - (romSize) : address;
        }
        return rom[address];
    }

    @Override
    public int readRamByte(int address) {
        if (address < ramSize) {
            return ram[address];
        }
        LOG.error("Invalid RAM read, address : " + Integer.toHexString(address));
        return 0;
    }

    @Override
    public void writeRamByte(int address, int data) {
        if (address < ramSize) {
            ram[address] = data;
        } else {
            LOG.error("Invalid RAM write, address : " + Integer.toHexString(address) + ", data: " + data);
        }
    }

    @Override
    public void setRomData(int[] data) {
        this.rom = data;
        this.romSize = data.length;
        this.romMask = (long) Math.pow(2, Util.log2(romSize) + 1) - 1;
    }

    @Override
    public void setChecksumRomValue(long value) {
        this.rom[CHECKSUM_START_ADDRESS] = (byte) ((value >> 8) & 0xFF);
        this.rom[CHECKSUM_START_ADDRESS + 1] = (byte) (value & 0xFF);
    }


    @Override
    public int[] getRomData() {
        return rom;
    }

    @Override
    public int[] getRamData() {
        return ram;
    }
}
