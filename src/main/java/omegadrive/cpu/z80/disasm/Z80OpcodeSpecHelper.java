package omegadrive.cpu.z80.disasm;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class Z80OpcodeSpecHelper {


    enum Z80SpecFlags {
        JUMP, LOAD_FROM_MEM, STORE_TO_MEM, LOAD_REG, ALU, DEST_REG, IMM8, IMM16, STACK, RET, RST, CALL, PREFIX;
    }

    //Modifies the Program Counter (PC). (0x01)
    private static final int JUMP_MASK = 1 << Z80SpecFlags.JUMP.ordinal();
    //Reads data from a memory address (e.g., (HL), (nn)). (0x02)
    private static final int LOAD_FROM_MEM_MASK = 1 << Z80SpecFlags.LOAD_FROM_MEM.ordinal();
    //        (0x04): Writes data to a memory address.
    private static final int STORE_TO_MEM_MASK = 1 << Z80SpecFlags.STORE_TO_MEM.ordinal();
    //(0x08): Register-to-register transfer (includes 16-bit loads).
    private static final int LOAD_REG_MASK = 1 << Z80SpecFlags.LOAD_REG.ordinal();
    //        (0x10): Updates CPU flags (S, Z, H, P/V, N, C).
    private static final int ALU_MASK = 1 << Z80SpecFlags.ALU.ordinal();
    //(0x20): The result of an operation is stored back into a register.
    private static final int DEST_REG_MASK = 1 << Z80SpecFlags.DEST_REG.ordinal();
    //      (0x40): 1-byte immediate follows.
    private static final int IMM8_MASK = 1 << Z80SpecFlags.IMM8.ordinal();

    //(0x80): 2-byte immediate follows.
    private static final int IMM16_MASK = 1 << Z80SpecFlags.IMM16.ordinal();

    private static final int STACK_MASK = 1 << Z80SpecFlags.STACK.ordinal();

    // (0x200): Specific to Return instructions (implies stack pop to PC).
    private static final int RET_MASK = 1 << Z80SpecFlags.RET.ordinal();

    private static final int RST_MASK = 1 << Z80SpecFlags.RST.ordinal();

    // (0x800): Specific to Call instructions (Stack Push + Jump).
    private static final int CALL_MASK = 1 << Z80SpecFlags.CALL.ordinal();

    // (0x1000): Indicates the opcode is a prefix (CB, DD, ED, FD).
    private static final int PREFIX_MASK = 1 << Z80SpecFlags.PREFIX.ordinal();

    public enum Z80OpcodeSpec {
        // 0x00 - 0x0F
        OP_0x00("NOP", 0),
        OP_0x01("LD BC, nn", LOAD_REG_MASK | IMM16_MASK),
        OP_0x02("LD (BC), A", STORE_TO_MEM_MASK),
        OP_0x03("INC BC", DEST_REG_MASK),
        OP_0x04("INC B", ALU_MASK | DEST_REG_MASK),
        OP_0x05("DEC B", ALU_MASK | DEST_REG_MASK),
        OP_0x06("LD B, n", LOAD_REG_MASK | IMM8_MASK),
        OP_0x07("RLCA", ALU_MASK | DEST_REG_MASK),
        OP_0x08("EX AF, AF'", 0),
        OP_0x09("ADD HL, BC", ALU_MASK | DEST_REG_MASK),
        OP_0x0A("LD A, (BC)", LOAD_FROM_MEM_MASK | DEST_REG_MASK),
        OP_0x0B("DEC BC", DEST_REG_MASK),
        OP_0x0C("INC C", ALU_MASK | DEST_REG_MASK),
        OP_0x0D("DEC C", ALU_MASK | DEST_REG_MASK),
        OP_0x0E("LD C, n", LOAD_REG_MASK | IMM8_MASK),
        OP_0x0F("RRCA", ALU_MASK | DEST_REG_MASK),

        // 0x10 - 0x1F
        OP_0x10("DJNZ d", JUMP_MASK | ALU_MASK | IMM8_MASK),
        OP_0x11("LD DE, nn", LOAD_REG_MASK | IMM16_MASK),
        OP_0x12("LD (DE), A", STORE_TO_MEM_MASK),
        OP_0x13("INC DE", DEST_REG_MASK),
        OP_0x14("INC D", ALU_MASK | DEST_REG_MASK),
        OP_0x15("DEC D", ALU_MASK | DEST_REG_MASK),
        OP_0x16("LD D, n", LOAD_REG_MASK | IMM8_MASK),
        OP_0x17("RLA", ALU_MASK | DEST_REG_MASK),
        OP_0x18("JR d", JUMP_MASK | IMM8_MASK),
        OP_0x19("ADD HL, DE", ALU_MASK | DEST_REG_MASK),
        OP_0x1A("LD A, (DE)", LOAD_FROM_MEM_MASK | DEST_REG_MASK),
        OP_0x1B("DEC DE", DEST_REG_MASK),
        OP_0x1C("INC E", ALU_MASK | DEST_REG_MASK),
        OP_0x1D("DEC E", ALU_MASK | DEST_REG_MASK),
        OP_0x1E("LD E, n", LOAD_REG_MASK | IMM8_MASK),
        OP_0x1F("RRA", ALU_MASK | DEST_REG_MASK),

        // 0x20 - 0x2F
        OP_0x20("JR NZ, d", JUMP_MASK | IMM8_MASK),
        OP_0x21("LD HL, nn", LOAD_REG_MASK | IMM16_MASK),
        OP_0x22("LD (nn), HL", STORE_TO_MEM_MASK | IMM16_MASK),
        OP_0x23("INC HL", DEST_REG_MASK),
        OP_0x24("INC H", ALU_MASK | DEST_REG_MASK),
        OP_0x25("DEC H", ALU_MASK | DEST_REG_MASK),
        OP_0x26("LD H, n", LOAD_REG_MASK | IMM8_MASK),
        OP_0x27("DAA", ALU_MASK | DEST_REG_MASK),
        OP_0x28("JR Z, d", JUMP_MASK | IMM8_MASK),
        OP_0x29("ADD HL, HL", ALU_MASK | DEST_REG_MASK),
        OP_0x2A("LD HL, (nn)", LOAD_FROM_MEM_MASK | DEST_REG_MASK | IMM16_MASK),
        OP_0x2B("DEC HL", DEST_REG_MASK),
        OP_0x2C("INC L", ALU_MASK | DEST_REG_MASK),
        OP_0x2D("DEC L", ALU_MASK | DEST_REG_MASK),
        OP_0x2E("LD L, n", LOAD_REG_MASK | IMM8_MASK),
        OP_0x2F("CPL", ALU_MASK | DEST_REG_MASK),

        // 0x30 - 0x3F
        OP_0x30("JR NC, d", JUMP_MASK | IMM8_MASK),
        OP_0x31("LD SP, nn", LOAD_REG_MASK | IMM16_MASK),
        OP_0x32("LD (nn), A", STORE_TO_MEM_MASK | IMM16_MASK),
        OP_0x33("INC SP", DEST_REG_MASK),
        OP_0x34("INC (HL)", ALU_MASK | LOAD_FROM_MEM_MASK | STORE_TO_MEM_MASK),
        OP_0x35("DEC (HL)", ALU_MASK | LOAD_FROM_MEM_MASK | STORE_TO_MEM_MASK),
        OP_0x36("LD (HL), n", STORE_TO_MEM_MASK | IMM8_MASK),
        OP_0x37("SCF", ALU_MASK),
        OP_0x38("JR C, d", JUMP_MASK | IMM8_MASK),
        OP_0x39("ADD HL, SP", ALU_MASK | DEST_REG_MASK),
        OP_0x3A("LD A, (nn)", LOAD_FROM_MEM_MASK | DEST_REG_MASK | IMM16_MASK),
        OP_0x3B("DEC SP", DEST_REG_MASK),
        OP_0x3C("INC A", ALU_MASK | DEST_REG_MASK),
        OP_0x3D("DEC A", ALU_MASK | DEST_REG_MASK),
        OP_0x3E("LD A, n", LOAD_REG_MASK | IMM8_MASK),
        OP_0x3F("CCF", ALU_MASK),

        // 0x40 - 0x47: LD B, r
        OP_0x40("LD B, B", LOAD_REG_MASK), OP_0x41("LD B, C", LOAD_REG_MASK), OP_0x42("LD B, D", LOAD_REG_MASK), OP_0x43("LD B, E", LOAD_REG_MASK),
        OP_0x44("LD B, H", LOAD_REG_MASK), OP_0x45("LD B, L", LOAD_REG_MASK), OP_0x46("LD B, (HL)", LOAD_FROM_MEM_MASK | DEST_REG_MASK), OP_0x47("LD B, A", LOAD_REG_MASK),

        // 0x48 - 0x4F: LD C, r
        OP_0x48("LD C, B", LOAD_REG_MASK), OP_0x49("LD C, C", LOAD_REG_MASK), OP_0x4A("LD C, D", LOAD_REG_MASK), OP_0x4B("LD C, E", LOAD_REG_MASK),
        OP_0x4C("LD C, H", LOAD_REG_MASK), OP_0x4D("LD C, L", LOAD_REG_MASK), OP_0x4E("LD C, (HL)", LOAD_FROM_MEM_MASK | DEST_REG_MASK), OP_0x4F("LD C, A", LOAD_REG_MASK),

        // 0x50 - 0x57: LD D, r
        OP_0x50("LD D, B", LOAD_REG_MASK), OP_0x51("LD D, C", LOAD_REG_MASK), OP_0x52("LD D, D", LOAD_REG_MASK), OP_0x53("LD D, E", LOAD_REG_MASK),
        OP_0x54("LD D, H", LOAD_REG_MASK), OP_0x55("LD D, L", LOAD_REG_MASK), OP_0x56("LD D, (HL)", LOAD_FROM_MEM_MASK | DEST_REG_MASK), OP_0x57("LD D, A", LOAD_REG_MASK),

        // 0x58 - 0x5F: LD E, r
        OP_0x58("LD E, B", LOAD_REG_MASK), OP_0x59("LD E, C", LOAD_REG_MASK), OP_0x5A("LD E, D", LOAD_REG_MASK), OP_0x5B("LD E, E", LOAD_REG_MASK),
        // Note: 0x5C/0x5D are often undocumented IXL/IYH in some contexts, but standard Z80 is LD E, H/L
        OP_0x5C("LD E, H", LOAD_REG_MASK), OP_0x5D("LD E, L", LOAD_REG_MASK), OP_0x5E("LD E, (HL)", LOAD_FROM_MEM_MASK | DEST_REG_MASK), OP_0x5F("LD E, A", LOAD_REG_MASK),

        // 0x60 - 0x67: LD H, r
        OP_0x60("LD H, B", LOAD_REG_MASK), OP_0x61("LD H, C", LOAD_REG_MASK), OP_0x62("LD H, D", LOAD_REG_MASK), OP_0x63("LD H, E", LOAD_REG_MASK),
        OP_0x64("LD H, H", LOAD_REG_MASK), OP_0x65("LD H, L", LOAD_REG_MASK), OP_0x66("LD H, (HL)", LOAD_FROM_MEM_MASK | DEST_REG_MASK), OP_0x67("LD H, A", LOAD_REG_MASK),

        // 0x68 - 0x6F: LD L, r
        OP_0x68("LD L, B", LOAD_REG_MASK), OP_0x69("LD L, C", LOAD_REG_MASK), OP_0x6A("LD L, D", LOAD_REG_MASK), OP_0x6B("LD L, E", LOAD_REG_MASK),
        OP_0x6C("LD L, H", LOAD_REG_MASK), OP_0x6D("LD L, L", LOAD_REG_MASK), OP_0x6E("LD L, (HL)", LOAD_FROM_MEM_MASK | DEST_REG_MASK), OP_0x6F("LD L, A", LOAD_REG_MASK),

        // 0x70 - 0x77: LD (HL), r and HALT
        OP_0x70("LD (HL), B", STORE_TO_MEM_MASK), OP_0x71("LD (HL), C", STORE_TO_MEM_MASK), OP_0x72("LD (HL), D", STORE_TO_MEM_MASK), OP_0x73("LD (HL), E", STORE_TO_MEM_MASK),
        OP_0x74("LD (HL), H", STORE_TO_MEM_MASK), OP_0x75("LD (HL), L", STORE_TO_MEM_MASK), OP_0x76("HALT", 0), OP_0x77("LD (HL), A", STORE_TO_MEM_MASK),

        // 0x78 - 0x7F: LD A, r
        OP_0x78("LD A, B", LOAD_REG_MASK), OP_0x79("LD A, C", LOAD_REG_MASK), OP_0x7A("LD A, D", LOAD_REG_MASK), OP_0x7B("LD A, E", LOAD_REG_MASK),
        OP_0x7C("LD A, H", LOAD_REG_MASK), OP_0x7D("LD A, L", LOAD_REG_MASK), OP_0x7E("LD A, (HL)", LOAD_FROM_MEM_MASK | DEST_REG_MASK), OP_0x7F("LD A, A", LOAD_REG_MASK),

        // 0x80 - 0x87: ADD A, r
        OP_0x80("ADD A, B", ALU_MASK | DEST_REG_MASK), OP_0x81("ADD A, C", ALU_MASK | DEST_REG_MASK), OP_0x82("ADD A, D", ALU_MASK | DEST_REG_MASK), OP_0x83("ADD A, E", ALU_MASK | DEST_REG_MASK),
        OP_0x84("ADD A, H", ALU_MASK | DEST_REG_MASK), OP_0x85("ADD A, L", ALU_MASK | DEST_REG_MASK), OP_0x86("ADD A, (HL)", ALU_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK), OP_0x87("ADD A, A", ALU_MASK | DEST_REG_MASK),

        // 0x88 - 0x8F: ADC A, r
        OP_0x88("ADC A, B", ALU_MASK | DEST_REG_MASK), OP_0x89("ADC A, C", ALU_MASK | DEST_REG_MASK), OP_0x8A("ADC A, D", ALU_MASK | DEST_REG_MASK), OP_0x8B("ADC A, E", ALU_MASK | DEST_REG_MASK),
        OP_0x8C("ADC A, H", ALU_MASK | DEST_REG_MASK), OP_0x8D("ADC A, L", ALU_MASK | DEST_REG_MASK), OP_0x8E("ADC A, (HL)", ALU_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK), OP_0x8F("ADC A, A", ALU_MASK | DEST_REG_MASK),

        // 0x90 - 0x97: SUB r
        OP_0x90("SUB B", ALU_MASK | DEST_REG_MASK), OP_0x91("SUB C", ALU_MASK | DEST_REG_MASK), OP_0x92("SUB D", ALU_MASK | DEST_REG_MASK), OP_0x93("SUB E", ALU_MASK | DEST_REG_MASK),
        OP_0x94("SUB H", ALU_MASK | DEST_REG_MASK), OP_0x95("SUB L", ALU_MASK | DEST_REG_MASK), OP_0x96("SUB (HL)", ALU_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK), OP_0x97("SUB A", ALU_MASK | DEST_REG_MASK),

        // 0x98 - 0x9F: SBC A, r
        OP_0x98("SBC A, B", ALU_MASK | DEST_REG_MASK), OP_0x99("SBC A, C", ALU_MASK | DEST_REG_MASK), OP_0x9A("SBC A, D", ALU_MASK | DEST_REG_MASK), OP_0x9B("SBC A, E", ALU_MASK | DEST_REG_MASK),
        OP_0x9C("SBC A, H", ALU_MASK | DEST_REG_MASK), OP_0x9D("SBC A, L", ALU_MASK | DEST_REG_MASK), OP_0x9E("SBC A, (HL)", ALU_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK), OP_0x9F("SBC A, A", ALU_MASK | DEST_REG_MASK),

        // 0xA0 - 0xA7: AND r
        OP_0xA0("AND B", ALU_MASK | DEST_REG_MASK), OP_0xA1("AND C", ALU_MASK | DEST_REG_MASK), OP_0xA2("AND D", ALU_MASK | DEST_REG_MASK), OP_0xA3("AND E", ALU_MASK | DEST_REG_MASK),
        OP_0xA4("AND H", ALU_MASK | DEST_REG_MASK), OP_0xA5("AND L", ALU_MASK | DEST_REG_MASK), OP_0xA6("AND (HL)", ALU_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK), OP_0xA7("AND A", ALU_MASK), // OR/AND A = Test

        // 0xA8 - 0xAF: XOR r
        OP_0xA8("XOR B", ALU_MASK | DEST_REG_MASK), OP_0xA9("XOR C", ALU_MASK | DEST_REG_MASK), OP_0xAA("XOR D", ALU_MASK | DEST_REG_MASK), OP_0xAB("XOR E", ALU_MASK | DEST_REG_MASK),
        OP_0xAC("XOR H", ALU_MASK | DEST_REG_MASK), OP_0xAD("XOR L", ALU_MASK | DEST_REG_MASK), OP_0xAE("XOR (HL)", ALU_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK), OP_0xAF("XOR A", ALU_MASK), // XOR A = Clear

        // 0xB0 - 0xB7: OR r
        OP_0xB0("OR B", ALU_MASK | DEST_REG_MASK), OP_0xB1("OR C", ALU_MASK | DEST_REG_MASK), OP_0xB2("OR D", ALU_MASK | DEST_REG_MASK), OP_0xB3("OR E", ALU_MASK | DEST_REG_MASK),
        OP_0xB4("OR H", ALU_MASK | DEST_REG_MASK), OP_0xB5("OR L", ALU_MASK | DEST_REG_MASK), OP_0xB6("OR (HL)", ALU_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK), OP_0xB7("OR A", ALU_MASK),

        // 0xB8 - 0xBF: CP r (Compare - never writes to register)
        OP_0xB8("CP B", ALU_MASK), OP_0xB9("CP C", ALU_MASK), OP_0xBA("CP D", ALU_MASK), OP_0xBB("CP E", ALU_MASK),
        OP_0xBC("CP H", ALU_MASK), OP_0xBD("CP L", ALU_MASK), OP_0xBE("CP (HL)", ALU_MASK | LOAD_FROM_MEM_MASK), OP_0xBF("CP A", ALU_MASK),

        // 0xC0 - 0xC7: Returns and Jumps
        OP_0xC0("RET NZ", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK), OP_0xC1("POP BC", STACK_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK),
        OP_0xC2("JP NZ, nn", JUMP_MASK | IMM16_MASK), OP_0xC3("JP nn", JUMP_MASK | IMM16_MASK),
        OP_0xC4("CALL NZ, nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK), OP_0xC5("PUSH BC", STACK_MASK | STORE_TO_MEM_MASK),
        OP_0xC6("ADD A, n", ALU_MASK | DEST_REG_MASK | IMM8_MASK), OP_0xC7("RST 00H", JUMP_MASK | RST_MASK | STORE_TO_MEM_MASK),

        // 0xC8 - 0xCF
        OP_0xC8("RET Z", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK), OP_0xC9("RET", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK),
        OP_0xCA("JP Z, nn", JUMP_MASK | IMM16_MASK), OP_0xCB("PREFIX CB", PREFIX_MASK),
        OP_0xCC("CALL Z, nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK), OP_0xCD("CALL nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK),
        OP_0xCE("ADC A, n", ALU_MASK | DEST_REG_MASK | IMM8_MASK), OP_0xCF("RST 08H", JUMP_MASK | RST_MASK | STORE_TO_MEM_MASK),
        // 0xD0 - 0xD7
        OP_0xD0("RET NC", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK), OP_0xD1("POP DE", STACK_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK),
        OP_0xD2("JP NC, nn", JUMP_MASK | IMM16_MASK), OP_0xD3("OUT (n), A", IMM8_MASK), // IO
        OP_0xD4("CALL NC, nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK), OP_0xD5("PUSH DE", STACK_MASK | STORE_TO_MEM_MASK),
        OP_0xD6("SUB n", ALU_MASK | DEST_REG_MASK | IMM8_MASK), OP_0xD7("RST 10H", JUMP_MASK | RST_MASK | STORE_TO_MEM_MASK),

        // 0xD8 - 0xDF
        OP_0xD8("RET C", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK), OP_0xD9("EXX", 0),
        OP_0xDA("JP C, nn", JUMP_MASK | IMM16_MASK), OP_0xDB("IN A, (n)", DEST_REG_MASK | IMM8_MASK), // IO
        OP_0xDC("CALL C, nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK), OP_0xDD("PREFIX DD", PREFIX_MASK),
        OP_0xDE("SBC A, n", ALU_MASK | DEST_REG_MASK | IMM8_MASK), OP_0xDF("RST 18H", JUMP_MASK | RST_MASK | STORE_TO_MEM_MASK),

        // 0xE0 - 0xE7
        OP_0xE0("RET PO", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK), OP_0xE1("POP HL", STACK_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK),
        OP_0xE2("JP PO, nn", JUMP_MASK | IMM16_MASK), OP_0xE3("EX (SP), HL", STACK_MASK | LOAD_FROM_MEM_MASK | STORE_TO_MEM_MASK),
        OP_0xE4("CALL PO, nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK), OP_0xE5("PUSH HL", STACK_MASK | STORE_TO_MEM_MASK),
        OP_0xE6("AND n", ALU_MASK | DEST_REG_MASK | IMM8_MASK), OP_0xE7("RST 20H", JUMP_MASK | RST_MASK | STORE_TO_MEM_MASK),

        // 0xE8 - 0xEF
        OP_0xE8("RET PE", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK), OP_0xE9("JP (HL)", JUMP_MASK),
        OP_0xEA("JP PE, nn", JUMP_MASK | IMM16_MASK), OP_0xEB("EX DE, HL", LOAD_REG_MASK),
        OP_0xEC("CALL PE, nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK), OP_0xED("PREFIX ED", PREFIX_MASK),
        OP_0xEE("XOR n", ALU_MASK | DEST_REG_MASK | IMM8_MASK), OP_0xEF("RST 28H", JUMP_MASK | RST_MASK | STORE_TO_MEM_MASK),

        // 0xF0 - 0xF7
        OP_0xF0("RET P", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK), OP_0xF1("POP AF", STACK_MASK | ALU_MASK | DEST_REG_MASK | LOAD_FROM_MEM_MASK),
        OP_0xF2("JP P, nn", JUMP_MASK | IMM16_MASK), OP_0xF3("DI", 0),
        OP_0xF4("CALL P, nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK), OP_0xF5("PUSH AF", STACK_MASK | STORE_TO_MEM_MASK),
        OP_0xF6("OR n", ALU_MASK | DEST_REG_MASK | IMM8_MASK), OP_0xF7("RST 30H", JUMP_MASK | RST_MASK | STORE_TO_MEM_MASK),

        // 0xF8 - 0xFF
        OOP_0xF8("RET M", JUMP_MASK | RET_MASK | LOAD_FROM_MEM_MASK), OP_0xF9("LD SP, HL", LOAD_REG_MASK),
        OP_0xFA("JP M, nn", JUMP_MASK | IMM16_MASK), OP_0xFB("EI", 0),
        OP_0xFC("CALL M, nn", JUMP_MASK | CALL_MASK | STORE_TO_MEM_MASK | IMM16_MASK), OP_0xFD("PREFIX FD", PREFIX_MASK),
        OP_0xFE("CP n", ALU_MASK | IMM8_MASK), OP_0xFF("RST 38H", JUMP_MASK | RST_MASK | STORE_TO_MEM_MASK);

        private final int opcode, traits, immSize;
        private final String mnemonic;

        public static final Z80OpcodeSpec[] opValues = Z80OpcodeSpec.values();
        Z80OpcodeSpec(String m, int t) {
            opcode = ordinal();
            traits = t;
            mnemonic = m;
            immSize = calcImmediateSize();
        }

        public int getOpcode() {
            return opcode;
        }

        public int getImmSize() {
            return immSize;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public boolean isJumpOpcode() {
            return (traits & JUMP_MASK) > 0;
        }

        public boolean isLoadFromMem() {
            return (traits & LOAD_FROM_MEM_MASK) > 0;
        }

        public boolean isLoadReg() {
            return (traits & LOAD_REG_MASK) > 0;
        }

        public boolean isPrefix() {
            return (traits & PREFIX_MASK) > 0;
        }

        public boolean isRet() {
            return (traits & RET_MASK) > 0;
        }

        public boolean isStack() {
            return (traits & STACK_MASK) > 0;
        }

        private int calcImmediateSize() {
            if ((traits & IMM16_MASK) != 0) return 2;
            if ((traits & IMM8_MASK) != 0) return 1;
            return 0;
        }

        public static Z80OpcodeSpec fromOpcode(int val) {
            assert val >= 0 && val < 0x100;
            return opValues[val];
        }

        public static Z80OpcodeSpec fromOpcode(byte valb) {
            return opValues[valb & 0xFF];
        }
    }

    public static void main(String[] args) {
        for (Z80OpcodeSpec op : Z80OpcodeSpec.values()) {
            if (op.isJumpOpcode() && op.getImmSize() == 1) {
                System.out.println(op);
            }
        }
    }
}
