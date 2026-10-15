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

package omegadrive.ui.filter;

import java.awt.*;

public class RenderingStrategy {

    public static void subImageWithOffset(int[] src, int[] dest, Dimension srcDim, Dimension destDim,
                                          int xOffset, int yOffset) {
        int start = ((yOffset + 1) * srcDim.width) + xOffset + 1;
        int k = 0;
        for (int i = start; k < dest.length; i += srcDim.width) {
            System.arraycopy(src, i, dest, k, destDim.width);
            k += destDim.width;
        }
    }


    public static void renderNearestOld(int[] srcPixels, int[] outputPixels, Dimension src, Dimension dest) {
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

    private static int[] lineBuf = new int[0];

    public static void renderNearest(final int[] srcPixels, final int[] outputPixels, Dimension src, Dimension dest) {
        final int sw = src.width, sh = src.height;
        final int dw = dest.width, dh = dest.height;
        final int factor = 16;
        final int xRatio = ((sw << factor) / dw) + 1;
        final int yRatio = ((sh << factor) / dh) + 1;

        if (lineBuf.length < dw) {
            lineBuf = new int[dw];
        }

        final int[] line = lineBuf;

        int lastSrcRow = -1;
        for (int i = 0; i < dh; i++) {
            int sy = (i * yRatio) >> factor;
            int destRow = i * dw;
            if (sy != lastSrcRow) {
                int srcRow = sy * sw;
                for (int j = 0; j < dw; j++) {
                    line[j] = srcPixels[srcRow + ((j * xRatio) >> factor)];
                }
                lastSrcRow = sy;
            }
            System.arraycopy(line, 0, outputPixels, destRow, dw);
        }

    }
}
