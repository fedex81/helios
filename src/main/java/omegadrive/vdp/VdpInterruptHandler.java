package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.VdpCounterMode;
import omegadrive.vdp.model.VdpHLineProvider;
import omegadrive.vdp.model.VdpSlotType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
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
    private static Logger LOG = LogManager.getLogger(VdpInterruptHandler.class.getSimpleName());

    public static int COUNTER_LIMIT = 0x1FF;
    public static int VBLANK_CLEAR = COUNTER_LIMIT;
    public static int VINT_SET_ON_HCOUNTER_VALUE = 1; //TODO setting this to 1 breaks Spot,

    private int hCounterInternal;
    private int vCounterInternal;
    protected int hLinePassed = 0;
    private int pixelNumber = 0;
    private int slotNumber = 0;

    private VideoMode videoMode;
    private VdpCounterMode vdpCounterMode;
    private VdpHLineProvider vdpHLineProvider;

    private boolean vBlankSet;
    private boolean hBlankSet;
    private boolean vIntPending;
    private boolean hIntPending;

    private Random rnd = new Random();

    protected static boolean veryVerbose = false || Genesis.verbose;
    protected static boolean verbose = false || veryVerbose;

    private boolean eventFlag;

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
            reset();
        }
    }

    private void reset() {
        if (vdpCounterMode != null) {
            int hValue = (10 + rnd.nextInt(10)) << 1; //even, no blanking
            int vValue = rnd.nextInt(10) << 1; //even
            hCounterInternal = pixelNumber = hValue;
            vCounterInternal = vValue;
            slotNumber = pixelNumber >> 1;
        }
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
        vCounterInternal = updateCounterValue(vCounterInternal, vdpCounterMode.vJumpTrigger,
                vdpCounterMode.vTotalCount);
        handleHLinesCounterDecrement();
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
            pixelNumber = 0;
            slotNumber = 0;
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

    private void handleHLinesCounterDecrement() {
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

    public boolean isFirstSlot() {
        return slotNumber == 0;
    }

    public boolean isLastSlot() {
        return slotNumber == vdpCounterMode.getSlotsPerLine() - 1;
    }

    public boolean isDrawFrameSlot() {
        return isLastSlot() && vCounterInternal == 0;
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
