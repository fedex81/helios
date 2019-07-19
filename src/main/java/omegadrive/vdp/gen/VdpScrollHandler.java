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

package omegadrive.vdp.gen;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
import omegadrive.vdp.model.InterlaceMode;
import omegadrive.vdp.model.VdpMemoryInterface;

import java.util.EnumSet;
import java.util.Map;

//A horizontal scroll mode setting of 01b is not valid; however the unlicensed version
//                of Populous uses it. This mode is identical to per-line scrolling, however
//                the VDP will only read the first sixteen entries in the scroll table for
//                every line of the display.
public class VdpScrollHandler {

    protected VdpMemoryInterface memoryInterface;
    protected int[] vram;
    protected int[] vsram;

    public static VdpScrollHandler createInstance(VdpMemoryInterface memoryInterface) {
        VdpScrollHandler v = new VdpScrollHandler();
        v.memoryInterface = memoryInterface;
        v.vram = memoryInterface.getVram();
        v.vsram = memoryInterface.getVsram();
        return v;
    }

    public int getHorizontalScroll(int line, ScrollContext sc) {
        int scrollDataShift = sc.planeWidth << 3;
        int scrollMask = scrollDataShift - 1;
        int vramOffset = 0, scrollAmount;
        switch (sc.hScrollType) {
            case SCREEN:
                vramOffset = sc.planeA ? sc.hScrollTableLocation : sc.hScrollTableLocation + 2;
                break;
            case CELL:
                int scrollLine = sc.hScrollTableLocation + ((line >> 3) << 5);
                vramOffset = sc.planeA ? scrollLine : scrollLine + 2;
                break;
            case INVALID:
            case LINE:
                int scrollLine1 = sc.hScrollTableLocation + (line << 2);
                vramOffset = sc.planeA ? scrollLine1 : scrollLine1 + 2;
                break;
        }
        scrollAmount = (vram[vramOffset] << 8 | vram[vramOffset + 1]) & scrollMask;
        return (sc.planeWidth << 3) - scrollAmount;
    }

    public int getVerticalScroll(int cell, ScrollContext sc) {
        int vramOffset = sc.planeA ? 0 : 2;
        vramOffset += sc.vScrollType == VSCROLL.TWO_CELLS ? cell << 2 : 0;
        int scrollAmount = vsram[vramOffset] << 8 | vsram[vramOffset + 1];
        return scrollAmount >> sc.interlaceMode.verticalScrollShift();
    }

    public static class ScrollContext {
        boolean planeA;
        int planeWidth;
        int planeHeight;
        int hScrollTableLocation;
        VSCROLL vScrollType;
        HSCROLL hScrollType;
        InterlaceMode interlaceMode;

        public static ScrollContext createInstance(boolean isPlaneA) {
            ScrollContext s = new ScrollContext();
            s.planeA = isPlaneA;
            return s;
        }
    }

    enum HSCROLL {
        SCREEN(0b00),
        CELL(0b10),
        LINE(0b11),
        INVALID(0b01);

        private static Map<Integer, HSCROLL> lookup = ImmutableBiMap.copyOf(
                Maps.toMap(EnumSet.allOf(HSCROLL.class), HSCROLL::getRegValue)).inverse();
        private int regValue;

        HSCROLL(int regValue) {
            this.regValue = regValue;
        }

        public static HSCROLL getHScrollType(int regValue) {
            return lookup.get(regValue);
        }

        public int getRegValue() {
            return regValue;
        }
    }

    enum VSCROLL {
        SCREEN(0),
        TWO_CELLS(1);

        private static Map<Integer, VSCROLL> lookup = ImmutableBiMap.copyOf(
                Maps.toMap(EnumSet.allOf(VSCROLL.class), VSCROLL::getRegValue)).inverse();
        private int regValue;

        VSCROLL(int regValue) {
            this.regValue = regValue;
        }

        public static VSCROLL getVScrollType(int regValue) {
            return lookup.get(regValue);
        }

        public int getRegValue() {
            return regValue;
        }
    }
}
