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

package omegadrive.vdp.gen;

import omegadrive.Device;
import omegadrive.bus.gen.BusArbiter;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.VdpCounterMode;
import omegadrive.vdp.model.VdpSlotType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private final static Logger LOG = LogManager.getLogger(VdpInterruptHandler.class.getSimpleName());

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
        }

        //TODO sms should use 0x1E8
        //Lotus2, line interrupt sensitive,  H32, 0x10A (=vCounterIncrementOn), < 0x127 ko, >= 0x127 (=hJumpTrigger) ok
        //Dracula, vcounter increment sensitive, H40, 0x14A (=vCounterIncrementOn) ok, <= 0x150 ok, > 0x150 ko, hJumpTrigger = 0x16C
        if (hCounterInternal == vdpCounterMode.vCounterIncrementOn) {
            increaseVCounterInternal();
        }
        if (vCounterInternal == vdpCounterMode.vBlankSet &&
                hCounterInternal == VINT_SET_ON_HCOUNTER_VALUE) {
            vIntPending = true;
            vdpEvent.fireVdpEvent(INTERRUPT, BusArbiter.InterruptEvent.Z80_INT_ON);
            logVerbose("Set VIP: true");
        } else if (vCounterInternal == vdpCounterMode.vBlankSet + 1 &&
                hCounterInternal == VINT_SET_ON_HCOUNTER_VALUE) {
            vdpEvent.fireVdpEvent(INTERRUPT, BusArbiter.InterruptEvent.Z80_INT_OFF);
            logVerbose("Set Z80Int: false");
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
            logVerbose("Set HIP: true, hLinePassed: %s", hLinePassed);
            vdpEvent.fireVdpEvent(H_LINE_UNDERFLOW, vCounterInternal);
            resetHLinesCounter();
        }
    }

    public boolean isvBlankSet() {
        return vBlankSet;
    }

    public boolean ishBlankSet() {
        return hBlankSet;
    }

    public int getvCounterInternal() {
        return vCounterInternal;
    }

    public int gethCounterInternal() {
        return hCounterInternal;
    }

    public int getVCounterExternal() {
        return vCounterInternal & 0xFF;
    }

    public int getHCounterExternal() {
        return (hCounterInternal >> 1) & 0xFF;
    }

    public boolean isvIntPending() {
        return vIntPending;
    }

    public void setvIntPending(boolean vIntPending) {
        this.vIntPending = vIntPending;
        logVerbose("Set VIP: %s", vIntPending);
    }

    public boolean isHIntPending() {
        return hIntPending;
    }

    public void setHIntPending(boolean hIntPending) {
        logVerbose("Set HIP: %s", hIntPending);
        this.hIntPending = hIntPending;
    }

    public boolean isDrawLineSlot() {
        return slotNumber == vdpCounterMode.hBlankSet >> 1;
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

    public boolean isDrawFrameSlot() {
        return hCounterInternal == 0 && vCounterInternal == COUNTER_LIMIT;
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
        logVerbose("Reset hLinePassed: %s", hLinePassed);
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
            default:
                break;
        }
    }

    private void logVerbose(String str) {
        if (verbose && LOG.isEnabled(Level.INFO)) {
            printStateString(str);
        }
    }

    protected final void logVerbose(String str, long arg) {
        if (verbose && LOG.isEnabled(Level.INFO)) {
            printStateString(String.format(str, arg));
        }
    }

    private void logVerbose(String str, int arg) {
        if (verbose && LOG.isEnabled(Level.INFO)) {
            printStateString(String.format(str, arg));
        }
    }

    private void logVerbose(String str, boolean arg) {
        if (verbose && LOG.isEnabled(Level.INFO)) {
            printStateString(String.format(str, arg));
        }
    }

    private static final String STATE_FMT_STR =
            "%s, slot=0x%x, hce=0x%x(0x%x), vce=0x%x(0x%x), hb%d, vb%d, VINTPend%d, HINTPend%d, hLines=%d";

    public final String getStateString(String head) {
        return String.format(STATE_FMT_STR,
                head, slotNumber, (hCounterInternal >> 1) & 0xFF, hCounterInternal, vCounterInternal & 0xFF, vCounterInternal,
                (hBlankSet ? 1 : 0), (vBlankSet ? 1 : 0), (vIntPending ? 1 : 0), (hIntPending ? 1 : 0), hLinesCounter);
    }

    private void printStateString(String head) {
        LOG.info(getStateString(head));
    }
}
