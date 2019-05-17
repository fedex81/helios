/*
 * CartridgeInfoProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive.util;

import omegadrive.bus.gen.GenesisBus;
import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * TODO support EEPROM, f1 world champ edition
 */
public class GenesisCartInfoProvider extends CartridgeInfoProvider{

    private static Logger LOG = LogManager.getLogger(GenesisCartInfoProvider.class.getSimpleName());

    public static final long DEFAULT_ROM_START_ADDRESS = 0x00_0000;
    public static final long DEFAULT_RAM_END_ADDRESS = GenesisBusProvider.ADDRESS_UPPER_LIMIT;
    public static final long DEFAULT_RAM_START_ADDRESS = 0xFF_0000;
    public static final long DEFAULT_SRAM_START_ADDRESS = 0x20_0000;
    public static final long DEFAULT_SRAM_END_ADDRESS = 0x20_FFFF;
    public static final int DEFAULT_SRAM_BYTE_SIZE = (int) (DEFAULT_SRAM_END_ADDRESS - DEFAULT_SRAM_START_ADDRESS) + 1;


    public static final int ROM_START_ADDRESS = 0x1A0;
    public static final int ROM_END_ADDRESS = 0x1A4;
    public static final int RAM_START_ADDRESS = 0x1A8;
    public static final int RAM_END_ADDRESS = 0x1AC;
    public static final int SRAM_FLAG_ADDRESS = 0x1B0;
    public static final int SRAM_START_ADDRESS = 0x1B4;
    public static final int SRAM_END_ADDRESS = 0x1B8;
    public static final int CHECKSUM_START_ADDRESS = 0x18E;

    public static final String EXTERNAL_RAM_FLAG_VALUE = "RA";

    private long romStart;
    private long romEnd;
    private long ramStart;
    private long ramEnd;
    private long sramStart;
    private long sramEnd;
    private boolean sramEnabled;

    public long getRomStart() {
        return romStart;
    }

    public long getRomEnd() {
        return romEnd;
    }

    public long getRamStart() {
        return ramStart;
    }

    public long getRamEnd() {
        return ramEnd;
    }

    public long getSramStart() {
        return sramStart;
    }

    public long getSramEnd() {
        return sramEnd;
    }

    public int getSramSizeBytes() {
        return (int) (sramEnd - sramStart + 1);
    }

    public boolean isSramEnabled() {
        return sramEnabled;
    }

    public void setSramEnd(long sramEnd) {
        this.sramEnd = sramEnd;
    }

    public static GenesisCartInfoProvider createInstance(IMemoryProvider memoryProvider, String rom) {
        GenesisCartInfoProvider provider = new GenesisCartInfoProvider();
        provider.memoryProvider = memoryProvider;
        provider.romName = rom;
        provider.init();
        return provider;
    }

    @Override
    public int getChecksumStartAddress() {
        return CHECKSUM_START_ADDRESS;
    }

    @Override
    protected void init() {
        this.initMemoryLayout(memoryProvider);
        super.init();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROM size: " + (romEnd - romStart + 1) + " bytes, start-end: " + Long.toHexString(romStart) + " - " +
                Long.toHexString(romEnd)).append("\n");
        sb.append("RAM size: " + (ramEnd - ramStart + 1) + " bytes, start-end: " + Long.toHexString(ramStart) + " - " +
                Long.toHexString(ramEnd)).append("\n");
        sb.append("SRAM flag: " + sramEnabled).append("\n");
        sb.append(super.toString());
        if (sramEnabled) {
            sb.append("\nSRAM size: " + getSramSizeBytes() + " bytes, start-end: " + Long.toHexString(sramStart) + " - " +
                    Long.toHexString(sramEnd));
        }
        return sb.toString();
    }

    public String toSramCsvString() {
        return sramEnabled + ";" + Long.toHexString(sramStart) + ";" + Long.toHexString(sramEnd) +
                ";" + getSramSizeBytes();
    }

    private void initMemoryLayout(IMemoryProvider memoryProvider) {
        romStart = Util.readRom(memoryProvider, Size.WORD, ROM_START_ADDRESS) << 16;
        romStart |= Util.readRom(memoryProvider, Size.WORD, ROM_START_ADDRESS + 2);
        romEnd = Util.readRom(memoryProvider, Size.WORD, ROM_END_ADDRESS) << 16;
        romEnd |= Util.readRom(memoryProvider, Size.WORD, ROM_END_ADDRESS + 2);

        ramStart = Util.readRom(memoryProvider, Size.WORD, RAM_START_ADDRESS) << 16;
        ramStart |= Util.readRom(memoryProvider, Size.WORD, RAM_START_ADDRESS + 2);
        ramEnd = Util.readRom(memoryProvider, Size.WORD, RAM_END_ADDRESS) << 16;
        ramEnd |= Util.readRom(memoryProvider, Size.WORD, RAM_END_ADDRESS + 2);
        detectSram();
        checkLayout();
    }


    private void detectSram() {
        String sramFlag = "" + (char) memoryProvider.readRomByte(SRAM_FLAG_ADDRESS);
        sramFlag += (char) memoryProvider.readRomByte(SRAM_FLAG_ADDRESS + 1);
        boolean externalRamEnabled = EXTERNAL_RAM_FLAG_VALUE.equals(sramFlag);

        if (externalRamEnabled) {
            long byte1 = memoryProvider.readRomByte(SRAM_FLAG_ADDRESS + 2);
            long byte2 = memoryProvider.readRomByte(SRAM_FLAG_ADDRESS + 3);
            boolean isBackup = Util.bitSetTest(byte1, 7); //backup vs volatile
            boolean isSramType = (byte2 & 0x20) == 0x20; //sram vs EEPROM
            if (isBackup) { //&& isSramType) {
                sramEnabled = true;
                sramStart = Util.readRom(memoryProvider, Size.WORD, SRAM_START_ADDRESS) << 16;
                sramStart |= Util.readRom(memoryProvider, Size.WORD, SRAM_START_ADDRESS + 2);
                sramEnd = Util.readRom(memoryProvider, Size.WORD, SRAM_END_ADDRESS) << 16;
                sramEnd |= Util.readRom(memoryProvider, Size.WORD, SRAM_END_ADDRESS + 2);
                if (sramEnd - sramStart < 0) {
                    LOG.error("Unexpected SRAM setup: " + toString());
                    sramStart = DEFAULT_SRAM_START_ADDRESS;
                    sramEnd = DEFAULT_SRAM_END_ADDRESS;
                }
            } else if (!isBackup && isSramType) {
                LOG.warn("Volatile SRAM? " + romName);
            }
        }
    }

    //homebrew roms are sometimes broken
    private void checkLayout() {
        if (romStart > romEnd) {
            LOG.warn("Invalid ROM START ADDRESS: " + romStart);
            LOG.warn("Invalid ROM END ADDRESS: " + romEnd);
            romStart = DEFAULT_ROM_START_ADDRESS;
            romEnd = GenesisBusProvider.DEFAULT_ROM_END_ADDRESS;
        }
        if (romStart != 0) {
            LOG.warn("Invalid ROM START ADDRESS: " + romStart);
            romStart = DEFAULT_ROM_START_ADDRESS;
        }
        int romEndAddressFromFile = memoryProvider.getRomSize() - 1;
        if (romEnd != romEndAddressFromFile) {
            LOG.warn("Invalid ROM END ADDRESS: " + romEnd + ", setting to: " + romEndAddressFromFile);
            romEnd = romEndAddressFromFile;
        }
        if (ramStart == 0) {
            LOG.warn("Unable to parse RAM START ADDRESS");
            ramStart = DEFAULT_RAM_START_ADDRESS;
        }
        if (ramEnd == 0) {
            LOG.warn("Unable to parse RAM END ADDRESS");
            ramEnd = DEFAULT_RAM_END_ADDRESS;
        }
        if (Math.abs(ramEnd - ramStart) > MemoryProvider.M68K_RAM_SIZE) {
            LOG.warn("Invalid RAM size: " + (ramEnd - ramStart) + " bytes");
            ramStart = DEFAULT_RAM_START_ADDRESS;
            ramEnd = DEFAULT_RAM_END_ADDRESS;
        }
    }

    public static boolean isSramUsedWithBrokenHeader(long address) {
        boolean noOverlapBetweenRomAndSram =
                GenesisCartInfoProvider.DEFAULT_SRAM_START_ADDRESS > GenesisBus.ROM_END_ADDRESS;
        return noOverlapBetweenRomAndSram &&
                (address >= GenesisCartInfoProvider.DEFAULT_SRAM_START_ADDRESS &&
                        address <= GenesisCartInfoProvider.DEFAULT_SRAM_END_ADDRESS);
    }

    public boolean adjustSramLimits(long address) {
        //FIFA 96
        boolean adjust = getSramEnd() < GenesisCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        adjust &= address > getSramEnd() && address < GenesisCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        if (adjust) {
            LOG.warn("Adjusting SRAM limit from: {} to: {}", Long.toHexString(getSramEnd()),
                    Long.toHexString(GenesisCartInfoProvider.DEFAULT_SRAM_END_ADDRESS));
            setSramEnd(GenesisCartInfoProvider.DEFAULT_SRAM_END_ADDRESS);
        }
        return adjust;
    }
}
