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

    public static int DEFAULT_MULTIPLIER = 3;

    public static int DEFAULT_X = 256;
    public static int DEFAULT_Y = 224;

    public static Dimension getScreenSize(VideoMode videoMode, double multiplier) {
        Dimension dim = videoMode.getDimension();
        if (multiplier != 1.0) {
            dim = new Dimension((int) (dim.width * multiplier), (int) (dim.height * multiplier));
        }
        return dim;
    }
}
