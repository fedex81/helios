/*
 * Copyright (c) 2018-2019 Federico Berti
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

import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpCounterMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class VdpInterruptHandlerTest {

    private static Logger LOG = LogManager.getLogger(VdpInterruptHandlerTest.class.getSimpleName());
    static boolean verbose = true;


    @Test
    @Ignore
    public void stressTest() {
        do {
            testHLinesCounter_01();
            testHLinesCounter_02();
            testHLinesCounterPending();
            Util.sleep(1000);
        } while (true);
    }

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
        hLinesCounterBasic(h, VideoMode.PAL_H32_V28);
        hLinesCounterBasic(h, VideoMode.PAL_H32_V30);
        hLinesCounterBasic(h, VideoMode.NTSCU_H32_V28);
        hLinesCounterBasic(h, VideoMode.NTSCU_H40_V28);
        hLinesCounterBasic(h, VideoMode.NTSCJ_H32_V28);
        hLinesCounterBasic(h, VideoMode.NTSCJ_H40_V28);
    }

    public static void hLinesCounterBasic(VdpInterruptHandler h, VideoMode mode) {
        h.setMode(mode);
        VdpCounterMode counterMode = VdpCounterMode.getCounterMode(mode);

        int totalCount = counterMode.vTotalCount * 3 + 5;
        //V28: triggers on line [0-E0] - includes vblank line
        int expectedNumberOfHint = (counterMode.vBlankSet + 1) * 3 + 5;
        int numberOfHint = 0;
        int count = 0;
        int line = 0;
        System.out.println("STARTING: " + mode);
        MdVdpTestUtil.runCounterToStartFrame(h);
        printMsg(h.getStateString("Start frame: "));
        do {
            int hLine = h.hLinePassed;
            if (h.gethCounterInternal() == 0) {
                if (h.getvCounterInternal() == 0) {
                    line = 0;
                    printMsg(h.getStateString("Start frame, count: " + count));
                }
                printMsg(h.getStateString("Start Line: " + line));
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

    @Test
    public void testHLinesCounterPending() {
        int hLinePassed = 0x80;
        VdpInterruptHandler h = VdpInterruptHandler.createInstance(() -> hLinePassed);
        h.setMode(VideoMode.PAL_H40_V28);
        VdpCounterMode counterMode = VdpCounterMode.getCounterMode(VideoMode.PAL_H40_V28);
        int totalCount = counterMode.vTotalCount * 3 + 5;
        int count = 0;
        int line = 0;

        MdVdpTestUtil.runCounterToStartFrame(h);
        printMsg(h.getStateString("Start frame: "));
        do {
            int hLine = h.hLinePassed;
            if (h.gethCounterInternal() == 0) {
                if (h.getvCounterInternal() == 0) {
                    line = 0;
                    printMsg(h.getStateString("Start frame, count: " + count));
                }
                printMsg(h.getStateString("Start Line: " + line));
            }
            h.increaseHCounter();
            if (h.isvIntPending()) {
                h.setvIntPending(false);
            }
            if (h.isHIntPending()) {
//                printMsg(h.getStateString("Line: " + line + ", HINT pending"));
                Assert.assertEquals(hLinePassed, h.getVCounterExternal());
                h.setHIntPending(false);
            }
            if (h.hLinePassed != hLine) {
//                printMsg(h.getStateString("Line: " + line + ", hLine Counter changed"));
            }
            if (h.gethCounterInternal() == VdpInterruptHandler.COUNTER_LIMIT) {
                line++;
                count++;
            }

        } while (count < totalCount);
    }

    /**
     * LegendOfGalahad intro
     * hCounterLinePassed = 0xb0
     *
     */
    @Test
    public void testHLinesCounter_02() {
        int hLinePassed = 0xb0;
        int hIntOnLine = hLinePassed - 1; //0 based

        VdpInterruptHandler h = VdpInterruptHandler.createInstance(() -> hLinePassed);
        h.setMode(VideoMode.NTSCU_H40_V28);

        int totalCount = GenesisVdpProvider.NTSC_SCANLINES * 10 + 5;
        int count = 0;
        int line = 0;
        MdVdpTestUtil.runCounterToStartFrame(h);
        printMsg(h.getStateString("Start frame: "));

        do {
            int hLine = h.hLinePassed;
            if (h.gethCounterInternal() == 0) {
                if (h.getvCounterInternal() == 0) {
                    line = 0;
                    printMsg(h.getStateString("Start frame, count: " + count));
                }
                printMsg(h.getStateString("Start Line: " + line));
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


    private static void printMsg(String msg) {
        if (verbose) {
            System.out.println(msg);
        }
    }
}
