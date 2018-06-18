package omegadrive.vdp;

import omegadrive.util.VideoMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * TODO Lotus II
 */
public class VdpInterruptHandler {

    private static Logger LOG = LogManager.getLogger(VdpInterruptHandler.class.getSimpleName());

    public static int PAL_SCANLINES = 313;
    public static int NTSC_SCANLINES = 262;
    public static int H32_PIXELS = 342;
    public static int H40_PIXELS = 422;
    public static int COUNTER_LIMIT = 0x1FF;

    private int hCounterInternal;
    private int vCounterInternal = 0;

    private VideoMode videoMode;
    private VdpCounterMode vdpCounterMode;
    private boolean vBlankSet;
    private boolean hBlankSet;
    private boolean vIntPending;

    enum VdpCounterMode {
        PAL_H32_V28(VideoMode.PAL_H32_V28,
                H32_PIXELS, 296,  //hcount, hjumptrigger
                PAL_SCANLINES, 259, //vcount, vjumptrigger
                0x93 << 1, 0x05 << 1, //hblankset, hblankclear
                0xE0, 0x85 << 1  //vblankset, vCounterIncrementOn
        ),
        PAL_H32_V30(VideoMode.PAL_H32_V30,
                H32_PIXELS, 296,
                PAL_SCANLINES, 267,
                0x93 << 1, 0x05 << 1, 0xF0, 0x85 << 1
        ),
        PAL_H40_V28(VideoMode.PAL_H40_V28,
                H40_PIXELS, 366,
                PAL_SCANLINES, 259,
                0xB3 << 1, 0x06 << 1, 0xE0, 0xA5 << 1
        ),
        PAL_H40_V30(VideoMode.PAL_H40_V30,
                H40_PIXELS, 366,
                PAL_SCANLINES, 267,
                0xB3 << 1, 0x06 << 1, 0xF0, 0xA5 << 1
        ),
        NTSCJ_H32_V28(VideoMode.NTSCJ_H32_V28,
                H32_PIXELS, 296,
                NTSC_SCANLINES, 235,
                0x93 << 1, 0x05 << 1, 0xE0, 0x85 << 1
        ),
        NTSCU_H32_V28(VideoMode.NTSCU_H32_V28,
                H32_PIXELS, 296,
                NTSC_SCANLINES, 235,
                0x93 << 1, 0x05 << 1, 0xE0, 0x85 << 1
        ),
        NTSCJ_H40_V28(VideoMode.NTSCJ_H40_V28,
                H40_PIXELS, 366,
                NTSC_SCANLINES, 235,
                0xB3 << 1, 0x06 << 1, 0xE0, 0xA5 << 1
        ),
        NTSCU_H40_V28(VideoMode.NTSCU_H40_V28,
                H40_PIXELS, 366,
                NTSC_SCANLINES, 235,
                0xB3 << 1, 0x06 << 1, 0xE0, 0xA5 << 1
        ),;

        int hTotalCount;
        int hJumpTrigger;
        int hBlankSet;
        int hBlankClear;
        int vTotalCount;
        int vJumpTrigger;
        int vBlankSet;
        int vCounterIncrementOn;
        VideoMode videoMode;

        VdpCounterMode(VideoMode videoMode,
                       int hTotalCount, int hJumpTrigger,
                       int vTotalCount, int vJumpTrigger,
                       int hBlankSet, int hBlankClear,
                       int vBlankSet, int vCounterIncrementOn) {
            this.hTotalCount = hTotalCount;
            this.hJumpTrigger = hJumpTrigger;
            this.hBlankSet = hBlankSet;
            this.hBlankClear = hBlankClear;
            this.vTotalCount = vTotalCount;
            this.vJumpTrigger = vJumpTrigger;
            this.vBlankSet = vBlankSet;
            this.videoMode = videoMode;
            this.vCounterIncrementOn = vCounterIncrementOn;
        }

        public static VdpCounterMode getCounterMode(VideoMode videoMode) {
            for (VdpCounterMode v : VdpCounterMode.values()) {
                if (v.videoMode == videoMode) {
                    return v;
                }
            }
            LOG.error("Unable to find counter mode for videoMode: " + videoMode);
            return null;
        }
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

        if (counterInternal == jumpTrigger) {
            counterInternal = 1 + COUNTER_LIMIT +
                    jumpTrigger - totalCount;
        }
        return counterInternal;
    }

    public int increaseVCounter() {
        vCounterInternal = updateCounterValue(vCounterInternal, vdpCounterMode.vJumpTrigger,
                vdpCounterMode.vTotalCount);
        if (vCounterInternal == vdpCounterMode.vBlankSet) {
            vBlankSet = true;
        }
        if (vCounterInternal == COUNTER_LIMIT) {
            vBlankSet = false;
        }
        return vCounterInternal;
    }

    public int increaseHCounter() {
        vIntPending = false;
        hCounterInternal = updateCounterValue(hCounterInternal, vdpCounterMode.hJumpTrigger,
                vdpCounterMode.hTotalCount);
        if (hCounterInternal == vdpCounterMode.hBlankSet) {
            hBlankSet = true;
        }

        if (hCounterInternal == vdpCounterMode.hBlankClear) {
            hBlankSet = false;
        }
        if (hCounterInternal == vdpCounterMode.vCounterIncrementOn) {
            increaseVCounter();
        }
        if (hCounterInternal == 0x01 && vCounterInternal == vdpCounterMode.vBlankSet) {
            vIntPending = true;
        }
        return hCounterInternal;
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

    public static void main(String[] args) {
        VdpInterruptHandler h = new VdpInterruptHandler();
        h.setMode(VideoMode.PAL_H32_V30);

//        h.vCounterInternal =  h.vdpCounterMode.vBlankSet-1;

        for (int i = 1; i < 50000; i++) {
            int hc = h.increaseHCounter();
            int hce = h.getHCounterExternal();
            int vc = h.getvCounter();
            int vce = h.getVCounterExternal();
            System.out.println(i + ",hce=" + Integer.toHexString(hce) +
                    "(" + Integer.toHexString(hc) + "), vce=" + Integer.toHexString(vce)
                    + "(" + vc + ")" + ", hBlankSet=" + h.hBlankSet + ",vBlankSet=" + h.vBlankSet

            );
        }

    }
}
