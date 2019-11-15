/*
 * M68kProvider
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

package omegadrive.m68k;

import omegadrive.Device;

public interface M68kProvider extends Device {

    int VBLANK_INTERRUPT_LEVEL = 6;
    int HBLANK_INTERRUPT_LEVEL = 4;
    int ILLEGAL_ACCESS_EXCEPTION = 4;
    int LEV4_EXCEPTION = 24 + HBLANK_INTERRUPT_LEVEL;
    int LEV6_EXCEPTION = 24 + VBLANK_INTERRUPT_LEVEL;

    long getPC();

    boolean isStopped();

    boolean raiseInterrupt(int level);

    int runInstruction();

    void addCyclePenalty(int value);

    default String getInfo(){
        return "NOT SUPPORTED";
    }
}
