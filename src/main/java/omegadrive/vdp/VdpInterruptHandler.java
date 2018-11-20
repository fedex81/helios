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
     */
    private static Logger LOG = LogManager.getLogger(VdpInterruptHandler.class.getSimpleName());

    public static int COUNTER_LIMIT = 0x1FF;
    public static int VBLANK_CLEAR = COUNTER_LIMIT;
    public static int VINT_SET_ON_HCOUNTER_VALUE = 1; //TODO setting this to 1 breaks Spot,

    private int hCounterInternal;
    private int vCounterInternal = 0;
    private int hLinePassed = 0;

    private VideoMode videoMode;
    private VdpCounterMode vdpCounterMode;
    private VdpHLineProvider vdpHLineProvider;

    private boolean vBlankSet;
    private boolean hBlankSet;
    private boolean vIntPending;
    private boolean hIntPending;

    private static boolean veryVerbose = false || Genesis.verbose;
    private static boolean verbose = false || veryVerbose;

    private boolean eventFlag;

    public static VdpInterruptHandler createInstance(VdpHLineProvider vdpHLineProvider) {
        VdpInterruptHandler handler = new VdpInterruptHandler();
        handler.vdpHLineProvider = vdpHLineProvider;
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
            if (vCounterInternal <= vdpCounterMode.vBlankSet) {
                hLinePassed--;
            }
            boolean isValidVCounterForHip = vCounterInternal > 0x00; //Double Clutch intro
            boolean triggerHip = isValidVCounterForHip && hLinePassed == -1; //aka triggerHippy
            if (triggerHip) {
                hIntPending = true;
                logVerbose("Set HIP: true, hLinePassed: %s", hLinePassed);
                eventFlag = true;
            }
            //reload on line = 0 and vblank
            boolean isForceResetVCounter = vCounterInternal == 0x00 || vCounterInternal > vdpCounterMode.vBlankSet;
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

    public int getvCounter() {
        return vCounterInternal;
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

    public static void main(String[] args) {
//        testHCounter();
        testVCounter();
    }

    private static void testHCounter() {
        VdpInterruptHandler.verbose = true;
        VdpInterruptHandler h = createInstance(() -> {
            return 0;
        });
        h.setMode(VideoMode.PAL_H40_V30);
        int count = 0;
        h.printStateString("" + count++);
        do {
            h.increaseHCounter();
            h.printStateString("" + count++);
        } while (count < 500);

    }

    private static void testVCounter() {
        VdpInterruptHandler.verbose = true;
        VdpInterruptHandler h = createInstance(() -> {
            return 1;
        });
        h.setMode(VideoMode.PAL_H40_V28);
        int count = 0;
        h.printStateString("Start Line: " + count++);
        do {
            h.increaseHCounter();
            if (h.hCounterInternal == 0) {
                h.printStateString("Start Line: " + count++);
            }
            if (h.eventFlag) {
                h.printStateString("");
                h.eventFlag = false;
            }
            if (h.isvIntPending()) {
                h.setvIntPending(false);
            }
            if (h.isHIntPending()) {
                h.setHIntPending(false);
            }
        } while (count < 500);

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

    private void printStateString(String head) {
        String str = head + ", hce=" + Integer.toHexString((hCounterInternal >> 1) & 0xFF) +
                "(" + Integer.toHexString(this.hCounterInternal) + "), vce=" + Integer.toHexString(vCounterInternal & 0xFF)
                + "(" + Integer.toHexString(this.vCounterInternal) + ")" + ", hBlankSet=" + hBlankSet + ",vBlankSet=" + vBlankSet
                + ", vIntPending=" + vIntPending + ", hIntPending=" + hIntPending + ", hLinePassed=" + hLinePassed;
        LOG.info(str);
        System.out.println(str);
    }
}
