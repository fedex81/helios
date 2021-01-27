/*
 * BaseVdpProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 10:54
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

package omegadrive.vdp.model;

import omegadrive.Device;
import omegadrive.util.RegionDetector;
import omegadrive.util.VideoMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface BaseVdpProvider extends Device, BaseVdpAdapterEventSupport {

    Logger LOG = LogManager.getLogger(BaseVdpProvider.class.getSimpleName());

    int MCLK_DIVIDER_FAST_VDP = 4;
    int MCLK_DIVIDER_SLOW_VDP = 5;

    int V18_CELL = 144;
    int V24_CELL = 192;
    int V28_CELL = 224;
    int V30_CELL = 240;
    int H40 = 320;
    int H32 = 256;
    int H20 = 160;
    int H32_TILES = H32 / 8;
    int H40_TILES = H40 / 8;

    //vdp counter data
    int PAL_SCANLINES = 313;
    int NTSC_SCANLINES = 262;
    int H32_PIXELS = 342;
    int H40_PIXELS = 420;
    int H32_JUMP = 0x127;
    int H40_JUMP = 0x16C;
    int H32_HBLANK_SET = 0x126;
    int H40_HBLANK_SET = 0x166;
    int H32_HBLANK_CLEAR = 0xA;
    int H40_HBLANK_CLEAR = 0xB;
    int V24_VBLANK_SET = 0xC0;
    int V28_VBLANK_SET = 0xE0;
    int V30_VBLANK_SET = 0xF0;
    int H32_VCOUNTER_INC_ON = 0x10A;
    int H40_VCOUNTER_INC_ON = 0x14A;
    int V24_PAL_JUMP = 0xF2;
    int V24_NTSC_JUMP = 0xDA;
    int V28_PAL_JUMP = 0x102;
    int V28_NTSC_JUMP = 0xEA;
    int V30_PAL_JUMP = 0x10A;
    int V30_NTSC_JUMP = -1; //never
    int H32_SLOTS = H32_PIXELS / 2;
    int H40_SLOTS = H40_PIXELS / 2;
    int H40_SLOW_CLOCK = 0x1E2;

    void init();

    int runSlot();

    int getRegisterData(int reg);

    void updateRegisterData(int reg, int data);

    VideoMode getVideoMode();

    VdpMemory getVdpMemory();

    int[] getScreenDataLinear();

    void setRegion(RegionDetector.Region region);

    //after loading a state
    default void reload() {
        //DO NOTHING
    }

    default void dumpScreenData() {
        throw new UnsupportedOperationException("Not supported");
    }

    default String getVdpStateString() {
        return "vdpState: unsupported";
    }

    default void resetVideoMode(boolean force) {
        throw new UnsupportedOperationException("Not supported");
    }
}
