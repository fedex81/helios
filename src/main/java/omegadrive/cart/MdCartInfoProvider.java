/*
 * MdCartInfoProvider
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

import omegadrive.cart.header.MdHeader.DeviceSupportField;
import omegadrive.cart.header.MdHeader.MdRomHeaderField;
import omegadrive.cart.loader.MdLoader;
import omegadrive.cart.loader.MdRomDbModel;
import omegadrive.cart.mapper.md.MdMapperType;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static omegadrive.cart.header.MdHeader.MdRomHeaderField.*;
import static omegadrive.util.Util.th;

public class MdCartInfoProvider extends MediaInfoProvider {
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

    public static final int HEADER_SIZE = 0x200;
    private ByteBuffer headerBuf;

    private long sramStart;
    private long sramEnd;
    private boolean sramEnabled;
    private String systemType, region = "";

    private Set<DeviceSupportField> deviceSupport = Collections.emptySet();

    private String headerInfo = "";
    private MdMapperType forceMapper = null;

    private MdRomDbModel.RomDbEntry entry = MdRomDbModel.NO_ENTRY;
    private boolean isSvp;
    private String serial = "MISSING";

    public static final MdCartInfoProvider NO_PROVIDER = new MdCartInfoProvider();

    public int getSramSizeBytes() {
        return (int) (sramEnd - sramStart + 1);
    }

    public boolean isSramEnabled() {
        return sramEnabled;
    }

    @Override
    public int getChecksumStartAddress() {
        return MdRomHeaderField.ROM_CHECKSUM.startOffset;
    }

    @Override
    protected void init() {
        super.init();
        initMemoryLayout();
        entry = MdLoader.getEntry(serial);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(systemType).append(", serial: ").append(serial);
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



    public static MdCartInfoProvider createMdInstance(RandomAccessFile raf) {
        MdCartInfoProvider m = new MdCartInfoProvider(raf);
        m.init();
        return m;
    }

    public static MdCartInfoProvider createMdInstance(byte[] header) {
        assert header.length >= HEADER_SIZE;
        MdCartInfoProvider m = new MdCartInfoProvider();
        m.headerBuf = ByteBuffer.wrap(header, 0, HEADER_SIZE);
        m.init();
        m.romSize = header.length;
        return m;
    }

    protected MdCartInfoProvider() {
    }

    protected MdCartInfoProvider(RandomAccessFile raf) {
        byte[] hd = new byte[HEADER_SIZE];
        try {
            raf.seek(0);
            raf.read(hd);
            this.headerBuf = ByteBuffer.wrap(hd);
            romSize = (int) raf.length();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private void initMemoryLayout() {
        detectHeaderMetadata(headerBuf.array());
        detectSram();
    }


    private void detectSram() {
        String sramFlag = String.valueOf((char) headerBuf.get(SRAM_FLAG_ADDRESS));
        sramFlag += (char) headerBuf.get(SRAM_FLAG_ADDRESS + 1);
        boolean externalRamEnabled = EXTERNAL_RAM_FLAG_VALUE.equals(sramFlag);

        if (externalRamEnabled) {
            long byte1 = headerBuf.get(SRAM_FLAG_ADDRESS + 2);
            long byte2 = headerBuf.get(SRAM_FLAG_ADDRESS + 3);
            boolean isBackup = Util.bitSetTest(byte1, 7); //backup vs volatile
            boolean isSramType = (byte2 & 0x20) == 0x20; //sram vs EEPROM
            if (isBackup) { //&& isSramType) {
                sramEnabled = true;
                sramStart = Util.readData(headerBuf.array(), SRAM_START_ADDRESS, Size.LONG);
                sramEnd = Util.readData(headerBuf.array(), SRAM_END_ADDRESS, Size.LONG);
                if (sramEnd - sramStart < 0) {
                    LOG.error("Unexpected SRAM setup: {}", this);
                    sramStart = DEFAULT_SRAM_START_ADDRESS;
                    sramEnd = DEFAULT_SRAM_END_ADDRESS;
                }
            } else if (isSramType) {
                LOG.warn("Volatile SRAM? {}", "TODO romName");
            }
        }
    }

    /**
     * @param romHeader initial 0x200 bytes
     */
    private void detectHeaderMetadata(byte[] romHeader) {
        int send = SERIAL_NUMBER.startOffset + SERIAL_NUMBER.len;
        if (romHeader.length < send) {
            return;
        }
        assert romHeader.length >= 0x200;
        StringBuilder sb = new StringBuilder("\nRom header:\n");
        for (MdRomHeaderField f : rhf) {
            String sv = f.getStringView(romHeader);
            if (f == DEVICE_SUPPORT) {
                processDeviceSupport(romHeader);
                if (!deviceSupport.isEmpty()) {
                    sv += "\n\t" + Arrays.toString(deviceSupport.stream().map(df -> df.explain).toArray());
                }
            }
            sb.append(sv).append("\n");
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

    private void processDeviceSupport(byte[] romHeader) {
        String str = DEVICE_SUPPORT.getValue(romHeader).trim();
        Set<DeviceSupportField> set = new LinkedHashSet<>();
        for (int i = 0; i < str.length(); i++) {
            String s = "" + str.charAt(i);
            if (!s.trim().isEmpty()) {
                DeviceSupportField.getDeviceMappingIfAny(s).ifPresent(set::add);
            }
        }
        deviceSupport = Collections.unmodifiableSet(set);
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

    public Set<DeviceSupportField> getDeviceSupport() {
        return deviceSupport;
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
