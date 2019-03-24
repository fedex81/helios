package omegadrive.bus.mapper;

import omegadrive.Genesis;
import omegadrive.bus.gen.GenesisBus;
import omegadrive.util.CartridgeInfoProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 *
 * TODO
 * http://gendev.spritesmind.net/forum/viewtopic.php?f=2&t=2476&p=29985&hilit=sram#p29985
 */
public class BackupMemoryMapper implements GenesisMapper {

    private static Logger LOG = LogManager.getLogger(BackupMemoryMapper.class.getSimpleName());

    private final static String DEFAULT_SRAM_FOLDER = System.getProperty("user.home") + "/.omgdrv/sram";

    private static boolean verbose = false || Genesis.verbose;

    public static boolean SRAM_AVAILABLE;
    public static long SRAM_START_ADDRESS;
    public static long SRAM_END_ADDRESS;

    private GenesisMapper baseMapper;
    private CartridgeInfoProvider cartridgeInfoProvider;
    private SramMode sramMode = SramMode.DISABLE;

    private static String fileType = "srm";
    private Path backupFile;

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
        LOG.info("BackupMemoryMapper created, using folder: " + DEFAULT_SRAM_FOLDER);
        initBackupFileIfNecessary(mapper);
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
            initBackupFileIfNecessary(this);
            address = (address & 0xFFFF);
            long res = Util.readSram(sram, size, address);
            logInfo("SRAM read at: {} {}, result: {} ", address, size, res);
            return res;
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
            initBackupFileIfNecessary(this);
            address = (address & 0xFFFF);
            logInfo("SRAM write at: {} {}, data: {} ", address, size, data);
            Util.writeSram(sram, size, (int) address, data);
        } else {
            baseMapper.writeData(address, data, size);
        }
    }

    private void writeFile(int[] sram) {
        initBackupFileIfNecessary(this);
        try {
            if (Files.isWritable(backupFile)) {
                FileLoader.writeFile(backupFile, sram);
            }
        } catch (IOException e) {
            LOG.error("Unable to write to file: " + backupFile, e);
        }
    }

    private static void initBackupFileIfNecessary(BackupMemoryMapper mapper) {
        if (mapper.backupFile == null) {
            try {
                mapper.backupFile = Paths.get(DEFAULT_SRAM_FOLDER,
                        mapper.cartridgeInfoProvider.getRomName() + "." + fileType);
                long size = 0;
                if (Files.isReadable(mapper.backupFile)) {
                    size = Files.size(mapper.backupFile);
                } else {
                    LOG.info("Creating backup memory file: " + mapper.backupFile);
                    mapper.sram = new int[CartridgeInfoProvider.DEFAULT_SRAM_BYTE_SIZE];
                    size = mapper.sram.length;
                    FileLoader.writeFile(mapper.backupFile, mapper.sram);
                }
                LOG.info("Using sram file: " + mapper.backupFile + " size: " + size + " bytes");
            } catch (Exception e) {
                LOG.error("Unable to create file for: " + mapper.cartridgeInfoProvider.getRomName());
            }
        }
    }

    @Override
    public void closeRom() {
        LOG.info("Writing to sram file: " + this.backupFile);
        writeFile(this.sram);
    }

    public static void logInfo(String str, Object... res) {
        if (verbose) {
            LOG.info(str, res);
        }
    }
}
