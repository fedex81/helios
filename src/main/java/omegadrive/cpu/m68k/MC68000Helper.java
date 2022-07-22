/*
 * MC68000Helper
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

import com.google.common.base.Strings;
import m68k.cpu.Cpu;
import m68k.cpu.DisassembledInstruction;
import m68k.cpu.Instruction;
import m68k.cpu.MC68000;
import m68k.cpu.instructions.TAS;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static omegadrive.util.Util.th;

public class MC68000Helper {

    private final static Logger LOG = LogHelper.getLogger(MC68000Helper.class.getSimpleName());

    public static final boolean STOP_ON_EXCEPTION;
    public static final boolean GENESIS_TAS_BROKEN;
    public static final boolean M68K_DEBUG;
    public final static int OVERCLOCK_FACTOR;

    static {
        STOP_ON_EXCEPTION =
                Boolean.parseBoolean(System.getProperty("68k.stop.on.exception", "false"));
        GENESIS_TAS_BROKEN = Boolean.parseBoolean(System.getProperty("68k.broken.tas", "true"));
        M68K_DEBUG = Boolean.parseBoolean(System.getProperty("68k.debug", "false"));
        OVERCLOCK_FACTOR = Integer.parseInt(System.getProperty("68k.overclock.factor", "0"));
        if (GENESIS_TAS_BROKEN != TAS.EMULATE_BROKEN_TAS) {
            LOG.info("Overriding 68k TAS broken setting: {}", GENESIS_TAS_BROKEN);
        }
        if (M68K_DEBUG) {
            LOG.info("68k debug mode: true");
        }
        if (OVERCLOCK_FACTOR > 0) {
            LOG.info("68k overclock factor: {}", OVERCLOCK_FACTOR);
        }
    }

    private static Set<String> instSet = new TreeSet<>();

    private static StringBuilder dumpOp(StringBuilder sb, Cpu cpu, int pc, int opcode) {
        int wrapPc = pc & 0xFF_FFFF; //PC is 24 bits
        if (wrapPc >= 0) {
            Instruction i = cpu.getInstructionFor(opcode);
            DisassembledInstruction di = i.disassemble(wrapPc, opcode);
            di.formatInstruction(sb);
//            di.shortFormat(this.buffer);
        } else {
            sb.append(String.format("%08x   ????", wrapPc));
        }
        return sb;
    }

    private static StringBuilder dumpOp(StringBuilder sb, Cpu cpu, int pc) {
        int wrapPc = pc & 0xFF_FFFF; //PC is 24 bits
        if (wrapPc >= 0) {
            int opcode = cpu.readMemoryWord(wrapPc);
            Instruction i = cpu.getInstructionFor(opcode);
            DisassembledInstruction di = i.disassemble(wrapPc, opcode);
            di.formatInstruction(sb);
//            di.shortFormat(this.buffer);
        } else {
            sb.append(String.format("%08x   ????", wrapPc));
        }
        return sb;
    }

    public static String dumpOp(Cpu cpu, int pc, int opcode) {
        return dumpOp(new StringBuilder(), cpu, pc, opcode).toString();
    }

    public static String dumpOp(Cpu cpu, int pc) {
        assert cpu.getPC() == pc;
        return dumpOp(new StringBuilder(), cpu, pc).toString();
    }

    public static String dumpInfo(Cpu cpu, int pc, boolean showBytes, int memorySize) {
        StringBuilder sb = new StringBuilder("\n");
        int wrapPc = pc & 0xFF_FFFF; //PC is 24 bits

        sb.append(String.format("D0: %08x   D4: %08x   A0: %08x   A4: %08x     PC:  %08x\n",
                cpu.getDataRegisterLong(0), cpu.getDataRegisterLong(4), cpu.getAddrRegisterLong(0),
                cpu.getAddrRegisterLong(4), wrapPc));
        sb.append(String.format("D1: %08x   D5: %08x   A1: %08x   A5: %08x     SR:  %04x %s\n",
                cpu.getDataRegisterLong(1), cpu.getDataRegisterLong(5), cpu.getAddrRegisterLong(1),
                cpu.getAddrRegisterLong(5), cpu.getSR(), makeFlagView(cpu)));
        sb.append(String.format("D2: %08x   D6: %08x   A2: %08x   A6: %08x     USP: %08x\n",
                cpu.getDataRegisterLong(2), cpu.getDataRegisterLong(6), cpu.getAddrRegisterLong(2),
                cpu.getAddrRegisterLong(6), cpu.getUSP()));
        sb.append(String.format("D3: %08x   D7: %08x   A3: %08x   A7: %08x     SSP: %08x\n\n",
                cpu.getDataRegisterLong(3), cpu.getDataRegisterLong(7), cpu.getAddrRegisterLong(3),
                cpu.getAddrRegisterLong(7), cpu.getSSP()));
        StringBuilder sb2 = new StringBuilder();
        if (wrapPc >= 0 && wrapPc < memorySize) {
            int opcode = cpu.readMemoryWord(wrapPc);
            Instruction i = cpu.getInstructionFor(opcode);
            DisassembledInstruction di = i.disassemble(wrapPc, opcode);
            if (showBytes) {
                di.formatInstruction(sb2);
            } else {
                di.shortFormat(sb2);
            }
        } else {
            sb2.append(String.format("%08x   ????", wrapPc));
        }
        return sb.append(String.format("\n==> %s\n\n", sb2)).toString();
    }

    public static String dumpInfo(Cpu cpu, M68kState state, int memorySize) {
        StringBuilder sb = new StringBuilder("\n");
        int wrapPc = state.pc & 0xFF_FFFF;
        sb.append("D0: " + th(state.dr[0]) +
                "   D4: " + th(state.dr[4]) +
                "   A0: " + th(state.ar[0]) +
                "   A4: " + th(state.ar[4]) +
                "     PC: " + th(wrapPc) + "\n");
        sb.append("D1: " + th(state.dr[1]) +
                "   D5: " + th(state.dr[5]) +
                "   A1: " + th(state.ar[1]) +
                "   A5: " + th(state.ar[5]) +
                "     SR: " + toHex(state.sr, 4) + " " + makeFlagView(cpu) + "\n");
        sb.append("D2: " + th(state.dr[2]) +
                "   D6: " + th(state.dr[6]) +
                "   A2: " + th(state.ar[2]) +
                "   A6: " + th(state.ar[6]) +
                "    USP: " + th(state.usp) + "\n");
        sb.append("D3: " + th(state.dr[3]) +
                "   D7: " + th(state.dr[7]) +
                "   A3: " + th(state.ar[3]) +
                "   A7: " + th(state.ar[7]) +
                "    SSP: " + th(state.ssp) + "\n");
        StringBuilder sb2 = new StringBuilder();
        if (wrapPc >= 0 && wrapPc < memorySize) {
            int opcode = cpu.readMemoryWord(wrapPc);
            Instruction i = cpu.getInstructionFor(opcode);
            DisassembledInstruction di = i.disassemble(wrapPc, opcode);
            di.formatInstruction(sb2);
        }
        sb.append("\n==> " + sb2 + "\n\n");
        sb.append(state.memAccess);
        return sb.toString();
    }

    public static String toHex(int val, int digits) {
        return Strings.padStart(Integer.toHexString(val), digits, '0');
    }

    protected static String makeFlagView(Cpu cpu) {
        StringBuilder sb = new StringBuilder(5);
        sb.append(cpu.isFlagSet(16) ? 'X' : '-');
        sb.append(cpu.isFlagSet(8) ? 'N' : '-');
        sb.append(cpu.isFlagSet(4) ? 'Z' : '-');
        sb.append(cpu.isFlagSet(2) ? 'V' : '-');
        sb.append(cpu.isFlagSet(1) ? 'C' : '-');
        return sb.toString();
    }

    public static boolean addToInstructionSet(MC68000 cpu) {
        int wrapPc = cpu.getPC() & 0xFF_FFFF; //PC is 24 bits
        int opcode = cpu.readMemoryWord(wrapPc);
        Instruction i = cpu.getInstructionFor(opcode);
        String name = i.getClass().getTypeName();
        String str = name.substring(name.lastIndexOf('.') + 1);
        return instSet.add(str);
    }

    public static String dumpInstructionSet() {
        StringBuilder sb = new StringBuilder();
        sb.append("Instruction set: " + instSet.size() + "\n").append(Arrays.toString(instSet.toArray()));
        return sb.toString();
    }

    public static String getCpuState(Cpu cpu, String head, int memorySize) {
        try {
            return head + MC68000Helper.dumpInfo(cpu, cpu.getPC(), true, memorySize);
        } catch (Exception e) {
            String pc = Long.toHexString(cpu.getPC() & 0xFF_FFFF);
            LOG.warn("Unable to dump the state, pc: {}", pc, e);
        }
        return "????";
    }

    public static void printCpuState(Cpu cpu, String head, int memorySize) {
        LOG.info("{}{}", head, getCpuState(cpu, head, memorySize));
    }

    public static class M68kState {
        public int sr, pc, ssp, usp, opcode;
        public int[] dr = new int[8], ar = new int[8];
        public String memAccess;

        @Override
        public String toString() {
            return "M68kState{" +
                    "sr=" + th(sr) +
                    ", pc=" + th(pc) +
                    ", ssp=" + th(ssp) +
                    ", usp=" + th(usp) +
                    ", opcode=" + th(opcode) +
                    ", dr=" + Arrays.toString(dr) +
                    ", ar=" + Arrays.toString(ar) +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            M68kState state = (M68kState) o;

            if (sr != state.sr) return false;
            if (pc != state.pc) return false;
            if (ssp != state.ssp) return false;
            if (usp != state.usp) return false;
            if (opcode != state.opcode) return false;
            if (!Arrays.equals(dr, state.dr)) return false;
            return Arrays.equals(ar, state.ar);
        }

        @Override
        public int hashCode() {
            int result = sr;
            result = 31 * result + pc;
            result = 31 * result + ssp;
            result = 31 * result + usp;
            result = 31 * result + opcode;
            result = 31 * result + Arrays.hashCode(dr);
            result = 31 * result + Arrays.hashCode(ar);
            return result;
        }
    }
}
