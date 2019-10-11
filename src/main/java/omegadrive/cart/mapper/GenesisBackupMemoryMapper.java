/*
 * GenesisBackupMemoryMapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 15:05
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.cart.mapper;

import omegadrive.SystemLoader;
import omegadrive.bus.gen.GenesisBus;
import omegadrive.cart.GenesisCartInfoProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GenesisBackupMemoryMapper extends BackupMemoryMapper implements RomMapper {

    public static boolean SRAM_AVAILABLE;
    public static long SRAM_START_ADDRESS;
    public static long SRAM_END_ADDRESS;
    /**
     * TODO
     * http://gendev.spritesmind.net/forum/viewtopic.php?f=2&t=2476&p=29985&hilit=sram#p29985
     */

    private static Logger LOG = LogManager.getLogger(GenesisBackupMemoryMapper.class.getSimpleName());
    private static boolean verbose = false;
    private static String fileType = "srm";
    private RomMapper baseMapper;
    private GenesisCartInfoProvider cartridgeInfoProvider;
    private SramMode sramMode = SramMode.DISABLE;

    private GenesisBackupMemoryMapper(String romName) {
        super(SystemLoader.SystemType.GENESIS, fileType, romName, GenesisCartInfoProvider.DEFAULT_SRAM_BYTE_SIZE);
    }

    public static RomMapper createInstance(RomMapper baseMapper, GenesisCartInfoProvider cart) {
        return createInstance(baseMapper, cart, SramMode.DISABLE);
    }

    private static RomMapper createInstance(RomMapper baseMapper, GenesisCartInfoProvider cart,
                                            SramMode sramMode) {
        GenesisBackupMemoryMapper mapper = new GenesisBackupMemoryMapper(cart.getRomName());
        mapper.baseMapper = baseMapper;
        mapper.sramMode = sramMode;
        mapper.cartridgeInfoProvider = cart;
        SRAM_START_ADDRESS = mapper.cartridgeInfoProvider.getSramStart();
        SRAM_START_ADDRESS = SRAM_START_ADDRESS > 0 ? SRAM_START_ADDRESS : GenesisCartInfoProvider.DEFAULT_SRAM_START_ADDRESS;
        SRAM_END_ADDRESS = mapper.cartridgeInfoProvider.getSramEnd();
        SRAM_END_ADDRESS = SRAM_END_ADDRESS > 0 ? SRAM_END_ADDRESS : GenesisCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        SRAM_AVAILABLE = true; //mapper.cartridgeInfoProvider.isSramEnabled();
        LOG.info("BackupMemoryMapper created, using folder: " + mapper.sramFolder);
        mapper.initBackupFileIfNecessary();
        return mapper;
    }

    public static RomMapper getOrCreateInstance(RomMapper baseMapper,
                                                RomMapper currentMapper,
                                                GenesisCartInfoProvider cartridgeInfoProvider,
                                                SramMode sramMode) {
        if (baseMapper != currentMapper) {
            currentMapper.setSramMode(sramMode);
            return currentMapper;
        }
        return createInstance(baseMapper, cartridgeInfoProvider, sramMode);

    }

    private static boolean noOverlapBetweenRomAndSram() {
        return SRAM_START_ADDRESS > GenesisBus.ROM_END_ADDRESS;
    }

    public static void logInfo(String str, Object... res) {
        if (verbose) {
            LOG.info(str, res);
        }
    }

    @Override
    public void setSramMode(SramMode sramMode) {
        if (this.sramMode != sramMode) {
            LOG.info("SramMode from: " + this.sramMode + " to: " + sramMode);
        }
        this.sramMode = sramMode;
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
            initBackupFileIfNecessary();
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
            initBackupFileIfNecessary();
            address = (address & 0xFFFF);
            logInfo("SRAM write at: {} {}, data: {} ", address, size, data);
            Util.writeSram(sram, size, (int) address, data);
        } else {
            baseMapper.writeData(address, data, size);
        }
    }

    @Override
    public void closeRom() {
        writeFile();
    }
}
