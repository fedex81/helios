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

import java.util.function.Predicate;

import static omegadrive.util.Util.th;

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

    public static CpuDebugContext createContext() {
        CpuDebugContext ctx = new CpuDebugContext();
        ctx.pcAreas = new int[]{0, 1, 2, 3, 8, 9, 0xF};
        ctx.pcAreasNumber = 0x10;
        ctx.pcAreaSize = 0x10_0000;
        ctx.pcAreaShift = 20;
        ctx.pcMask = 0xF_FFFF;
        ctx.isLoopOpcode = isLoopOpcode;
        ctx.isIgnoreOpcode = isIgnoreOpcode;
        return ctx;
    }

    @Override
    public int runInstruction() {
        //printDebugMaybe();
        return fastDebug.isBusyLoop(m68k.getPC() & 0xFF_FFFF, m68k.getOpcode()) + super.runInstruction();
    }

    private void printDebugMaybe() {
        currentPC = m68k.getPC(); //needs to be set
        opcode = getOpcode();
        fastDebug.printDebugMaybe();
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
    public String getInstructionOnly(int pc) {
        try {
            return MC68000Helper.dumpOp(m68k, pc);
        } catch (Exception e) {
            LOG.warn("Unable to dump the instruction: {}", th(pc & 0xFF_FFFF), e);
        }
        return "????";
    }

    private static final Predicate<Integer> isBranch = op -> (op & 0x6000) == 0x6000 || (op & 0xFFC0) == 0x4ec0;
    //btst     #imm,dX; btst     #imm,addr; btst     dX,(aY)
    private static final Predicate<Integer> isTest = op -> (op & 0xFFC0) == 0x800 || (op & 0xFF00) == 0x4a00 || (op & 0xFFC0) == 0x500;
    //move   (aX),dY; move #imm, dX; move   n(aX),dY
    private static final Predicate<Integer> isMov = op -> (op & 0xC1F8) == 0x10 || (op & 0xC1F8) == 0x38 || (op & 0xC1F8) == 0x28;
    //andi   #imm,dX;  and    (aX),dY
    private static final Predicate<Integer> isAndi = op -> (op & 0xFF38) == 0x200 || (op & 0xF138) == 0xC010 || (op & 0xF138) == 0xC038;
    //cmpi   #imm, val;  cmp #imm, (aX); cmp #imm, dX; cmp n(aX), dY, cmp (aX), dY
    private static final Predicate<Integer> isCmp = op -> (op & 0xFF38) == 0xC38 || (op & 0xFF38) == 0xC28 || (op & 0xFF38) == 0xC00
            || (op & 0xFF38) == 0xC10 || (op & 0xF138) == 0xB038 || (op & 0xF138) == 0xB028 || (op & 0xF138) == 0xB010;
    private static final Predicate<Integer> isNop = op -> op == 0x4e71;

    public static final Predicate<Integer> isLoopOpcode = isBranch.or(isTest).or(isMov).or(isAndi).or(isCmp).or(isNop);
    public static final Predicate<Integer> isIgnoreOpcode = op ->
            (op & 0xF0F8) == 0x50C8 ||   //dbcc
                    (op & 0xFF00) == 0x400 ||   //subi
                    ((op >> 8) & 0xF1) == 0x51 && (op & 0xC0) != 0xC0       //subq
            ;
}