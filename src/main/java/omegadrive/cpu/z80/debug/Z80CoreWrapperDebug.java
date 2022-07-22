/*
 * Z80CoreWrapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 15:05
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

package omegadrive.cpu.z80.debug;

import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Helper;
import omegadrive.cpu.z80.Z80MemIoOps;
import omegadrive.cpu.z80.disasm.Z80Dasm;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import z80core.Z80State;

import java.util.stream.IntStream;


/**
 * Slow debugger, monitors memory accesses
 */
public class Z80CoreWrapperDebug extends Z80CoreWrapperFastDebug {

    private final static Logger LOG = LogHelper.getLogger(Z80CoreWrapperDebug.class.getSimpleName());
    public static boolean verbose = false;

    protected Z80Dasm z80Disasm;
    protected int logAddressAccess = -1;
    protected StringBuilder sb = new StringBuilder();
    private int lastN = 100;
    private int index = 0;
    private Z80Helper.Z80StateExt current = new Z80Helper.Z80StateExt();
    private Z80Helper.Z80StateExt[] traceArray = new Z80Helper.Z80StateExt[lastN];
    private Z80MemIoOps.Z80MemIoOpsDbg memIoOpsDbg;
    private boolean m68kActivity = false;

    @Override
    protected Z80CoreWrapper setupInternal(Z80State z80State) {
        super.setupInternal(z80State);
        IntStream.range(0, lastN).forEach(i -> traceArray[i] = new Z80Helper.Z80StateExt());
        z80Disasm = new Z80Dasm();
        return this;
    }

    //NOTE: halt sets PC = PC - 1
    @Override
    public int executeInstruction() {
//        handlePreRunState();
        //      dumpHistory();
        // dumpCurrent()
        sb.setLength(0); //avoid mem leak
        LOG.info(z80Disasm.disassemble(z80Core.getRegPC(), memIoOps));
        int res = super.executeInstruction();
        handlePostRunState();
        return res;
    }

    private void handlePreRunState() {
        traceArray[index].memAccess = sb.toString(); //save mem access between prev instr and now
        if (m68kActivity) {
//            dumpCurrent();
            m68kActivity = false;
        }
        sb.setLength(0);
        Z80Helper.getZ80State(z80Core, current);
    }

    private void handlePostRunState() {
        index = (index + 1) % lastN;
        Z80Helper.copyState(current, traceArray[index]);
    }

    private void dumpHistory() {
        IntStream.range(0, lastN).forEach(i -> {
            int idx = (lastN + (index - i)) % lastN;
            System.out.println(idx + ":" + Z80Helper.toStringExt(traceArray[idx], z80Disasm, memIoOpsDbg));
        });
    }

    private void dumpCurrent() {
        System.out.println(index + "\n" + Z80Helper.toStringExt(traceArray[index], z80Disasm, memIoOpsDbg));
    }

    @Override
    public int readMemory(int address) {
        m68kActivity = true;
        return memIoOpsDbg.peek8Ext(address);
    }

    @Override
    public void writeMemory(int address, int data) {
        memIoOpsDbg.poke8Ext(address, data);
        m68kActivity = true;
    }
}