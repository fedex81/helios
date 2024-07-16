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

import omegadrive.cart.loader.MdLoader;
import omegadrive.cart.loader.MdRomDbModel;
import omegadrive.cart.mapper.md.MdMapperType;
import omegadrive.memory.IMemoryProvider;
import omegadrive.system.SysUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static omegadrive.cart.MdCartInfoProvider.MdRomHeaderField.*;
import static omegadrive.system.SystemProvider.RomContext;
import static omegadrive.util.Util.th;

public class MdCartInfoProvider extends CartridgeInfoProvider {

    public static Charset SHIFT_JIS;

    static {
        try {
            SHIFT_JIS = Charset.forName("SHIFT-JIS");
        } catch (Exception e) {
            System.out.println("Charset SHIFT-JIS not supported");
            SHIFT_JIS = null;
        }
    }

    //from https://plutiedev.com/rom-header
    public enum MdRomHeaderField {
        SYSTEM_TYPE(0x100, 16),
        COPYRIGHT_RELEASE_DATE(0x110, 16),
        TITLE_DOMESTIC(0x120, 48),
        TITLE_OVERSEAS(0x150, 48),
        SERIAL_NUMBER(0x180, 14),
        ROM_CHECKSUM(0x18E, 2, true),
        DEVICE_SUPPORT(0x190, 16),
        ROM_ADDR_RANGE(0x1A0, 8, true),
        RAM_ADDR_RANGE(0x1A8, 8, true),
        EXTRA_MEMORY(0x1B0, 12),
        MODEM_SUPPORT(0x1BC, 12),
        RESERVED1(0x1C8, 40),
        REGION_SUPPORT(0x1F0, 3),
        RESERVED2(0x1F3, 13);

        static HexFormat hf = HexFormat.of().withSuffix(" ");

        public final int startOffset;
        public final int len;
        public final boolean rawNumber;

        MdRomHeaderField(int so, int l) {
            this(so, l, false);
        }

        MdRomHeaderField(int so, int l, boolean rn) {
            startOffset = so;
            len = l;
            rawNumber = rn;
        }

        public String getValue(byte[] data) {
            if (this == EXTRA_MEMORY) {
                return extraMemStr(data);
            }
            if (this == TITLE_DOMESTIC) {
                return titleDomesticStr(data);
            }
            return rawNumber
                    ? hf.formatHex(data, startOffset, startOffset + len).trim()
                    : new String(data, startOffset, len, StandardCharsets.US_ASCII);
        }

        public String getStringView(byte[] data) {
            return this + ": " + getValue(data);
        }

        private static String titleDomesticStr(byte[] data) {
            String s1 = new String(data, TITLE_DOMESTIC.startOffset, TITLE_DOMESTIC.len);
            if (SHIFT_JIS != null) {
                String s2 = new String(data, TITLE_DOMESTIC.startOffset, TITLE_DOMESTIC.len, SHIFT_JIS);
                if (!s1.equals(s2)) {
                    s1 = s2;
                }
            }
            return s1;
        }


        private static String extraMemStr(byte[] data) {
            int skipRaOffset = 2;
            String s = new String(data, EXTRA_MEMORY.startOffset, skipRaOffset) + " ";
            if (s.trim().length() == 0) {
                skipRaOffset = 0;
            }
            s += hf.formatHex(data, EXTRA_MEMORY.startOffset + skipRaOffset,
                    EXTRA_MEMORY.startOffset + EXTRA_MEMORY.len).trim();
            return s;
        }
    }

    public static final MdRomHeaderField[] rhf = MdRomHeaderField.values();
    public static final long DEFAULT_SRAM_START_ADDRESS = 0x20_0000;
    public static final long DEFAULT_SRAM_END_ADDRESS = 0x20_FFFF;
    public static final int DEFAULT_SRAM_BYTE_SIZE = (int) (DEFAULT_SRAM_END_ADDRESS - DEFAULT_SRAM_START_ADDRESS) + 1;

    //see https://github.com/jdesiloniz/svpdev/wiki/Internal-ROM
    public static final int SVP_SV_TOKEN_ADDRESS = RESERVED1.startOffset;
    public static final String SVP_SV_TOKEN = "SV";
    private static final Logger LOG = LogHelper.getLogger(MdCartInfoProvider.class.getSimpleName());
    public static final int SRAM_FLAG_ADDRESS = EXTRA_MEMORY.startOffset;
    public static final int SRAM_START_ADDRESS = 0x1B4;
    public static final int SRAM_END_ADDRESS = 0x1B8;
    public static final String EXTERNAL_RAM_FLAG_VALUE = "RA";

    private long sramStart;
    private long sramEnd;
    private boolean sramEnabled;
    private int romSize;
    private String systemType, region = "";

    private String headerInfo = "";
    private MdMapperType forceMapper = null;

    private MdRomDbModel.RomDbEntry entry = MdRomDbModel.NO_ENTRY;
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
        return MdRomHeaderField.ROM_CHECKSUM.startOffset;
    }

    @Override
    protected void init() {
        super.init();
        initMemoryLayout(memoryProvider);
        entry = MdLoader.getEntry(serial);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(systemType).append(", serial: ").append(serial);
        sb.append(", ").append(memoryProvider.getRomHolder().toString());
        sb.append(", SRAM flag: ").append(sramEnabled).append("\n");
        sb.append(super.toString());
        if (sramEnabled) {
            sb.append("\nSRAM size: ").append(getSramSizeBytes()).append(" bytes, start-end: ").append(th(sramStart)).append(" - ").append(th(sramEnd));
        }
        if (entry != MdRomDbModel.NO_ENTRY) {
            sb.append("\n").append(entry);
        }
        return sb.append(headerInfo).toString();
    }

    public static MdCartInfoProvider createMdInstance(IMemoryProvider memoryProvider, RomContext rom) {
        MdCartInfoProvider provider = new MdCartInfoProvider();
        provider.memoryProvider = memoryProvider;
        provider.romContext = rom;
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
        byte[] header = memoryProvider.getRomData();
        if (romContext.romFileType == SysUtil.RomFileType.BIN_CUE) {
            header = romContext.sheet.getRomHeader();
        }
        detectHeaderMetadata(header);
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
                sramStart = Util.readData(memoryProvider.getRomData(), SRAM_START_ADDRESS, Size.LONG);
                sramEnd = Util.readData(memoryProvider.getRomData(), SRAM_END_ADDRESS, Size.LONG);
                if (sramEnd - sramStart < 0) {
                    LOG.error("Unexpected SRAM setup: {}", this);
                    sramStart = DEFAULT_SRAM_START_ADDRESS;
                    sramEnd = DEFAULT_SRAM_END_ADDRESS;
                }
            } else if (isSramType) {
                LOG.warn("Volatile SRAM? {}", romName);
            }
        }
    }

    /**
     * @param romHeader initial 0x200 bytes
     */
    private void detectHeaderMetadata(byte[] romHeader) {
        romSize = memoryProvider.getRomData().length;
        int send = SERIAL_NUMBER.startOffset + SERIAL_NUMBER.len;
        if (romHeader.length < send) {
            return;
        }
        assert romHeader.length >= 0x200;
        StringBuilder sb = new StringBuilder("\nRom header:\n");
        for (MdRomHeaderField f : rhf) {
            sb.append(f.getStringView(romHeader)).append("\n");
        }
        headerInfo = sb.toString();
        systemType = SYSTEM_TYPE.getValue(romHeader).trim();
        region = REGION_SUPPORT.getValue(romHeader);
        forceMapper = MdMapperType.getMdMapperType(systemType);
        serial = SERIAL_NUMBER.getValue(romHeader);
        if (romHeader.length > SVP_SV_TOKEN_ADDRESS + 1) {
            isSvp = SVP_SV_TOKEN.equals(new String(romHeader, SVP_SV_TOKEN_ADDRESS, 2).trim());
        }
    }

    public MdRomDbModel.RomDbEntry getEntry() {
        return entry;
    }

    @Override
    public String getRegion() {
        return region;
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
            LOG.warn("Adjusting SRAM limit from: {} to: {}", th(sramEnd),
                    th(MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS));
            sramEnd = MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        }
        return adjust;
    }
}
