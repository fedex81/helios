/*
 * VdpCounterMode
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 10:55
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

package omegadrive.vdp.model;

import omegadrive.util.LogHelper;
import omegadrive.util.VideoMode;
import org.slf4j.Logger;

import java.util.EnumSet;

import static omegadrive.vdp.model.BaseVdpProvider.*;

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
    NTSCU_H40_V30(VideoMode.NTSCU_H40_V30),

    //SMS/GG/TMS
    NTSCJ_H32_V24(VideoMode.NTSCJ_H32_V24),
    NTSCU_H32_V24(VideoMode.NTSCU_H32_V24),
    PAL_H32_V24(VideoMode.PAL_H32_V24),
    ;

    private final static EnumSet<VdpCounterMode> values = EnumSet.allOf(VdpCounterMode.class);
    private final static Logger LOG = LogHelper.getLogger(VdpCounterMode.class.getSimpleName());

    public final int hTotalCount;
    public final int hJumpTrigger;
    public final int hBlankSet;
    public final int hBlankClear;
    public final int hBlankClearSlot;
    public final int hActiveDisplayStart;
    public final int hActiveDisplayEnd;
    public final int vTotalCount;
    public final int vJumpTrigger;
    public final int vBlankSet;
    public final int vCounterIncrementOn;
    public final int slotsPerLine;
    final VideoMode videoMode;
    final VdpSlotType[] slotTypes;

    VdpCounterMode(VideoMode videoMode) {
        boolean isH32 = videoMode.isH32();
        boolean isV30 = videoMode.isV30();
        boolean isV28 = videoMode.isV28();
        boolean isV24 = videoMode.isV24();
        boolean isPal = videoMode.isPal();
        this.hTotalCount = isH32 ? H32_PIXELS : H40_PIXELS;
        this.hJumpTrigger = isH32 ? H32_JUMP : H40_JUMP;
        this.hBlankSet = isH32 ? H32_HBLANK_SET : H40_HBLANK_SET;
        this.hBlankClear = isH32 ? H32_HBLANK_CLEAR : H40_HBLANK_CLEAR;
        this.hBlankClearSlot = hBlankClear >> 1;
        this.hActiveDisplayStart = isH32 ? H32_ACTIVE_DISPLAY_START : H40_ACTIVE_DISPLAY_START;
        this.hActiveDisplayEnd = isH32 ? H32_ACTIVE_DISPLAY_END : H40_ACTIVE_DISPLAY_END;
        this.vTotalCount = videoMode.isPal() ? PAL_SCANLINES : NTSC_SCANLINES;
        this.vCounterIncrementOn = isH32 ? H32_VCOUNTER_INC_ON : H40_VCOUNTER_INC_ON;
        this.videoMode = videoMode;
        this.slotTypes = isH32 ? VdpSlotType.h32Slots : VdpSlotType.h40Slots;
        this.slotsPerLine = isH32 ? H32_SLOTS : H40_SLOTS;
        this.vJumpTrigger = isPal ?
                (isV30 ? V30_PAL_JUMP : (isV28 ? V28_PAL_JUMP : V24_PAL_JUMP)) :
                (isV30 ? V30_NTSC_JUMP : (isV28 ? V28_NTSC_JUMP : V24_NTSC_JUMP));
        this.vBlankSet = isV30 ? V30_VBLANK_SET : (isV28 ? V28_VBLANK_SET : V24_VBLANK_SET);
    }

    public static VdpCounterMode getCounterMode(VideoMode videoMode) {
        for (VdpCounterMode v : VdpCounterMode.values) {
            if (v.videoMode == videoMode) {
                return v;
            }
        }
        LOG.error("Unable to find counter mode for videoMode: {}", videoMode);
        return null;
    }

    public VdpSlotType[] getSlotTypes() {
        return slotTypes;
    }

    public int getSlotsPerLine() {
        return slotsPerLine;
    }
}
