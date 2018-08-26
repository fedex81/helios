package omegadrive.bus;

import omegadrive.Genesis;
import omegadrive.util.CartridgeInfoProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class BackupMemoryMapper implements GenesisMapper {

    private static Logger LOG = LogManager.getLogger(BackupMemoryMapper.class.getSimpleName());

    private static boolean verbose = false || Genesis.verbose;

    public static boolean SRAM_AVAILABLE;
    public static long SRAM_START_ADDRESS;
    public static long SRAM_END_ADDRESS;

    private GenesisMapper baseMapper;
    private CartridgeInfoProvider cartridgeInfoProvider;
    private SramMode sramMode = SramMode.DISABLE;

    private int[] sram = new int[0];

    public static GenesisMapper createInstance(GenesisMapper baseMapper, CartridgeInfoProvider cart) {
        return createInstance(baseMapper, cart, SramMode.DISABLE);
    }

    private static GenesisMapper createInstance(GenesisMapper baseMapper, CartridgeInfoProvider cart,
                                                SramMode sramMode) {
        BackupMemoryMapper mapper = new BackupMemoryMapper();
        mapper.baseMapper = baseMapper;
        mapper.sramMode = sramMode;
        mapper.cartridgeInfoProvider = cart;
        SRAM_START_ADDRESS = mapper.cartridgeInfoProvider.getSramStart();
        SRAM_START_ADDRESS = SRAM_START_ADDRESS > 0 ? SRAM_START_ADDRESS : CartridgeInfoProvider.DEFAULT_SRAM_START_ADDRESS;
        SRAM_END_ADDRESS = mapper.cartridgeInfoProvider.getSramEnd();
        SRAM_END_ADDRESS = SRAM_END_ADDRESS > 0 ? SRAM_END_ADDRESS : CartridgeInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        SRAM_AVAILABLE = true; //mapper.cartridgeInfoProvider.isSramEnabled();
        mapper.sram = new int[CartridgeInfoProvider.DEFAULT_SRAM_BYTE_SIZE];
        LOG.info("BackupMemoryMapper created");
        return mapper;
    }

    public static GenesisMapper getOrCreateInstance(GenesisMapper baseMapper,
                                                    GenesisMapper currentMapper,
                                                    CartridgeInfoProvider cartridgeInfoProvider,
                                                    SramMode sramMode) {
        if (baseMapper != currentMapper) {
            currentMapper.setSramMode(sramMode);
            return currentMapper;
        }
        return createInstance(baseMapper, cartridgeInfoProvider, sramMode);

    }

    @Override
    public void setSramMode(SramMode sramMode) {
        if (this.sramMode != sramMode) {
            LOG.info("SramMode from: " + this.sramMode + " to: " + sramMode);
        }
        this.sramMode = sramMode;
    }

    private static boolean noOverlapBetweenRomAndSram() {
        return SRAM_START_ADDRESS > GenesisBus.ROM_END_ADDRESS;
    }

    private static boolean isBrokenSramHeader(long address) {
        boolean shouldUseSram = !SRAM_AVAILABLE && noOverlapBetweenRomAndSram() &&
                (address >= CartridgeInfoProvider.DEFAULT_SRAM_START_ADDRESS &&
                        address <= CartridgeInfoProvider.DEFAULT_SRAM_END_ADDRESS);
        return shouldUseSram;
    }

    @Override
    public long readData(long address, Size size) {
        address = address & 0xFF_FFFF;
        boolean noOverlap = noOverlapBetweenRomAndSram();
        boolean sramRead = sramMode != SramMode.DISABLE;
        sramRead |= noOverlap; //if no overlap allow to read
        //TODO EEPROM reads serially
        if (sramRead && address == (SRAM_START_ADDRESS - 1) && size == Size.WORD) {
            address = SRAM_START_ADDRESS;
        }
        sramRead &= address >= SRAM_START_ADDRESS && address <= SRAM_END_ADDRESS;
        if (sramRead) {
            address = address - SRAM_START_ADDRESS;
            return Util.readSram(sram, size, address);
        }
        return baseMapper.readData(address, size);
    }

    @Override
    public void writeData(long address, long data, Size size) {
        address = address & 0xFF_FFFF;
        boolean sramWrite = sramMode == SramMode.READ_WRITE;
        sramWrite |= noOverlapBetweenRomAndSram();  //if no overlap allow to write
        //TODO EEPROM, writes serially NBA Jam
        if (sramWrite && address == (SRAM_START_ADDRESS - 1) && size == Size.WORD) {
            address = SRAM_START_ADDRESS;
            data = data << 8 | data;
        }
        sramWrite &= address >= SRAM_START_ADDRESS && address <= SRAM_END_ADDRESS;
        if (sramWrite) {
            address = address - SRAM_START_ADDRESS;
            Util.writeSram(sram, size, address, data);
        } else {
            baseMapper.writeData(address, data, size);
        }
    }
}
