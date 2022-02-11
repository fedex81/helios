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

package omegadrive.cpu.m68k.debug;

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.cpu.CpuFastDebug;
import omegadrive.cpu.CpuFastDebug.CpuDebugContext;
import omegadrive.cpu.m68k.MC68000Helper;
import omegadrive.cpu.m68k.MC68000Wrapper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MC68000WrapperFastDebug extends MC68000Wrapper implements CpuFastDebug.CpuDebugInfoProvider {

    private static final Logger LOG = LogManager.getLogger(MC68000WrapperFastDebug.class.getSimpleName());

    private CpuFastDebug fastDebug;
    private int opcode;

    public MC68000WrapperFastDebug(GenesisBusProvider busProvider) {
        super(busProvider);
        fastDebug = new CpuFastDebug(this, createContext());
        fastDebug.debugMode = CpuFastDebug.DebugMode.NONE;
        init();
    }

    private CpuDebugContext createContext() {
        CpuDebugContext ctx = new CpuDebugContext();
        ctx.pcAreas = new int[]{0, 1, 2, 3, 8, 9, 0xF};
        ctx.pcAreasNumber = 0x10;
        ctx.pcAreaSize = 0x10_0000;
        ctx.pcAreaShift = 20;
        ctx.pcMask = 0xFF_FFFF;
        return ctx;
    }

    @Override
    public int runInstruction() {
        int res = 0;
        try {
            currentPC = m68k.getPC(); //needs to be set
            opcode = m68k.readMemoryWord(currentPC);
            fastDebug.printDebugMaybe();
            fastDebug.isBusyLoop(currentPC, opcode);
            res = super.runInstruction();

        } catch (Exception e) {
            LOG.error("68k error", e);
        }
        return res;
    }

    protected void printCpuState(String head) {
        MC68000Helper.printCpuState(m68k, Level.INFO, head, addressSpace.size());
    }

    @Override
    public String getCpuState(String head) {
        return MC68000Helper.getCpuState(m68k, head, addressSpace.size());
    }

    @Override
    public int getPc() {
        return currentPC;
    }

    @Override
    public int getOpcode() {
        return opcode;
    }

    @Override
    public String getInstructionOnly() {
        try {
            return MC68000Helper.dumpOp(m68k);
        } catch (Exception e) {
            String pc = Long.toHexString(m68k.getPC() & 0xFF_FFFF);
            LOG.warn("Unable to dump the instruction: {}", pc, e);
        }
        return "????";
    }
}