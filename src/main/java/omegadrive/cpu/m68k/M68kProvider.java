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

package omegadrive.cpu.m68k;

import m68k.cpu.Cpu;
import omegadrive.Device;

public interface M68kProvider extends Device {

    //M68kVectors.LEV6_VECTOR_30.pos - EXCEPTION_OFFSET;
    int VBLANK_INTERRUPT_LEVEL = 6;

    //M68kVectors.LEV4_VECTOR_28.pos - EXCEPTION_OFFSET;
    int HBLANK_INTERRUPT_LEVEL = 4;

    //PC is 24 bits
    int MD_PC_MASK = Cpu.PC_MASK;

    int getPC();

    boolean isStopped();

    /**
     * @return has the interrupt level changed?
     */
    boolean raiseInterrupt(int level);

    int runInstruction();

    void addCyclePenalty(int value);

    void softReset();

    default int getPrefetchWord() {
        return 0;
    }

    default String getInfo() {
        return "NOT SUPPORTED";
    }
}
