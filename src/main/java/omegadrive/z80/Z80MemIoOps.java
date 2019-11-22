/*
 * Z80MemIoOps
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

package omegadrive.z80;

import omegadrive.bus.BaseBusProvider;
import omegadrive.memory.IMemoryRam;
import omegadrive.util.Size;
import z80core.MemIoOps;

public class Z80MemIoOps extends MemIoOps {

    private BaseBusProvider z80BusProvider;
    private long tstatesCount = 0;
    private boolean activeInterrupt;
    private int[] ram;
    private int ramSizeMask;

    public static Z80MemIoOps createGenesisInstance(BaseBusProvider z80BusProvider) {
        Z80MemIoOps m = new Z80MemIoOps();
        m.z80BusProvider = z80BusProvider;
        IMemoryRam mem = z80BusProvider.getDeviceIfAny(IMemoryRam.class).
                orElseThrow(() -> new RuntimeException("Invalid setup"));
        m.ram = mem.getRamData();
        m.ramSizeMask = m.ram.length - 1;
        return m;
    }

    public static Z80MemIoOps createInstance(BaseBusProvider z80BusProvider) {
        Z80MemIoOps m = new Z80MemIoOps() {
            @Override
            public int fetchOpcode(int address) {
                return fetchOpcodeBus(address);
            }
        };
        m.z80BusProvider = z80BusProvider;
        return m;
    }

    protected final int fetchOpcodeBus(int address) {
        tstatesCount += 4;
        return (int) z80BusProvider.read(address, Size.BYTE) & 0xFF;
    }

    @Override
    public int fetchOpcode(int address) {
        tstatesCount += 4;
        address &= ramSizeMask;
        return ram[address];
    }

    @Override
    public int peek8(int address) {
        tstatesCount += 3;
        return (int) z80BusProvider.read(address, Size.BYTE) & 0xFF;
    }

    @Override
    public void poke8(int address, int value) {
        tstatesCount += 3;
        z80BusProvider.write(address, value, Size.BYTE);
    }

    @Override
    public int inPort(int port) {
        tstatesCount += 4;
        return z80BusProvider.readIoPort(port) & 0xFF;
    }

    @Override
    public void outPort(int port, int value) {
        tstatesCount += 4;
        z80BusProvider.writeIoPort(port, value);
    }

    @Override
    public boolean isActiveINT() {
        boolean res = activeInterrupt;
        //TODO this is needed for Gen .ie Sonic 2
        activeInterrupt = false;
        return res;
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        this.tstatesCount += tstates;
    }

    @Override
    public void interruptHandlingTime(int tstates) {
        this.tstatesCount += tstates;
    }

    @Override
    public long getTstates() {
        return tstatesCount;
    }

    @Override
    public void reset() {
        tstatesCount = 0;
    }

    public void setActiveInterrupt(boolean activeInterrupt) {
        this.activeInterrupt = activeInterrupt;
    }
}
