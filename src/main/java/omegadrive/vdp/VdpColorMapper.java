package omegadrive.vdp;

import omegadrive.vdp.model.ShadowHighlightType;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class VdpColorMapper {
    /**
     * http://gendev.spritesmind.net/forum/viewtopic.php?t=1389
     * <p>
     * Shadow   Highlight    Normal
     * -------- -------- ---------
     * (0) 0.0             0.0 (0)
     * (1) 0.5
     * (2) 0.9             0.9 (1)
     * (3) 1.3
     * (4) 1.6             1.6 (2)
     * (5) 1.9
     * (6) 2.2             2.2 (3)
     * (7) 2.4   (0) 2.4
     * (1) 2.7   2.7 (4)
     * (2) 2.9
     * (3) 3.2   3.2 (5)
     * (4) 3.5
     * (5) 3.8   3.8 (6)
     * (6) 4.2
     * (7) 4.7   4.7 (7)
     */

    public static int VDP_TONES_PER_CHANNEL = 8;  // 3 bits per channel
    public static int OUTPUT_TONES_PER_CHANNEL = 256; // 8 bits per channel
    public static double VDP_MAX_COLOR_LEVEL = 4.7; //voltage ouput

    private int[][][] colorsCache = new int[VDP_TONES_PER_CHANNEL][VDP_TONES_PER_CHANNEL][VDP_TONES_PER_CHANNEL];
    private int[][][] colorsCacheShadow = new int[VDP_TONES_PER_CHANNEL][VDP_TONES_PER_CHANNEL][VDP_TONES_PER_CHANNEL];
    private int[][][] colorsCacheHighLight = new int[VDP_TONES_PER_CHANNEL][VDP_TONES_PER_CHANNEL][VDP_TONES_PER_CHANNEL];

    private final static double[] NORMAL_LEVELS = {0, 0.9, 1.6, 2.2, 2.7, 3.2, 3.8, 4.7};
    private final static double[] SHADOW_LEVELS = {0, 0.5, 0.9, 1.3, 1.6, 1.9, 2.2, 2.4};
    private final static double[] HIGHLIGHT_LEVELS = {2.4, 2.7, 2.9, 3.2, 3.5, 3.8, 4.2, 4.7};

    public VdpColorMapper() {
        initColorsCache(colorsCache, NORMAL_LEVELS);
        initColorsCache(colorsCacheShadow, SHADOW_LEVELS);
        initColorsCache(colorsCacheHighLight, HIGHLIGHT_LEVELS);
    }

    /**
     * outputs 24bit RGB Colors
     */
    public int getColor(int red, int green, int blue, ShadowHighlightType shadowHighlightType) {
        switch (shadowHighlightType) {
            case NORMAL:
                return colorsCache[red][green][blue];
            case HIGHLIGHT:
                return colorsCacheHighLight[red][green][blue];
            case SHADOW:
                return colorsCacheShadow[red][green][blue];
        }
        return colorsCache[red][green][blue];
    }

    public int getColor(int red, int green, int blue) {
        return colorsCache[red][green][blue];
    }


    private static void initColorsCache(int[][][] colorsCache, double[] levels) {
        double factor = (OUTPUT_TONES_PER_CHANNEL - 1) / VDP_MAX_COLOR_LEVEL;
        for (int r = 0; r < VDP_TONES_PER_CHANNEL; r++) {
            for (int g = 0; g < VDP_TONES_PER_CHANNEL; g++) {
                for (int b = 0; b < VDP_TONES_PER_CHANNEL; b++) {
                    int red = (int) Math.round(levels[r] * factor);
                    int green = (int) Math.round(levels[g] * factor);
                    int blue = (int) Math.round(levels[b] * factor);
                    int color = red << VDP_TONES_PER_CHANNEL * 2 | green << VDP_TONES_PER_CHANNEL | blue;
                    colorsCache[r][g][b] = color;
                }
            }
        }
    }
}
