/*
 * GenesisCartInfoProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/05/19 13:33
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

package omegadrive.cart;

import omegadrive.cart.mapper.md.MdMapperType;
import omegadrive.memory.IMemoryProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public class MdCartInfoProvider extends CartridgeInfoProvider {

    public static final int ROM_HEADER_START = 0x100;

    static int SERIAL_NUMBER_START = 0x180;
    public static final long DEFAULT_SRAM_START_ADDRESS = 0x20_0000;
    public static final long DEFAULT_SRAM_END_ADDRESS = 0x20_FFFF;
    public static final int DEFAULT_SRAM_BYTE_SIZE = (int) (DEFAULT_SRAM_END_ADDRESS - DEFAULT_SRAM_START_ADDRESS) + 1;

    //see https://github.com/jdesiloniz/svpdev/wiki/Internal-ROM
    public static final int SVP_SV_TOKEN_ADDRESS = 0x1C8;
    public static final String SVP_SV_TOKEN = "SV";
    private static final Logger LOG = LogManager.getLogger(MdCartInfoProvider.class.getSimpleName());
    public static final int SRAM_FLAG_ADDRESS = 0x1B0;
    public static final int SRAM_START_ADDRESS = 0x1B4;
    public static final int SRAM_END_ADDRESS = 0x1B8;
    public static final int CHECKSUM_START_ADDRESS = 0x18E;

    public static final String EXTERNAL_RAM_FLAG_VALUE = "RA";

    private long sramStart;
    private long sramEnd;
    private boolean sramEnabled;
    static int SERIAL_NUMBER_END = SERIAL_NUMBER_START + 14;
    private int romSize;
    private String systemType;
    private MdMapperType forceMapper = null;
    private boolean isSvp;
    private String serial = "MISSING";

    public int getSramSizeBytes() {
        return (int) (sramEnd - sramStart + 1);
    }

    public boolean isSramEnabled() {
        return sramEnabled;
    }

    public int getRomSize() {
        return romSize;
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
        sb.append(systemType + ", serial: " + serial);
        sb.append(", " + memoryProvider.getRomHolder().toString());
        sb.append(", SRAM flag: " + sramEnabled).append("\n");
        sb.append(super.toString());
        if (sramEnabled) {
            sb.append("\nSRAM size: " + getSramSizeBytes() + " bytes, start-end: " + Long.toHexString(sramStart) + " - " +
                    Long.toHexString(sramEnd));
        }
        return sb.toString();
    }

    public static MdCartInfoProvider createInstance(IMemoryProvider memoryProvider, Path rom) {
        MdCartInfoProvider provider = new MdCartInfoProvider();
        provider.memoryProvider = memoryProvider;
        provider.romName = Optional.ofNullable(rom).map(p -> p.getFileName().toString()).orElse("norom.bin");
        provider.init();
        return provider;
    }

    public boolean isSramUsedWithBrokenHeader(long address) {
        boolean noOverlapBetweenRomAndSram =
                MdCartInfoProvider.DEFAULT_SRAM_START_ADDRESS + 1 > romSize;
        return noOverlapBetweenRomAndSram &&
                (address >= MdCartInfoProvider.DEFAULT_SRAM_START_ADDRESS &&
                        address <= MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS);
    }

    public String getSerial() {
        return serial;
    }

    private void initMemoryLayout(IMemoryProvider memoryProvider) {
        detectHeaderMetadata();
        romSize = memoryProvider.getRomData().length;
        detectSram();
    }


    private void detectSram() {
        String sramFlag = String.valueOf((char) memoryProvider.readRomByte(SRAM_FLAG_ADDRESS));
        sramFlag += (char) memoryProvider.readRomByte(SRAM_FLAG_ADDRESS + 1);
        boolean externalRamEnabled = EXTERNAL_RAM_FLAG_VALUE.equals(sramFlag);

        if (externalRamEnabled) {
            long byte1 = memoryProvider.readRomByte(SRAM_FLAG_ADDRESS + 2);
            long byte2 = memoryProvider.readRomByte(SRAM_FLAG_ADDRESS + 3);
            boolean isBackup = Util.bitSetTest(byte1, 7); //backup vs volatile
            boolean isSramType = (byte2 & 0x20) == 0x20; //sram vs EEPROM
            if (isBackup) { //&& isSramType) {
                sramEnabled = true;
                sramStart = readData(memoryProvider.getRomData(), Size.WORD, SRAM_START_ADDRESS) << 16;
                sramStart |= readData(memoryProvider.getRomData(), Size.WORD, SRAM_START_ADDRESS + 2);
                sramEnd = readData(memoryProvider.getRomData(), Size.WORD, SRAM_END_ADDRESS) << 16;
                sramEnd |= readData(memoryProvider.getRomData(), Size.WORD, SRAM_END_ADDRESS + 2);
                if (sramEnd - sramStart < 0) {
                    LOG.error("Unexpected SRAM setup: {}", toString());
                    sramStart = DEFAULT_SRAM_START_ADDRESS;
                    sramEnd = DEFAULT_SRAM_END_ADDRESS;
                }
            } else if (!isBackup && isSramType) {
                LOG.warn("Volatile SRAM? {}", romName);
            }
        }
    }

    private void detectHeaderMetadata() {
        int[] rom = memoryProvider.getRomData();
        if (rom.length < SERIAL_NUMBER_END) {
            return;
        }

        systemType = new String(rom, ROM_HEADER_START, 16).trim();
        forceMapper = MdMapperType.getMdMapperType(systemType);
        int[] serialArray = Arrays.copyOfRange(memoryProvider.getRomData(), SERIAL_NUMBER_START, SERIAL_NUMBER_END);
        this.serial = Util.toStringValue(serialArray);
        if (rom.length > SVP_SV_TOKEN_ADDRESS + 1) {
            isSvp = SVP_SV_TOKEN.equals(new String(rom, SVP_SV_TOKEN_ADDRESS, 2).trim());
        }
    }

    public boolean isSsfMapper() {
        return forceMapper != null;
    }

    public boolean isSvp() {
        return isSvp;
    }

    public boolean adjustSramLimits(long address) {
        //FIFA 96
        boolean adjust = sramEnd < MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        adjust &= address > sramEnd && address < MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        if (adjust) {
            LOG.warn("Adjusting SRAM limit from: {} to: {}", Long.toHexString(sramEnd),
                    Long.toHexString(MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS));
            sramEnd = MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        }
        return adjust;
    }

    private static long readData(int[] src, Size size, int address) {
        return Util.readDataMask(src, size, address, Integer.MAX_VALUE);
    }
}
