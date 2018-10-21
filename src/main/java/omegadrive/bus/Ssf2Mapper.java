package omegadrive.bus;

import omegadrive.memory.MemoryProvider;
import omegadrive.util.CartridgeInfoProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
//	https://emu-docs.org/Genesis/ssf2.txt
public class Ssf2Mapper implements GenesisMapper {

    private static Logger LOG = LogManager.getLogger(Ssf2Mapper.class.getSimpleName());

    public static final int BANK_SET_START_ADDRESS = 0xA130F3;
    public static final int BANK_SET_END_ADDRESS = 0xA130FF;

    public static final int BANK_SIZE = 0x80000;
    public static final int BANKABLE_START_ADDRESS = 0x80000;

    private int[] banks = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
    private GenesisMapper baseMapper;
    private MemoryProvider memory;

    public static GenesisMapper getOrCreateInstance(GenesisMapper baseMapper, GenesisMapper currentMapper,
                                                    MemoryProvider memoryProvider) {
        if (baseMapper != currentMapper) {
            return currentMapper;
        }
        return createInstance(baseMapper, memoryProvider);

    }

    private static Ssf2Mapper createInstance(GenesisMapper baseMapper, MemoryProvider memoryProvider) {
        Ssf2Mapper mapper = new Ssf2Mapper();
        mapper.baseMapper = baseMapper;
        mapper.memory = memoryProvider;
        LOG.info("Ssf2Mapper created and enabled");
        return mapper;
    }

    @Override
    public long readData(long address, Size size) {
        address = address & 0xFF_FFFF;
        if (address >= BANKABLE_START_ADDRESS && address <= CartridgeInfoProvider.DEFAULT_ROM_END_ADDRESS) {
            LogHelper.printLevel(LOG, Level.DEBUG, "Bank read: {}", address);
            int bankSelector = (int) (address / BANK_SIZE);
            address = (banks[bankSelector] * BANK_SIZE) + (address - bankSelector * BANK_SIZE);
            return Util.readRom(memory, size, address);
        }
        return baseMapper.readData(address, size);
    }

    @Override
    public void writeData(long addressL, long data, Size size) {
        if (addressL >= BANK_SET_START_ADDRESS && addressL <= BANK_SET_END_ADDRESS) {
            writeBankData(addressL, data);
            return;
        }
        baseMapper.writeData(addressL, data, size);
    }

    //	A page is specified with 6 bits (bits 7 and 6 are always 0) thus allowing a possible 64 pages
    // (SSFII only has 10, though.)
    @Override
    public void writeBankData(long addressL, long data) {
        if (addressL == 0xA130F3) {    //	0x080000 - 0x0FFFFF
            data = data & 0x3F;
            banks[1] = (int) data;

        } else if (addressL == 0xA130F5) {    //	0x100000 - 0x17FFFF
            data = data & 0x3F;
            banks[2] = (int) data;

        } else if (addressL == 0xA130F7) {    //	0x180000 - 0x1FFFFF
            data = data & 0x3F;
            banks[3] = (int) data;

        } else if (addressL == 0xA130F9) {    //	0x200000 - 0x27FFFF
            data = data & 0x3F;
            banks[4] = (int) data;

        } else if (addressL == 0xA130FB) {    //	0x280000 - 0x2FFFFF
            data = data & 0x3F;
            banks[5] = (int) data;

        } else if (addressL == 0xA130FD) {    //	0x300000 - 0x37FFFF
            data = data & 0x3F;
            banks[6] = (int) data;

        } else if (addressL == 0xA130FF) {    //	0x380000 - 0x3FFFFF
            data = data & 0x3F;
            banks[7] = (int) data;
        }
        LogHelper.printLevel(LOG, Level.INFO, "Bank write to: {}", addressL);
    }

}
