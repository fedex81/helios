package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.VdpHLineProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;

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

    public static int PAL_SCANLINES = 313;
    public static int NTSC_SCANLINES = 262;
    public static int H32_PIXELS = 342;
    public static int H40_PIXELS = 420;
    public static int H32_JUMP = 0x127;
    public static int H40_JUMP = 0x16C;
    public static int H32_HBLANK_SET = 0x126;
    public static int H40_HBLANK_SET = 0x166;
    public static int H32_HBLANK_CLEAR = 0xA;
    public static int H40_HBLANK_CLEAR = 0xB;
    public static int V28_VBLANK_SET = 0xE0;
    public static int V30_VBLANK_SET = 0xF0;
    public static int VBLANK_CLEAR = COUNTER_LIMIT;
    public static int H32_VCOUNTER_INC_ON = 0x10A;
    public static int H40_VCOUNTER_INC_ON = 0x14A;
    public static int VINT_SET_ON_HCOUNTER_VALUE = 2; //TODO setting this to 1 breaks Spot,
    public static int V28_PAL_JUMP = 0x102;
    public static int V28_NTSC_JUMP = 0xEA;
    public static int V30_PAL_JUMP = 0x10A;
    public static int V30_NTSC_JUMP = -1; //never

    private int hCounterInternal;
    private int vCounterInternal = 0;
    private int hLinePassed = 0;
    private int baseHLinePassed = 0;

    private VideoMode videoMode;
    private VdpCounterMode vdpCounterMode;
    private VdpHLineProvider vdpHLineProvider;

    private boolean vBlankSet;
    private boolean hBlankSet;
    private boolean vIntPending;
    private boolean hIntPending;

    private static boolean veryVerbose = false || Genesis.verbose;
    private static boolean verbose = false || veryVerbose;

    enum VdpCounterMode {
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

        int hTotalCount;
        int hJumpTrigger;
        int hBlankSet;
        int hBlankClear;
        int vTotalCount;
        int vJumpTrigger;
        int vBlankSet;
        int vCounterIncrementOn;
        VideoMode videoMode;

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
    }

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

    private int increaseVCounter() {
        return increaseVCounterInternal();
    }

    private int increaseVCounterInternal() {

        vCounterInternal = updateCounterValue(vCounterInternal, vdpCounterMode.vJumpTrigger,
                vdpCounterMode.vTotalCount);

        if (vCounterInternal == vdpCounterMode.vBlankSet) {
            vBlankSet = true;
        }
        if (vCounterInternal == VBLANK_CLEAR) {
            vBlankSet = false;
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
        }

        if (hCounterInternal == vdpCounterMode.hBlankClear) {
            hBlankSet = false;
        }

        if (hCounterInternal == vdpCounterMode.vCounterIncrementOn) {
            increaseVCounter();
        }
        if (hCounterInternal == VINT_SET_ON_HCOUNTER_VALUE && vCounterInternal == vdpCounterMode.vBlankSet) {
            vIntPending = true;
            logVerbose("Set VIP: true");
        }
        return hCounterInternal;
    }

    private void handleHLinesCounter() {
        //Vcounter is incremented just before HINT pending flag is set,
        if (hCounterInternal == vdpCounterMode.vCounterIncrementOn + 2) {
            //it is decremented on each lines between line 0 and line $E0
            if (vCounterInternal <= vdpCounterMode.vBlankSet) {
                hLinePassed--;
            }
            boolean isValidVCounterForHip = vCounterInternal > 0x00; //Lotus II
            boolean triggerHip = isValidVCounterForHip && hLinePassed == -1; //aka triggerHippy
            if (triggerHip) {
                hIntPending = true;
                logVerbose("Set HIP: true, hLinePassed: %s", hLinePassed);
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
        this.baseHLinePassed = value;
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
            return 1000;
        });
        h.setMode(VideoMode.PAL_H40_V30);
        int count = 0;
        do {
            if (h.hCounterInternal == 0) {
                h.printStateString("Start Line:" + count);
            }
            h.increaseHCounter();
            if (h.isLastHCounter()) {
                h.printStateString("Done Line: " + count++);
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
        LOG.info(head + ", hce=" + Integer.toHexString((hCounterInternal >> 1) & 0xFF) +
                "(" + Integer.toHexString(this.hCounterInternal) + "), vce=" + Integer.toHexString(vCounterInternal & 0xFF)
                + "(" + Integer.toHexString(this.vCounterInternal) + ")" + ", hBlankSet=" + hBlankSet + ",vBlankSet=" + vBlankSet
                + ", vIntPending=" + vIntPending
        );
    }
}
