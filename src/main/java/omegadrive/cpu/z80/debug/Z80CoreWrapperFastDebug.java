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

import omegadrive.cpu.CpuFastDebug;
import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Helper;
import omegadrive.cpu.z80.disasm.Z80Dasm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80State;

public class Z80CoreWrapperFastDebug extends Z80CoreWrapper implements CpuFastDebug.CpuDebugInfoProvider {

    private final static Logger LOG = LogManager.getLogger(Z80CoreWrapperFastDebug.class.getSimpleName());

    protected Z80Dasm z80Disasm;
    private CpuFastDebug fastDebug;
    private int pc, opcode;

    @Override
    protected Z80CoreWrapper setupInternal(Z80State z80State) {
        super.setupInternal(z80State);
        z80Disasm = new Z80Dasm();
        fastDebug = new CpuFastDebug(this, createContext());
        fastDebug.debugMode = CpuFastDebug.DebugMode.NEW_INST_ONLY;
        return this;
    }

    //NOTE: halt sets PC = PC - 1
    @Override
    public int executeInstruction() {
        pc = z80Core.getRegPC();
        opcode = memIoOps.fetchOpcode(pc);
        fastDebug.printDebugMaybe();
        int res = super.executeInstruction();
        return res;
    }

    private CpuFastDebug.CpuDebugContext createContext() {
        CpuFastDebug.CpuDebugContext ctx = new CpuFastDebug.CpuDebugContext();
        ctx.pcAreas = new int[]{0};
        ctx.pcAreasNumber = 1;
        ctx.pcAreaSize = 0x1_0000;
        ctx.pcAreaShift = 31;
        ctx.pcMask = ctx.pcAreaSize - 1;
        return ctx;
    }

    @Override
    public String getInstructionOnly() {
        return Z80Helper.dumpInfo(z80Disasm, memIoOps, z80Core.getRegPC());
    }

    @Override
    public String getCpuState(String head) {
        return head + getInstructionOnly() + "\n" + Z80Helper.toString(z80Core.getZ80State());
    }

    @Override
    public int getPc() {
        return pc;
    }

    @Override
    public int getOpcode() {
        return opcode;
    }
}