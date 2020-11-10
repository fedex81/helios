/*
 * MsxMapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 13:52
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

package omegadrive.cart.mapper.msx;

import omegadrive.cart.mapper.RomMapper;
import omegadrive.memory.IMemoryProvider;

public class MsxMapper {

    public static RomMapper getMapper(String name, IMemoryProvider memoryProvider) {
        RomMapper mapper = RomMapper.NO_OP_MAPPER;
        if (RomMapper.NO_MAPPER_NAME.equalsIgnoreCase(name)) {
            return mapper;
        }
        mapper = MsxAsciiMapper.createMapper(memoryProvider.getRomData(), name);
        if (mapper != RomMapper.NO_OP_MAPPER) {
            return mapper;
        }
        mapper = KonamiMapper.createMapper(memoryProvider.getRomData(), name);
        return mapper;
    }
}
