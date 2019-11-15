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

public class RenderingStrategy {

    public static void toLinearLine(int[] pixels, int[][] screenData, Dimension output) {
        int k = 0;
        for (int line = 0; line < output.height; line++) {
            System.arraycopy(screenData[line], 0, pixels, k, screenData[line].length);
            k += screenData[line].length;
        }
    }

    public static void subImageWithOffset(int[] src, int[] dest, Dimension srcDim, Dimension destDim,
                                          int xOffset, int yOffset){
        int start = ((yOffset+1) * srcDim.width) + xOffset + 1;
        int k = 0;
        for (int i = start; k < dest.length; i+= srcDim.width) {
            System.arraycopy(src, i, dest, k, destDim.width);
            k += destDim.width;
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
