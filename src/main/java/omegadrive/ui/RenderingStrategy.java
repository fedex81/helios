package omegadrive.ui;

import java.awt.*;
import java.util.stream.IntStream;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
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
     */
    public static void renderNearest(int[] srcPixels, int[] outputPixels, Dimension src, Dimension dest) {
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

    @Deprecated
    protected static void renderNearestOld(int[] srcPixels, int[] outputPixels, Dimension src, Dimension dest) {
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
