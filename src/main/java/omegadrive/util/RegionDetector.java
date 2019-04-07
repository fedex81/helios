/*
 * RegionDetector
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

import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class RegionDetector {

    private static Logger LOG = LogManager.getLogger(RegionDetector.class.getSimpleName());

    private static Comparator<Region> REGION_COMPARATOR = Comparator.comparingInt(r -> r.order);

    public static int PAL_FPS = 50;
    public static int NTSC_FPS = 60;

    public static int FIRST_REGION_ADDRESS = 0x1f0;
    public static int SECOND_REGION_ADDRESS = 0x1f1;
    public static int THIRD_REGION_ADDRESS = 0x1f2;

    public static Region detectRegion(IMemoryProvider memoryProvider, boolean verbose) {
        char char1 = (char) memoryProvider.readRomByte(FIRST_REGION_ADDRESS);
        char char2 = (char) memoryProvider.readRomByte(SECOND_REGION_ADDRESS);
        char char3 = (char) memoryProvider.readRomByte(THIRD_REGION_ADDRESS);
        String s = String.valueOf(char1) + String.valueOf(char2) + String.valueOf(char3);

        Region[] regions = new Region[3];
        regions[0] = Region.getRegion(char1);
        regions[1] = Region.getRegion(char2);
        regions[2] = Region.getRegion(char3);

        Optional<Region> optRegion = Arrays.stream(regions).filter(Objects::nonNull).sorted(REGION_COMPARATOR).findFirst();

        Region res = optRegion.orElse(null);
        if (!optRegion.isPresent()) {
            LOG.warn("Unable to find a region, defaulting to USA");
            res = Region.USA;
        }

        if (verbose) {
            LOG.info(res.name() + " (" + s + ")");
        }
        return res;
    }

    public static Region detectRegion(IMemoryProvider memoryProvider) {
        return detectRegion(memoryProvider, false);
    }

    public static void main(String[] args) throws IOException {
        Path romFolder = Paths.get(FileLoader.basePath);
        Files.list(romFolder).
                peek(System.out::print).
                map(FileLoader::readFileSafe).
                map(r -> MemoryProvider.createInstance(r, MemoryProvider.M68K_RAM_SIZE)).
                forEach(RegionDetector::detectRegion);
    }

    //REGION_JAPAN_NTSC 0x00
    //REGION_JAPAN_PAL  0x40
    //REGION_USA        0x80
    //REGION_EUROPE     0xC0
    // TMSS = REGION_CODE + 1
    public enum Region {
        JAPAN('J', 2, 0x00, NTSC_FPS),
        USA('U', 0, 0xA0, NTSC_FPS),
        EUROPE('E', 1, 0xC0, PAL_FPS);

        private static EnumSet<Region> values = EnumSet.allOf(Region.class);

        private char region;
        private long versionCode;
        private int fps;
        private int order;
        private double frameIntervalMs;

        Region(char region, int order, long versionCode, int fps) {
            this.region = region;
            this.versionCode = versionCode;
            this.fps = fps;
            this.order = order;
            this.frameIntervalMs = 1000d / fps;
        }

        public static Region getRegion(char region) {
            Region res = null;
            for (Region r : Region.values) {
                res = r.region == region ? r : res;
            }
            return res;
        }

        public int getFps() {
            return fps;
        }

        public double getFrameIntervalMs() {
            return frameIntervalMs;
        }
        //	US:	A0A0 rev 0 o A1A1 rev 1
        //	EU:	C1C1
        //	JP: ????
        //	US SEGA CD:	8181

        //REGION_JAPAN_NTSC 0x00
        //REGION_JAPAN_PAL  0x40
        //REGION_USA        0x80
        //REGION_EUROPE     0xC0
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
