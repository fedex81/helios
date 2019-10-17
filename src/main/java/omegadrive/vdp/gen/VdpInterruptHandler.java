/*
 * VdpInterruptHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 10:58
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

import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.VdpCounterMode;
import omegadrive.vdp.model.VdpHLineProvider;
import omegadrive.vdp.model.VdpSlotType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 *  VDPTEST - needs NTSC
 *  http://gendev.spritesmind.net/forum/viewtopic.php?t=787
 */
public class VdpInterruptHandler {

    /**
     * Relevant Games:
     * Kawasaki
     * Outrun
     * Gunstar Heroes
     * Lotus II
     * Legend of Galahad
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

    private VideoMode videoMode;
    protected VdpCounterMode vdpCounterMode;
    protected VdpHLineProvider vdpHLineProvider;
    protected boolean h40;

    protected boolean vBlankSet;
    private boolean hBlankSet;
    private boolean vIntPending;
    protected boolean hIntPending;

    protected static boolean veryVerbose = false;
    protected static boolean verbose = false || veryVerbose;

    protected boolean eventFlag;

    public static VdpInterruptHandler createInstance(VdpHLineProvider vdpHLineProvider) {
        VdpInterruptHandler handler = new VdpInterruptHandler();
        handler.vdpHLineProvider = vdpHLineProvider;
        handler.reset();
        return handler;
    }

    public void setMode(VideoMode videoMode) {
        if (this.videoMode != videoMode) {
            this.videoMode = videoMode;
            this.vdpCounterMode = VdpCounterMode.getCounterMode(videoMode);
            this.h40 = videoMode.isH40();
            reset();
        }
    }

    protected void reset() {
        hCounterInternal = 0;
        vCounterInternal = 0;
        pixelNumber = hCounterInternal;
        slotNumber = pixelNumber >> 1;
        hBlankSet = false;
        vBlankSet = false;
        vIntPending = false;
        hIntPending = false;
        resetHLinesCounter(vdpHLineProvider.getHLinesCounter());
    }

    private int updateCounterValue(int counterInternal, int jumpTrigger, int totalCount) {
        counterInternal++;
        counterInternal &= COUNTER_LIMIT;

        if (counterInternal == jumpTrigger + 1) {
            counterInternal = (1 + COUNTER_LIMIT) +
                    (jumpTrigger + 1) - totalCount;
            eventFlag = true;
        }
        return counterInternal;
    }

    private int increaseVCounter() {
        return increaseVCounterInternal();
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
        handleHLinesCounterDecrement();
        vCounterInternal = updateCounterValue(vCounterInternal, vdpCounterMode.vJumpTrigger,
                vdpCounterMode.vTotalCount);
        if (vCounterInternal == vdpCounterMode.vBlankSet) {
            vBlankSet = true;
            eventFlag = true;
        }
        if (vCounterInternal == VBLANK_CLEAR) {
            vBlankSet = false;
            eventFlag = true;
        }
        return vCounterInternal;
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
            hBlankSet = true;
            eventFlag = true;
        }

        if (hCounterInternal == vdpCounterMode.hBlankClear) {
            hBlankSet = false;
            eventFlag = true;
        }

        if (hCounterInternal == vdpCounterMode.vCounterIncrementOn) {
            increaseVCounter();
            eventFlag = true;
        }
        if (vCounterInternal == vdpCounterMode.vBlankSet &&
                hCounterInternal == VINT_SET_ON_HCOUNTER_VALUE) {
            vIntPending = true;
            logVerbose("Set VIP: true");
            eventFlag = true;
        }
        return hCounterInternal;
    }

    protected void handleHLinesCounterDecrement() {
        hLinePassed = vBlankSet ? resetHLinesCounter(vdpHLineProvider.getHLinesCounter()) : hLinePassed - 1;
        if (hLinePassed < 0) {
            hIntPending = true;
            logVerbose("Set HIP: true, hLinePassed: %s", hLinePassed);
            eventFlag = true;
            resetHLinesCounter(vdpHLineProvider.getHLinesCounter());
        }
    }

    public boolean isvBlankSet() {
        return vBlankSet;
    }

    public boolean ishBlankSet() {
        return hBlankSet;
    }

    public boolean isActiveScreen() {
        return !hBlankSet && !vBlankSet;
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

    public boolean isFirstLineSlot() {
        return slotNumber == vdpCounterMode.hBlankClearSlot;
    }

    /**
     * H32 = 171 slots = 171/5 = 42.75
     * H40 = 210 slots = 195/4 + 15/5 = 42.75
     *
     * @return
     */
    public int getVdpClockSpeed() {
        return h40 && hCounterInternal < BaseVdpProvider.H40_SLOW_CLOCK ?
                BaseVdpProvider.MCLK_DIVIDER_FAST_VDP : BaseVdpProvider.MCLK_DIVIDER_SLOW_VDP;
    }

    public boolean isEndOfFrameCounter() {
        return vCounterInternal == COUNTER_LIMIT && hCounterInternal == COUNTER_LIMIT;
    }

    public boolean isDrawFrameSlot() {
        //TODO check, should it be?
//        return hCounterInternal == 0 && vCounterInternal == 0;
        return vCounterInternal == 0 && slotNumber == vdpCounterMode.slotsPerLine - 1;
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

    public int resetHLinesCounter(int value) {
        this.hLinePassed = value;
        logVeryVerbose("Reset hLinePassed: %s", value);
        return hLinePassed;
    }

    public void logVerbose(String str) {
        if (verbose && LOG.isEnabled(Level.INFO)) {
            printStateString(str);
        }
    }

    public void logVerbose(String str, long arg) {
        if (verbose && LOG.isEnabled(Level.INFO)) {
            printStateString(String.format(str, arg));
        }
    }

    public void logVerbose(String str, int arg) {
        if (verbose && LOG.isEnabled(Level.INFO)) {
            printStateString(String.format(str, arg));
        }
    }

    public void logVerbose(String str, boolean arg) {
        if (verbose && LOG.isEnabled(Level.INFO)) {
            printStateString(String.format(str, arg));
        }
    }

    public void logVeryVerbose(String str, int arg) {
        if (veryVerbose && LOG.isEnabled(Level.INFO)) {
            printStateString(String.format(str, arg));
        }
    }

    public String getStateString(String head) {
        return head + ", slot=" + Integer.toHexString(slotNumber) + "h,hce=" + Integer.toHexString((hCounterInternal >> 1) & 0xFF) +
                "(" + Integer.toHexString(this.hCounterInternal) + ")h, vce=" + Integer.toHexString(vCounterInternal & 0xFF)
                + "(" + Integer.toHexString(this.vCounterInternal) + ")" +
                "h, hb" + (hBlankSet ? "1" : "0") + ",vb" + (vBlankSet ? "1" : "0")
                + ", VINTPen" + (vIntPending ? "1" : "0") + ", HINTPend" + (hIntPending ? "1" : "0") + ", hLines=" + hLinePassed;
    }

    private void printStateString(String head) {
        String str = getStateString(head);
        LOG.info(str);
    }
}
