/*
 * Size
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/05/19 16:16
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

public enum Size {

    BYTE(0x80, 0xFF), WORD(0x8000, 0xFFFF), LONG(0x8000_0000, 0xFFFF_FFFF);

    public static final Size[] vals = Size.values();

    final int msb;
    final int max;
    final int byteSize;

    Size(int msb, int maxSize) {
        this.msb = msb;
        this.max = maxSize;
        this.byteSize = Math.max(1, ordinal() << 1);
    }

    public int getMsb() {
        return this.msb;
    }

    public int getMax() {
        return this.max;
    }

    public int getMask() {
        return this.max;
    }

    public int getByteSize() {
        return byteSize;
    }

    public static int getMaxFromByteCount(int byteCount) {
        switch (byteCount) {
            case 1:
                return BYTE.max;
            case 2:
                return WORD.max;
            case 4:
                return LONG.max;
        }
        return 0;
    }
}
