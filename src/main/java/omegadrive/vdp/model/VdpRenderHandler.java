/*
 * VdpRenderHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 11:48
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

import java.util.Objects;

import static omegadrive.vdp.model.BaseVdpProvider.H32;
import static omegadrive.vdp.model.BaseVdpProvider.H32_TILES;
import static omegadrive.vdp.model.BaseVdpProvider.H40;
import static omegadrive.vdp.model.BaseVdpProvider.H40_TILES;
import static omegadrive.vdp.model.GenesisVdpProvider.*;
import static omegadrive.vdp.model.GenesisVdpProvider.VdpRegisterName.*;

public interface VdpRenderHandler {

    int ROWS = VDP_VIDEO_ROWS;
    int COLS = VDP_VIDEO_COLS;
    int INDEXES_NUM = ROWS;
    int HOR_SCROLL_SHIFT = 10;
    int WINDOW_TABLE_SHIFT = 10;
    int SPRITE_TABLE_SHIFT = 9;
    int PLANE_A_SHIFT = 10;
    int PLANE_B_SHIFT = 13;
    int PALETTE_INDEX_SHIFT = 5;
    int TILE_HOR_FLIP_MASK = 1 << 11;
    int TILE_VERT_FLIP_MASK = 1 << 12;
    int TILE_PRIORITY_MASK = 1 << 15;
    int TILE_INDEX_MASK = 0x7FF;
    int CELL_WIDTH = 8; //in pixels
    int BYTES_PER_TILE = 4; //32 bit

    void dumpScreenData();

    int[][] renderFrame();

    void setVideoMode(VideoMode videoMode);

    void renderLine(int line);

    void initLineData(int line);

    int[][] getScreenData();

    static int getHorizontalTiles(boolean isH40) {
        return isH40 ? H40_TILES : H32_TILES;
    }

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

    static int maxSpritesPerFrame(boolean isH40) {
        return isH40 ? MAX_SPRITES_PER_FRAME_H40 : MAX_SPRITES_PER_FRAME_H32;
    }

    static int maxSpritesPerLine(boolean isH40) {
        return isH40 ? MAX_SPRITES_PER_LINE_H40 : MAX_SPRITES_PER_LINE_H32;
    }

    static int maxSpritesPixelPerLine(boolean isH40) {
        return isH40 ? H40 : H32;
    }

    static int getHScrollDataLocation(GenesisVdpProvider vdp) {
        //	bit 6 = mode 128k
        return (vdp.getRegisterData(HORIZONTAL_SCROLL_DATA_LOC) & 0x3F) << HOR_SCROLL_SHIFT;
    }

    static int getWindowPlaneNameTableLocation(GenesisVdpProvider vdp, boolean isH40) {
        int reg3 = vdp.getRegisterData(WINDOW_NAMETABLE);
        //	WD11 is ignored if the display resolution is 320px wide (H40),
        // which limits the Window nametable address to multiples of $1000.
        // TODO bit 6 = 128k mode
        int nameTableLocation = isH40 ? reg3 & 0x3C : reg3 & 0x3E;
        return nameTableLocation << WINDOW_TABLE_SHIFT;
    }

    static int getSpriteTableLocation(GenesisVdpProvider vdp) {
        //	AT16 is only valid if 128 KB mode is enabled,
        // and allows for rebasing the Sprite Attribute Table to the second 64 KB of VRAM.
        return (vdp.getRegisterData(SPRITE_TABLE_LOC) & 0x7F) << SPRITE_TABLE_SHIFT;
    }

    void updateSatCache(int satLocation, int vramAddress);

    class TileDataHolder {
        public int tileIndex;
        public boolean horFlip;
        public boolean vertFlip;
        public int paletteLineIndex;
        public boolean priority;

        @Override
        public String toString() {
            return "TileDataHolder{" +
                    "tileIndex=" + tileIndex +
                    ", horFlip=" + horFlip +
                    ", vertFlip=" + vertFlip +
                    ", paletteLineIndex=" + paletteLineIndex +
                    ", priority=" + priority +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TileDataHolder that = (TileDataHolder) o;
            return tileIndex == that.tileIndex &&
                    horFlip == that.horFlip &&
                    vertFlip == that.vertFlip &&
                    paletteLineIndex == that.paletteLineIndex &&
                    priority == that.priority;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tileIndex, horFlip, vertFlip, paletteLineIndex, priority);
        }
    }

    class SpriteDataHolder extends TileDataHolder {
        public int verticalPos;
        public int horizontalPos;
        public int horizontalCellSize;
        public int verticalCellSize;
        public int linkData;

        @Override
        public String toString() {
            return "SpriteDataHolder{" +
                    "verticalPos=" + verticalPos +
                    ", horizontalPos=" + horizontalPos +
                    ", horizontalCellSize=" + horizontalCellSize +
                    ", verticalCellSize=" + verticalCellSize +
                    ", linkData=" + linkData +
                    ", " + super.toString() + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SpriteDataHolder that = (SpriteDataHolder) o;
            return verticalPos == that.verticalPos &&
                    horizontalPos == that.horizontalPos &&
                    horizontalCellSize == that.horizontalCellSize &&
                    verticalCellSize == that.verticalCellSize &&
                    linkData == that.linkData;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), verticalPos, horizontalPos, horizontalCellSize, verticalCellSize, linkData);
        }
    }
}
