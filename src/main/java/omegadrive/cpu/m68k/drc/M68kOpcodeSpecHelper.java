package omegadrive.cpu.m68k.drc;

import m68k.cpu.Cpu;
import m68k.cpu.DisassembledInstruction;

import static omegadrive.cpu.m68k.drc.M68kOpcodeSpecHelper.M68kFlags.*;
import static omegadrive.cpu.m68k.drc.M68kOpcodeSpecHelper.M68kOpcodeSpec.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class M68kOpcodeSpecHelper {

    public class M68kFlags {
        public static final int SIZE_BYTE = 0x0001;
        public static final int SIZE_WORD = 0x0002;
        public static final int SIZE_LONG = 0x0004;

        public static final int TYPE_MOVE = 0x0010;
        public static final int TYPE_MOVEA = 0x0020;
        public static final int TYPE_ARITH = 0x0040;
        public static final int TYPE_BRANCH = 0x0080;
        public static final int TYPE_TEST = 0x0100;
        public static final int TYPE_DECIMAL = 0x0200;
        public static final int TYPE_CMP = 0x0400;
        public static final int TYPE_LOGIC = 0x0800; // ORI, ANDI, EORI

        public static final int TYPE_SYSTEM = 0x1000;
        public static final int TYPE_SHIFT = 0x2000;

        public static final int HAS_IMM8 = 0x10000;
        public static final int HAS_IMM16 = 0x20000;
        public static final int HAS_IMM32 = 0x40000;
    }


    /**
     * 0001b397	b079 ff4ae067           cmp.w    $ff4ae067,d0	(null)
     */

    public enum M68kOpcodeSpec {
        // Opcode Identification: (opcode & mask) == pattern

        // ABCD family: 1100 (Rx) 1 0000 (M) (Ry)
        // Mask: 1111 0001 1111 0000 -> 0xF1F0
        // Pattern: 1100 0001 0000 0000 -> 0xC100

        // Catch-all for both Reg-to-Reg and Mem-to-Mem
        ABCD("ABCD", 0xF1F0, 0xC100, TYPE_DECIMAL | SIZE_BYTE),

        // SBCD: Subtract Decimal with Extend
        // Like ABCD, it supports Register-to-Register (m=0) and Memory-to-Memory (m=1)
        // Using 0xF1F0 mask catches the base instruction for both modes.
        SBCD("SBCD", 0xF1F0, 0x8100, TYPE_DECIMAL),

        // NBCD: 0100 1000 00 [Effective Address]
        // Always operates on a Byte (.B)
        NBCD("NBCD", 0xFFC0, 0x4800, TYPE_DECIMAL | SIZE_BYTE),

        // Arithmetic Shift (ASL/ASR)
        ASR("ASR", 0xF118, 0xE000, TYPE_SHIFT),
        ASL("ASL", 0xF118, 0xE100, TYPE_SHIFT),

        // Logical Shift (LSR/LSL)
        LSR("LSR", 0xF118, 0xE008, TYPE_SHIFT),
        LSL("LSL", 0xF118, 0xE108, TYPE_SHIFT),

        // Rotate (ROR/ROL)
        ROR("ROR", 0xF118, 0xE018, TYPE_SHIFT),
        ROL("ROL", 0xF118, 0xE118, TYPE_SHIFT),

        // ROXR: 1110 .... 0 .. 10 ....
// Mask F118: 1111 0001 0001 1000
// Value E010: 1110 0000 0001 0000 (Bit 8 is 0)
        ROXR_ALL("ROXR", 0xF118, 0xE010, TYPE_SHIFT),

        // ROXL: 1110 .... 1 .. 10 ....
// Value E110: 1110 0001 0001 0000 (Bit 8 is 1)
        ROXL_ALL("ROXL", 0xF118, 0xE110, TYPE_SHIFT),

        // Generalized ADD Group
// Mask 0x9000 captures the 'Add-ish' patterns (0000, 0101, 1101)
// while excluding other major opcodes.
// ADD/ADDA/ADDX: 1101 (0xD)
        ADD_STD("ADD*", 0xF000, 0xD000, TYPE_ARITH),

        // ADDQ: 0101 (0x5)
        ADD_QUICK("ADDQ", 0xF100, 0x5000, TYPE_ARITH),

        // ADDI: 0000 (0x0)
        ADD_IMM("ADDI", 0xFF00, 0x0600, TYPE_ARITH),

        // Standard SUB, SUBA, SUBX (High nibble 1001 = 0x9)
        SUB_STD("SUB*", 0xF000, 0x9000, TYPE_ARITH),

        // SUBQ (High nibble 0101 = 0x5, bit 8 must be 1)
        SUB_QUICK("SUBQ", 0xF100, 0x5100, TYPE_ARITH),

        // SUBI (High word starts with 0x04)
        SUB_IMM("SUBI", 0xFF00, 0x0400, TYPE_ARITH),

        // NEGX: 0100 0000 [Size:2] [EA:6]
        // Mask FF00 isolates NEGX (0x40xx); Bits 7-6 handle size (.B, .W, .L)
        NEGX_ALL("NEGX", 0xFF00, 0x4000, TYPE_ARITH),

        // Standard OR: 1000 [Reg:3] [Opmode:3] [EA:6]
// Mask F000 isolates the OR identifier (0x8)
        OR_STD("OR*", 0xF000, 0x8000, TYPE_LOGIC),

        // ORI: 0000 0000 [Size:2] [EA:6]
// Mask FF00 isolates ORI (0x00)
        ORI_ALL("ORI*", 0xFF00, 0x0000, TYPE_LOGIC),

        // Standard EOR: 1011 [Reg:3] 1 [Size:2] [EA:6]
        // Mask F100 isolates the 'B' nibble and the "1" in bit 8
        EOR_STD("EOR*", 0xF100, 0xB100, TYPE_LOGIC),

        // EORI: 0000 1010 [Size:2] [EA:6]
        // Mask FF00 isolates EORI (0x0A)
        EORI_ALL("EORI*", 0xFF00, 0x0A00, TYPE_LOGIC),

        // Standard AND: 1100 [Reg:3] [Opmode:3] [EA:6]
        // Mask F000 catches AND Dn, <ea> and AND <ea>, Dn
        AND_STD("AND*", 0xF000, 0xC000, TYPE_LOGIC),

        // ANDI: 0000 0010 [Size:2] [EA:6]
        // Mask FF00 catches ANDI.B/W/L and ANDI to CCR/SR
        AND_IMM("ANDI*", 0xFF00, 0x0200, TYPE_LOGIC),

        // Scc: 0101 [Condition:4] 11 [EA:6]
        // Masking 0xF0C0 isolates the '0101' and the '11' identifier
        Scc("Scc", 0xF0C0, 0x50C0, TYPE_LOGIC | SIZE_BYTE),

        // Unified EXT (Covers .W and .L)
        EXT("EXT", 0xFFB8, 0x4880, TYPE_ARITH),

        // SWAP: 0100 1000 0100 0 [Dn:3]
        // Mask FFF8 isolates the instruction; Pattern 4840 is the identity.
        SWAP("SWAP", 0xFFF8, 0x4840, TYPE_ARITH | SIZE_LONG),

        // Standard CMP & CMPA: 1011 [Reg:3] [Opmode:3] [EA:6]
        // Mask F000 catches CMP <ea>, Dn and CMPA <ea>, An
        CMP_STD("CMP*", 0xF000, 0xB000, TYPE_CMP),

        // CMPI: 0000 1100 [Size:2] [EA:6]
        // Mask FF00 isolates the CMPI identifier from ADDI/SUBI
        CMPI_ALL("CMPI", 0xFF00, 0x0C00, TYPE_CMP),

        // CMPM: 1011 [Reg:3] 1 [Size:2] 001 [Reg:3]
        // Memory-to-Memory (Post-increment). Mask F138 isolates the pattern.
        CMPM_ALL("CMPM", 0xF138, 0xB108, TYPE_CMP),

        // TAS: 0100 1010 11 [EA:6]
        // Mask FFC0 isolates the instruction identity
        TAS("TAS", 0xFFC0, 0x4AC0, TYPE_TEST | SIZE_BYTE),


        // TST: 0100 1010 [Size:2] [EA:6]
        // Mask FF00 isolates TST; bits 7-6 determine size, 5-0 determine <ea>
        TST_ALL("TST", 0xFF00, 0x4A00, TYPE_LOGIC),

        // BTST Dynamic: 0000 [Reg:3] 100 [EA:6]
        // Bit number is in the specified Data Register
        BTST_DYN("BTST", 0xF1C0, 0x0100, TYPE_TEST),
        BCHG_DYN("BCHG", 0xF1C0, 0x0140, TYPE_LOGIC),
        BCLR_DYN("BCLR", 0xF1C0, 0x0180, TYPE_LOGIC),
        BSET_DYN("BSET", 0xF1C0, 0x01C0, TYPE_LOGIC),

        // BTST Static: 0000 1000 00 [EA:6]
        // Bit number is in the immediate word following the instruction
        BTST_STA("BTST", 0xFFC0, 0x0800, TYPE_TEST),
        BCHG_STA("BCHG", 0xFFC0, 0x0840, TYPE_LOGIC | HAS_IMM16),
        BCLR_STA("BCLR", 0xFFC0, 0x0880, TYPE_LOGIC | HAS_IMM16),
        BSET_STA("BSET", 0xFFC0, 0x08C0, TYPE_LOGIC | HAS_IMM16),

        // CLR: 0100 0010 [Size:2] [EA:6]
        // Mask FF00 isolates CLR; Bits 7-6 handle size (.B, .W, .L)
        CLR_ALL("CLR", 0xFF00, 0x4200, TYPE_LOGIC),

        // NOT <ea>
        // Unified NOT (Covers .B, .W, and .L)
        NOT("NOT", 0xFF00, 0x4600, TYPE_LOGIC),

        // NEG <ea>
        // Unified NEG (Covers .B, .W, and .L)
        NEG("NEG", 0xFF00, 0x4400, TYPE_ARITH),

        // DBcc: 0101 [Condition:4] 1100 1 [Dn:3]
        // Always followed by a 16-bit displacement
        DBcc("DBcc", 0xF0F8, 0x50C8, TYPE_BRANCH | HAS_IMM16),

        // Bcc: Branch Conditionally (e.g., $67F8, $66FA)
        // Bcc.S (Short): 0110 [Cond:4] [Disp:8] (where Disp != 0)
        // Mask F000 catches the '6' block; we'll handle the zero-check in code.
        Bcc_S("Bcc.S", 0xF000, 0x6000, TYPE_BRANCH | HAS_IMM8),

        // Bcc.W (Word): 0110 [Cond:4] 00000000
        // Mask F0FF ensures the bottom 8 bits are zero.
        Bcc_W("Bcc.W", 0xF0FF, 0x6000, TYPE_BRANCH | HAS_IMM16),

        // JSR: Jump to Subroutine ($4E80 - $4EBF)
        JSR("JSR", 0xFFC0, 0x4E80, TYPE_BRANCH),

        // JMP: Jump ($4EC0 - $4EFF)
        JMP("JMP", 0xFFC0, 0x4EC0, TYPE_BRANCH),

        // ADDX: Add Extended (e.g., $C380)
        ADDX_L("ADDX.L Dy, Dx", 0xF1F8, 0xC180, TYPE_ARITH | SIZE_LONG),

        // MOVE patterns: 00 ss rrr mmm mmm rrr
        // ss = Size (01=byte, 11=word, 10=long)
        // Note: Destination EA (bits 11-6) is Mode then Register,
        // while Source EA (bits 5-0) is Mode then Register.

        MOVE_B("MOVE.B <ea>,<ea>", 0xF000, 0x1000, SIZE_BYTE | TYPE_MOVE),
        MOVE_W("MOVE.W <ea>,<ea>", 0xF000, 0x3000, SIZE_WORD | TYPE_MOVE),
        MOVE_L("MOVE.L <ea>,<ea>", 0xF000, 0x2000, SIZE_LONG | TYPE_MOVE),

        // MOVEA is a special case where destination is an Address Register
        MOVEA_W("MOVEA.W <ea>, An", 0xF1C0, 0x3040, SIZE_WORD | TYPE_MOVEA),
        MOVEA_L("MOVEA.L <ea>, An", 0xF1C0, 0x2040, SIZE_LONG | TYPE_MOVEA),

        // MOVEM: 0100 1[Direction]00 1[Size] [EA:6]
        // Followed by a 16-bit register mask word
        MOVEM_TO_MEM("MOVEM", 0xFB80, 0x4880, TYPE_MOVE | HAS_IMM16),
        MOVEM_FROM_MEM("MOVEM", 0xFB80, 0x4C80, TYPE_MOVE | HAS_IMM16),

        // MOVE USP: 0100 1110 0110 [dr:1] [Reg:3]
        // Mask FFF0 isolates the MOVE USP pattern; bit 3 (dr) is the direction
        MOVE_USP("MOVE USP", 0xFFF0, 0x4E60, TYPE_SYSTEM),

        // MUL: [Rx:3] 110 [S/U:1] [EA:6]
        MULU("MULU", 0xF1C0, 0xC0C0, TYPE_ARITH | SIZE_WORD), // Unsigned
        MULS("MULS", 0xF1C0, 0xC1C0, TYPE_ARITH | SIZE_WORD), // Signed

        // DIV: [Rx:3] 100 [S/U:1] [EA:6]
        DIVU("DIVU", 0xF1C0, 0x80C0, TYPE_ARITH | SIZE_LONG), // Unsigned
        DIVS("DIVS", 0xF1C0, 0x81C0, TYPE_ARITH | SIZE_LONG),  // Signed

        // MOVE from SR/CCR
        MOVE_FROM_SR("MOVE SR, <ea>", 0xFFC0, 0x40C0, TYPE_SYSTEM),
        MOVE_TO_SR("MOVE <ea>, SR", 0xFFC0, 0x46C0, TYPE_SYSTEM),

        // MOVEQ: 0111 (Reg:3) 0 (Data:8)
        // It is always a Long operation despite carrying only 8 bits of data.
        MOVEQ("MOVEQ", 0xF100, 0x7000, TYPE_MOVE | SIZE_LONG | HAS_IMM8),


        // LEA: 0100 [Reg:3] 111 [EA:6]
        // Mask F1C0 isolates the 0100 prefix and the 111 mode identifier
        LEA("LEA", 0xF1C0, 0x41C0, TYPE_MOVE | SIZE_LONG),

        // PEA: 0100 1000 01 [EA:6]
        // Mask FFC0 isolates the fixed 0100 1000 01 pattern
        PEA("PEA", 0xFFC0, 0x4840, TYPE_MOVE | SIZE_LONG),
        // Unified EXG (Covers DD, AA, and DA)
        EXG("EXG", 0xF100, 0xC100, TYPE_MOVE | SIZE_LONG),

        // NOP: No Operation ($4E71)
        NOP("NOP", 0xFFFF, 0x4E71, TYPE_SYSTEM),

        // LINK: 0100 1110 0101 0 [An:3]
        // Followed by a 16-bit signed displacement (space to reserve)
        LINK("LINK", 0xFFF8, 0x4E50, TYPE_SYSTEM | HAS_IMM16),

        // UNLK: 0100 1110 0101 1 [An:3]
        UNLK("UNLK", 0xFFF8, 0x4E58, TYPE_SYSTEM),

        // CHK: 0100 [Dn:3] 110 [EA:6]
        // Masking F1C0 isolates the 0100 prefix and 110 mode
        CHK("CHK", 0xF1C0, 0x4180, TYPE_SYSTEM | SIZE_WORD),

        // RTS: Return from Subroutine ($4E75)
        RTS("RTS", 0xFFFF, 0x4E75, TYPE_SYSTEM),

        // RTE: Return from Exception ($4E73) - Used in interrupt handlers
        RTE("RTE", 0xFFFF, 0x4E73, TYPE_SYSTEM),

        // RTR: Return and Restore Condition Codes ($4E77)
        RTR("RTR", 0xFFFF, 0x4E77, TYPE_SYSTEM),

        // ILLEGAL: Guaranteed to cause an illegal instruction exception ($4AFC)
        ILLEGAL("ILLEGAL", 0xFFFF, 0x4AFC, TYPE_SYSTEM),

        // RESET: Reset external devices ($4E70)
        RESET("RESET", 0xFFFF, 0x4E70, TYPE_SYSTEM),

        // TRAP: 0100 1110 0100 [Vector:4]
        // Masking 0xFFF0 allows us to catch TRAP #0 through TRAP #15
        TRAP("TRAP", 0xFFF0, 0x4E40, TYPE_SYSTEM),

        // TRAPV: 0100 1110 0111 0110
        TRAPV("TRAPV", 0xFFFF, 0x4E76, TYPE_SYSTEM),

        // STOP: 0100 1110 0111 0010
        // Followed by a 16-bit immediate for the Status Register
        STOP("STOP", 0xFFFF, 0x4E72, TYPE_SYSTEM | HAS_IMM16),

        LINE_F("LINE_F", 0xF000, 0xF000, TYPE_SYSTEM),
        LINE_A("LINE_A", 0xF000, 0xA000, TYPE_SYSTEM),
        ;

        private final String mnemonic;
        private final int mask;
        private final int pattern;
        private final int flags;

        public static final M68kOpcodeSpec[] vals = M68kOpcodeSpec.values();

        M68kOpcodeSpec(String mnemonic, int mask, int pattern, int flags) {
            this.mnemonic = mnemonic;
            this.mask = mask;
            this.pattern = pattern;
            this.flags = flags;
        }

        public boolean matches(int opcode) {
            return (opcode & mask) == pattern;
        }

        public boolean isBranch() {
            return (flags & TYPE_BRANCH) > 0;
        }

        protected static M68kOpcodeSpec findSlow(int opcode) {
            for (M68kOpcodeSpec spec : vals) {
                if ((opcode & spec.mask) == spec.pattern) {
                    return spec;
                }
            }
            return null;
        }

        public static M68kOpcodeSpec find(int opcode) {
            return vals[opcodes[opcode] & VALID_POLL_BIT_MASK];
        }

    }

    public static byte[] opcodes = new byte[0];
    /**
     * indicates that the opcode can be part of a polling loop
     */
    private static final int VALID_POLL_BIT = 0x80;
    private static final int VALID_POLL_BIT_MASK = VALID_POLL_BIT - 1;

    public static void generateOnce(Cpu cpu) {
        if (opcodes.length > 0) {
            return;
        }
        //ready to go?
        if (!checkReady(cpu)) {
            return;
        }
        opcodes = new byte[0xFFFF + 1];
        boolean valid;
        for (int i = 0; i < opcodes.length; i++) {
//            System.err.print(th(i));
            try {
                var di = cpu.getInstructionFor(i).disassemble(0, i);
//                String inst = di.getVeryShortFormat();
                valid = isValidOpcodeForLoopingGenerate(di);
            } catch (Exception e) {
                if (i == 0) {
                    throw new RuntimeException("Not ready", e);
                }
                valid = false;
            }
            var opc = findSlow(i);
            int opcPos = opc != null ? opc.ordinal() : 0;
            assert opcPos < VALID_POLL_BIT;
            opcodes[i] = (byte) ((valid ? VALID_POLL_BIT : 0) | opcPos);
//            System.err.println("," + inst + "," +valid);
        }
    }

    private static boolean checkReady(Cpu cpu) {
        //ready to go?
        boolean ready = false;
        try {
            var inst = cpu.getInstructionFor(0).disassemble(0, 0);
            ready = true;
        } catch (Exception e) {
            ready = false;
        }
        return ready;
    }

    public static boolean isValidOpcodeForLoopingGenerate(DisassembledInstruction di) {
        assert opcodes.length > 0;
        boolean process = false;
        final int op = di.opcode;
        if (!process) {
            String str = di.getVeryShortFormat();
            boolean nok = str.contains("+") || str.contains("-") || ADD_STD.matches(op) || ADD_IMM.matches(op) ||
                    ADD_QUICK.matches(op) || SUB_STD.matches(op) || SUB_IMM.matches(op) || SUB_QUICK.matches(op) ||
                    LSL.matches(op) || LSR.matches(op) || ROR.matches(op) || ROL.matches(op) || ASR.matches(op) ||
                    ASL.matches(op) || ROXL_ALL.matches(op) || ROXR_ALL.matches(op) || PEA.matches(op) ||
                    ABCD.matches(op) || NBCD.matches(op) || SBCD.matches(op) || LINK.matches(op) || UNLK.matches(op) ||
                    RESET.matches(op) || TRAP.matches(op) || TRAPV.matches(op) || RTS.matches(op) || RTE.matches(op) ||
                    RTR.matches(op) || JSR.matches(op) || DBcc.matches(op) || MULS.matches(op) || MULU.matches(op) ||
                    DIVU.matches(op) || DIVS.matches(op) || STOP.matches(op) || MOVE_USP.matches(op) ||
                    LINE_F.matches(op) || LINE_A.matches(op);
            if (!nok) {
                process = process || Bcc_S.matches(op) || Bcc_W.matches(op) || JMP.matches(op) || AND_STD.matches(op)
                        || AND_IMM.matches(op) || OR_STD.matches(op) || ORI_ALL.matches(op) || EOR_STD.matches(op)
                        || EORI_ALL.matches(op) || CMPI_ALL.matches(op) || CMP_STD.matches(op) || CMPM_ALL.matches(op)
                        || TST_ALL.matches(op) || NOP.matches(op) || BTST_DYN.matches(op) || BTST_STA.matches(op)
                        || BSET_DYN.matches(op) || BSET_STA.matches(op) || BCHG_STA.matches(op) || BCHG_DYN.matches(op)
                        || BCLR_STA.matches(op) || BCLR_DYN.matches(op) || CLR_ALL.matches(op) || Scc.matches(op)
                        || LEA.matches(op) || EXG.matches(op) || EXT.matches(op) || NEG.matches(op) || SWAP.matches(op)
                        || NOT.matches(op) || NEGX_ALL.matches(op) || CHK.matches(op);
                process = process || str.contains("move");
                if (!process) {
                    if (!str.contains("????"))
                        System.err.println(di);
                }
            }
        }
        return process;
    }

    public static boolean isValidOpcodeForLooping(int op) {
        return (opcodes[op] & VALID_POLL_BIT) != 0;
    }
}
