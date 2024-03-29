/*
 * VideoMode
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

import org.slf4j.Logger;

import java.awt.*;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static omegadrive.util.RegionDetector.Region.*;
import static omegadrive.vdp.model.BaseVdpProvider.*;

public enum VideoMode {
    PAL_H40_V30(EUROPE, H40, V30_CELL),
    PAL_H40_V28(EUROPE, H40, V28_CELL),
    PAL_H32_V30(EUROPE, H32, V30_CELL),
    PAL_H32_V28(EUROPE, H32, V28_CELL),
    NTSCU_H32_V28(USA, H32, V28_CELL),
    NTSCJ_H32_V28(JAPAN, H32, V28_CELL),
    NTSCU_H32_V30(USA, H32, V30_CELL),
    NTSCJ_H32_V30(JAPAN, H32, V30_CELL),
    NTSCU_H40_V28(USA, H40, V28_CELL),
    NTSCJ_H40_V28(JAPAN, H40, V28_CELL),
    NTSCU_H40_V30(USA, H40, V30_CELL),
    NTSCJ_H40_V30(JAPAN, H40, V30_CELL),

    //SMS, SG-1000
    NTSCJ_H32_V24(JAPAN, H32, V24_CELL),
    NTSCU_H32_V24(USA, H32, V24_CELL),
    PAL_H32_V24(EUROPE, H32, V24_CELL),

    //GG
    NTSCJ_H20_V18(JAPAN, H20, V18_CELL),
    NTSCU_H20_V18(USA, H20, V18_CELL),
    PAL_H20_V18(EUROPE, H20, V18_CELL),

    NTSCU_H32_V32(USA, H32, V32_CELL);

    private static final Logger LOG = LogHelper.getLogger(VideoMode.class.getSimpleName());

    private static final Set<VideoMode> values = new HashSet<>(EnumSet.allOf(VideoMode.class));

    private final RegionDetector.Region region;
    private final int h;
    private final int v;
    private final Dimension dimension;

    VideoMode(RegionDetector.Region region, int h, int v) {
        this.h = h;
        this.v = v;
        this.region = region;
        this.dimension = new Dimension(h, v);
    }

    public boolean isPal() {
        return region == RegionDetector.Region.EUROPE;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public RegionDetector.Region getRegion() {
        return region;
    }

    public boolean isH32() {
        return h == H32;
    }

    public boolean isH40() {
        return h == H40;
    }

    public boolean isV28() {
        return v == V28_CELL;
    }

    public boolean isV30() {
        return v == V30_CELL;
    }

    public boolean isV24() {
        return v == V24_CELL;
    }

    public static VideoMode getVideoMode(RegionDetector.Region region, boolean isH40, boolean isV30,
                                         VideoMode currentMode) {
        int hMode = isH40 ? H40 : H32;
        int vMode = isV30 ? V30_CELL : V28_CELL;
        for (VideoMode m : VideoMode.values) {
            if (m.region == region && m.v == vMode && m.h == hMode) {
                return m;
            }
        }
        LOG.error("Unable to find videoMode for: {}, isH40: {}, isV30: {}", region, isH40, isV30);
        return currentMode;
    }
}
