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
