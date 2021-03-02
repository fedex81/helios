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

package omegadrive.vdp.md;

import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.BaseVdpInterruptHandlerTest;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpCounterMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class VdpInterruptHandlerTest extends BaseVdpInterruptHandlerTest {

    private static final Logger LOG = LogManager.getLogger(VdpInterruptHandlerTest.class.getSimpleName());

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
    @Ignore("TODO fix")
    public void testHLinesCounter_01() {
        int hLinePassed = 0;
        BaseVdpProvider vdp = MdVdpTestUtil.createBaseTestVdp();
        VdpInterruptHandler h = VdpInterruptHandler.createMdInstance(vdp);
        MdVdpTestUtil.updateHCounter(vdp, hLinePassed);
        hLinesCounterBasic2(vdp, h, VideoMode.PAL_H40_V28);
        hLinesCounterBasic2(vdp, h, VideoMode.PAL_H40_V30);
        hLinesCounterBasic2(vdp, h, VideoMode.PAL_H32_V28);
        hLinesCounterBasic2(vdp, h, VideoMode.PAL_H32_V30);
        hLinesCounterBasic2(vdp, h, VideoMode.NTSCU_H32_V28);
        hLinesCounterBasic2(vdp, h, VideoMode.NTSCU_H40_V28);
        hLinesCounterBasic2(vdp, h, VideoMode.NTSCJ_H32_V28);
        hLinesCounterBasic2(vdp, h, VideoMode.NTSCJ_H40_V28);
    }

    @Test
    public void testHLinesCounterPending() {
        int hLinePassed = 0x80;
        VideoMode mode = VideoMode.PAL_H40_V28;
        BaseVdpProvider vdp = MdVdpTestUtil.createBaseTestVdp();
        VdpInterruptHandler h = VdpInterruptHandler.createMdInstance(vdp);
        MdVdpTestUtil.updateHCounter(vdp, hLinePassed);
        VdpCounterMode counterMode = VdpCounterMode.getCounterMode(mode);
        int totalCount = counterMode.vTotalCount * 3 + 5;
        prepareVdp(vdp, h, mode);

        do {
            h.increaseHCounter();
            if (h.isHIntPending()) {
                printMsg(h.getStateString("Line: " + line + ", HINT pending"));
                Assert.assertEquals(hLinePassed, h.getVCounterExternal());
                h.setHIntPending(false);
            }
        } while (lineCount < totalCount);
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

        BaseVdpProvider vdp = MdVdpTestUtil.createBaseTestVdp();
        VdpInterruptHandler h = VdpInterruptHandler.createMdInstance(vdp);
        MdVdpTestUtil.updateHCounter(vdp, hLinePassed);

        int totalCount = GenesisVdpProvider.NTSC_SCANLINES * 10 + 5;
        prepareVdp(vdp, h, VideoMode.NTSCU_H40_V28);
        do {
            h.increaseHCounter();
            if (h.isHIntPending()) {
                printMsg(h.getStateString("Line: " + line + ", HINT pending"));
                h.setHIntPending(false);
                Assert.assertEquals("Error on count: " + lineCount, hIntOnLine, line);
            }
        } while (lineCount < totalCount);
    }
}
