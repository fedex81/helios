/*
 * SmsVdpInterruptHandlerTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:02
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

package omegadrive.vdp;

import omegadrive.util.VideoMode;
import omegadrive.vdp.gen.VdpInterruptHandler;
import omegadrive.vdp.gen.VdpInterruptHandlerTest;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.VdpCounterMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

public class SmsVdpInterruptHandlerTest {

    private static Logger LOG = LogManager.getLogger(SmsVdpInterruptHandlerTest.class.getSimpleName());
    static boolean verbose = true;

    public static void hLinesCounterBasic(BaseVdpProvider vdp, VdpInterruptHandler h, VideoMode mode, boolean[] expectedLineInt) {
        boolean[] actualLineInt = new boolean[expectedLineInt.length];
        MdVdpTestUtil.updateVideoMode(vdp, mode);
        VdpCounterMode counterMode = VdpCounterMode.getCounterMode(mode);

        int totalCount = counterMode.vTotalCount * 3 + 5;
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
                actualLineInt[line] = true;
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
        Assert.assertArrayEquals(expectedLineInt, actualLineInt);
    }

    /**
     * According to http://www.smspower.org/Development/SMSOfficialDocs
     * <p>
     * R10 Value	Interrupt Requests at these HBLANK times
     * $C0-$FF	None
     * $00	1,2,3,4,5,...191
     * $01	2,4,6,8,10,..190
     * $02	3,6,9,12,....189
     * $03	4,8,12,16,...188
     * (etc)	(etc)
     */
    @Test
    public void testSmsHLinesCounter() {
        testSmsHLinesCounterInternal(0);
        testSmsHLinesCounterInternal(1);
        testSmsHLinesCounterInternal(2);
        testSmsHLinesCounterInternal(3);
        testSmsHLinesCounterInternal(0xBE);
        //TODO should this generate on line #192 ? with [0...192]
        testSmsHLinesCounterInternal(0xBF);
        testSmsHLinesCounterInternal(0xC0);
        testSmsHLinesCounterInternal(0xFF);
    }

    //TODO fix
    @Test
    public void testHLinesCounter() {
        int hLinePassed = 0;
        BaseVdpProvider vdp = MdVdpTestUtil.createBaseTestVdp();
        VdpInterruptHandler h = SmsVdpInterruptHandler.createSmsInstance(vdp);
        MdVdpTestUtil.updateHCounter(vdp, hLinePassed);
        VdpInterruptHandlerTest.hLinesCounterBasic(vdp, h, VideoMode.NTSCJ_H32_V24);
    }

    private void testSmsHLinesCounterInternal(final int lineCounter) {
        VideoMode vm = VideoMode.NTSCJ_H32_V24;
        boolean[] exp = new boolean[0xFF];
        int height = vm.getDimension().height;
        BaseVdpProvider vdp = MdVdpTestUtil.createBaseTestVdp();

        VdpInterruptHandler h = SmsVdpInterruptHandler.createSmsInstance(vdp);
        MdVdpTestUtil.updateHCounter(vdp, lineCounter);
        for (int i = lineCounter + 1; i < exp.length; i++) {
            exp[i] = (i < height) && (i % (lineCounter + 1)) == 0;
        }
        hLinesCounterBasic(vdp, h, vm, exp);
    }


    private static void printMsg(String msg) {
        if (verbose) {
            System.out.println(msg);
        }
    }
}
