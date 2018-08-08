package omegadrive.util;

import omegadrive.memory.MemoryProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 *
 * TODO support EEPROM, f1 world champ edition
 */
public class CartridgeInfoProvider {

    private static Logger LOG = LogManager.getLogger(CartridgeInfoProvider.class.getSimpleName());

    public static final long DEFAULT_ROM_START_ADDRESS = 0x00_0000;
    public static final long DEFAULT_ROM_END_ADDRESS = 0x3F_FFFF;
    public static final long DEFAULT_RAM_END_ADDRESS = 0xFF_FFFF;
    public static final long DEFAULT_RAM_START_ADDRESS = 0xFF_0000;
    public static final long DEFAULT_SRAM_START_ADDRESS = 0x20_0000;
    public static final long DEFAULT_SRAM_END_ADDRESS = 0x20_FFFF;
    public static final int DEFAULT_SRAM_BYTE_SIZE = (int) (DEFAULT_SRAM_END_ADDRESS - DEFAULT_SRAM_START_ADDRESS);

    public static final int ROM_START_ADDRESS = 0x1A0;
    public static final int ROM_END_ADDRESS = 0x1A4;
    public static final int RAM_START_ADDRESS = 0x1A8;
    public static final int RAM_END_ADDRESS = 0x1AC;
    public static final int SRAM_FLAG_ADDRESS = 0x1B0;
    public static final int SRAM_START_ADDRESS = 0x1B4;
    public static final int SRAM_END_ADDRESS = 0x1B8;

    public static final String EXTERNAL_RAM_FLAG_VALUE = "RA";

    private MemoryProvider memoryProvider;
    private RegionDetector.Region region;
    private long romStart;
    private long romEnd;
    private long ramStart;
    private long ramEnd;
    private long sramStart;
    private long sramEnd;
    private boolean sramEnabled;

    private String romName;

    public RegionDetector.Region getRegion() {
        return region;
    }

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

    public static CartridgeInfoProvider createInstance(MemoryProvider memoryProvider) {
        return createInstance(memoryProvider, null);
    }

    public static CartridgeInfoProvider createInstance(MemoryProvider memoryProvider, String rom) {
        CartridgeInfoProvider provider = new CartridgeInfoProvider();
        provider.memoryProvider = memoryProvider;
        provider.romName = rom;
        provider.init();
        return provider;
    }

    private void init() {
        this.region = RegionDetector.detectRegion(memoryProvider, false);
        this.initMemoryLayout(memoryProvider);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROM size: " + (romEnd - romStart + 1) + " bytes, start-end: " + Long.toHexString(romStart) + " - " +
                Long.toHexString(romEnd)).append("\n");
        sb.append("RAM size: " + (ramEnd - ramStart + 1) + " bytes, start-end: " + Long.toHexString(ramStart) + " - " +
                Long.toHexString(ramEnd)).append("\n");
        sb.append("SRAM flag: " + sramEnabled);
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

    private void initMemoryLayout(MemoryProvider memoryProvider) {
        romStart = memoryProvider.readCartridgeWord(ROM_START_ADDRESS) << 16;
        romStart |= memoryProvider.readCartridgeWord(ROM_START_ADDRESS + 2);
        romEnd = memoryProvider.readCartridgeWord(ROM_END_ADDRESS) << 16;
        romEnd |= memoryProvider.readCartridgeWord(ROM_END_ADDRESS + 2);

        ramStart = memoryProvider.readCartridgeWord(RAM_START_ADDRESS) << 16;
        ramStart |= memoryProvider.readCartridgeWord(RAM_START_ADDRESS + 2);
        ramEnd = memoryProvider.readCartridgeWord(RAM_END_ADDRESS) << 16;
        ramEnd |= memoryProvider.readCartridgeWord(RAM_END_ADDRESS + 2);
        detectSram();
        checkLayout();
    }


    private void detectSram() {
        String sramFlag = "" + (char) memoryProvider.readCartridgeByte(SRAM_FLAG_ADDRESS);
        sramFlag += (char) memoryProvider.readCartridgeByte(SRAM_FLAG_ADDRESS + 1);
        boolean externalRamEnabled = EXTERNAL_RAM_FLAG_VALUE.equals(sramFlag);

        if (externalRamEnabled) {
            long byte1 = memoryProvider.readCartridgeByte(SRAM_FLAG_ADDRESS + 2);
            long byte2 = memoryProvider.readCartridgeByte(SRAM_FLAG_ADDRESS + 3);
            boolean isBackup = Util.bitSetTest(byte1, 7); //backup vs volatile
            boolean isSramType = (byte2 & 0x20) == 0x20; //sram vs EEPROM
            if (isBackup) { //&& isSramType) {
                sramEnabled = true;
                sramStart = memoryProvider.readCartridgeWord(SRAM_START_ADDRESS) << 16;
                sramStart |= memoryProvider.readCartridgeWord(SRAM_START_ADDRESS + 2);
                sramEnd = memoryProvider.readCartridgeWord(SRAM_END_ADDRESS) << 16;
                sramEnd |= memoryProvider.readCartridgeWord(SRAM_END_ADDRESS + 2);
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
            romEnd = DEFAULT_ROM_END_ADDRESS;
        }
        if (romStart < 0) {
            LOG.warn("Invalid ROM START ADDRESS: " + romStart);
            romStart = DEFAULT_ROM_START_ADDRESS;
        }
        if (romEnd == 0) {
            LOG.warn("Invalid ROM END ADDRESS: " + romEnd);
            romEnd = DEFAULT_ROM_END_ADDRESS;
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
}
