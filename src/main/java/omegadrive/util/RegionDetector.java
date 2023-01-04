/*
 * RegionDetector
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/07/19 21:58
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
import org.slf4j.Logger;

import java.util.*;

public class RegionDetector {

    private static final Logger LOG = LogHelper.getLogger(RegionDetector.class.getSimpleName());

    private static final Comparator<Region> REGION_COMPARATOR = Comparator.comparingInt(r -> r.order);

    public final static int PAL_FPS = 50;
    public final static int NTSC_FPS = 60;

    public final static int FIRST_REGION_ADDRESS = 0x1f0;
    public final static int SECOND_REGION_ADDRESS = 0x1f1;
    public final static int THIRD_REGION_ADDRESS = 0x1f2;

    public static Region detectRegion(IMemoryProvider memoryProvider, boolean verbose) {
        char char1 = (char) memoryProvider.readRomByte(FIRST_REGION_ADDRESS);
        char char2 = (char) memoryProvider.readRomByte(SECOND_REGION_ADDRESS);
        char char3 = (char) memoryProvider.readRomByte(THIRD_REGION_ADDRESS);
        String s = String.valueOf(char1) + char2 + char3;

        Region[] regions = new Region[3];
        regions[0] = Region.getRegion(char1);
        regions[1] = Region.getRegion(char2);
        regions[2] = Region.getRegion(char3);

        Optional<Region> optRegion = Arrays.stream(regions).filter(Objects::nonNull).min(REGION_COMPARATOR);

        Region res = optRegion.orElse(detectRegionFallBack(memoryProvider).orElse(null));
        if (res == null) {
            LOG.warn("Unable to find a region, defaulting to USA");
            res = Region.USA;
        }

        if (verbose) {
            LOG.info("{} ({})", res.name(), s);
        }
        return res;
    }

    public static Region detectRegion(IMemoryProvider memoryProvider) {
        return detectRegion(memoryProvider, false);
    }

    /**
     * Bit 0: Domestic, NTSC (Japan)
     * Bit 1: Domestic, PAL (Invalid?)
     * Bit 2: Overseas, NTSC (America)
     * Bit 3: Overseas, PAL (Europe)
     */
    private static Optional<Region> detectRegionFallBack(IMemoryProvider memoryProvider) {
        char cval = (char) memoryProvider.readRomByte(FIRST_REGION_ADDRESS);
        int val = Character.getNumericValue(cval);
        Region region = null;
        if (val < 0x10) {
            region = (val & 4) > 0 ? Region.USA : region;
            region = region == null ? ((val & 1) > 0 ? Region.JAPAN : region) : region;
            region = region == null ? ((val & 8) > 0 ? Region.EUROPE : region) : region;
        }
        return Optional.ofNullable(region);
    }

    //REGION_JAPAN_NTSC 0x00
    //REGION_JAPAN_PAL  0x40
    //REGION_USA        0x80
    //REGION_EUROPE     0xC0
    public enum Region {
        JAPAN('J', 2, 0x00, NTSC_FPS),
        USA('U', 0, 0x80, NTSC_FPS),
        EUROPE('E', 1, 0xC0, PAL_FPS);

        private static final EnumSet<Region> values = EnumSet.allOf(Region.class);

        private final char region;
        private final int versionCode;
        private final int fps;
        private final int order;
        private final double frameIntervalMs;

        Region(char region, int order, int versionCode, int fps) {
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

        public int getVersionCode() {
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
