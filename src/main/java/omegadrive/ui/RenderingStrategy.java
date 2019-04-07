/*
 * RenderingStrategy
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

package omegadrive.ui;

import java.awt.*;
import java.util.stream.IntStream;

public class RenderingStrategy {

    public static void toLinear(int[] pixels, int[][] screenData, Dimension output) {
        int k = 0;
        for (int i = 0; i < output.height; i++) {
            for (int j = 0; j < output.width; j++) {
                pixels[k++] = screenData[j][i];
            }
        }
    }

    private static int[] srcPxShift;
    private static int xRatioCache = 0;


    /**
     * Works only for resolutions up to 4500x4500
     *
     * TODO: T2, SSF2 crash
     */
    public static void renderNearestBuggy(int[] srcPixels, int[] outputPixels, Dimension src, Dimension dest) {
        int factor = 16;
        int xRatio = ((src.width << factor) / dest.width) + 1;
        int yRatio = ((src.height << factor) / dest.height) + 1;
        int py, shiftSrc, shiftDest;
        if (xRatio != xRatioCache) {
            srcPxShift = IntStream.range(0, dest.width).map(j -> (j * xRatio) >> factor).toArray();
            xRatioCache = xRatio;
        }
        for (int i = 0; i < dest.height; i++) {
            py = (i * yRatio) >> factor;
            shiftDest = i * dest.width;
            shiftSrc = py * src.width;
            for (int j = 0; j < dest.width; j++) {
                outputPixels[shiftDest + j] = srcPixels[shiftSrc + srcPxShift[j]];
            }
        }
    }

    protected static void renderNearest(int[] srcPixels, int[] outputPixels, Dimension src, Dimension dest) {
        int factor = 16;
        int xRatio = ((src.width << factor) / dest.width) + 1;
        int yRatio = ((src.height << factor) / dest.height) + 1;
        int px, py, shiftSrc, shiftDest;
        for (int i = 0; i < dest.height; i++) {
            py = (i * yRatio) >> factor;
            shiftDest = i * dest.width;
            shiftSrc = py * src.width;
            for (int j = 0; j < dest.width; j++) {
                px = (j * xRatio) >> factor;
                outputPixels[shiftDest + j] = srcPixels[shiftSrc + px];
            }
        }
    }
}
