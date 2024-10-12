/*
 * MdBackupMemoryMapper
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
import omegadrive.cart.loader.MdRomDbModel.RomDbEntry;
import omegadrive.cart.loader.MdRomDbModel.RomDbEntry.EepromEntry;
import omegadrive.cart.mapper.BackupMemoryMapper;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.cart.mapper.md.eeprom.EepromBase;
import omegadrive.cart.mapper.md.eeprom.I2cEeprom;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.util.Optional;

import static omegadrive.cart.MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
import static omegadrive.cart.MdCartInfoProvider.DEFAULT_SRAM_START_ADDRESS;
import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;
import static omegadrive.util.Util.th;

public class MdBackupMemoryMapper extends BackupMemoryMapper implements RomMapper {

    private final static Logger LOG = LogHelper.getLogger(MdBackupMemoryMapper.class.getSimpleName());

    //astebros demo needs true
    public final static boolean allowSramWritesWhenReadOnly =
            Boolean.parseBoolean(java.lang.System.getProperty("md.sram.always.allow.writes", "true"));
    private final static boolean verbose = false;
    private static final String fileType = "srm";
    private RomMapper baseMapper;
    private SramMode sramMode = SramMode.DISABLE;
    private EepromEntry eepromDbEntry = MdRomDbModel.NO_EEPROM;
    private EepromBase eeprom = EepromBase.NO_OP;

    private MdBackupMemoryMapper(String romName, int size) {
        super(SystemLoader.SystemType.MD, fileType, romName, size);
    }

    public static RomMapper createInstance(RomMapper baseMapper, MdCartInfoProvider cart, RomDbEntry entry) {
        return createInstance(baseMapper, cart, SramMode.DISABLE, entry);
    }

    public static RomMapper createInstance(RomMapper baseMapper, MdCartInfoProvider cart,
                                           SramMode sramMode, RomDbEntry entry) {
        boolean isStm95 = entry.hasEeprom() ? entry.eeprom.getEepromType() == MdRomDbModel.EepromType.STM_95 : false;
        if (isStm95) {
            return MdT5740Mapper.createInstance(cart.getRomName(), baseMapper);
        }
        int size = !entry.hasEeprom() ? MdCartInfoProvider.DEFAULT_SRAM_BYTE_SIZE : entry.eeprom.getEepromSize();
        MdBackupMemoryMapper mapper = new MdBackupMemoryMapper(cart.getRomName(), size);
        mapper.baseMapper = baseMapper;
        mapper.sramMode = SramMode.READ_WRITE;
        mapper.eepromDbEntry = Optional.ofNullable(entry.eeprom).orElse(MdRomDbModel.NO_EEPROM);
        mapper.eeprom = I2cEeprom.createInstance(entry, mapper.sram);
        LOG.info("BackupMemoryMapper created, using folder: {}", mapper.sramFolder);
        mapper.initBackupFileIfNecessary();
        return mapper;
    }

    @Override
    public void setSramMode(SramMode sramMode) {
        if (this.sramMode != sramMode) {
            LOG.info("SramMode from: {} to: {}", this.sramMode, sramMode);
        }
        this.sramMode = sramMode;
    }

    @Override
    public int readData(int address, Size size) {
        return eepromDbEntry == MdRomDbModel.NO_EEPROM ? readDataSram(address, size) : readDataEeprom(address, size);
    }


    @Override
    public void writeData(int address, int data, Size size) {
        if (eepromDbEntry == MdRomDbModel.NO_EEPROM) {
            writeDataSram(address, data, size);
        } else {
            writeDataEeprom(address, data, size);
        }
    }

    private int readDataSram(int address, Size size) {
        address = address & MD_PC_MASK;
        boolean sramRead = sramMode != SramMode.DISABLE;
        sramRead &= address >= DEFAULT_SRAM_START_ADDRESS && address <= DEFAULT_SRAM_END_ADDRESS;
        if (sramRead) {
            initBackupFileIfNecessary();
//            assert size == Size.BYTE : size; //TODO MdMapperTest writes word/long, check sw doing that
            int res = Util.readDataMask(sram, address, sramMask, size);
            if (verbose) LOG.info("SRAM read at: {} {}, result: {} ", address & 0xFFFF, size, res);
            if (size != Size.BYTE && (address & 1) == 1) {
                LOG.error("sram read: {} {}, val: {}", th(address), size, th(res));
            }
            return res;
        }
        return baseMapper.readData(address, size);
    }

    private void writeDataSram(int address, int data, Size size) {
        address = address & MD_PC_MASK;
        boolean sramWrite = allowSramWritesWhenReadOnly || sramMode == SramMode.READ_WRITE;
        sramWrite &= address >= DEFAULT_SRAM_START_ADDRESS && address <= DEFAULT_SRAM_END_ADDRESS;
        if (!sramWrite) {
            baseMapper.writeData(address, data, size);
        } else {
            initBackupFileIfNecessary();
            if (verbose) LOG.info("SRAM write at: {} {}, data: {} ", address, size, data);
//            assert size == Size.BYTE : size; //TODO MdMapperTest writes word/long, check sw doing that
            Util.writeDataMask(sram, address, data, sramMask, size);
            if (size != Size.BYTE && (address & 1) == 1) {
                LOG.error("sram write: {} {}, val: {}", th(address), size, th(data));
            }
        }
    }

    private int readDataEeprom(int address, Size size) {
        address = address & MD_PC_MASK;
        //NFL Qc 32x reads to 0x20_0000 LONG with SRAM enabled and expects to read from cart
        boolean eepromRead = sramMode != SramMode.DISABLE && size != Size.LONG;
        eepromRead &= address == DEFAULT_SRAM_START_ADDRESS || address == DEFAULT_SRAM_START_ADDRESS + 1;
        if (eepromRead) {
            initBackupFileIfNecessary();
            int res = eeprom.readEeprom(address, size);
            if (verbose) LOG.info("EEPROM read at: {} {}, result: {} ", th(address), size, th(res));
            return res;
        }
        return baseMapper.readData(address, size);
    }

    private void writeDataEeprom(int address, int data, Size size) {
        address = address & MD_PC_MASK;
        boolean eepromWrite = sramMode == SramMode.READ_WRITE;
        eepromWrite &= address == DEFAULT_SRAM_START_ADDRESS || address == DEFAULT_SRAM_START_ADDRESS + 1;
        if (eepromWrite) {
            initBackupFileIfNecessary();
            eeprom.writeEeprom(address, (data & 0xFF), size);
            if (verbose) LOG.info("EEPROM write at: {} {}, data: {} ", th(address), size, th(data));
        } else {
            baseMapper.writeData(address, data, size);
        }
    }

    @Override
    protected void initBackupFileIfNecessary() {
        super.initBackupFileIfNecessary();
        eeprom.setSram(sram);
    }

    @Override
    public void closeRom() {
        writeFile();
    }
}