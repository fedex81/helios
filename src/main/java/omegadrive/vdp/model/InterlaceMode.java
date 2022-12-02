/*
 * InterlaceMode
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 13/07/19 19:02
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

public enum InterlaceMode {
    NONE,
    MODE_1,
    INVALID,
    MODE_2(1);

    public static final InterlaceMode[] lookup = InterlaceMode.values();

    private int tileShift = 5;
    private int verticalScrollShift = 0;
    private int verticalCellPixelSize = 8;
    private int interlaceAdjust = 0;
    private int tileIndexMask = 0xFFFF;

    InterlaceMode() {
    }

    InterlaceMode(int interlaceAdjust) {
        this.interlaceAdjust = interlaceAdjust;
        this.tileShift = tileShift + interlaceAdjust;
        this.verticalScrollShift = interlaceAdjust;
        this.verticalCellPixelSize = verticalCellPixelSize << interlaceAdjust;
        this.tileIndexMask = 0xFFFF - interlaceAdjust;
    }

    public static InterlaceMode getInterlaceMode(int index) {
        return lookup[index];
    }

    public int tileShift() {
        return tileShift;
    }

    public int interlaceAdjust() {
        return interlaceAdjust;
    }

    public int verticalScrollShift() {
        return verticalScrollShift;
    }

    public int getVerticalCellPixelSize() {
        return verticalCellPixelSize;
    }

    public int getTileIndexMask() {
        return tileIndexMask;
    }

    public boolean isInterlaced() {
        return this == MODE_1 || this == MODE_2;
    }
}
