/*
 * GenesisVdpTest2
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 14:04
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

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.system.Megadrive;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.SystemTestUtil;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.VdpDmaHandlerTest;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.Optional;

public class GenesisVdpSpeedTest {

    private static final Logger LOG = LogHelper.getLogger(VdpDmaHandlerTest.class.getSimpleName());

    GenesisVdpProvider vdpProvider;
    VdpMemoryInterface memoryInterface;
    GenesisBusProvider busProvider;

    @Before
    public void setup() {
        busProvider = SystemTestUtil.setupNewMdSystem();
        Optional<GenesisVdpProvider> opt = busProvider.getBusDeviceIfAny(GenesisVdpProvider.class);
        Assert.assertTrue(opt.isPresent());
        vdpProvider = opt.get();
        memoryInterface = (VdpMemoryInterface) vdpProvider.getVdpMemory();
        MdVdpTestUtil.vdpMode5(vdpProvider);
    }

    static double MCLK_PER_LINE = 3420.0;
    //the emulator runs a 7.68Mhz
    static double M68KCLK_PER_LINE = MCLK_PER_LINE / Megadrive.MCLK_DIVIDER;

    final static double EPSILON = 1e-10;

    /**
     * MCLK_NTSC = 53.7 Mhz
     * Each VDP line is 3420 clocks@MCLK
     * H32 uses a constant clock, MCLK/10
     * H40 uses a faster clock during hblank: MCLK/8
     */
    @Test
    public void testH32Speed() {
        testSpeedInternal(true);
    }

    @Test
    public void testH40Speed() {
        testSpeedInternal(false);
    }

    private void testSpeedInternal(boolean isH32) {
        vdpProvider.setRegion(RegionDetector.Region.USA);
        if (isH32) {
            MdVdpTestUtil.setH32(vdpProvider);
        } else {
            MdVdpTestUtil.setH40(vdpProvider);
        }
        MdVdpTestUtil.vdpDisplayEnable(vdpProvider, true);
        Assert.assertTrue(vdpProvider.isDisplayEnabled());
        MdVdpTestUtil.runToStartFrame(vdpProvider);
        MdVdpTestUtil.runToStartNextLine(vdpProvider);
        MdVdpTestUtil.runToStartNextLine(vdpProvider);
        int vc = vdpProvider.getVCounter();
        Assert.assertTrue(vc > 0 && vc < 10);
        //4 = fastDivider, 5 = slowDivider; 4 implies a vdp speed of MCLK/8, 5 implies MCLK/10
        int vdpSpeedDivider;
        do {
            int cnt = 0;
            double clocks_7_68Mhz = 0;
            boolean isStart;
            do {
                cnt++;
                vdpSpeedDivider = vdpProvider.runSlot();
                clocks_7_68Mhz += Megadrive.vdpVals[vdpSpeedDivider - 4];
                isStart = vdpProvider.getHCounter() == 0;
            } while (!isStart);
            Assert.assertTrue(Math.abs(clocks_7_68Mhz - M68KCLK_PER_LINE) < EPSILON);
            Assert.assertTrue(cnt < (isH32 ? 200 : 250)); //check h32 vs h40 is working
        } while (!MdVdpTestUtil.isVBlank(vdpProvider));
    }
}
