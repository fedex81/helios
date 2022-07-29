/*
 * GenesisMapper
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

package omegadrive.cart.mapper.md;

public enum MdMapperType {
    SEGA_SSF("SEGA SSF"),
    SEGA_SSF2("SEGA SSF2"),
    SEGA_EVERDRIVE("SEGA EVERDRIVE");

    String name;

    MdMapperType(String s) {
        this.name = s;
    }

    public static MdMapperType getMdMapperType(String systemType) {
        for (MdMapperType m : MdMapperType.values()) {
            if (m.name.equalsIgnoreCase(systemType)) {
                return m;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }
}