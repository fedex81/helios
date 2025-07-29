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

import omegadrive.util.LogHelper;
import omegadrive.util.VideoMode;
import omegadrive.vdp.md.VdpInterruptHandler;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport.VdpEventListener;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.VdpCounterMode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.slf4j.Logger;

@Ignore
public class BaseVdpInterruptHandlerTest {

    private static final Logger LOG = LogHelper.getLogger(BaseVdpInterruptHandlerTest.class.getSimpleName());
    static boolean verbose = false;

    protected boolean enableListener;
    protected int numberOfHint = 0;
    protected int line = 0, lineCount = 0;
    private boolean[] actualLineInt;

    protected static void printMsg(String msg) {
        if (verbose) {
            System.out.println(msg);
        }
    }

    @Before
    public void before() {
        enableListener = false;
        numberOfHint = line = lineCount = 0;
        actualLineInt = new boolean[0x200];
    }

    protected void prepareVdp(BaseVdpProvider vdp, VdpInterruptHandler h,
                              VideoMode mode) {
        MdVdpTestUtil.updateVideoMode(vdp, mode);
        vdp.addVdpEventListener(getVdpEventListener(h));
        System.out.println("STARTING: " + mode);
        MdVdpTestUtil.runCounterToStartFrame(h);
        printMsg(h.getStateString("Start frame: "));
        enableListener = true;
    }

    public void hLinesCounterBasic(BaseVdpProvider vdp, VdpInterruptHandler h,
                                   VideoMode mode, boolean[] expectedLineInt) {
        before();
        actualLineInt = new boolean[expectedLineInt.length];
        int totalCount = VdpCounterMode.getCounterMode(mode).vTotalCount * 3;
        prepareVdp(vdp, h, mode);
        do {
            h.increaseHCounter();
        } while (lineCount < totalCount);
        Assert.assertArrayEquals(expectedLineInt, actualLineInt);
    }

    public void hLinesCounterBasic2(BaseVdpProvider vdp, VdpInterruptHandler h, VideoMode mode) {
        before();
        VdpCounterMode counterMode = VdpCounterMode.getCounterMode(mode);
        int totalCount = counterMode.vTotalCount * 3 + 5;
        //V28: triggers on line [0-E0] - includes vblank line (??)
        int expectedNumberOfHint = (counterMode.vBlankSet + 1) * 3;
        prepareVdp(vdp, h, mode);
        do {
            h.increaseHCounter();
        } while (lineCount < totalCount);
        Assert.assertEquals(expectedNumberOfHint, numberOfHint);
    }

    private void newLineEvent(VdpInterruptHandler h) {
        if (h.getvCounterInternal() == 0) {
            line = 0;
        } else {
            line++;
        }
        lineCount++;
        printMsg(h.getStateString("Start Line: " + line));
    }

    protected void hIntPending(VdpInterruptHandler h, boolean value) {
        if (!value) {
            return;
        }
        printMsg(h.getStateString("HINT pending, Line: " + line));
        if (line >= actualLineInt.length) {
            Assert.fail(h.getStateString("Line: " + line));
        } else {
            actualLineInt[line] = true;
        }
        numberOfHint++;
        h.setHIntPending(false);
    }

    private VdpEventListener getVdpEventListener(VdpInterruptHandler h) {
        return new VdpEventListener() {
            @Override
            public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
                if (!enableListener) {
                    return;
                }
                switch (event) {
                    case INTERRUPT:
                        h.setvIntPending(false);
                        break;
                    case H_BLANK_CHANGE:
                        boolean hBlankState = (boolean) value;
                        if (!hBlankState) {
                            newLineEvent(h);
                        }
                        break;
                    case V_BLANK_CHANGE:
                        boolean vBlankState = (boolean) value;
                        if (!vBlankState) {
                            printMsg(h.getStateString("Start frame, count: " + lineCount));
                        }
                        break;
                    case REG_H_LINE_COUNTER_CHANGE:
                        printMsg(h.getStateString("Line: " + line + ", hLine Counter changed"));
                        break;
                    case VDP_HINT_PENDING:
                        hIntPending(h, (boolean) value);
                        break;
                }
            }
        };
    }
}
