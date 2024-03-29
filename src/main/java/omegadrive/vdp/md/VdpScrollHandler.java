/*
 * VdpScrollHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 19/07/19 13:35
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

import omegadrive.vdp.model.InterlaceMode;
import omegadrive.vdp.model.RenderPriority;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.model.VdpMisc.RenderType;

import java.nio.ByteBuffer;

import static omegadrive.util.Util.readBufferWord;

//A horizontal scroll mode setting of 01b is not valid; however the unlicensed version
//                of Populous uses it. This mode is identical to per-line scrolling, however
//                the VDP will only read the first sixteen entries in the scroll table for
//                every line of the display.
public class VdpScrollHandler {

    protected ByteBuffer vram;
    protected ByteBuffer vsram;

    public static VdpScrollHandler createInstance(VdpMemoryInterface memoryInterface) {
        VdpScrollHandler v = new VdpScrollHandler();
        v.vram = memoryInterface.getVram();
        v.vsram = memoryInterface.getVsram();
        return v;
    }

    public int getHorizontalScroll(int line, ScrollContext sc) {
        int vramOffset = sc.hScrollTableLocation;
        switch (sc.hScrollType) {
            case SCREEN:
                break;
            case CELL: //cluster of 8 lines
                //NOTE: each cluster's hScrollValue is 32 bytes apart in VRAM
                vramOffset += ((line >> 3) << 5);
                break;
            case INVALID:
            case LINE:
                vramOffset += (line << 2);
                break;
        }
        final int scrollDataShift = sc.planeWidth << 3;
        final int scrollMask = scrollDataShift - 1;
        vramOffset = sc.planeType == RenderType.PLANE_A ? vramOffset : vramOffset + 2;
        int scrollAmount = readBufferWord(vram, vramOffset) & scrollMask;
        return scrollDataShift - scrollAmount;
    }

    public int getVerticalScroll(int twoCell, ScrollContext sc) {
        int scrollMask = (sc.planeHeight << 3) - 1;
        int vramOffset = sc.planeType == RenderType.PLANE_A ? 0 : 2;
        vramOffset += sc.vScrollType == VSCROLL.TWO_CELLS ? twoCell << 2 : 0;
        int scrollAmount = readBufferWord(vsram, vramOffset) >> sc.interlaceMode.verticalScrollShift();
        return scrollAmount & scrollMask;
    }

    public static class ScrollContext {
        final RenderType planeType;
        final int[] plane;
        final RenderPriority highPrio;
        final RenderPriority lowPrio;
        int planeWidth;
        int planeHeight;
        int hScrollTableLocation;
        VSCROLL vScrollType;
        HSCROLL hScrollType;
        InterlaceMode interlaceMode;

        private ScrollContext(RenderType type, int[] plane) {
            this.planeType = type;
            this.highPrio = RenderPriority.getRenderPriority(type, true);
            this.lowPrio = RenderPriority.getRenderPriority(type, false);
            this.plane = plane;
        }

        public static ScrollContext createInstance(RenderType type, int[] plane) {
            return new ScrollContext(type, plane);
        }
    }

    enum HSCROLL {
        SCREEN(0b00),
        CELL(0b10),
        LINE(0b11),
        INVALID(0b01);

        private static final HSCROLL[] lookupRegValue = {SCREEN, INVALID, CELL, LINE};

        HSCROLL(int regValue) {
        }

        public static HSCROLL getHScrollType(int regValue) {
            return lookupRegValue[regValue];
        }
    }

    enum VSCROLL {
        SCREEN(0),
        TWO_CELLS(1);

        private static final VSCROLL[] lookupRegValue = {SCREEN, TWO_CELLS};

        VSCROLL(int regValue) {
        }

        public static VSCROLL getVScrollType(int regValue) {
            return lookupRegValue[regValue];
        }
    }
}
