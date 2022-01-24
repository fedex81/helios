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

package omegadrive.cpu.z80;

import omegadrive.bus.model.BaseBusProvider;
import omegadrive.bus.model.GenesisZ80BusProvider;
import omegadrive.memory.IMemoryRam;
import omegadrive.util.Size;
import omegadrive.util.Util;
import z80core.IMemIoOps;

public class Z80MemIoOps implements IMemIoOps {

    private BaseBusProvider z80BusProvider;
    private long tstatesCount = 0;
    private boolean activeInterrupt;
    private int[] ram;
    private int ramSizeMask;
    private int pcUpperLimit = 0xFFFF;
    public int lastFetch;

    public static Z80MemIoOps createGenesisInstance(BaseBusProvider z80BusProvider) {
        return createGenesisInstanceInternal(new Z80MemIoOps(), z80BusProvider);
    }

    public static Z80MemIoOps createDebugGenesisInstance(BaseBusProvider z80BusProvider, StringBuilder sb, int logAddressAccess) {
        return createGenesisInstanceInternal(createDbgMemIoOps(sb, logAddressAccess), z80BusProvider);
    }

    private static Z80MemIoOps createGenesisInstanceInternal(Z80MemIoOps m, BaseBusProvider z80BusProvider) {
        m.z80BusProvider = z80BusProvider;
        IMemoryRam mem = z80BusProvider.getBusDeviceIfAny(IMemoryRam.class).
                orElseThrow(() -> new RuntimeException("Invalid setup"));
        m.ram = mem.getRamData();
        m.ramSizeMask = m.ram.length - 1;
        m.pcUpperLimit = GenesisZ80BusProvider.END_RAM;
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

    public static Z80MemIoOpsDbg createDbgMemIoOps(StringBuilder sb, int logAddressAccess) {
        return new Z80MemIoOpsDbg() {
            @Override
            public int fetchOpcode(int address) {
                int res = super.fetchOpcode(address);
                traceAndCheck("READ , ", Size.BYTE, address, res);
                return res;
            }

            @Override
            public int peek8(int address) {
                int res = super.peek8(address);
                traceAndCheck("READ , ", Size.BYTE, address, res);
                return res;
            }

            @Override
            public int peek16(int address) {
                int res = (super.peek8(address + 1) << 8) | super.peek8(address);
                traceAndCheck("READ , ", Size.WORD, address, res);
                return res;
            }

            @Override
            public void poke8(int address, int value) {
                traceAndCheck("WRITE, ", Size.BYTE, address, value);
                super.poke8(address, value);
            }

            @Override
            public int peek8Ext(int address) {
                int res = super.peek8(address);
                traceAndCheck("68k READ , ", Size.BYTE, address, res);
                return res;
            }

            @Override
            public void poke8Ext(int address, int value) {
                traceAndCheck("68k WRITE, ", Size.BYTE, address, value);
                super.poke8(address, value);
            }

            @Override
            public void poke16(int address, int word) {
                traceAndCheck("WRITE, ", Size.WORD, address, word);
                super.poke8(address, word);
                super.poke8(address + 1, word >>> 8);
            }

            private final void traceAndCheck(String head, Size size, int address, int data) {
                sb.append(head + size + ", " + Util.toHex(address) + ", " + Util.toHex(data) + "\n");
                if (logAddressAccess >= 0 && address == logAddressAccess) {
                    //do something
                }
            }
        };
    }

    protected final int fetchOpcodeBus(int address) {
        tstatesCount += 4;
        lastFetch = (int) z80BusProvider.read(address, Size.BYTE) & 0xFF;
        return lastFetch;
    }

    @Override
    public int getPcUpperLimit() {
        return pcUpperLimit;
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
        return activeInterrupt;
    }

    @Override
    public boolean setActiveINT(boolean value) {
        activeInterrupt = value;
        return true;
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

    /**
     * Defaults on fetching the opcode from RAM only (MD mode)
     */
    @Override
    public int fetchOpcode(int address) {
        tstatesCount += 4;
        address &= ramSizeMask;
        return ram[address];
    }

    public static abstract class Z80MemIoOpsDbg extends Z80MemIoOps {
        public abstract void poke8Ext(int address, int value);

        public abstract int peek8Ext(int address);
    }
}
