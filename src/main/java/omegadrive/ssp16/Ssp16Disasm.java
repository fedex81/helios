package omegadrive.ssp16;

/*

SSP1601 disassembler
written by Pierpaolo Prazzoli
updated for SSP1601 by Grazvydas Ignotas

*/
public class Ssp16Disasm {

    static String[] reg =
            {
                    "-", "X", "Y", "A",
                    "ST", "STACK", "PC", "P",
                    "EXT0", "EXT1", "EXT2", "EXT3",
                    "EXT4", "EXT5", "EXT6", "AL"
            };

    static String[] rij =
            {
                    "r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7"
            };

    static String[] modifier =
            {
                    "", "+!", "-", "+"
            };

    static String[] modifier_sf =
            {
                    "|00", "|01", "|10", "|11"
            };

    static String[] cond =
            {
                    "always", "RESERVED", "gpi0", "gpi1",
                    "l", "z", "ov", "n",
                    "diof", "gpi2", "gpi3", "RESERVED",
                    "RESERVED", "RESERVED", "RESERVED", "RESERVED",
            };

    static String[] acc_op =
            {
                    "ror", "rol", "shr", "shl", "inc", "dec", "neg", "abs"
            };

    // pag. 81 uses different addresses!
    static String[] flag_op =
            {
                    "?", "?", "resl", "setl", "resie", "setie", "?", "?", "resop", "setop", "?", "?", "?", "?", "res", "set"
            };

    static String[] arith_ops =
            {
//        "", "add", "", "cmp", "add", "and", "or", "eor"
                    "", "sub", "", "cmp", "add", "and", "or", "eor"
            };
    static int DASMFLAG_STEP_OUT = 1 << 3;
    static int DASMFLAG_STEP_OVER = 1 << 4;
    static int DASMFLAG_SUPPORTED = 1 << 5;

    //#define BIT_B               ((op >> 8) & 1)
    static int BIT_B(int op) {
        return ((op >> 8) & 1);
    }

    //            #define RIJ                 rij[(BIT_B << 2) + (op & 3)]
    static String RIJ(int op) {
        return rij[(BIT_B(op) << 2) + (op & 3)];
    }

    //            #define RI(i)               rij[(i) & 3]
    static String RI(int i) {
        return rij[(i) & 3];
    }

    //            #define RJ(i)               rij[((i) & 3) + 4]
    static String RJ(int i) {
        return rij[((i) & 3) + 4];
    }

    //            #define MODIFIER(mod,r3)    (((r3) == 3) ? modifier_sf[mod] : modifier[mod])
    static String MODIFIER(int mod, int r3) {
        return (((r3) == 3) ? modifier_sf[mod] : modifier[mod]);
    }

    //            #define MODIFIER_LOW        MODIFIER((op >> 2) & 3, op&3)
    static String MODIFIER_LOW(int op) {
        return MODIFIER((op >> 2) & 3, op & 3);
    }

    //            #define MODIFIER_HIGH       MODIFIER((op >> 6) & 3, (op >> 4)&3)
    static String MODIFIER_HIGH(int op) {
        return MODIFIER((op >> 6) & 3, (op >> 4) & 3);
    }

    //            #define READ_OP_DASM(p) ((base_oprom[p] << 8) | base_oprom[(p) + 1])
    static int READ_OP_DASM_BYTE(int[] base_oprom, int p) {
        return ((base_oprom[p] << 8) | base_oprom[(p) + 1]);
    }

    static int READ_OP_DASM_WORD(int[] base_oprom, int pc, int byteOffset) {
        return base_oprom[pc + (byteOffset >> 1)] & 0xFFFF;
    }

    static String get_cond(int op) {
        StringBuilder sb = new StringBuilder();
        if ((op & 0xf0) > 0)
            sb.append(String.format("%s=%d", cond[(op >> 4) & 0xf], BIT_B(op)));
        else
            sb.append(String.format("%s", cond[(op >> 4) & 0xf]));
        return sb.toString();
    }

    static int dasm_ssp1601(StringBuilder sb, int pc, int[] oprom) {
        int[] base_oprom;
        int op;
        int size = 1;
        int flags = 0;

        base_oprom = oprom;

        op = READ_OP_DASM_WORD(base_oprom, pc, 0);

        switch (op >> 9) {
            case 0x00:
                if (op == 0) {
                    // nop
                    sb.append("nop");
                } else if ((op & 0xff) == 0x65) {
                    // ret
                    sb.append("ret");
                    flags |= DASMFLAG_STEP_OUT;
                } else {
                    // ld d, s
                    sb.append(String.format("ld %s, %s", reg[(op >> 4) & 0xf], reg[op & 0xf]));
                }
                break;

            // ld d, (ri)
            case 0x01:
                sb.append(String.format("ld %s, (%s%s)", reg[(op >> 4) & 0xf], RIJ(op), MODIFIER_LOW(op)));
                break;

            // ld (ri), s
            case 0x02:
                sb.append(String.format("ld (%s%s), %s", RIJ(op), MODIFIER_LOW(op), reg[(op >> 4) & 0xf]));
                break;

            // ld a, addr
            case 0x03:
                sb.append(String.format("ld A, %X", op & 0x1ff));
                break;

            // ldi d, imm
            case 0x04:
                sb.append(String.format("ld %s, %X", reg[(op >> 4) & 0xf],
                        READ_OP_DASM_WORD(base_oprom, pc, 2)));
                size = 2;
                break;

            // ld d, ((ri))
            case 0x05:
                sb.append(String.format("ld %s, ((%s%s))", reg[(op >> 4) & 0xf], RIJ(op), MODIFIER_LOW(op)));
                break;

            // ldi (ri), imm
            case 0x06:
                sb.append(String.format("ld (%s%s), %X", RIJ(op), MODIFIER_LOW(op),
                        READ_OP_DASM_WORD(base_oprom, pc, 2)));
                size = 2;
                break;

            // ld addr, a
            case 0x07:
                sb.append(String.format("ld %X, A", op & 0x1ff));
                break;

            // ld d, ri
            case 0x09:
                sb.append(String.format("ld %s, %s%s", reg[(op >> 4) & 0xf], RIJ(op), MODIFIER_LOW(op)));
                break;

            // ld ri, s
            case 0x0a:
                sb.append(String.format("ld %s%s, %s", RIJ(op), MODIFIER_LOW(op), reg[(op >> 4) & 0xf]));
                break;

            // ldi ri, simm
            case 0x0c:
            case 0x0d:
            case 0x0e:
            case 0x0f:
                sb.append(String.format("ldi %s, %X", rij[(op >> 8) & 7], op & 0xff));
                break;

            // op a, s
            case 0x10:
            case 0x30:
            case 0x40:
            case 0x50:
            case 0x60:
            case 0x70:
                sb.append(String.format("%s A, %s", arith_ops[op >> 13], reg[op & 0xf]));
                break;

            // op a, (ri)
            case 0x11:
            case 0x31:
            case 0x41:
            case 0x51:
            case 0x61:
            case 0x71:
                sb.append(String.format("%s A, (%s%s)", arith_ops[op >> 13], RIJ(op), MODIFIER_LOW(op)));
                break;

            // op a, adr
            case 0x13:
            case 0x33:
            case 0x43:
            case 0x53:
            case 0x63:
            case 0x73:
                sb.append(String.format("%s A, %X", arith_ops[op >> 13], op & 0x1ff));
                break;

            // subi a, imm
            case 0x14:
            case 0x34:
            case 0x44:
            case 0x54:
            case 0x64:
            case 0x74:
                sb.append(String.format("%si A, %X", arith_ops[op >> 13],
                        READ_OP_DASM_WORD(base_oprom, pc, 2)));
                size = 2;
                break;

            // op a, ((ri))
            case 0x15:
            case 0x35:
            case 0x45:
            case 0x55:
            case 0x65:
            case 0x75:
                sb.append(String.format("%s A, ((%s%s))", arith_ops[op >> 13], RIJ(op), MODIFIER_LOW(op)));
                break;

            // sub a, ri
            case 0x19:
            case 0x39:
            case 0x49:
            case 0x59:
            case 0x69:
            case 0x79:
                sb.append(String.format("%s A, %s%s", arith_ops[op >> 13], RIJ(op), MODIFIER_LOW(op)));
                break;

            // mpys (rj), (ri), b
            case 0x1b:
                sb.append(String.format("mpya (%s%s), (%s%s), %d", RJ(op >> 4), MODIFIER_HIGH(op), RI(op), MODIFIER_LOW(op), BIT_B(op)));
                break;

            // subi simm
            case 0x1c:
            case 0x3c:
            case 0x4c:
            case 0x5c:
            case 0x6c:
            case 0x7c:
                sb.append(String.format("%si %X", arith_ops[op >> 13], op & 0xff));
                break;

            // call cond, addr
            case 0x24:
                sb.append(String.format("call %s, %X", get_cond(op), READ_OP_DASM_WORD(base_oprom, pc, 2)));
                flags |= DASMFLAG_STEP_OVER;
                size = 2;
                break;

            // ld d, (a)
            case 0x25:
                sb.append(String.format("ld %s, (A)", reg[(op >> 4) & 0xf]));
                break;

            // bra cond, addr
            case 0x26:
                sb.append(String.format("bra %s, %X", get_cond(op), READ_OP_DASM_WORD(base_oprom, pc, 2)));
                size = 2;
                break;

            // mod cond, op
            case 0x48:
                sb.append(String.format("mod %s, %s", get_cond(op), acc_op[op & 7]));
                break;

            // mod f, op
            case 0x4a:
                sb.append(String.format("%s", flag_op[op & 0xf]));
                break;

            // mpya (rj), (ri), b
            case 0x4b:
                sb.append(String.format("mpya (%s%s), (%s%s), %d", RJ(op >> 4), MODIFIER_HIGH(op), RI(op), MODIFIER_LOW(op), BIT_B(op)));
                break;

            // mld (rj), (ri), b
            case 0x5b:
                sb.append(String.format("mld (%s%s), (%s%s), %d", RJ(op >> 4), MODIFIER_HIGH(op), RI(op), MODIFIER_LOW(op), BIT_B(op)));
                break;

            default:
                sb.append(String.format("Unknown OP = %04X", op));
                break;
        }
        return size | flags | DASMFLAG_SUPPORTED;
    }

// vim:ts=4

//    CPU_DISASSEMBLE( ssp1601 )
//    {
//        //ssp1601_state_t *ssp1601_state = get_safe_token(device);
//
//        return dasm_ssp1601(buffer, pc, oprom);
//    }
}
