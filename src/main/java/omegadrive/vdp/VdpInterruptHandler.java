package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.VdpCounterMode;
import omegadrive.vdp.model.VdpHLineProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
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
    private int vCounterInternal = 0;
    protected int hLinePassed = 0;

    private VideoMode videoMode;
    private VdpCounterMode vdpCounterMode;
    private VdpHLineProvider vdpHLineProvider;

    private boolean vBlankSet;
    private boolean hBlankSet;
    private boolean vIntPending;
    private boolean hIntPending;

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
        hCounterInternal = vCounterInternal = 0;
        hBlankSet = true;
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

    private int increaseVCounterInternal() {

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
        handleHLinesCounter();
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

    private void handleHLinesCounter() {
        //Vcounter is incremented just before HINT pending flag is set,
        if (hCounterInternal == vdpCounterMode.vCounterIncrementOn) {
            //it is decremented on each lines between line 0 and line $E0
            if (vCounterInternal < vdpCounterMode.vBlankSet) {
                hLinePassed--;
            }
            boolean triggerHip = hLinePassed == -1; //aka triggerHippy
            if (triggerHip) {
                hIntPending = true;
                logVerbose("Set HIP: true, hLinePassed: %s", hLinePassed);
                eventFlag = true;
            }
            //reload on line = 0 and vblank
            boolean isForceResetVCounter = vCounterInternal == COUNTER_LIMIT || vCounterInternal > vdpCounterMode.vBlankSet;
            if (isForceResetVCounter || triggerHip) {
                resetHLinesCounter(vdpHLineProvider.getHLinesCounter());
            }
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

    public boolean isLastHCounter() {
        return hCounterInternal == COUNTER_LIMIT;
    }

    public boolean isDrawFrameCounter() {
        return isLastHCounter() && vCounterInternal == COUNTER_LIMIT;
    }

    public void resetHLinesCounter(int value) {
        this.hLinePassed = value;
        logVeryVerbose("Reset hLinePassed: %s", value);
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
        return head + ", hce=" + Integer.toHexString((hCounterInternal >> 1) & 0xFF) +
                "(" + Integer.toHexString(this.hCounterInternal) + "), vce=" + Integer.toHexString(vCounterInternal & 0xFF)
                + "(" + Integer.toHexString(this.vCounterInternal) + ")" + ", hBlankSet=" + hBlankSet + ",vBlankSet=" + vBlankSet
                + ", vIntPending=" + vIntPending + ", hIntPending=" + hIntPending + ", hLinePassed=" + hLinePassed;
    }

    protected void printStateString(String head) {
        String str = getStateString(head);
        LOG.info(str);
    }
}
