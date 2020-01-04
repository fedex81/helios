package omegadrive.z80;

import emulib.plugins.cpu.DisassembledInstruction;
import omegadrive.z80.disasm.Z80DecoderExt;
import omegadrive.z80.disasm.Z80Disasm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.MemIoOps;
import z80core.Z80;
import z80core.Z80State;

import java.util.function.Function;

/**
 * Z80Helper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class Z80Helper {

    public static final Function<DisassembledInstruction, String> disasmToString = d ->
            String.format("%08x   %12s   %s", d.getAddress(), d.getOpCode(), d.getMnemo());

    private final static Logger LOG = LogManager.getLogger(Z80Helper.class.getSimpleName());
    public static boolean verbose = false;

    public static String toStringExt(Z80StateExt state, Z80Disasm disasm, MemIoOps memIoOps) {
        StringBuilder sb = new StringBuilder();
        sb.append(toString(state)).append("\n\n");
        sb.append(dumpInfo(disasm, memIoOps, state.getRegPC())).append("\n");
        sb.append(state.memAccess);
        return sb.toString();
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

    static class Z80StateExt extends Z80State {
        public String memAccess;
    }

    public static String dumpInfo(Z80Disasm z80Disasm, MemIoOps memIoOps, int pc) {
        DisassembledInstruction di = z80Disasm.disassemble(pc);
        String res = disasmToString.apply(di);
        if (Z80DecoderExt.UNKNOWN.equalsIgnoreCase(di.getMnemo())) {
            int w = (memIoOps.peek8(pc) << 8) | memIoOps.peek8(pc + 1);
            res = Z80DecoderExt.getMnemonic(w);
            if (Z80DecoderExt.UNKNOWN.equalsIgnoreCase(res)) {
                int mapOp = Z80DecoderExt.getMappingOpcode(w);
                if (mapOp != w) {
                    di = z80Disasm.disassemble(pc + 1);
                    res = disasmToString.apply(di) + " [" + Integer.toHexString(w) + "]";
                }
            }
        }
//        LOG.info(res);
        return res;
    }
}
