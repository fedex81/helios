/*
 * Tms9918a
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

package omegadrive.vdp.model;

import java.awt.*;

/**
 *
 * http://bifi.msxnet.org/msxnet/tech/tms9918a.txt
 */
public interface Tms9918a extends BaseVdpProvider {
    enum TmsMode {
        MODE_0,
        MODE_1,
        MODE_2,
        MODE_3
    }

    enum TmsRegisterName {
        VIDEO_MODE_1, // 0
        VIDEO_MODE_2, // 1
        TILEMAP_NAMETABLE, //2
        COLORMAP_NAMETABLE, //3
        TILE_START_ADDRESS, //4
        SPRITE_TABLE_LOC, //5
        SPRITE_TILE_BASE_ADDR, //6
        BACKGROUND_COLOR, //7
    }

    Color[] colors = {
            (new Color(0, 0, 0, 0)),        // 0
            (new Color(0, 0, 0)),            // 1
            (new Color(33, 200, 66)),        // 2
            (new Color(94, 220, 120)),    // 3
            (new Color(84, 85, 237)),        // 4
            (new Color(125, 118, 252)),    // 5
            (new Color(212, 82, 77)),        // 6
            (new Color(66, 235, 245)),    // 7
            (new Color(252, 85, 84)),        // 8
            (new Color(255, 121, 120)),    // 9
            (new Color(212, 193, 84)),    // A
            (new Color(230, 206, 128)),    // B
            (new Color(33, 176, 59)),        // C
            (new Color(201, 91, 186)),    // D
            (new Color(204, 204, 204)),    // E
            (new Color(255, 255, 255)),    // F
    };

    int RAM_SIZE = 0xFFFF;
    int REGISTERS = 8;

    default int getRegisterData(TmsRegisterName registerName) {
        return getRegisterData(registerName.ordinal());
    }

    default void updateRegisterData(TmsRegisterName registerName, int data) {
        updateRegisterData(registerName.ordinal(), data);
    }
}
