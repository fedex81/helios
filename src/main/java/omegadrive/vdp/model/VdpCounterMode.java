package omegadrive.vdp.model;

import omegadrive.util.VideoMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;

import static omegadrive.vdp.VdpProvider.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public enum VdpCounterMode {
    PAL_H32_V28(VideoMode.PAL_H32_V28),
    PAL_H32_V30(VideoMode.PAL_H32_V30),
    PAL_H40_V28(VideoMode.PAL_H40_V28),
    PAL_H40_V30(VideoMode.PAL_H40_V30),
    NTSCJ_H32_V28(VideoMode.NTSCJ_H32_V28),
    NTSCU_H32_V28(VideoMode.NTSCU_H32_V28),

    NTSCJ_H32_V30(VideoMode.NTSCJ_H32_V30),
    NTSCU_H32_V30(VideoMode.NTSCU_H32_V30),

    NTSCJ_H40_V28(VideoMode.NTSCJ_H40_V28),
    NTSCU_H40_V28(VideoMode.NTSCU_H40_V28),

    NTSCJ_H40_V30(VideoMode.NTSCJ_H40_V30),
    NTSCU_H40_V30(VideoMode.NTSCU_H40_V30),;

    private static EnumSet<VdpCounterMode> values = EnumSet.allOf(VdpCounterMode.class);
    private static Logger LOG = LogManager.getLogger(VdpCounterMode.class.getSimpleName());

    public int hTotalCount;
    public int hJumpTrigger;
    public int hBlankSet;
    public int hBlankClear;
    public int vTotalCount;
    public int vJumpTrigger;
    public int vBlankSet;
    public int vCounterIncrementOn;
    VideoMode videoMode;
    VdpSlotType[] slotTypes;
    VdpSlotType[] slotCounterTypes;

    VdpCounterMode(VideoMode videoMode) {
        boolean isH32 = videoMode.isH32();
        boolean isV28 = videoMode.isV28();
        boolean isPal = videoMode.isPal();
        this.hTotalCount = isH32 ? H32_PIXELS : H40_PIXELS;
        this.hJumpTrigger = isH32 ? H32_JUMP : H40_JUMP;
        this.hBlankSet = isH32 ? H32_HBLANK_SET : H40_HBLANK_SET;
        this.hBlankClear = isH32 ? H32_HBLANK_CLEAR : H40_HBLANK_CLEAR;
        this.vTotalCount = videoMode.isPal() ? PAL_SCANLINES : NTSC_SCANLINES;
        this.vJumpTrigger = isPal ? (isV28 ? V28_PAL_JUMP : V30_PAL_JUMP) : (isV28 ? V28_NTSC_JUMP : V30_NTSC_JUMP);
        this.vBlankSet = isV28 ? V28_VBLANK_SET : V30_VBLANK_SET;
        this.vCounterIncrementOn = isH32 ? H32_VCOUNTER_INC_ON : H40_VCOUNTER_INC_ON;
        this.videoMode = videoMode;
        this.slotTypes = isH32 ? VdpSlotType.h32Slots : VdpSlotType.h40Slots;
        this.slotCounterTypes = isH32 ? VdpSlotType.h32CounterSlots : VdpSlotType.h40CounterSlots;
    }

    public static VdpCounterMode getCounterMode(VideoMode videoMode) {
        for (VdpCounterMode v : VdpCounterMode.values) {
            if (v.videoMode == videoMode) {
                return v;
            }
        }
        LOG.error("Unable to find counter mode for videoMode: " + videoMode);
        return null;
    }

    public static int getNumberOfPixelsPerLine(VideoMode videoMode) {
        return videoMode.isH32() ? H32_PIXELS : H40_PIXELS;
    }

    public VdpSlotType[] getSlotTypes() {
        return slotTypes;
    }

    public VdpSlotType[] getCounterSlotTypes() {
        return slotCounterTypes;
    }
}
