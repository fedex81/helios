/*
 * MC68000WrapperDebug
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 12/07/19 20:51
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

import m68k.memory.AddressSpace;
import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CyclicBarrier;
import java.util.stream.IntStream;

public class MC68000WrapperDebug extends MC68000Wrapper {

    private static Logger LOG = LogManager.getLogger(MC68000WrapperDebug.class.getSimpleName());

    public static boolean verbose = false;
    StringBuilder sb = new StringBuilder();

    private CyclicBarrier stepBarrier = new CyclicBarrier(1);
    protected int[] pcList;
    private int lastN = 10;
    private int index = 0;
    private M68kState[] traceArray = new M68kState[lastN];
    private M68kState current = new M68kState();

    private int logAddressAccess = -1;
    private boolean dump = false;

    public MC68000WrapperDebug(GenesisBusProvider busProvider) {
        super(busProvider);
        pcList = new int[0]; //[0xFF_FFFF]; //PC is 24 bits]
        IntStream.range(0, lastN).forEach(i -> traceArray[i] = new M68kState());
    }

    @Override
    public int runInstruction() {
        int res = 0;
        sb.setLength(0);
        try {
            storeM68kState(current);
            res = super.runInstruction();
            printVerbose();
            printCpuStateIfVerbose("");
            handlePostRunState();
            stepBarrier.await();
        } catch (Exception e) {
            LOG.error("68k error", e);
        }
        return res;
    }

    @Override
    protected AddressSpace createAddressSpace() {
        return createDbgAddressSpace(super.createAddressSpace());
    }

    private void handlePostRunState() {
        traceArray[index].sr = current.sr;
        traceArray[index].pc = current.pc;
        traceArray[index].ssp = current.ssp;
        traceArray[index].usp = current.usp;
        IntStream.range(0, 8).forEach(i -> traceArray[index].dr[i] = current.dr[i]);
        IntStream.range(0, 8).forEach(i -> traceArray[index].ar[i] = current.ar[i]);
        traceArray[index].memAccess = sb.toString();
        if (dump) {
            System.out.println(MC68000Helper.dumpInfo(m68k, traceArray[index], addressSpace.size()));
            dump = false;
        }
        index = (index + 1) % lastN;

        if (pcList.length > 0) {
            pcList[currentPC & 0xFF_FFFF]++;
            if (pcList[currentPC & 0xFF_FFFF] == 1) {
//                LOG.info(this::getInfo);
            }
        }
    }

    private void storeM68kState(M68kState state) {
        state.sr = m68k.getSR();
        state.pc = m68k.getPC();
        state.ssp = m68k.getSSP();
        state.usp = m68k.getUSP();
        IntStream.range(0, 8).forEach(i -> state.dr[i] = m68k.getDataRegisterLong(i));
        IntStream.range(0, 8).forEach(i -> state.ar[i] = m68k.getAddrRegisterLong(i));
    }

    private void dumpHistory() {
        final int s = addressSpace.size();
        IntStream.range(0, lastN).forEach(i -> {
            System.out.println(MC68000Helper.dumpInfo(m68k, traceArray[(i + index) % lastN], s));
        });
    }

    @Override
    public boolean raiseInterrupt(int level) {
        m68k.raiseInterrupt(level);
        boolean raise = m68k.getInterruptLevel() == level;
        if (raise) {
//            LOG.info("M68K before INT, level: {}, newLevel: {}", m68k.getInterruptLevel(), level);
        }
        return raise;
    }

    protected void handleException(int vector) {
        if (vector == LEV4_EXCEPTION && vector == LEV6_EXCEPTION) {
            return;
        }
        if (vector == ILLEGAL_ACCESS_EXCEPTION) {
            printCpuState("Exception: " + vector);
            if (STOP_ON_EXCEPTION) {
                setStop(true);
            }
        }
    }

    private AddressSpace createDbgAddressSpace(AddressSpace as) {
        return new MC68000AddressSpace() {
            @Override
            public int readByte(int addr) {
                int res = as.readByte(addr);
                traceAndCheck("READ , ", Size.BYTE, addr, res);
                return res;
            }

            @Override
            public int readWord(int addr) {
                int res = as.readWord(addr);
                traceAndCheck("READ , ", Size.WORD, addr, res);
                return res;
            }

            @Override
            public int readLong(int addr) {
                int res = as.readLong(addr);
                traceAndCheck("READ , ", Size.LONG, addr, res);
                return res;
            }

            @Override
            public void writeByte(int addr, int value) {
                traceAndCheck("WRITE , ", Size.BYTE, addr, value);
                as.writeByte(addr, value);
            }

            @Override
            public void writeWord(int addr, int value) {
                traceAndCheck("WRITE , ", Size.WORD, addr, value);
                as.writeWord(addr, value);
            }

            @Override
            public void writeLong(int addr, int value) {
                traceAndCheck("WRITE , ", Size.LONG, addr, value);
                as.writeLong(addr, value);
            }

            private final void traceAndCheck(String head, Size size, int address, int data) {
                sb.append(head + size + ", " + Util.toHex(address) + ", " + Util.toHex(data) + "\n");
                if (address == logAddressAccess) {
                    dump = true;
                }
            }
        };
    }

    public void doStep() {
        try {
            stepBarrier.await();
        } catch (Exception e) {
            LOG.error("barrier error", e);
        }
    }

    protected void printCpuState(String head) {
        MC68000Helper.printCpuState(m68k, Level.INFO, head, addressSpace.size());
    }

    protected void printCpuStateIfVerbose(String head) {
        if (!verbose) {
            return;
        }
        MC68000Helper.printCpuState(m68k, Level.INFO, head, addressSpace.size());
    }

    protected void printVerbose() {
        if (!verbose) {
            return;
        }
        try {
            String res = MC68000Helper.dumpOp(m68k);
            LOG.info(res);
            if (MC68000Helper.addToInstructionSet(m68k)) {
                LOG.info(MC68000Helper.dumpInstructionSet());
            }
        } catch (Exception e) {
            String pc = Long.toHexString(m68k.getPC() & 0xFF_FFFF);
            LOG.warn("Unable to dump the instruction: " + pc, e);
        }
    }

    static class M68kState {
        public int sr, pc, ssp, usp, opcode;
        public int[] dr = new int[8], ar = new int[8];
        public String memAccess;
    }
}
