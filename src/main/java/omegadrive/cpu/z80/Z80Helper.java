package omegadrive.cpu.z80;

import omegadrive.bus.model.Z80BusProvider;
import omegadrive.cpu.CpuBusyLoopDetection;
import omegadrive.cpu.z80.disasm.Z80Dasm;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.ArrayEndianUtil;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import z80core.IMemIoOps;
import z80core.MemIoOps;
import z80core.Z80;
import z80core.Z80State;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Z80Helper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class Z80Helper {

    private final static Logger LOG = LogHelper.getLogger(Z80Helper.class.getSimpleName());

    private static final Z80.IntMode[] values = Z80.IntMode.values();
    public static final boolean[][] isBusyOpcode = generateBLOpcodes();

    public static Z80.IntMode parseIntMode(int ordinal) {
        return ordinal < values.length ? values[ordinal] : null;
    }

    public static String toStringExt(Z80StateExt state, Z80Dasm disasm, IMemIoOps memIoOps) {
        String sb = toString(state) + "\n\n" +
                dumpInfo(disasm, memIoOps, state.getRegPC()) + "\n" +
                state.memAccess;
        return sb;
    }

    public static String toString(Z80State state) {
        String str = "\n";
        str += String.format("SP: %04x   PC: %04x  I : %02x   R : %02x  IX: %04x  IY: %04x\n",
                state.getRegSP(), state.getRegPC(), state.getRegI(), state.getRegR(), state.getRegIX(), state.getRegIY());
        str += String.format("A : %02x   B : %02x  C : %02x   D : %02x  E : %02x  F : %02x   L : %02x   H : %02x\n",
                state.getRegA(), state.getRegB(),
                state.getRegC(), state.getRegD(), state.getRegE(), state.getRegF(), state.getRegL(), state.getRegH());
        str += String.format("Ax: %02x   Bx: %02x  Cx: %02x   Dx: %02x  Ex: %02x  Fx: %02x   Lx: %02x   Hx: %02x\n",
                state.getRegAx(), state.getRegBx(),
                state.getRegCx(), state.getRegDx(), state.getRegEx(), state.getRegFx(), state.getRegLx(), state.getRegHx());
        str += String.format("AF : %04x   BC : %04x  DE : %04x   HL : %04x\n",
                state.getRegAF(), state.getRegBC(), state.getRegDE(), state.getRegHL());
        str += String.format("AFx: %04x   BCx: %04x  DEx: %04x   HLx: %04x\n",
                state.getRegAFx(), state.getRegBCx(), state.getRegDEx(), state.getRegHLx());
        str += String.format("IM: %s  iff1: %s  iff2: %s  memPtr: %04x  flagQ: %s\n",
                state.getIM().name(), state.isIFF1(), state.isIFF2(), state.getMemPtr(), state.isFlagQ());
        str += String.format("NMI: %s  INTLine: %s  pendingE1: %s\n", state.isNMI(), state.isINTLine(),
                state.isPendingEI());
        return str;
    }

    public static Z80State copyState(Z80State z, Z80State state) {
        state.setRegA(z.getRegA());
        state.setRegF(z.getRegF());
        state.setRegB(z.getRegB());
        state.setRegC(z.getRegC());
        state.setRegD(z.getRegD());
        state.setRegE(z.getRegE());
        state.setRegH(z.getRegH());
        state.setRegL(z.getRegL());
        state.setRegAx(z.getRegAx());
        state.setRegFx(z.getRegFx());
        state.setRegBx(z.getRegBx());
        state.setRegCx(z.getRegCx());
        state.setRegDx(z.getRegDx());
        state.setRegEx(z.getRegEx());
        state.setRegHx(z.getRegHx());
        state.setRegLx(z.getRegLx());
        state.setRegIX(z.getRegIX());
        state.setRegIY(z.getRegIY());
        state.setRegSP(z.getRegSP());
        state.setRegPC(z.getRegPC());
        state.setRegI(z.getRegI());
        state.setRegR(z.getRegR());
        state.setMemPtr(z.getMemPtr());
        state.setHalted(z.isHalted());
        state.setIFF1(z.isIFF1());
        state.setIFF2(z.isIFF2());
        state.setIM(z.getIM());
        state.setINTLine(z.isINTLine());
        state.setPendingEI(z.isPendingEI());
        state.setNMI(z.isNMI());
        return state;
    }

    public static Z80State getZ80State(Z80 z, Z80State state) {
        state.setRegA(z.getRegA());
        state.setRegF(z.getFlags());
        state.setRegB(z.getRegB());
        state.setRegC(z.getRegC());
        state.setRegD(z.getRegD());
        state.setRegE(z.getRegE());
        state.setRegH(z.getRegH());
        state.setRegL(z.getRegL());
        state.setRegAx(z.getRegAx());
        state.setRegFx(z.getRegFx());
        state.setRegBx(z.getRegBx());
        state.setRegCx(z.getRegCx());
        state.setRegDx(z.getRegDx());
        state.setRegEx(z.getRegEx());
        state.setRegHx(z.getRegHx());
        state.setRegLx(z.getRegLx());
        state.setRegIX(z.getRegIX());
        state.setRegIY(z.getRegIY());
        state.setRegSP(z.getRegSP());
        state.setRegPC(z.getRegPC());
        state.setRegI(z.getRegI());
        state.setRegR(z.getRegR());
        state.setMemPtr(z.getMemPtr());
        state.setHalted(z.isHalted());
        state.setIFF1(z.isIFF1());
        state.setIFF2(z.isIFF2());
        state.setIM(z.getIM());
        state.setINTLine(z.isINTLine());
        state.setPendingEI(z.isPendingEI());
        state.setNMI(z.isNMI());
//        state.setFlagQ(lastFlagQ);
        return state;
    }

    public static class Z80StateExt extends Z80State {
        public String memAccess;
    }

    public static String dumpInfo(Z80Dasm z80Disasm, IMemIoOps memIoOps, int pc) {
        return z80Disasm.disassemble(pc, memIoOps);
    }

    public static boolean isBusyLoop(int opByte1, int opByte2) {
        return isBusyOpcode[opByte1][opByte2];
    }

    private static boolean[][] generateBLOpcodes() {
        boolean[][] isBusyOpcode = new boolean[0x100][0x100];
        IMemIoOps memIoOps = new MemIoOps();
        for (int i = 0; i < 0x100; i++) {
            memIoOps.poke8(0, i);
            if (i == 0xCB || i == 0xED) {
                for (int j = 0; j < 0x100; j++) {
                    memIoOps.poke8(1, j);
                    isBusyOpcode[i][j] = isBusyLoopOpcode(memIoOps);
                }
            } else {
                boolean res = isBusyLoopOpcode(memIoOps);
                Arrays.fill(isBusyOpcode[i], res);
            }
        }
        return isBusyOpcode;
    }

    private static boolean isBusyLoopOpcode(IMemIoOps memIoOps) {
        boolean res = false;
        int[] opcodes = new int[5];
        String s = Z80Dasm.disassemble(0, opcodes, memIoOps);
        if (s.contains(" ld ") || s.contains(" nop") || s.contains(" jr ") || s.contains("halt") || s.contains("and")
                || s.contains(" or ") || s.contains(" jp ") || s.contains(" bit ")) {
            res = true;
        }
//        System.out.println(s + ": " + res);
        return res;
    }

    private static Z80Dasm z80Dasm = new Z80Dasm();
    static LogHelper logHelper = new LogHelper();
    public static long hits = 0;

    enum LoopType {NONE, INFINITE_LOOP, BUSY_LOOP}

    private static final Predicate<Integer> validJpOpcode1byte = op -> op == 0x20 || op == 0x28 || op == 0x18;
    private static final Predicate<Integer> validJpOpcode2bytes = op -> op == 0xF2 || op == 0xC2
            || op == 0xCA || op == 0xFA || op == 0xC3 || op == 0xE2;

    public static LoopType checkLoops(Z80 z80, ReadableByteMemory bus, IMemIoOps memIoOps) {
        return checkLoops(z80, z80.getRegPC(), bus, memIoOps);
    }

    public static LoopType checkLoops(Z80 z80, int pc, ReadableByteMemory bus, IMemIoOps memIoOps) {
        final int opcode = bus.readRamByte(pc) & 0xFF;
        LoopType res = switch (opcode) {
            case 0x7E, 0x1A -> {
                boolean val = check_AND_JP(bus, pc, 1, memIoOps);
                val |= checkOR_JR(bus, pc, memIoOps);
                yield val ? LoopType.BUSY_LOOP : LoopType.NONE;
            }
            case 0x3A -> {
                boolean val = check_AND_JP(bus, pc, 3, memIoOps);
                val |= checkOR_JR(bus, pc, memIoOps);
                yield val ? LoopType.BUSY_LOOP : LoopType.NONE;
            }
            case 0 -> {
                //0000009a            00    nop
                //[...] more Nops
                //0000009b      C3 9A 00    jp $009A
                int b1;
                int lastNopIdx = pc;
                do {
                    lastNopIdx++;
                    b1 = bus.readRamByte(lastNopIdx) & 0xFF;
                } while (b1 == 0);
                yield checkJump(bus, lastNopIdx, pc) ? LoopType.INFINITE_LOOP : LoopType.NONE;
            }
            //00000048            76    halt
            case 0x76 -> LoopType.INFINITE_LOOP;
            //00000000            E9    jp (hl)
            case 0xE9 -> pc == z80.getRegHL() ? LoopType.INFINITE_LOOP : LoopType.NONE;
            //00000201      C3 01 02    jp $0201
            case 0xC3 -> checkJump(bus, pc, pc) ? LoopType.INFINITE_LOOP : LoopType.NONE;
            //00000003         18 FE    jr $0003
            case 0x18 -> checkJump(bus, pc, pc) ? LoopType.INFINITE_LOOP : LoopType.NONE;
            //0000011c            B6    or (hl) //hl = 0x4080 ??
            //0000011d      E2 1C 01    jp po,$011C
            case 0xB6 -> checkJump(bus, pc + 1, pc) ? LoopType.BUSY_LOOP : LoopType.NONE;
            default -> LoopType.NONE;
        };
        if (res != LoopType.NONE) {
            //logHelper.logWarningOnceWhenEnRepeat(LOG, "Z80 loop: {}", Z80Helper.dumpInfo(z80Dasm, memIoOps, z80Core.getRegPC()));
            hits++;
            if ((hits & 0xFFF) == 0) {
                LOG.warn("{} Z80 loop: {}", hits, Z80Helper.dumpInfo(z80Dasm, memIoOps, z80.getRegPC()));
            }
        }
        return res;
    }

    /**
     * 00000a0a
     * 00000a0b         E6 0X    and $0X
     * 00000a0d         28 FB    jr z,-3
     * <p>
     * or
     * 00000a0b         A7          and a
     * 00000a0d         C2 0a 0a    jp nz,$0a0a
     */
    private static boolean check_AND_JP(ReadableByteMemory bus, int pc, int andOffset, IMemIoOps memIoOps) {
        int idxAnd = pc + andOffset;
        int b1 = bus.readRamByte(idxAnd) & 0xFF;
        boolean checkRet = b1 == 0xA7 || b1 == 0xA2; //and A, and D
        int jpIdx = idxAnd + 1;
        if (b1 == 0xE6) { //and 0x
            byte b2 = (byte) bus.readRamByte(idxAnd + 1);
            if (b2 == 1 || b2 == 2 || b2 == 3) {
                checkRet = true;
                jpIdx = idxAnd + 2;
            }
        }
        if (checkRet) {
            return checkJump(bus, jpIdx, pc);
        } else {
//                            System.out.println(Z80Helper.dumpInfo(z80Dasm, memIoOps, pc + 3));
            if (memIoOps != null) {
                LogHelper.logWarnOnce(LOG, Z80Helper.dumpInfo(z80Dasm, memIoOps, idxAnd + 2));
            }
        }
        return false;
    }

    /**
     * 0000002a
     * 0000002b            B7    or a
     * 0000002c         28 FC    jr z, -2
     * <p>
     * or
     * <p>
     * 0000002c      CA 2A 00    jp z,$002A
     */
    private static boolean checkOR_JR(ReadableByteMemory bus, int pc, IMemIoOps memIoOps) {
        boolean ok = false;
        int orIdx = pc + 1;
        do {
            ok |= checkOR_JP(bus, pc, orIdx);
            orIdx++;
        } while (orIdx - pc < 4);
        if (ok) {
            hits++;
            if ((hits & 0xFFF) == 0) {
                LOG.warn("{} Z80 loop: {}", hits, Z80Helper.dumpInfo(z80Dasm, memIoOps, pc));
            }
        }
        return ok;
    }

    private static boolean checkOR_JP(ReadableByteMemory bus, int pc, int orIdx) {
        int b1 = bus.readRamByte(orIdx) & 0xFF;
        boolean res = false;
        if (b1 == 0xB7) { //or
            res = checkJump(bus, orIdx + 1, pc);
        }
        return res;
    }

    private static boolean checkJump(ReadableByteMemory bus, int firstOpcodeIdx, int pc) {
        byte b2 = (byte) bus.readRamByte(firstOpcodeIdx);
        byte b3 = (byte) bus.readRamByte(firstOpcodeIdx + 1);
        if (validJpOpcode1byte.test(b2 & 0xFF)) {
            int pcDist = 0xFF - (firstOpcodeIdx - pc) - 1;
            boolean jumpOk = (b3 & 0xFF) == pcDist;
            if (!jumpOk) {
                byte b4 = (byte) bus.readRamByte(firstOpcodeIdx + 2);
                if (validJpOpcode1byte.test(b4 & 0xFF)) {
                    byte b5 = (byte) bus.readRamByte(firstOpcodeIdx + 3);
                    int pcDist1 = pcDist - 2;
                    jumpOk = (b5 & 0xFF) == pcDist1;
                }
                return jumpOk;
            }
            return jumpOk;
        } else if (validJpOpcode2bytes.test(b2 & 0xFF)) {
            byte b4 = (byte) bus.readRamByte(firstOpcodeIdx + 2);
            int dest = ArrayEndianUtil.getUShort16LE(b3, b4);
            return dest == pc;
        } else {
//                        LOG.warn("{} Z80 check: {}", hits, Z80Helper.dumpInfo(z80Dasm, memIoOps, pc + 2));
//                System.out.println(Z80Helper.dumpInfo(z80Dasm, memIoOps, pc + 2));
        }
        return false;
    }

    private static Set<String> missedLoops = new HashSet<>();

    public static void checkMissedLoops(Z80 z80, Z80BusProvider bus, int loopPc, IMemIoOps memIoOps, CpuBusyLoopDetection bld) {
        LoopType res = Z80Helper.checkLoops(z80, loopPc, bus, memIoOps);
        if (res == LoopType.NONE) {
            if (missedLoops.add(bld.getLoopInfo())) {
                System.out.println("Missed Z80 loop: " + bld.getLoopInfoVerbose());
//                LogHelper.logWarnOnce(LOG, "Missed Z80 loop: {}", bld.getLoopInfoVerbose());
            }
        }
    }
}
