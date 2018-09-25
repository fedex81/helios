/* Auto-generated file. Do not modify. */
package omegadrive.z80.disasm;

import emulib.plugins.cpu.DecodedInstruction;
import emulib.plugins.cpu.Decoder;
import emulib.plugins.memory.MemoryContext;
import emulib.runtime.exceptions.InvalidInstructionException;

import java.util.Arrays;

/**
 * The instruction decoder.
 */
public class Z80Decoder implements Decoder {
    private MemoryContext memory;
    private int memoryPosition;
    private int unit;
    private byte[] instructionBytes = new byte[1024];
    private int bytesRead;
    private DecodedInstruction instruction;

    public static final int ADD = 1;
    public static final int ADC = 2;
    public static final int SUB = 3;
    public static final int SBC = 4;
    public static final int AND = 5;
    public static final int XOR = 6;
    public static final int OR = 7;
    public static final int CP = 8;
    public static final int LD = 9;
    public static final int NOP = 10;
    public static final int EX__SP__HL = 11;
    public static final int EX_DE_HL = 12;
    public static final int LD_SP_HL = 13;
    public static final int EI = 14;
    public static final int DI = 15;
    public static final int HALT = 16;
    public static final int DAA = 17;
    public static final int CPL = 18;
    public static final int SCF = 19;
    public static final int CCF = 20;
    public static final int RET = 21;
    public static final int RLCA = 22;
    public static final int RRCA = 23;
    public static final int RLA = 24;
    public static final int RRA = 25;
    public static final int JP__HL_ = 26;
    public static final int RET_NZ = 27;
    public static final int RET_Z = 28;
    public static final int RET_NC = 29;
    public static final int RET_C = 30;
    public static final int RET_PO = 31;
    public static final int RET_PE = 32;
    public static final int RET_P = 33;
    public static final int RET_M = 34;
    public static final int EX_AF_AF_ = 35;
    public static final int EXX = 36;
    public static final int ADD_A_ = 37;
    public static final int ADC_A_ = 38;
    public static final int IN_A_ = 39;
    public static final int JR = 40;
    public static final int DJNZ = 41;
    public static final int JR_NZ_ = 42;
    public static final int JR_Z_ = 43;
    public static final int JR_NC_ = 44;
    public static final int JR_C_ = 45;
    public static final int JP = 46;
    public static final int JP_NZ_ = 47;
    public static final int JP_Z_ = 48;
    public static final int JP_NC_ = 49;
    public static final int JP_C_ = 50;
    public static final int JP_PO_ = 51;
    public static final int JP_PE_ = 52;
    public static final int JP_P_ = 53;
    public static final int JP_M_ = 54;
    public static final int CALL = 55;
    public static final int CALL_NZ_ = 56;
    public static final int CALL_Z_ = 57;
    public static final int CALL_NC_ = 58;
    public static final int CALL_C_ = 59;
    public static final int CALL_PO_ = 60;
    public static final int CALL_PE_ = 61;
    public static final int CALL_P_ = 62;
    public static final int CALL_M_ = 63;
    public static final int OUT = 64;
    public static final int INC = 65;
    public static final int DEC = 66;
    public static final int ADD_HL_ = 67;
    public static final int POP = 68;
    public static final int PUSH = 69;
    public static final int RST = 70;
    public static final int INSTRUCTION = 71;
    public static final int RLC = 72;
    public static final int RL = 73;
    public static final int RRC = 74;
    public static final int RR = 75;
    public static final int SLA = 76;
    public static final int SRA = 77;
    public static final int SRL = 78;
    public static final int SLL = 79;
    public static final int BIT = 80;
    public static final int RES = 81;
    public static final int SET = 82;
    public static final int CBINSTR = 83;
    public static final int LD_SP_IX = 84;
    public static final int EX__SP__IX = 85;
    public static final int INC_IX = 86;
    public static final int DEC_IX = 87;
    public static final int JP__IX_ = 88;
    public static final int PUSH_IX = 89;
    public static final int POP_IX = 90;
    public static final int DDINSTR = 91;
    public static final int LD_SP_IY = 92;
    public static final int EX__SP__IY = 93;
    public static final int INC_IY = 94;
    public static final int DEC_IY = 95;
    public static final int JP__IY_ = 96;
    public static final int PUSH_IY = 97;
    public static final int POP_IY = 98;
    public static final int FDINSTR = 99;
    public static final int DDCBINSTR = 100;
    public static final int FDCBINSTR = 101;
    public static final int ADC_HL_ = 102;
    public static final int SBC_HL_ = 103;
    public static final int IM_0 = 104;
    public static final int IM_1 = 105;
    public static final int IM_2 = 106;
    public static final int LD_A_I = 107;
    public static final int LD_I_A = 108;
    public static final int LD_A_R = 109;
    public static final int LD_R_A = 110;
    public static final int NEG = 111;
    public static final int RLD = 112;
    public static final int RRD = 113;
    public static final int CPI = 114;
    public static final int CPIR = 115;
    public static final int CPD = 116;
    public static final int CPDR = 117;
    public static final int RETI = 118;
    public static final int RETN = 119;
    public static final int INI = 120;
    public static final int INIR = 121;
    public static final int IND = 122;
    public static final int INDR = 123;
    public static final int OUTI = 124;
    public static final int OTIR = 125;
    public static final int OUTD = 126;
    public static final int OTDR = 127;
    public static final int LDI = 128;
    public static final int LDIR = 129;
    public static final int LDD = 130;
    public static final int LDDR = 131;
    public static final int IN = 132;
    public static final int EDINSTR = 133;
    public static final int BC = 134;
    public static final int DE = 135;
    public static final int IX = 136;
    public static final int SP = 137;
    public static final int BCDEIXSP = 138;
    public static final int IY = 139;
    public static final int BCDEIYSP = 140;
    public static final int BCDE = 141;
    public static final int HL = 142;
    public static final int HLSP = 143;
    public static final int BCDE_STAX = 144;
    public static final int BCDE_LDAX = 145;
    public static final int AF = 146;
    public static final int HLPSW = 147;
    public static final int B = 148;
    public static final int C = 149;
    public static final int D = 150;
    public static final int E = 151;
    public static final int H = 152;
    public static final int L = 153;
    public static final int _HL_ = 154;
    public static final int A = 155;
    public static final int REG = 156;
    public static final int REG_OUT = 157;
    public static final int REG_BCDE = 158;
    public static final int REG_HL = 159;
    public static final int REG_A = 160;
    public static final int M = 161;
    public static final int _00 = 162;
    public static final int _08 = 163;
    public static final int _10 = 164;
    public static final int _18 = 165;
    public static final int _20 = 166;
    public static final int _28 = 167;
    public static final int _30 = 168;
    public static final int _38 = 169;
    public static final int NUMBER = 170;
    public static final int IMM8 = 171;
    public static final int INDEX = 172;
    public static final int IMM8_OUT = 173;
    public static final int IMM16 = 174;
    public static final int ADDRESS = 175;
    public static final int ADDRESS_LDIX = 176;
    public static final int IMM16_SHLD = 177;
    public static final int IMM16_LHLD = 178;
    public static final int IMM16_LDA = 179;
    public static final int IMM16_STA = 180;


    /**
     * The constructor.
     *
     * @param memory the memory context which will be used to read instructions
     */
    public Z80Decoder(MemoryContext memory) {
        this.memory = memory;
    }

    /**
     * Decodes an instruction.
     *
     * @param memoryPosition the address of the start of the instruction
     * @return the decoded instruction object
     * @throws InvalidInstructionException when decoding is not successful
     */
    @Override
    public DecodedInstruction decode(int memoryPosition) throws InvalidInstructionException {
        this.memoryPosition = memoryPosition;
        bytesRead = 0;

        instruction = new DecodedInstruction();
        instruction(0);
        instruction.setImage(Arrays.copyOfRange(instructionBytes, 0, bytesRead));
        return instruction;
    }

    /**
     * Reads an arbitrary number of bits of the current instruction into a byte array.
     *
     * @param start  the number of bits from the start of the current instruction
     * @param length the number of bits to read
     * @return the bytes read
     */
    private byte[] readBytes(int start, int length) {
        int startByte = start / 8;
        int endByte = (start + length - 1) / 8;
        int clear = start % 8;
        int shift = (8 - ((start + length) % 8)) % 8;

        while (bytesRead <= endByte) {
            instructionBytes[bytesRead++] = ((Number) memory.read(memoryPosition++)).byteValue();
        }

        byte[] result = Arrays.copyOfRange(instructionBytes, startByte, endByte + 1);
        result[0] &= 0xFF >> clear;

        // right shift all bits
        for (int i = result.length - 1; i >= 0; i--) {
            result[i] = (byte) ((result[i] & 0xFF) >>> shift);
            if (i > 0)
                result[i] |= (result[i - 1] & (0xFF >>> (8 - shift))) << (8 - shift);
        }

        // if the leftmost byte is now unused
        if (result.length > 8 * length)
            result = Arrays.copyOfRange(result, 1, result.length);

        return result;
    }

    /**
     * Reads at most one unit (int) of the current instruction.
     *
     * @param start  the number of bits from the start of the current instruction
     * @param length the number of bits to read
     * @return the bits read
     */
    private int read(int start, int length) {
        int number = 0;
        byte[] bytes = readBytes(start, length);

        for (int i = 0; i < bytes.length; i++)
            number |= (bytes[i] & 0xFF) << (8 * (bytes.length - i - 1));

        return number;
    }

    private void instruction(int start) throws InvalidInstructionException {
        unit = read(start + 0, 5);

        switch (unit & 0x1f) {
            case 0x10:
                instruction.add(INSTRUCTION, "add", ADD);
                REG(start + 5, REG);
                break;
            case 0x11:
                instruction.add(INSTRUCTION, "adc", ADC);
                REG(start + 5, REG);
                break;
            case 0x12:
                instruction.add(INSTRUCTION, "sub", SUB);
                REG(start + 5, REG);
                break;
            case 0x13:
                instruction.add(INSTRUCTION, "sbc", SBC);
                REG(start + 5, REG);
                break;
            case 0x14:
                instruction.add(INSTRUCTION, "and", AND);
                REG(start + 5, REG);
                break;
            case 0x15:
                instruction.add(INSTRUCTION, "xor", XOR);
                REG(start + 5, REG);
                break;
            case 0x16:
                instruction.add(INSTRUCTION, "or", OR);
                REG(start + 5, REG);
                break;
            case 0x17:
                instruction.add(INSTRUCTION, "cp", CP);
                REG(start + 5, REG);
                break;
            case 0x0f:
                instruction.add(INSTRUCTION, "ld", LD);
                REG_A(start + 2);
                REG(start + 5, REG);
                break;
            default:
                unit = read(start + 0, 5);

                switch (unit & 0x1c) {
                    case 0x08:
                        instruction.add(INSTRUCTION, "ld", LD);
                        REG_BCDE(start + 3);
                        REG(start + 5, REG);
                        break;
                    default:
                        unit = read(start + 0, 5);

                        switch (unit & 0x1e) {
                            case 0x0c:
                                instruction.add(INSTRUCTION, "ld", LD);
                                REG_HL(start + 4);
                                REG(start + 5, REG);
                                break;
                            default:
                                unit = read(start + 0, 6);

                                switch (unit & 0x3f) {
                                    case 0x1c:
                                        instruction.add(INSTRUCTION, "ld", LD);
                                        REG_BCDE(start + 6);
                                        break;
                                    default:
                                        unit = read(start + 0, 7);

                                        switch (unit & 0x7f) {
                                            case 0x3a:
                                                instruction.add(INSTRUCTION, "ld", LD);
                                                REG_HL(start + 7);
                                                break;
                                            default:
                                                unit = read(start + 0, 8);

                                                switch (unit & 0xff) {
                                                    case 0x00:
                                                        instruction.add(INSTRUCTION, "nop", NOP);
                                                        break;
                                                    case 0xe3:
                                                        instruction.add(INSTRUCTION, "ex (SP),HL", EX__SP__HL);
                                                        break;
                                                    case 0xeb:
                                                        instruction.add(INSTRUCTION, "ex DE,HL", EX_DE_HL);
                                                        break;
                                                    case 0xf9:
                                                        instruction.add(INSTRUCTION, "ld SP,HL", LD_SP_HL);
                                                        break;
                                                    case 0xfb:
                                                        instruction.add(INSTRUCTION, "ei", EI);
                                                        break;
                                                    case 0xf3:
                                                        instruction.add(INSTRUCTION, "di", DI);
                                                        break;
                                                    case 0x76:
                                                        instruction.add(INSTRUCTION, "halt", HALT);
                                                        break;
                                                    case 0x27:
                                                        instruction.add(INSTRUCTION, "daa", DAA);
                                                        break;
                                                    case 0x2f:
                                                        instruction.add(INSTRUCTION, "cpl", CPL);
                                                        break;
                                                    case 0x37:
                                                        instruction.add(INSTRUCTION, "scf", SCF);
                                                        break;
                                                    case 0x3f:
                                                        instruction.add(INSTRUCTION, "ccf", CCF);
                                                        break;
                                                    case 0xc9:
                                                        instruction.add(INSTRUCTION, "ret", RET);
                                                        break;
                                                    case 0x07:
                                                        instruction.add(INSTRUCTION, "rlca", RLCA);
                                                        break;
                                                    case 0x0f:
                                                        instruction.add(INSTRUCTION, "rrca", RRCA);
                                                        break;
                                                    case 0x17:
                                                        instruction.add(INSTRUCTION, "rla", RLA);
                                                        break;
                                                    case 0x1f:
                                                        instruction.add(INSTRUCTION, "rra", RRA);
                                                        break;
                                                    case 0xe9:
                                                        instruction.add(INSTRUCTION, "jp (HL)", JP__HL_);
                                                        break;
                                                    case 0xc0:
                                                        instruction.add(INSTRUCTION, "ret NZ", RET_NZ);
                                                        break;
                                                    case 0xc8:
                                                        instruction.add(INSTRUCTION, "ret Z", RET_Z);
                                                        break;
                                                    case 0xd0:
                                                        instruction.add(INSTRUCTION, "ret NC", RET_NC);
                                                        break;
                                                    case 0xd8:
                                                        instruction.add(INSTRUCTION, "ret C", RET_C);
                                                        break;
                                                    case 0xe0:
                                                        instruction.add(INSTRUCTION, "ret PO", RET_PO);
                                                        break;
                                                    case 0xe8:
                                                        instruction.add(INSTRUCTION, "ret PE", RET_PE);
                                                        break;
                                                    case 0xf0:
                                                        instruction.add(INSTRUCTION, "ret P", RET_P);
                                                        break;
                                                    case 0xf8:
                                                        instruction.add(INSTRUCTION, "ret M", RET_M);
                                                        break;
                                                    case 0x08:
                                                        instruction.add(INSTRUCTION, "ex AF,AF'", EX_AF_AF_);
                                                        break;
                                                    case 0xd9:
                                                        instruction.add(INSTRUCTION, "exx", EXX);
                                                        break;
                                                    case 0xde:
                                                        instruction.add(INSTRUCTION, "sbc", SBC);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xc6:
                                                        instruction.add(INSTRUCTION, "add A,", ADD_A_);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xce:
                                                        instruction.add(INSTRUCTION, "adc A,", ADC_A_);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xd6:
                                                        instruction.add(INSTRUCTION, "sub", SUB);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xe6:
                                                        instruction.add(INSTRUCTION, "and", AND);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xf6:
                                                        instruction.add(INSTRUCTION, "or", OR);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xee:
                                                        instruction.add(INSTRUCTION, "xor", XOR);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xfe:
                                                        instruction.add(INSTRUCTION, "cp", CP);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xdb:
                                                        instruction.add(INSTRUCTION, "in A,", IN_A_);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0x18:
                                                        instruction.add(INSTRUCTION, "jr", JR);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0x10:
                                                        instruction.add(INSTRUCTION, "djnz", DJNZ);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0x20:
                                                        instruction.add(INSTRUCTION, "jr NZ,", JR_NZ_);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0x28:
                                                        instruction.add(INSTRUCTION, "jr Z,", JR_Z_);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0x30:
                                                        instruction.add(INSTRUCTION, "jr NC,", JR_NC_);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0x38:
                                                        instruction.add(INSTRUCTION, "jr C,", JR_C_);
                                                        imm8(start + 8, IMM8);
                                                        break;
                                                    case 0xc3:
                                                        instruction.add(INSTRUCTION, "jp", JP);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xc2:
                                                        instruction.add(INSTRUCTION, "jp NZ,", JP_NZ_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xca:
                                                        instruction.add(INSTRUCTION, "jp Z,", JP_Z_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xd2:
                                                        instruction.add(INSTRUCTION, "jp NC,", JP_NC_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xda:
                                                        instruction.add(INSTRUCTION, "jp C,", JP_C_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xe2:
                                                        instruction.add(INSTRUCTION, "jp PO,", JP_PO_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xea:
                                                        instruction.add(INSTRUCTION, "jp PE,", JP_PE_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xf2:
                                                        instruction.add(INSTRUCTION, "jp P,", JP_P_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xfa:
                                                        instruction.add(INSTRUCTION, "jp M,", JP_M_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xcd:
                                                        instruction.add(INSTRUCTION, "call", CALL);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xc4:
                                                        instruction.add(INSTRUCTION, "call NZ,", CALL_NZ_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xcc:
                                                        instruction.add(INSTRUCTION, "call Z,", CALL_Z_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xd4:
                                                        instruction.add(INSTRUCTION, "call NC,", CALL_NC_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xdc:
                                                        instruction.add(INSTRUCTION, "call C,", CALL_C_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xe4:
                                                        instruction.add(INSTRUCTION, "call PO,", CALL_PO_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xec:
                                                        instruction.add(INSTRUCTION, "call PE,", CALL_PE_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xf4:
                                                        instruction.add(INSTRUCTION, "call P,", CALL_P_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xfc:
                                                        instruction.add(INSTRUCTION, "call M,", CALL_M_);
                                                        imm16(start + 8, ADDRESS);
                                                        break;
                                                    case 0xd3:
                                                        instruction.add(INSTRUCTION, "out", OUT);
                                                        imm8(start + 8, IMM8_OUT);
                                                        break;
                                                    case 0x2a:
                                                        instruction.add(INSTRUCTION, "ld", LD);
                                                        imm16(start + 8, IMM16_LHLD);
                                                        break;
                                                    case 0x22:
                                                        instruction.add(INSTRUCTION, "ld", LD);
                                                        imm16(start + 8, IMM16_SHLD);
                                                        break;
                                                    case 0x3a:
                                                        instruction.add(INSTRUCTION, "ld", LD);
                                                        imm16(start + 8, IMM16_LDA);
                                                        break;
                                                    case 0x32:
                                                        instruction.add(INSTRUCTION, "ld", LD);
                                                        imm16(start + 8, IMM16_STA);
                                                        break;
                                                    case 0x77:
                                                        instruction.add(INSTRUCTION, "ld", LD);
                                                        REG_A(start + 5);
                                                        break;
                                                    case 0xdd:
                                                        ddINSTR(start + 8);
                                                        break;
                                                    case 0xfd:
                                                        fdINSTR(start + 8);
                                                        break;
                                                    case 0xcb:
                                                        cbINSTR(start + 8);
                                                        break;
                                                    case 0xed:
                                                        edINSTR(start + 8);
                                                        break;
                                                    default:
                                                        unit = read(start + 0, 8);

                                                        switch (unit & 0xef) {
                                                            case 0x03:
                                                                instruction.add(INSTRUCTION, "inc", INC);
                                                                BCDE(start + 3);
                                                                break;
                                                            case 0x0b:
                                                                instruction.add(INSTRUCTION, "dec", DEC);
                                                                BCDE(start + 3);
                                                                break;
                                                            case 0x09:
                                                                instruction.add(INSTRUCTION, "add HL,", ADD_HL_);
                                                                BCDE(start + 3);
                                                                break;
                                                            case 0xc1:
                                                                instruction.add(INSTRUCTION, "pop", POP);
                                                                BCDE(start + 3);
                                                                break;
                                                            case 0xc5:
                                                                instruction.add(INSTRUCTION, "push", PUSH);
                                                                BCDE(start + 3);
                                                                break;
                                                            case 0x23:
                                                                instruction.add(INSTRUCTION, "inc", INC);
                                                                HLSP(start + 3);
                                                                break;
                                                            case 0x2b:
                                                                instruction.add(INSTRUCTION, "dec", DEC);
                                                                HLSP(start + 3);
                                                                break;
                                                            case 0x29:
                                                                instruction.add(INSTRUCTION, "add HL,", ADD_HL_);
                                                                HLSP(start + 3);
                                                                break;
                                                            case 0xe1:
                                                                instruction.add(INSTRUCTION, "pop", POP);
                                                                HLPSW(start + 3);
                                                                break;
                                                            case 0xe5:
                                                                instruction.add(INSTRUCTION, "push", PUSH);
                                                                HLPSW(start + 3);
                                                                break;
                                                            case 0x01:
                                                                instruction.add(INSTRUCTION, "ld", LD);
                                                                BCDE(start + 3);
                                                                imm16(start + 8, IMM16);
                                                                break;
                                                            case 0x21:
                                                                instruction.add(INSTRUCTION, "ld", LD);
                                                                HLSP(start + 3);
                                                                imm16(start + 8, IMM16);
                                                                break;
                                                            case 0x0a:
                                                                instruction.add(INSTRUCTION, "ld", LD);
                                                                BCDE_STAX(start + 3, BCDE_LDAX);
                                                                break;
                                                            case 0x02:
                                                                instruction.add(INSTRUCTION, "ld", LD);
                                                                BCDE_STAX(start + 3, BCDE_STAX);
                                                                break;
                                                            default:
                                                                unit = read(start + 0, 8);

                                                                switch (unit & 0xc7) {
                                                                    case 0x04:
                                                                        instruction.add(INSTRUCTION, "inc", INC);
                                                                        REG(start + 2, REG);
                                                                        break;
                                                                    case 0x05:
                                                                        instruction.add(INSTRUCTION, "dec", DEC);
                                                                        REG(start + 2, REG);
                                                                        break;
                                                                    case 0xc7:
                                                                        instruction.add(INSTRUCTION, "rst", RST);
                                                                        NUMBER(start + 2);
                                                                        break;
                                                                    case 0x06:
                                                                        instruction.add(INSTRUCTION, "ld", LD);
                                                                        REG(start + 2, REG);
                                                                        imm8(start + 8, IMM8);
                                                                        break;
                                                                    default:
                                                                        throw new InvalidInstructionException();
                                                                }
                                                                break;
                                                        }
                                                        break;
                                                }
                                                break;
                                        }
                                        break;
                                }
                                break;
                        }
                        break;
                }
                break;
        }
    }

    private void cbINSTR(int start) throws InvalidInstructionException {
        unit = read(start + 0, 5);

        switch (unit & 0x1f) {
            case 0x00:
                instruction.add(CBINSTR, "rlc", RLC);
                REG(start + 5, REG);
                break;
            case 0x02:
                instruction.add(CBINSTR, "rl", RL);
                REG(start + 5, REG);
                break;
            case 0x01:
                instruction.add(CBINSTR, "rrc", RRC);
                REG(start + 5, REG);
                break;
            case 0x03:
                instruction.add(CBINSTR, "rr", RR);
                REG(start + 5, REG);
                break;
            case 0x04:
                instruction.add(CBINSTR, "sla", SLA);
                REG(start + 5, REG);
                break;
            case 0x05:
                instruction.add(CBINSTR, "sra", SRA);
                REG(start + 5, REG);
                break;
            case 0x07:
                instruction.add(CBINSTR, "srl", SRL);
                REG(start + 5, REG);
                break;
            case 0x06:
                instruction.add(CBINSTR, "sll", SLL);
                REG(start + 5, REG);
                break;
            default:
                unit = read(start + 0, 5);

                switch (unit & 0x18) {
                    case 0x08:
                        instruction.add(CBINSTR, "bit", BIT);
                        BIT(start + 2);
                        REG(start + 5, REG);
                        break;
                    case 0x10:
                        instruction.add(CBINSTR, "res", RES);
                        BIT(start + 2);
                        REG(start + 5, REG);
                        break;
                    case 0x18:
                        instruction.add(CBINSTR, "set", SET);
                        BIT(start + 2);
                        REG(start + 5, REG);
                        break;
                    default:
                        throw new InvalidInstructionException();
                }
                break;
        }
    }

    private void ddINSTR(int start) throws InvalidInstructionException {
        unit = read(start + 0, 8);

        switch (unit & 0xe7) {
            case 0x46:
                instruction.add(DDINSTR, "ld", LD);
                REG_BCDE(start + 3);
                imm8(start + 8, INDEX);
                break;
            default:
                unit = read(start + 0, 8);

                switch (unit & 0xf7) {
                    case 0x66:
                        instruction.add(DDINSTR, "ld", LD);
                        REG_HL(start + 4);
                        imm8(start + 8, INDEX);
                        break;
                    default:
                        unit = read(start + 0, 8);

                        switch (unit & 0xff) {
                            case 0x7e:
                                instruction.add(DDINSTR, "ld", LD);
                                REG_A(start + 2);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x77:
                                instruction.add(DDINSTR, "ld", LD);
                                M(start + 2);
                                REG_A(start + 5);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x21:
                                instruction.add(DDINSTR, "ld", LD);
                                imm16(start + 8, IMM16);
                                break;
                            case 0x2a:
                                instruction.add(DDINSTR, "ld", LD);
                                imm16(start + 8, ADDRESS);
                                break;
                            case 0x22:
                                instruction.add(DDINSTR, "ld", LD);
                                imm16(start + 8, ADDRESS_LDIX);
                                break;
                            case 0xf9:
                                instruction.add(DDINSTR, "ld SP,IX", LD_SP_IX);
                                break;
                            case 0xe3:
                                instruction.add(DDINSTR, "ex (SP),IX", EX__SP__IX);
                                break;
                            case 0x23:
                                instruction.add(DDINSTR, "inc IX", INC_IX);
                                break;
                            case 0x2b:
                                instruction.add(DDINSTR, "dec IX", DEC_IX);
                                break;
                            case 0xe9:
                                instruction.add(DDINSTR, "jp (IX)", JP__IX_);
                                break;
                            case 0xe5:
                                instruction.add(DDINSTR, "push IX", PUSH_IX);
                                break;
                            case 0xe1:
                                instruction.add(DDINSTR, "pop IX", POP_IX);
                                break;
                            case 0x86:
                                instruction.add(DDINSTR, "add", ADD);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x8e:
                                instruction.add(DDINSTR, "adc", ADC);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x96:
                                instruction.add(DDINSTR, "sub", SUB);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x9e:
                                instruction.add(DDINSTR, "sbc", SBC);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x34:
                                instruction.add(DDINSTR, "inc", INC);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x35:
                                instruction.add(DDINSTR, "dec", DEC);
                                imm8(start + 8, INDEX);
                                break;
                            case 0xa6:
                                instruction.add(DDINSTR, "and", AND);
                                imm8(start + 8, INDEX);
                                break;
                            case 0xae:
                                instruction.add(DDINSTR, "xor", XOR);
                                imm8(start + 8, INDEX);
                                break;
                            case 0xb6:
                                instruction.add(DDINSTR, "or", OR);
                                imm8(start + 8, INDEX);
                                break;
                            case 0xbe:
                                instruction.add(DDINSTR, "cp", CP);
                                imm8(start + 8, INDEX);
                                break;
                            default:
                                unit = read(start + 0, 8);

                                switch (unit & 0xfc) {
                                    case 0x70:
                                        instruction.add(DDINSTR, "ld", LD);
                                        M(start + 2);
                                        REG_BCDE(start + 6);
                                        imm8(start + 8, INDEX);
                                        break;
                                    default:
                                        unit = read(start + 0, 8);

                                        switch (unit & 0xfe) {
                                            case 0x74:
                                                instruction.add(DDINSTR, "ld", LD);
                                                M(start + 2);
                                                REG_HL(start + 7);
                                                imm8(start + 8, INDEX);
                                                break;
                                            default:
                                                unit = read(start + 0, 8);

                                                switch (unit & 0xcf) {
                                                    case 0x09:
                                                        instruction.add(DDINSTR, "add", ADD);
                                                        BCDEIXSP(start + 2);
                                                        break;
                                                    default:
                                                        unit = read(start + 0, 16);

                                                        switch (unit & 0xff00) {
                                                            case 0x3600:
                                                                instruction.add(DDINSTR, "ld", LD);
                                                                imm8(start + 8, INDEX);
                                                                imm8(start + 16, IMM8);
                                                                break;
                                                            case 0x7600:
                                                                instruction.add(DDINSTR, "ld", LD);
                                                                imm8(start + 8, INDEX);
                                                                imm8(start + 16, IMM8);
                                                                break;
                                                            case 0xcb00:
                                                                imm8(start + 8, INDEX);
                                                                ddcbINSTR(start + 16);
                                                                break;
                                                            default:
                                                                throw new InvalidInstructionException();
                                                        }
                                                        break;
                                                }
                                                break;
                                        }
                                        break;
                                }
                                break;
                        }
                        break;
                }
                break;
        }
    }

    private void fdINSTR(int start) throws InvalidInstructionException {
        unit = read(start + 0, 8);

        switch (unit & 0xe7) {
            case 0x46:
                instruction.add(FDINSTR, "ld", LD);
                REG_BCDE(start + 3);
                imm8(start + 8, INDEX);
                break;
            default:
                unit = read(start + 0, 8);

                switch (unit & 0xf7) {
                    case 0x66:
                        instruction.add(FDINSTR, "ld", LD);
                        REG_HL(start + 4);
                        imm8(start + 8, INDEX);
                        break;
                    default:
                        unit = read(start + 0, 8);

                        switch (unit & 0xff) {
                            case 0x7e:
                                instruction.add(FDINSTR, "ld", LD);
                                REG_A(start + 2);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x77:
                                instruction.add(FDINSTR, "ld", LD);
                                M(start + 2);
                                REG_A(start + 5);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x21:
                                instruction.add(FDINSTR, "ld", LD);
                                imm16(start + 8, IMM16);
                                break;
                            case 0x2a:
                                instruction.add(FDINSTR, "ld", LD);
                                imm16(start + 8, ADDRESS);
                                break;
                            case 0x22:
                                instruction.add(FDINSTR, "ld", LD);
                                imm16(start + 8, ADDRESS_LDIX);
                                break;
                            case 0xf9:
                                instruction.add(FDINSTR, "ld SP,IY", LD_SP_IY);
                                break;
                            case 0xe3:
                                instruction.add(FDINSTR, "ex (SP),IY", EX__SP__IY);
                                break;
                            case 0x23:
                                instruction.add(FDINSTR, "inc IY", INC_IY);
                                break;
                            case 0x2b:
                                instruction.add(FDINSTR, "dec IY", DEC_IY);
                                break;
                            case 0xe9:
                                instruction.add(FDINSTR, "jp (IY)", JP__IY_);
                                break;
                            case 0xe5:
                                instruction.add(FDINSTR, "push IY", PUSH_IY);
                                break;
                            case 0xe1:
                                instruction.add(FDINSTR, "pop IY", POP_IY);
                                break;
                            case 0x86:
                                instruction.add(FDINSTR, "add", ADD);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x8e:
                                instruction.add(FDINSTR, "adc", ADC);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x96:
                                instruction.add(FDINSTR, "sub", SUB);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x9e:
                                instruction.add(FDINSTR, "sbc", SBC);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x34:
                                instruction.add(FDINSTR, "inc", INC);
                                imm8(start + 8, INDEX);
                                break;
                            case 0x35:
                                instruction.add(FDINSTR, "dec", DEC);
                                imm8(start + 8, INDEX);
                                break;
                            case 0xa6:
                                instruction.add(FDINSTR, "and", AND);
                                imm8(start + 8, INDEX);
                                break;
                            case 0xae:
                                instruction.add(FDINSTR, "xor", XOR);
                                imm8(start + 8, INDEX);
                                break;
                            case 0xb6:
                                instruction.add(FDINSTR, "or", OR);
                                imm8(start + 8, INDEX);
                                break;
                            case 0xbe:
                                instruction.add(FDINSTR, "cp", CP);
                                imm8(start + 8, INDEX);
                                break;
                            default:
                                unit = read(start + 0, 8);

                                switch (unit & 0xfc) {
                                    case 0x70:
                                        instruction.add(FDINSTR, "ld", LD);
                                        M(start + 2);
                                        REG_BCDE(start + 6);
                                        imm8(start + 8, INDEX);
                                        break;
                                    default:
                                        unit = read(start + 0, 8);

                                        switch (unit & 0xfe) {
                                            case 0x74:
                                                instruction.add(FDINSTR, "ld", LD);
                                                M(start + 2);
                                                REG_HL(start + 7);
                                                imm8(start + 8, INDEX);
                                                break;
                                            default:
                                                unit = read(start + 0, 8);

                                                switch (unit & 0xcf) {
                                                    case 0x09:
                                                        instruction.add(FDINSTR, "add", ADD);
                                                        BCDEIYSP(start + 2);
                                                        break;
                                                    default:
                                                        unit = read(start + 0, 16);

                                                        switch (unit & 0xff00) {
                                                            case 0x3600:
                                                                instruction.add(FDINSTR, "ld", LD);
                                                                imm8(start + 8, INDEX);
                                                                imm8(start + 16, IMM8);
                                                                break;
                                                            case 0x7600:
                                                                instruction.add(FDINSTR, "ld", LD);
                                                                imm8(start + 8, INDEX);
                                                                imm8(start + 16, IMM8);
                                                                break;
                                                            case 0xcb00:
                                                                imm8(start + 8, INDEX);
                                                                fdcbINSTR(start + 16);
                                                                break;
                                                            default:
                                                                throw new InvalidInstructionException();
                                                        }
                                                        break;
                                                }
                                                break;
                                        }
                                        break;
                                }
                                break;
                        }
                        break;
                }
                break;
        }
    }

    private void ddcbINSTR(int start) throws InvalidInstructionException {
        unit = read(start + 0, 8);

        switch (unit & 0xff) {
            case 0x06:
                instruction.add(DDCBINSTR, "rlc", RLC);
                break;
            case 0x16:
                instruction.add(DDCBINSTR, "rl", RL);
                break;
            case 0x0e:
                instruction.add(DDCBINSTR, "rrc", RRC);
                break;
            case 0x1e:
                instruction.add(DDCBINSTR, "rr", RR);
                break;
            case 0x26:
                instruction.add(DDCBINSTR, "sla", SLA);
                break;
            case 0x2e:
                instruction.add(DDCBINSTR, "sra", SRA);
                break;
            case 0x3e:
                instruction.add(DDCBINSTR, "srl", SRL);
                break;
            case 0x36:
                instruction.add(DDCBINSTR, "sll", SLL);
                break;
            default:
                unit = read(start + 0, 8);

                switch (unit & 0xc7) {
                    case 0x46:
                        instruction.add(DDCBINSTR, "bit", BIT);
                        BIT(start + 2);
                        break;
                    case 0x86:
                        instruction.add(DDCBINSTR, "res", RES);
                        BIT(start + 2);
                        break;
                    case 0xc6:
                        instruction.add(DDCBINSTR, "set", SET);
                        BIT(start + 2);
                        break;
                    default:
                        throw new InvalidInstructionException();
                }
                break;
        }
    }

    private void fdcbINSTR(int start) throws InvalidInstructionException {
        unit = read(start + 0, 8);

        switch (unit & 0xff) {
            case 0x06:
                instruction.add(FDCBINSTR, "rlc", RLC);
                break;
            case 0x16:
                instruction.add(FDCBINSTR, "rl", RL);
                break;
            case 0x0e:
                instruction.add(FDCBINSTR, "rrc", RRC);
                break;
            case 0x1e:
                instruction.add(FDCBINSTR, "rr", RR);
                break;
            case 0x26:
                instruction.add(FDCBINSTR, "sla", SLA);
                break;
            case 0x2e:
                instruction.add(FDCBINSTR, "sra", SRA);
                break;
            case 0x3e:
                instruction.add(FDCBINSTR, "srl", SRL);
                break;
            case 0x36:
                instruction.add(FDCBINSTR, "sll", SLL);
                break;
            default:
                unit = read(start + 0, 8);

                switch (unit & 0xc7) {
                    case 0x46:
                        instruction.add(FDCBINSTR, "bit", BIT);
                        BIT(start + 2);
                        break;
                    case 0x86:
                        instruction.add(FDCBINSTR, "res", RES);
                        BIT(start + 2);
                        break;
                    case 0xc6:
                        instruction.add(FDCBINSTR, "set", SET);
                        BIT(start + 2);
                        break;
                    default:
                        throw new InvalidInstructionException();
                }
                break;
        }
    }

    private void edINSTR(int start) throws InvalidInstructionException {
        unit = read(start + 0, 8);

        switch (unit & 0xef) {
            case 0x4b:
                instruction.add(EDINSTR, "ld", LD);
                BCDE(start + 3);
                imm16(start + 8, IMM16);
                break;
            case 0x6b:
                instruction.add(EDINSTR, "ld", LD);
                HLSP(start + 3);
                imm16(start + 8, IMM16);
                break;
            case 0x43:
                instruction.add(EDINSTR, "ld", LD);
                BCDE(start + 3);
                imm16(start + 8, ADDRESS);
                break;
            case 0x63:
                instruction.add(EDINSTR, "ld", LD);
                HLSP(start + 3);
                imm16(start + 8, ADDRESS);
                break;
            case 0x4a:
                instruction.add(EDINSTR, "adc HL,", ADC_HL_);
                BCDE(start + 3);
                break;
            case 0x42:
                instruction.add(EDINSTR, "sbc HL,", SBC_HL_);
                BCDE(start + 3);
                break;
            case 0x6a:
                instruction.add(EDINSTR, "adc HL,", ADC_HL_);
                HLSP(start + 3);
                break;
            case 0x62:
                instruction.add(EDINSTR, "sbc HL,", SBC_HL_);
                HLSP(start + 3);
                break;
            default:
                unit = read(start + 0, 8);

                switch (unit & 0xff) {
                    case 0x46:
                        instruction.add(EDINSTR, "im 0", IM_0);
                        break;
                    case 0x56:
                        instruction.add(EDINSTR, "im 1", IM_1);
                        break;
                    case 0x5e:
                        instruction.add(EDINSTR, "im 2", IM_2);
                        break;
                    case 0x57:
                        instruction.add(EDINSTR, "ld A,I", LD_A_I);
                        break;
                    case 0x47:
                        instruction.add(EDINSTR, "ld I,A", LD_I_A);
                        break;
                    case 0x5f:
                        instruction.add(EDINSTR, "ld A,R", LD_A_R);
                        break;
                    case 0x4f:
                        instruction.add(EDINSTR, "ld R,A", LD_R_A);
                        break;
                    case 0x44:
                        instruction.add(EDINSTR, "neg", NEG);
                        break;
                    case 0x6f:
                        instruction.add(EDINSTR, "rld", RLD);
                        break;
                    case 0x67:
                        instruction.add(EDINSTR, "rrd", RRD);
                        break;
                    case 0xa1:
                        instruction.add(EDINSTR, "cpi", CPI);
                        break;
                    case 0xb1:
                        instruction.add(EDINSTR, "cpir", CPIR);
                        break;
                    case 0xa9:
                        instruction.add(EDINSTR, "cpd", CPD);
                        break;
                    case 0xb9:
                        instruction.add(EDINSTR, "cpdr", CPDR);
                        break;
                    case 0x4d:
                        instruction.add(EDINSTR, "reti", RETI);
                        break;
                    case 0x45:
                        instruction.add(EDINSTR, "retn", RETN);
                        break;
                    case 0xa2:
                        instruction.add(EDINSTR, "ini", INI);
                        break;
                    case 0xb2:
                        instruction.add(EDINSTR, "inir", INIR);
                        break;
                    case 0xaa:
                        instruction.add(EDINSTR, "ind", IND);
                        break;
                    case 0xba:
                        instruction.add(EDINSTR, "indr", INDR);
                        break;
                    case 0xa3:
                        instruction.add(EDINSTR, "outi", OUTI);
                        break;
                    case 0xb3:
                        instruction.add(EDINSTR, "otir", OTIR);
                        break;
                    case 0xab:
                        instruction.add(EDINSTR, "outd", OUTD);
                        break;
                    case 0xbb:
                        instruction.add(EDINSTR, "otdr", OTDR);
                        break;
                    case 0xa0:
                        instruction.add(EDINSTR, "ldi", LDI);
                        break;
                    case 0xb0:
                        instruction.add(EDINSTR, "ldir", LDIR);
                        break;
                    case 0xa8:
                        instruction.add(EDINSTR, "ldd", LDD);
                        break;
                    case 0xb8:
                        instruction.add(EDINSTR, "lddr", LDDR);
                        break;
                    default:
                        unit = read(start + 0, 8);

                        switch (unit & 0xc7) {
                            case 0x40:
                                instruction.add(EDINSTR, "in", IN);
                                REG(start + 2, REG);
                                break;
                            case 0x41:
                                instruction.add(EDINSTR, "out", OUT);
                                REG(start + 2, REG_OUT);
                                break;
                            default:
                                throw new InvalidInstructionException();
                        }
                        break;
                }
                break;
        }
    }

    private void BCDEIXSP(int start) throws InvalidInstructionException {
        unit = read(start + 0, 2);

        switch (unit & 0x3) {
            case 0x0:
                instruction.add(BCDEIXSP, "BC", BC);
                break;
            case 0x1:
                instruction.add(BCDEIXSP, "DE", DE);
                break;
            case 0x2:
                instruction.add(BCDEIXSP, "IX", IX);
                break;
            case 0x3:
                instruction.add(BCDEIXSP, "SP", SP);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void BCDEIYSP(int start) throws InvalidInstructionException {
        unit = read(start + 0, 2);

        switch (unit & 0x3) {
            case 0x0:
                instruction.add(BCDEIYSP, "BC", BC);
                break;
            case 0x1:
                instruction.add(BCDEIYSP, "DE", DE);
                break;
            case 0x2:
                instruction.add(BCDEIYSP, "IY", IY);
                break;
            case 0x3:
                instruction.add(BCDEIYSP, "SP", SP);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void BCDE(int start) throws InvalidInstructionException {
        unit = read(start + 0, 1);

        switch (unit & 0x1) {
            case 0x0:
                instruction.add(BCDE, "BC", BC);
                break;
            case 0x1:
                instruction.add(BCDE, "DE", DE);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void HLSP(int start) throws InvalidInstructionException {
        unit = read(start + 0, 1);

        switch (unit & 0x1) {
            case 0x0:
                instruction.add(HLSP, "HL", HL);
                break;
            case 0x1:
                instruction.add(HLSP, "SP", SP);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void BCDE_STAX(int start, int rule) throws InvalidInstructionException {
        unit = read(start + 0, 1);

        switch (unit & 0x1) {
            case 0x0:
                instruction.add(rule, "BC", BC);
                break;
            case 0x1:
                instruction.add(rule, "DE", DE);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void HLPSW(int start) throws InvalidInstructionException {
        unit = read(start + 0, 1);

        switch (unit & 0x1) {
            case 0x0:
                instruction.add(HLPSW, "HL", HL);
                break;
            case 0x1:
                instruction.add(HLPSW, "AF", AF);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void REG(int start, int rule) throws InvalidInstructionException {
        unit = read(start + 0, 3);

        switch (unit & 0x7) {
            case 0x0:
                instruction.add(rule, "B", B);
                break;
            case 0x1:
                instruction.add(rule, "C", C);
                break;
            case 0x2:
                instruction.add(rule, "D", D);
                break;
            case 0x3:
                instruction.add(rule, "E", E);
                break;
            case 0x4:
                instruction.add(rule, "H", H);
                break;
            case 0x5:
                instruction.add(rule, "L", L);
                break;
            case 0x6:
                instruction.add(rule, "(HL)", _HL_);
                break;
            case 0x7:
                instruction.add(rule, "A", A);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void REG_BCDE(int start) throws InvalidInstructionException {
        unit = read(start + 0, 2);

        switch (unit & 0x3) {
            case 0x0:
                instruction.add(REG_BCDE, "B", B);
                break;
            case 0x1:
                instruction.add(REG_BCDE, "C", C);
                break;
            case 0x2:
                instruction.add(REG_BCDE, "D", D);
                break;
            case 0x3:
                instruction.add(REG_BCDE, "E", E);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void REG_HL(int start) throws InvalidInstructionException {
        unit = read(start + 0, 1);

        switch (unit & 0x1) {
            case 0x0:
                instruction.add(REG_HL, "H", H);
                break;
            case 0x1:
                instruction.add(REG_HL, "L", L);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void REG_A(int start) throws InvalidInstructionException {
        unit = read(start + 0, 3);

        switch (unit & 0x7) {
            case 0x7:
                instruction.add(REG_A, "A", A);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void M(int start) throws InvalidInstructionException {
        unit = read(start + 0, 3);

        switch (unit & 0x7) {
            case 0x6:
                instruction.add(M, "(HL)", _HL_);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void NUMBER(int start) throws InvalidInstructionException {
        unit = read(start + 0, 3);

        switch (unit & 0x7) {
            case 0x0:
                instruction.add(NUMBER, "00", _00);
                break;
            case 0x1:
                instruction.add(NUMBER, "08", _08);
                break;
            case 0x2:
                instruction.add(NUMBER, "10", _10);
                break;
            case 0x3:
                instruction.add(NUMBER, "18", _18);
                break;
            case 0x4:
                instruction.add(NUMBER, "20", _20);
                break;
            case 0x5:
                instruction.add(NUMBER, "28", _28);
                break;
            case 0x6:
                instruction.add(NUMBER, "30", _30);
                break;
            case 0x7:
                instruction.add(NUMBER, "38", _38);
                break;
            default:
                throw new InvalidInstructionException();
        }
    }

    private void imm8(int start, int rule) throws InvalidInstructionException {
        unit = read(start + 0, 8);

        instruction.add(rule, readBytes(start + 0, 8));
    }

    private void BIT(int start) throws InvalidInstructionException {
        unit = read(start + 0, 3);

        instruction.add(BIT, readBytes(start + 0, 3));
    }

    private void imm16(int start, int rule) throws InvalidInstructionException {
        unit = read(start + 0, 16);

        instruction.add(rule, readBytes(start + 0, 16));
    }


}
