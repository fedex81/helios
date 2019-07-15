/*
 * VdpScrollHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 15/07/19 15:48
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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;

import static omegadrive.vdp.model.BaseVdpProvider.V30_CELL;

//A horizontal scroll mode setting of 01b is not valid; however the unlicensed version
//                of Populous uses it. This mode is identical to per-line scrolling, however
//                the VDP will only read the first sixteen entries in the scroll table for
//                every line of the display.
public class VdpScrollHandler {

    private VdpMemoryInterface memoryInterface;
    private int[] vram;

    public static VdpScrollHandler createInstance(VdpMemoryInterface memoryInterface) {
        VdpScrollHandler v = new VdpScrollHandler();
        v.memoryInterface = memoryInterface;
        v.vram = memoryInterface.getVram();
        return v;
    }

    private static void compareHScrollValues(VdpRenderHandlerImpl vdpRenderHandler, VdpScrollHandler scrollHandler) {
        int[] expectedA = new int[V30_CELL];
        int[] expectedB = new int[V30_CELL];
        int[] hPlaneSize = {32, 64, 128};
        for (HSCROLL type : HSCROLL.values()) {
            for (int size : hPlaneSize) {
                for (int line = 0; line < V30_CELL; line++) {
                    expectedA[line] = vdpRenderHandler.horizontalScrolling(line, type.getRegValue(), 0, size, true);
                    expectedB[line] = vdpRenderHandler.horizontalScrolling(line, type.getRegValue(), 0, size, false);
                }
                int[] actualA = scrollHandler.computeHorizontalScrolling(type, 0, size, true);
                int[] actualB = scrollHandler.computeHorizontalScrolling(type, 0, size, false);
                System.out.println(Arrays.equals(expectedA, actualA));
                System.out.println(Arrays.equals(expectedB, actualB));
            }
        }
    }

    //TODO
    private static void compareVScrollValues(VdpRenderHandlerImpl vdpRenderHandler, VdpScrollHandler scrollHandler) {
        int[] expectedA = new int[V30_CELL];
        int[] expectedB = new int[V30_CELL];
        int[] vPlaneSize = {32, 64, 128};
        for (VSCROLL type : VSCROLL.values()) {
            for (int size : vPlaneSize) {
                for (int line = 0; line < V30_CELL; line++) {
                    expectedA[line] = vdpRenderHandler.horizontalScrolling(line, 0, 0, size, false);
                    expectedB[line] = vdpRenderHandler.horizontalScrolling(line, 0, 0, size, false);
                }
                int[] actualA = scrollHandler.computeHorizontalScrolling(HSCROLL.CELL, 0, size, true);
                int[] actualB = scrollHandler.computeHorizontalScrolling(HSCROLL.CELL, 0, size, false);
                System.out.println(Arrays.equals(expectedA, actualA));
                System.out.println(Arrays.equals(expectedB, actualB));
            }
        }
    }

    public static void main(String[] args) {
        VdpMemoryInterface v = GenesisVdpMemoryInterface.createInstance();
        Random r = new Random(1981);
        for (int i = 0; i < v.getVram().length; i++) {
            v.getVram()[i] = r.nextInt(0x100);
        }
        VdpRenderHandlerImpl vdpRenderHandler = new VdpRenderHandlerImpl(null, v);
        VdpScrollHandler scrollHandler = createInstance(v);
        compareHScrollValues(vdpRenderHandler, scrollHandler);
    }

    /**
     * public int[] verticalScrolling(int line, int VS, int pixel, int tileLocator, int verticalPlaneSize,
     * boolean isPlaneA, int[] fullScreenVerticalOffset) {
     *
     * @param hscrollType
     * @param hScrollBase
     * @param horizontalPlaneSize
     * @param isPlaneA
     * @return
     */


    public int[] computeHorizontalScrolling(HSCROLL hscrollType, int hScrollBase, int horizontalPlaneSize, boolean isPlaneA) {
        int[] res = new int[V30_CELL];
        int scrollDataShift = horizontalPlaneSize << 3;
        int horScrollMask = scrollDataShift - 1;
        int vramOffset, scrollAmount;
        switch (hscrollType) {
            case SCREEN:
                //entire screen is scrolled at once by one longword in the horizontal scroll table
                vramOffset = isPlaneA ? hScrollBase : hScrollBase + 2;
                scrollAmount = (vram[vramOffset] << 8 | vram[vramOffset + 1]) & horScrollMask;
                Arrays.fill(res, scrollDataShift - scrollAmount);
                break;
            case CELL:
                //every long scrolls 8 pixels, 32 bytes x 8 scanlines
                for (int line = 0; line < res.length; line++) {
                    int scrollLine = hScrollBase + ((line >> 3) << 5); //do not simplify the shift
                    vramOffset = isPlaneA ? scrollLine : scrollLine + 2;
                    scrollAmount = (vram[vramOffset] << 8 | vram[vramOffset + 1]) & horScrollMask;
                    res[line] = scrollDataShift - scrollAmount;
                }
                break;
            case INVALID:
                //fall-through
            case LINE:
                //every longword scrolls one scanline
                for (int line = 0; line < res.length; line++) {
                    int scrollLine1 = hScrollBase + (line << 2);    // 4 bytes x 1 scanline
                    vramOffset = isPlaneA ? scrollLine1 : scrollLine1 + 2;
                    scrollAmount = (vram[vramOffset] << 8 | vram[vramOffset + 1]) & horScrollMask;
                    res[line] = scrollDataShift - scrollAmount;
                }
                break;
        }
        return res;
    }

    private int[] computeVerticalScrolling(VSCROLL vScrollType, int pixel, int tileLocator, int verticalPlaneSize,
                                           boolean isPlaneA, InterlaceMode interlaceMode) {
        int[] res = new int[V30_CELL];
        int vsramOffset, scrollData;
        int verticalScrollMask = (verticalPlaneSize << 3) - 1;
        switch (vScrollType) {
            case SCREEN:
                vsramOffset = isPlaneA ? 0 : 2;
                scrollData =
                        (memoryInterface.readVsramWord(vsramOffset) >> interlaceMode.verticalScrollShift()) & verticalScrollMask;
                Arrays.fill(res, scrollData);
                break;
            case TWO_CELLS:
                break;
        }
        return res;
//        if (VS == 0 && fullScreenVerticalOffset != null) {
//            return fullScreenVerticalOffset;
//        }
//        //VS == 1 -> 2 cell scroll
//        int scrollLine = VS == 1 ? (pixel >> 4) << 2 : 0; //do not reduce the shift
//        int vsramOffset = isPlaneA ? scrollLine : scrollLine + 2;
//        int scrollDataVer = memoryInterface.readVsramWord(vsramOffset);
//
//        scrollDataVer = scrollDataVer >> interlaceMode.verticalScrollShift();
//
//        int verticalScrollMask = (verticalPlaneSize << 3) - 1;
//
//
//        int scrollMap = (scrollDataVer + line) & verticalScrollMask;
//        int tileLocatorFactor = getHorizontalPlaneSize() << 1;
//        tileLocator += (scrollMap >> 3) * tileLocatorFactor;
//        verticalScrollRes[0] = tileLocator;
//        verticalScrollRes[1] = scrollMap;
//        return verticalScrollRes;
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
