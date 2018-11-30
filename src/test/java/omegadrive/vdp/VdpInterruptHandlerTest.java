package omegadrive.vdp;

import omegadrive.util.VideoMode;
import omegadrive.vdp.model.VdpCounterMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 */
public class VdpInterruptHandlerTest {

    private static Logger LOG = LogManager.getLogger(VdpInterruptHandlerTest.class.getSimpleName());
    static boolean verbose = false;

    /**
     * GunstarHeroes intro
     * Outrun
     * hCounterLinePassed = 0
     * <p>
     * Basic test really:
     * 1. hint triggers every line (when hlinePassed = 0)
     * 2. hint doenst trigger during vblank
     * 3. hint gets reloaded correctly
     */
    @Test
    public void testHLinesCounter_01() {
        int hLinePassed = 0;
        VdpInterruptHandler h = VdpInterruptHandler.createInstance(() -> hLinePassed);
        hLinesCounterBasic(h, VideoMode.PAL_H40_V28);
        hLinesCounterBasic(h, VideoMode.PAL_H40_V30);
    }

    private void hLinesCounterBasic(VdpInterruptHandler h, VideoMode mode) {
        h.setMode(mode);
        VdpCounterMode counterMode = VdpCounterMode.getCounterMode(mode);
        int totalCount = counterMode.vTotalCount * 3 + 5;
        int expectedNumberOfHint = counterMode.vBlankSet * 3 + 5;
        int numberOfHint = 0;
        int count = 0;
        int line = 0;
        do {
            int hLine = h.hLinePassed;
            if (h.gethCounterInternal() == 0) {
                if (h.getvCounterInternal() == 0) {
                    line = 0;
                    printMsg(h.getStateString("Start frame, count: " + count));
                }
                h.printStateString("Start Line: " + line);
            }
            h.increaseHCounter();
            if (h.isvIntPending()) {
                h.setvIntPending(false);
            }
            if (h.isHIntPending()) {
                printMsg(h.getStateString("Line: " + line + ", HINT pending"));
                numberOfHint++;
                h.setHIntPending(false);
            }
            if (h.hLinePassed != hLine) {
                printMsg(h.getStateString("Line: " + line + ", hLine Counter changed"));
            }
            if (h.gethCounterInternal() == VdpInterruptHandler.COUNTER_LIMIT) {
                line++;
                count++;
            }

        } while (count < totalCount);
        Assert.assertEquals(expectedNumberOfHint, numberOfHint);
    }

    /**
     * LegendOfGalahad intro
     * hCounterLinePassed = 0xb0
     * TODO fix
     */
    @Test
    public void testHLinesCounter_02() {
        int hLinePassed = 0xb0;
        int hIntOnLine = hLinePassed - 1; //0 based

        VdpInterruptHandler h = VdpInterruptHandler.createInstance(() -> hLinePassed);
        h.setMode(VideoMode.NTSCU_H40_V28);


        int totalCount = VdpProvider.NTSC_SCANLINES * 300 + 5;
        int count = 0;
        int line = 0;
        do {
            int hLine = h.hLinePassed;
            if (h.gethCounterInternal() == 0) {
                if (h.getvCounterInternal() == 0) {
                    line = 0;
                    printMsg(h.getStateString("Start frame, count: " + count));
                }
                h.printStateString("Start Line: " + line);
            }
            h.increaseHCounter();
            if (h.isvIntPending()) {
                h.setvIntPending(false);
            }
            if (h.isHIntPending()) {
                printMsg(h.getStateString("Line: " + line + ", HINT pending"));
                h.setHIntPending(false);
                Assert.assertEquals("Error on count: " + count, hIntOnLine, line);
            }
            if (h.hLinePassed != hLine) {
                printMsg(h.getStateString("Line: " + line + ", hLine Counter changed"));
            }
            if (h.gethCounterInternal() == VdpInterruptHandler.COUNTER_LIMIT) {
                line++;
                count++;
            }

        } while (count < totalCount);
    }


    private void printMsg(String msg) {
        if (verbose) {
            System.out.println(msg);
        }
    }
}
