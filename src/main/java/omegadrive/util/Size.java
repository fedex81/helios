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

    BYTE(0x80, 0xFF), WORD(0x8000, 0xFFFF), LONG(0x8000_0000L, 0xFFFF_FFFFL);

    long msb;
    long max;

    Size(long msb, long maxSize) {
        this.msb = msb;
        this.max = maxSize;
    }

    public long getMsb() {
        return this.msb;
    }

    public long getMax() {
        return this.max;
    }

    public long getMask() {
        return this.max;
    }

    public static long getMaxFromByteCount(int byteCount) {
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
