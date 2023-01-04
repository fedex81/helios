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

    private static final TransferFnHolder transferFnHolder = new TransferFnHolder();

    public static void subImageWithOffset(int[] src, int[] dest, Dimension srcDim, Dimension destDim,
                                          int xOffset, int yOffset) {
        int start = ((yOffset + 1) * srcDim.width) + xOffset + 1;
        int k = 0;
        for (int i = start; k < dest.length; i += srcDim.width) {
            System.arraycopy(src, i, dest, k, destDim.width);
            k += destDim.width;
        }
    }

    //still faster on my laptop
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

    protected static void renderNearestNew(final int[] srcPixels, final int[] outputPixels,
                                           Dimension src, Dimension dest) {
        if (dest != transferFnHolder.dest || src != transferFnHolder.src) {
            System.out.println("recalc");
            //recalc
            transferFnHolder.src = src;
            transferFnHolder.dest = dest;
            transferFnHolder.transferFn = new int[dest.height * dest.width];
            transferFnHolder.computeTransferFn();
        }
        final int[] tFn = transferFnHolder.transferFn;
        for (int i = 0; i < tFn.length; i++) {
            outputPixels[i] = srcPixels[tFn[i]];
        }
    }

    static class TransferFnHolder {
        Dimension src = new Dimension(0, 0);
        Dimension dest = new Dimension(0, 0);
        int[] transferFn = new int[0];

        protected void computeTransferFn() {
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
                    transferFn[shiftDest + j] = shiftSrc + px;
                }
            }
        }
    }
}
