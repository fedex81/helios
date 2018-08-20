package omegadrive.util;

import omegadrive.memory.MemoryProvider;
import omegadrive.ui.EmuFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class RegionDetector {

    private static Logger LOG = LogManager.getLogger(RegionDetector.class.getSimpleName());

    private static Comparator<Region> REGION_COMPARATOR = Comparator.comparingInt(r -> r.order);

    public static int PAL_FPS = 50;
    public static int NTSC_FPS = 60;

    public static long FIRST_REGION_ADDRESS = 0x1f0;
    public static long SECOND_REGION_ADDRESS = 0x1f1;
    public static long THIRD_REGION_ADDRESS = 0x1f2;

    public static Region detectRegion(MemoryProvider memoryProvider, boolean verbose) {
        char char1 = (char) memoryProvider.readCartridgeByte(FIRST_REGION_ADDRESS);
        char char2 = (char) memoryProvider.readCartridgeByte(SECOND_REGION_ADDRESS);
        char char3 = (char) memoryProvider.readCartridgeByte(THIRD_REGION_ADDRESS);
        String s = String.valueOf(char1) + String.valueOf(char2) + String.valueOf(char3);

        Region[] regions = new Region[3];
        regions[0] = Region.getRegion(char1);
        regions[1] = Region.getRegion(char2);
        regions[2] = Region.getRegion(char3);

        Optional<Region> optRegion = Arrays.stream(regions).filter(Objects::nonNull).sorted(REGION_COMPARATOR).findFirst();

        Region res = optRegion.orElse(null);
        if (!optRegion.isPresent()) {
            LOG.warn("Unable to find a region, defaulting to EUROPE");
            res = Region.EUROPE;
        }

        if (verbose) {
            LOG.info(res.name() + " (" + s + ")");
        }
        return res;
    }

    public static Region detectRegion(MemoryProvider memoryProvider) {
        return detectRegion(memoryProvider, false);
    }

    public static void main(String[] args) throws IOException {
        Path romFolder = Paths.get(EmuFrame.basePath);
        Files.list(romFolder).
                peek(System.out::print).
                map(FileLoader::readFileSafe).
                map(MemoryProvider::createInstance).
                forEach(RegionDetector::detectRegion);
    }

    public enum Region {
        JAPAN('J', 2, 0x41, NTSC_FPS), //01000001
        USA('U', 1, 0xA0, NTSC_FPS), //10100000
        EUROPE('E', 0, 0xC1, PAL_FPS); //11000001

        private char region;
        private long versionCode;
        private int fps;
        private int order;

        Region(char region, int order, long versionCode, int fps) {
            this.region = region;
            this.versionCode = versionCode;
            this.fps = fps;
            this.order = order;
        }

        public static Region getRegion(char region) {
            Region res = null;
            for (Region r : Region.values()) {
                res = r.region == region ? r : res;
            }
            return res;
        }

        public int getFps() {
            return fps;
        }

        //	US:	A0A0 rev 0 o A1A1 rev 1
        //	EU:	C1C1
        //	JP: ????
        //	US SEGA CD:	8181
        public long getVersionCode() {
            return versionCode;
        }
    }

    public static Region getRegion(String regionName) {
        if (Objects.isNull(regionName) || regionName.length() < 1) {
            return null;
        }
        return Region.getRegion(regionName.charAt(0));
    }


}
