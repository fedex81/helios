package omegadrive.util;

import java.awt.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class ScreenSizeHelper {

    public static int DEFAULT_X = 320;
    public static int DEFAULT_Y = 240;

    public static final int DEFAULT_SCREEN = 1;
    public static final int DEFAULT_SCALE_FACTOR =
            Integer.valueOf(System.getProperty("emu.ui.scale", "2"));
    public static final double FULL_SCREEN_WITH_TITLE_BAR_FACTOR =
            Double.valueOf(System.getProperty("emu.ui.fsTitle.factor", "1"));

    public static Dimension DEFAULT_SCALED_SCREEN_SIZE = new Dimension(ScreenSizeHelper.DEFAULT_X * DEFAULT_SCALE_FACTOR,
            ScreenSizeHelper.DEFAULT_Y * DEFAULT_SCALE_FACTOR);
    public static Dimension DEFAULT_BASE_SCREEN_SIZE = new Dimension(ScreenSizeHelper.DEFAULT_X,
            ScreenSizeHelper.DEFAULT_Y);
    public static Dimension DEFAULT_FRAME_SIZE = new Dimension((int) (DEFAULT_SCALED_SCREEN_SIZE.width * 1.02),
            (int) (DEFAULT_SCALED_SCREEN_SIZE.height * 1.10));

    public static Dimension getScreenSize(VideoMode videoMode, double multiplier) {
        Dimension dim = videoMode.getDimension();
        if (multiplier != 1.0) {
            dim = new Dimension((int) (dim.width * multiplier), (int) (dim.height * multiplier));
        }
        return dim;
    }
}
