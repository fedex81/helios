/*
 * VdpInterruptHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 14:53
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

package omegadrive.vdp.md;

import omegadrive.Device;
import omegadrive.bus.md.BusArbiter;
import omegadrive.util.LogHelper;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.*;
import org.slf4j.Logger;

import static omegadrive.vdp.model.BaseVdpAdapterEventSupport.VdpEvent.*;

/**
 * VDPTEST - needs NTSC
 * http://gendev.spritesmind.net/forum/viewtopic.php?t=787
 */
public class VdpInterruptHandler implements BaseVdpProvider.VdpEventListener, Device {

    /**
     * Relevant Games:
     * Kawasaki, Outrun, Gunstar Heroes,Lotus II,Legend of Galahad, wobble.bin, Vscrollexperiment,
     * Road rash, lemmings, Bram Stoker Dracula
     */
    private final static Logger LOG = LogHelper.getLogger(VdpInterruptHandler.class.getSimpleName());

    public static final int COUNTER_LIMIT = 0x1FF;
    public static final int VBLANK_CLEAR = COUNTER_LIMIT;
    public static int VINT_SET_ON_HCOUNTER_VALUE = 1; // TODO SMS = 0x1EA??

    protected int hCounterInternal;
    protected int vCounterInternal;
    public int hLinePassed = 0;
    private int pixelNumber = 0;
    private int slotNumber = 0;
    private int hLinesCounter = 0;

    private VideoMode videoMode;
    private InterlaceMode interlaceMode = InterlaceMode.NONE;
    protected VdpCounterMode vdpCounterMode;
    protected BaseVdpAdapterEventSupport vdpEvent;
    protected boolean h40;

    protected boolean vBlankSet;
    private boolean hBlankSet;
    private boolean vIntPending;
    protected boolean hIntPending;

    protected static final boolean verbose = false;

    protected VdpInterruptHandler(BaseVdpAdapterEventSupport vdp) {
        this.vdpEvent = vdp;
        vdp.addVdpEventListener(this);
    }

    public static VdpInterruptHandler createMdInstance(BaseVdpProvider vdp) {
        VdpInterruptHandler handler = new VdpInterruptHandler(vdp);
        handler.reset();
        return handler;
    }

    protected void setMode(VideoMode videoMode) {
        if (this.videoMode != videoMode) {
            this.videoMode = videoMode;
            this.vdpCounterMode = VdpCounterMode.getCounterMode(videoMode);
            this.h40 = videoMode.isH40();
            reset();
        }
    }

    @Override
    public void reset() {
        hCounterInternal = pixelNumber = slotNumber = 0;
        vCounterInternal = COUNTER_LIMIT;
        hBlankSet = false;
        vBlankSet = false;
        vIntPending = false;
        hIntPending = false;
        resetHLinesCounter();
    }

    private int updateCounterValue(int counterInternal, int jumpTrigger, int totalCount) {
        counterInternal++;
        counterInternal &= COUNTER_LIMIT;

        if (counterInternal == jumpTrigger + 1) {
            counterInternal = (1 + COUNTER_LIMIT) +
                    (jumpTrigger + 1) - totalCount;
        }
        return counterInternal;
    }

    /**
     * 1) every line, wait for line end (Hcounter $84->$85, or more likely the 9-bits value) then increment VCounter.
     * 2) if VBLANK flag is set, reload HINT Counter else decrement it.
     * 3) if VCounter=$E0($F0), set VBLANK flag else if VCounter=$FF, clear it.
     * 4) if HINT Counter overflows, set HINT flag then reload HINT Counter.
     * 5) if HINT is enabled and HINT flag is set, interrupt control asserts /IPL2.
     * 6) if VCounter=$E0($F0), wait for Hcounter $00->$01 then set VINT flag.
     * 7) if VINT is enabled and VINT flag is set, interrupt control asserts /IPL2 and /IPL1.
     */
    private int increaseVCounterInternal() {
        vCounterInternal = updateCounterValue(vCounterInternal, vdpCounterMode.vJumpTrigger,
                vdpCounterMode.vTotalCount);
        vdpEvent.fireVdpEvent(V_COUNT_INC, vCounterInternal);
        handleHLinesCounterDecrement();
        if (vCounterInternal == vdpCounterMode.vBlankSet) {
            vBlankSet = true;
            vdpEvent.fireVdpEvent(V_BLANK_CHANGE, true);
        } else if (vCounterInternal == VBLANK_CLEAR) {
            vBlankSet = false;
            vdpEvent.fireVdpEvent(V_BLANK_CHANGE, false);
        }
        return vCounterInternal;
    }

    public final int increaseHCounterSlot() {
        increaseHCounterInternal();
        return increaseHCounterInternal();
    }

    public int increaseHCounter() {
        return increaseHCounterInternal();
    }

    private int increaseHCounterInternal() {
        hCounterInternal = updateCounterValue(hCounterInternal, vdpCounterMode.hJumpTrigger,
                vdpCounterMode.hTotalCount);

        pixelNumber = (pixelNumber + 1) % vdpCounterMode.hTotalCount;
        slotNumber = pixelNumber >> 1;

        if (hCounterInternal == vdpCounterMode.hBlankSet) {
            vdpEvent.fireVdpEvent(H_BLANK_CHANGE, true);
            hBlankSet = true;
        } else if (hCounterInternal == vdpCounterMode.hBlankClear) {
            vdpEvent.fireVdpEvent(H_BLANK_CHANGE, false);
            hBlankSet = false;
        } else if (hCounterInternal == vdpCounterMode.hActiveDisplayEnd) {
            vdpEvent.fireVdpEvent(VDP_ACTIVE_DISPLAY_CHANGE, false);
        } else if (hCounterInternal == vdpCounterMode.hActiveDisplayStart) {
            vdpEvent.fireVdpEvent(VDP_ACTIVE_DISPLAY_CHANGE, true);
        }

        //TODO sms should use 0x1E8
        //Dracula, vcounter increment sensitive, H40, 0x14A (=vCounterIncrementOn) ok, <= 0x150 ok, > 0x150 ko, hJumpTrigger = 0x16C
        if (hCounterInternal == vdpCounterMode.vCounterIncrementOn) {
            increaseVCounterInternal();
        }
        if (vCounterInternal == vdpCounterMode.vBlankSet &&
                hCounterInternal == VINT_SET_ON_HCOUNTER_VALUE) {
            vIntPending = true;
            vdpEvent.fireVdpEvent(INTERRUPT, BusArbiter.InterruptEvent.Z80_INT_ON);
            vdpEvent.fireVdpEvent(VDP_VINT_PENDING, true);
            if (verbose) LOG.info("Set VIP: true");
        } else if (vCounterInternal == vdpCounterMode.vBlankSet + 1 &&
                hCounterInternal == VINT_SET_ON_HCOUNTER_VALUE) {
            vdpEvent.fireVdpEvent(INTERRUPT, BusArbiter.InterruptEvent.Z80_INT_OFF);
            if (verbose) LOG.info("Set Z80Int: false");
        }
        return hCounterInternal;
    }

    private void handleHLinesCounterDecrement() {
        hLinePassed--;
        if (vCounterInternal >= vdpCounterMode.vBlankSet - 1) {
            resetHLinesCounter();
        }
        if (hLinePassed < 0) {
            hIntPending = true;
            if (verbose) LOG.info("Set HIP: true, hLinePassed: {}", hLinePassed);
            vdpEvent.fireVdpEvent(H_LINE_UNDERFLOW, vCounterInternal);
            resetHLinesCounter();
        }
    }

    public boolean isvBlankSet() {
        return vBlankSet;
    }

    public int getvCounterInternal() {
        return vCounterInternal;
    }

    public int gethCounterInternal() {
        return hCounterInternal;
    }

    public int getVCounterExternal() {
        if (interlaceMode.isInterlaced()) {
            return getVCounterExternalInterlace();
        }
        return vCounterInternal & 0xFF;
    }

    private int getVCounterExternalInterlace() {
        int vc = vCounterInternal;
        /* Interlace mode 2 (Sonic the Hedgehog 2, Combat Cars) */
        vc <<= (interlaceMode.ordinal() >> 1);
        /* Replace bit 0 with bit 8 */
        vc = (vc & ~1) | ((vc >> 8) & 1);
        return vc & 0xFF;
    }

    public int getHCounterExternal() {
        return (hCounterInternal >> 1) & 0xFF;
    }

    public boolean isvIntPending() {
        return vIntPending;
    }

    public void setvIntPending(boolean vIntPending) {
        this.vIntPending = vIntPending;
        if (verbose) LOG.info("Set VIP: {}", vIntPending);
    }

    public boolean isHIntPending() {
        return hIntPending;
    }

    public void setHIntPending(boolean hIntPending) {
        if (verbose) LOG.info("Set HIP: {}", hIntPending);
        this.hIntPending = hIntPending;
    }

    /**
     * H32 = 171 slots = 171/5 = 42.75
     * H40 = 210 slots = 195/4 + 15/5 = 42.75
     */
    public int getVdpClockSpeed() {
        return h40 && hCounterInternal < BaseVdpProvider.H40_SLOW_CLOCK ?
                BaseVdpProvider.MCLK_DIVIDER_FAST_VDP : BaseVdpProvider.MCLK_DIVIDER_SLOW_VDP;
    }

    public boolean isEndOfFrameCounter() {
        return vCounterInternal == COUNTER_LIMIT && hCounterInternal == COUNTER_LIMIT;
    }

    public boolean isExternalSlot(boolean isBlanking) {
        VdpSlotType type = vdpCounterMode.getSlotTypes()[slotNumber];
        if (!isBlanking) {
            //active screen
            return type == VdpSlotType.EXTERNAL;
        }
        //blanking all but refresh slots
        return type != VdpSlotType.REFRESH;
    }

    public int resetHLinesCounter() {
        this.hLinePassed = hLinesCounter;
        if (verbose) LOG.info("Reset hLinePassed: {}", hLinePassed);
        return hLinePassed;
    }

    @Override
    public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        switch (event) {
            case VIDEO_MODE:
                setMode((VideoMode) value);
                break;
            case REG_H_LINE_COUNTER_CHANGE:
                hLinesCounter = (int) value;
                break;
            case INTERLACE_MODE_CHANGE:
                interlaceMode = (InterlaceMode) value;
                break;
            default:
                break;
        }
    }

    private static final String STATE_FMT_STR =
            "%s, slot=0x%x, hce=0x%x(0x%x), vce=0x%x(0x%x), hb%d, vb%d, VINTPend%d, HINTPend%d, hLines=%d";

    public final String getStateString(String head) {
        return String.format(STATE_FMT_STR,
                head, slotNumber, (hCounterInternal >> 1) & 0xFF, hCounterInternal, vCounterInternal & 0xFF, vCounterInternal,
                (hBlankSet ? 1 : 0), (vBlankSet ? 1 : 0), (vIntPending ? 1 : 0), (hIntPending ? 1 : 0), hLinesCounter);
    }
}
