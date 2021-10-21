/*
 * MekaSavestateVersion
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/06/19 17:15
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

package omegadrive.savestate;

public enum MekaSavestateVersion {
    VER_B_OR_EARLIER(-1, 0xB, 0x7, 0x63),
    VER_C(0, 0xC, 0xB, 0x67),
    VER_D(0, 0xD, 0xB, 0x69),
    VER_E_OR_LATER(1, 0xE, 0xB, 0x69);

    public final static int VERSION_POS = 5;
    public final static int CRC_POS = 7;
    final static int EQ = 0, LESS = -1, MORE = 1;
    private final int endHeaderPos;
    private final int memoryStartPos;
    private final int version;
    private final int orderToken;

    MekaSavestateVersion(int orderToken, int version, int endHeaderPos, int memoryStartPos) {
        this.endHeaderPos = endHeaderPos;
        this.orderToken = orderToken;
        this.version = version;
        this.memoryStartPos = memoryStartPos;
    }

    public static MekaSavestateVersion getMekaVersion(int version) {
        for (MekaSavestateVersion v : MekaSavestateVersion.values()) {
            if (v.orderToken == EQ && v.version == version) {
                return v;
            }
        }
        for (MekaSavestateVersion v : MekaSavestateVersion.values()) {
            if (v.orderToken != EQ) {
                boolean match = v.orderToken > EQ ? version >= v.version : version <= v.version;
                if (match) {
                    return v;
                }
            }
        }
        return null;
    }

    public int getEndHeaderPos() {
        return endHeaderPos;
    }

    public int getMemoryStartPos() {
        return memoryStartPos;
    }

    public int getMemoryEndPos() {
        return memoryStartPos + 0x6020;
    }

    public int getVersion() {
        return version;
    }
}
