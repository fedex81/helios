package omegadrive.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;

import static omegadrive.util.RegionDetector.Region.*;
import static omegadrive.vdp.VdpProvider.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
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
    NTSCJ_H40_V30(JAPAN, H40, V30_CELL),;

    private static Logger LOG = LogManager.getLogger(VideoMode.class.getSimpleName());

    private RegionDetector.Region region;
    private int h;
    private int v;
    private Dimension dimension;

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

    public static VideoMode getVideoMode(RegionDetector.Region region, boolean isH40, boolean isV30,
                                         VideoMode currentMode) {
        int hMode = isH40 ? H40 : H32;
        int vMode = isV30 ? V30_CELL : V28_CELL;
        for (VideoMode m : VideoMode.values()) {
            if (m.getRegion() == region && m.v == vMode && m.h == hMode) {
                return m;
            }
        }
        LOG.error("Unable to find videoMode for: " + region + ", isH40: " + isH40 + ", isV30: " + isV30);
        return currentMode;
    }
}
