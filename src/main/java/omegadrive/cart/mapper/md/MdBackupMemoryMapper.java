/*
 * GenesisBackupMemoryMapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 13:51
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

package omegadrive.cart.mapper.md;

import omegadrive.SystemLoader;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cart.loader.MdRomDbModel;
import omegadrive.cart.mapper.BackupMemoryMapper;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static omegadrive.cart.MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
import static omegadrive.cart.MdCartInfoProvider.DEFAULT_SRAM_START_ADDRESS;

public class MdBackupMemoryMapper extends BackupMemoryMapper implements RomMapper {

    private final static Logger LOG = LogManager.getLogger(MdBackupMemoryMapper.class.getSimpleName());

    private final static boolean verbose = false;
    private static String fileType = "srm";
    private RomMapper baseMapper;
    private SramMode sramMode = SramMode.DISABLE;
    private MdRomDbModel.EEPROM eeprom = MdRomDbModel.NO_EEPROM;
    private I2cEeprom i2c = I2cEeprom.NO_OP;

    private MdBackupMemoryMapper(String romName, int size) {
        super(SystemLoader.SystemType.GENESIS, fileType, romName, size);
    }

    public static RomMapper createInstance(RomMapper baseMapper, MdCartInfoProvider cart, MdRomDbModel.Entry entry) {
        return createInstance(baseMapper, cart, SramMode.DISABLE, entry);
    }

    public static RomMapper createInstance(RomMapper baseMapper, MdCartInfoProvider cart,
                                           SramMode sramMode, MdRomDbModel.Entry entry) {
        int size = !entry.hasEeprom() ? MdCartInfoProvider.DEFAULT_SRAM_BYTE_SIZE : entry.getEeprom().getSize();
        MdBackupMemoryMapper mapper = new MdBackupMemoryMapper(cart.getRomName(), size);
        mapper.baseMapper = baseMapper;
        mapper.sramMode = SramMode.READ_WRITE;
        mapper.eeprom = entry.getEeprom();
        mapper.i2c = I2cEeprom.createInstance(entry);
        LOG.info("BackupMemoryMapper created, using folder: {}", mapper.sramFolder);
        mapper.initBackupFileIfNecessary();
        return mapper;
    }

    public static void logInfo(String str, Object... res) {
        if (verbose) {
            LOG.info(str, res);
        }
    }

    @Override
    public void setSramMode(SramMode sramMode) {
        if (this.sramMode != sramMode) {
            LOG.info("SramMode from: {} to: {}", this.sramMode, sramMode);
        }
        this.sramMode = sramMode;
    }

    @Override
    public long readData(long address, Size size) {
        return eeprom == MdRomDbModel.NO_EEPROM ? readDataSram(address, size) : readDataEeprom(address, size);
    }


    @Override
    public void writeData(long address, long data, Size size) {
        if (eeprom == MdRomDbModel.NO_EEPROM) {
            writeDataSram(address, data, size);
        } else {
            writeDataEeprom(address, data, size);
        }
    }

    private long readDataSram(long address, Size size) {
        address = address & 0xFF_FFFF;
        boolean sramRead = sramMode != SramMode.DISABLE;
        sramRead &= address >= DEFAULT_SRAM_START_ADDRESS && address <= DEFAULT_SRAM_END_ADDRESS;
        if (sramRead) {
            initBackupFileIfNecessary();
            long res = Util.readData(sram, size, (int) (address & 0xFFFF));
            logInfo("SRAM read at: {} {}, result: {} ", address & 0xFFFF, size, res);
            return res;
        }
        return baseMapper.readData(address, size);
    }

    private void writeDataSram(long address, long data, Size size) {
        address = address & 0xFF_FFFF;
        boolean sramWrite = sramMode == SramMode.READ_WRITE;
        sramWrite &= address >= DEFAULT_SRAM_START_ADDRESS && address <= DEFAULT_SRAM_END_ADDRESS;
        if (!sramWrite) {
            baseMapper.writeData(address, data, size);
        } else if (sramWrite) {
            initBackupFileIfNecessary();
            address = (address & 0xFFFF);
            logInfo("SRAM write at: {} {}, data: {} ", address, size, data);
            Util.writeData(sram, size, (int) address, data);
        }
    }

    private long readDataEeprom(long address, Size size) {
        address = address & 0xFF_FFFF;
        boolean eepromRead = sramMode != SramMode.DISABLE;
        eepromRead &= address >= DEFAULT_SRAM_START_ADDRESS && address <= DEFAULT_SRAM_END_ADDRESS;
        if (eepromRead) {
            initBackupFileIfNecessary();
            long res = i2c.eeprom_i2c_out();
            logInfo("EEPROM read at: {} {}, result: {} ", address, size, res);
            return res;
        }
        return baseMapper.readData(address, size);
    }

    private void writeDataEeprom(long address, long data, Size size) {
        address = address & 0xFF_FFFF;
        boolean eepromWrite = sramMode == SramMode.READ_WRITE;
        eepromWrite &= address >= DEFAULT_SRAM_START_ADDRESS && address <= DEFAULT_SRAM_END_ADDRESS;
        if (eepromWrite) {
            initBackupFileIfNecessary();
            i2c.eeprom_i2c_in((int) (data & 0xFF));
            logInfo("EEPROM write at: {} {}, data: {} ", address, size, data);
        } else {
            baseMapper.writeData(address, data, size);
        }
    }

    @Override
    public void closeRom() {
        writeFile();
    }
}