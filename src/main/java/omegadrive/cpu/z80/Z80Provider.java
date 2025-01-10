/*
 * Z80Provider
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

package omegadrive.cpu.z80;

import omegadrive.Device;
import omegadrive.bus.model.Z80BusProvider;
import z80core.Z80State;

public interface Z80Provider extends Device {

    enum Interrupt {NMI, IM0, IM1, IM2}

    int executeInstruction();

    boolean interrupt(boolean value);

    void triggerNMI();

    boolean isHalted();

    int readMemory(int address);

    void writeMemory(int address, int data);

    Z80BusProvider getZ80BusProvider();

    void addCyclePenalty(int value);

    void loadZ80State(Z80State z80State);

    Z80State getZ80State();
}
