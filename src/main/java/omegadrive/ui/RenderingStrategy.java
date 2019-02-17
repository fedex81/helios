package omegadrive.ui;

import java.awt.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class RenderingStrategy {

    public static void toLinear(int[] pixels, int[][] screenData, Dimension output) {
        int shiftX;
        for (int i = 0; i < output.height; i++) {
            shiftX = i * output.width;
            for (int j = 0; j < output.width; j++) {
                pixels[shiftX + j] = screenData[j][i];
            }
        }
    }

    public static void renderNearest(int[] srcPixels, int[] outputPixels, Dimension src, Dimension dest) {
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
