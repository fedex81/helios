/*
 * ScreenSizeHelper
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

package omegadrive.util;

import java.awt.*;

public class ScreenSizeHelper {

    public static final double FOUR_BY_THREE = 4.0 / 3.0;

    public static int DEFAULT_X = 320;
    public static int DEFAULT_Y = 256; //TODO check this

    public static final int DEFAULT_SCALE_FACTOR =
            Integer.parseInt(System.getProperty("helios.ui.scale", "2"));
    public static final double FULL_SCREEN_WITH_TITLE_BAR_FACTOR =
            Double.parseDouble(System.getProperty("helios.ui.fsTitle.factor", "1"));

    public static final boolean FIX_ASPECT_RATIO =
            Boolean.parseBoolean(System.getProperty("ui.fix.aspect.ratio", "true"));

    public static Dimension DEFAULT_SCALED_SCREEN_SIZE = new Dimension(ScreenSizeHelper.DEFAULT_X * DEFAULT_SCALE_FACTOR,
            ScreenSizeHelper.DEFAULT_Y * DEFAULT_SCALE_FACTOR);
    public static Dimension DEFAULT_BASE_SCREEN_SIZE = new Dimension(ScreenSizeHelper.DEFAULT_X,
            ScreenSizeHelper.DEFAULT_Y);
    public static Dimension DEFAULT_FRAME_SIZE = new Dimension((int) (DEFAULT_SCALED_SCREEN_SIZE.width * 1.02),
            (int) (DEFAULT_SCALED_SCREEN_SIZE.height * 1.10));

    public static Dimension getScreenSize(VideoMode videoMode, double multiplier, boolean mantainAspectRatio) {
        return getScreenSize(videoMode.getDimension(), multiplier, mantainAspectRatio);
    }

    public static Dimension getScreenSize(Dimension src, double multiplier, boolean mantainAspectRatio) {
        Dimension dim = src;
        if (mantainAspectRatio || multiplier != 1.0) {
            double w = src.width * multiplier;
            double h = w / FOUR_BY_THREE;
            dim = new Dimension((int) w, (int) h);
        }
        return dim;
    }

    public static double getFullScreenScaleFactor(Dimension fullScreenSize, Dimension nativeScreenSize) {
        double scaleW = fullScreenSize.getWidth() / nativeScreenSize.getWidth();
        double baseH = nativeScreenSize.getHeight();
        baseH = FIX_ASPECT_RATIO ? nativeScreenSize.getWidth() / FOUR_BY_THREE : baseH;
        double scaleH = fullScreenSize.getHeight() * FULL_SCREEN_WITH_TITLE_BAR_FACTOR / baseH;
        return Math.min(scaleW, scaleH);
    }
}
