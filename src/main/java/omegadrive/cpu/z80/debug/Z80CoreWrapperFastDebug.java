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

import java.util.function.Predicate;

public class Z80CoreWrapperFastDebug extends Z80CoreWrapper implements CpuFastDebug.CpuDebugInfoProvider {

    private final static Logger LOG = LogManager.getLogger(Z80CoreWrapperFastDebug.class.getSimpleName());
    private static final int debugMode = Integer.parseInt(System.getProperty("helios.z80.debug.mode", "0"));

    protected Z80Dasm z80Disasm;
    private CpuFastDebug fastDebug;
    private int pc, opcode;

    @Override
    protected Z80CoreWrapper setupInternal(Z80State z80State) {
        super.setupInternal(z80State);
        z80Disasm = new Z80Dasm();
        fastDebug = new CpuFastDebug(this, createContext());
        return this;
    }

    //NOTE: halt sets PC = PC - 1
    @Override
    public int executeInstruction() {
        printDebugMaybe();
        return fastDebug.isBusyLoop(pc, opcode) + super.executeInstruction();
    }

    private void printDebugMaybe() {
        pc = z80Core.getRegPC();
        opcode = memIoOps.fetchOpcode(pc);
        fastDebug.printDebugMaybe();
    }

    private CpuFastDebug.CpuDebugContext createContext() {
        CpuFastDebug.CpuDebugContext ctx = new CpuFastDebug.CpuDebugContext();
        ctx.pcAreas = new int[]{0};
        ctx.pcAreasNumber = 1;
        ctx.pcAreaSize = 0x1_0000;
        ctx.pcAreaShift = 31;
        ctx.pcMask = ctx.pcAreaSize - 1;
        ctx.isLoopOpcode = isLoopOpcode;
        ctx.isIgnoreOpcode = isIgnoreOpcode;
        ctx.debugMode = debugMode;
        return ctx;
    }

    @Override
    public String getInstructionOnly(int pc) {
        return Z80Helper.dumpInfo(z80Disasm, memIoOps, pc);
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

    public static final Predicate<Integer> isLoopOpcode = op -> {
        int byte2 = 0;
        if (op == 0xCB || op == 0xDD || op == 0xED || op == 0xFD) { //TODO limitation
            return false;
        }
        return Z80Helper.isBusyLoop(op, byte2);
    };

    //TODO limitation
    public static final Predicate<Integer> isIgnoreOpcode = op -> op == 0xCB || op == 0xDD || op == 0xED || op == 0xFD;
}