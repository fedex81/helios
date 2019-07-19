/*
 * VdpRenderHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 19/07/19 13:35
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

package omegadrive.vdp.model;

import omegadrive.util.VideoMode;

public interface VdpRenderHandler {

    void dumpScreenData();

    int[][] renderFrame();

    void setVideoMode(VideoMode videoMode);

    void renderLine(int line);

    void initLineData(int line);

    static int getVerticalPlaneSize(int reg10) {
        int horScrollSize = reg10 & 3;
        int vertScrollSize = (reg10 >> 4) & 3;
        switch (vertScrollSize) {
            case 0b00:
                return horScrollSize == 0b10 ? 1 : 32;
            case 0b01:
            case 0b10:
                return horScrollSize == 0b10 ? 1 : (horScrollSize == 0b11 ? 32 : 64);
            case 0b11:
                return horScrollSize == 0b10 ? 1 :
                        (horScrollSize == 0b11 ? 32 : (horScrollSize == 0b01 ? 64 : 128));

        }
        return 0;
    }

    static int getHorizontalPlaneSize(int reg10) {
        int horScrollSize = reg10 & 3;
        switch (horScrollSize) {
            case 0:
                return 32;
            case 0b01:
                return 64;
            case 0b10:
                return 32;
            case 0b11:
                return 128;

        }
        return 0;
    }
}
