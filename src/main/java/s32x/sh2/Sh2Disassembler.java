package s32x.sh2;

public class Sh2Disassembler {


    /* Fixed-Point Transfer Instructions */
    public static final int MOV0 = 0xe000;    /* MOV		#imm,Rn 	*/
    public static final int MOVW0 = 0x9000;    /* MOV.W	@(disp,PC),Rn	*/
    public static final int MOVL0 = 0xd000;    /* MOV.L	@(disp,PC),Rn	*/
    public static final int MOV1 = 0x6003;    /* MOV		Rm,Rn		*/
    public static final int MOVB1 = 0x2000;    /* MOV.B	Rm,@Rn		*/
    public static final int MOVW1 = 0x2001;    /* MOV.W	Rm,@Rn		*/
    public static final int MOVL1 = 0x2002;    /* MOV.L	Rm,@Rn		*/

    /* all instructions are 16-bit */
    public static final int MOVB2 = 0x6000;    /* MOV.B	@Rm,Rn		*/
    public static final int MOVW2 = 0x6001;    /* MOV.W	@Rm,Rn		*/
    public static final int MOVL2 = 0x6002;    /* MOV.L	@Rm,Rn		*/
    public static final int MOVB3 = 0x2004;    /* MOV.B	Rm,@-Rn		*/
    public static final int MOVW3 = 0x2005;    /* MOV.W	Rm,@-Rn		*/
    public static final int MOVL3 = 0x2006;    /* MOV.L	Rm,@-Rn		*/
    public static final int MOVB4 = 0x6004;    /* MOV.B	@Rm+,Rn		*/
    public static final int MOVW4 = 0x6005;    /* MOV.W	@Rm+,Rn		*/
    public static final int MOVL4 = 0x6006;    /* MOV.L	@Rm+,Rn		*/
    public static final int MOVB5 = 0x8000;    /* MOV.B	R0,@(disp,Rn)	*/
    public static final int MOVW5 = 0x8100;    /* MOV.W	R0,@(disp,Rn)	*/
    public static final int MOVL5 = 0x1000;    /* MOV.L	Rm,@(disp,Rn)	*/
    public static final int MOVB6 = 0x8400;    /* MOV.B	@(disp,Rm),R0	*/
    public static final int MOVW6 = 0x8500;    /* MOV.W	@(disp,Rm),R0	*/
    public static final int MOVL6 = 0x5000;    /* MOV.L	@(disp,Rm),Rn	*/
    public static final int MOVB7 = 0x0004;    /* MOV.B	Rm,@(R0,Rn)	*/
    public static final int MOVW7 = 0x0005;    /* MOV.W	Rm,@(R0,Rn)	*/
    public static final int MOVL7 = 0x0006;    /* MOV.L	Rm,@(R0,Rn)	*/
    public static final int MOVB8 = 0x000c;    /* MOV.B	@(R0,Rm),Rn	*/
    public static final int MOVW8 = 0x000d;    /* MOV.W	@(R0,Rm),Rn	*/
    public static final int MOVL8 = 0x000e;    /* MOV.L	@(R0,Rm),Rn	*/
    public static final int MOVB9 = 0xc000;    /* MOV.B	R0,@(disp,GBR)	*/
    public static final int MOVW9 = 0xc100;    /* MOV.W	R0,@(disp,GBR)	*/
    public static final int MOVL9 = 0xc200;    /* MOV.L	R0,@(disp,GBR)	*/
    public static final int MOVB10 = 0xc400;    /* MOV.B	@(disp,GBR),R0	*/
    public static final int MOVW10 = 0xc500;    /* MOV.W	@(disp,GBR),R0	*/
    public static final int MOVL10 = 0xc600;    /* MOV.L	@(disp,GBR),R0	*/
    public static final int MOVA = 0xc700;    /* MOVA		@(disp,PC),R0	*/
    public static final int MOVT = 0x0029;    /* MOVT		Rn		*/
    public static final int SWAPB = 0x6008;/* SWAP.B	Rm,Rn		*/
    public static final int SWAPW = 0x6009;    /* SWAP.W	Rm,Rn		*/
    public static final int XTRCT = 0x200d;    /* XTRCT	Rm,Rn		*/
    /* Arithmetic Operation Instructions */
    public static final int ADD0 = 0x300c;    /* ADD		Rm,Rn		*/
    public static final int ADD1 = 0x7000;    /* ADD		#imm,Rn		*/
    public static final int ADDC = 0x300e;    /* ADDC		Rm,Rn		*/
    public static final int ADDV = 0x300f;    /* ADDV		Rm,Rn		*/
    public static final int CMPEQ0 = 0x8800;    /* CMP/EQ	#imm,R0		*/
    public static final int CMPEQ1 = 0x3000;    /* CMP/EQ	Rm,Rn		*/
    public static final int CMPHS = 0x3002;    /* CMP/HS	Rm,Rn		*/
    public static final int CMPGE = 0x3003;    /* CMP/GE	Rm,Rn		*/
    public static final int CMPHI = 0x3006;    /* CMP/HI	Rm,Rn		*/
    public static final int CMPGT = 0x3007;    /* CMP/GT	Rm,Rn		*/
    public static final int CMPPZ = 0x4011;    /* CMP/PZ	Rn		*/
    public static final int CMPPL = 0x4015;    /* CMP/PL	Rn		*/
    public static final int CMPSTR = 0x200c;    /* CMP/STR	Rm,Rn		*/
    public static final int DIV1 = 0x3004;    /* DIV1		Rm,Rn		*/
    public static final int DIV0S = 0x2007;    /* DIV0S	Rm,Rn		*/
    public static final int DIV0U = 0x0019;    /* DIV0U			*/
    public static final int DMULSL = 0x300d;    /* DMULS.L	Rm,Rn		*/
    public static final int DMULUL = 0x3005;    /* DMULU.L	Rm,Rn		*/
    public static final int DT = 0x4010;    /* DT		Rn		*/
    public static final int EXTSB = 0x600e;    /* EXTS.B	Rm,Rn		*/
    public static final int EXTSW = 0x600f;    /* EXTS.W	Rm,Rn		*/
    public static final int EXTUB = 0x600c;    /* EXTU.B	Rm,Rn		*/
    public static final int EXTUW = 0x600d;    /* EXTU.W	Rm,Rn		*/
    public static final int MACL = 0x000f;    /* MAC.L	@Rm+,@Rn+	*/
    public static final int MACW = 0x400f;    /* MAC.W	@Rm+,@Rn+	*/
    public static final int MULL = 0x0007;    /* MUL.L	Rm,Rn		*/
    public static final int MULSW = 0x200f;    /* MULS.W	Rm,Rn		*/
    public static final int MULUW = 0x200e;    /* MULU.W	Rm,Rn		*/
    public static final int NEG = 0x600b;    /* NEG		Rm,Rn		*/
    public static final int NEGC = 0x600a;    /* NEGC		Rm,Rn		*/
    public static final int SUB = 0x3008;    /* SUB		Rm,Rn		*/
    public static final int SUBC = 0x300a;    /* SUBC		Rm,Rn		*/
    public static final int SUBV = 0x300b;    /* SUBV		Rm,Rn		*/
    /* Logic Operation Instructions */
    public static final int AND0 = 0x2009;    /* AND		Rm,Rn		*/
    public static final int AND1 = 0xc900;    /* AND		#imm,R0		*/
    public static final int ANDB = 0xcd00;    /* AND.B	#imm,@(R0,GBR)	*/
    public static final int NOT = 0x6007;    /* NOT		Rm,Rn		*/
    public static final int OR0 = 0x200b;    /* OR		Rm,Rn		*/
    public static final int OR1 = 0xcb00;    /* OR		#imm,R0		*/
    public static final int ORB = 0xcf00;/* OR.B		#imm,@(R0,GBR)	*/
    public static final int TASB = 0x401b;    /* TAS.B	@Rn		*/
    public static final int TST0 = 0x2008;    /* TST		Rm,Rn		*/
    public static final int TST1 = 0xc800;    /* TST		#imm,R0		*/
    public static final int TSTB = 0xcc00;    /* TST.B	#imm,@(R0,GBR)	*/
    public static final int XOR0 = 0x200a;    /* XOR		Rm,Rn		*/
    public static final int XOR1 = 0xca00;    /* XOR		#imm,R0		*/
    public static final int XORB = 0xce00;    /* XOR.B	#imm,@(R0,GBR)	*/
    /* Shift Instructions */
    public static final int ROTL = 0x4004;    /* ROTL		Rn		*/
    public static final int ROTR = 0x4005;    /* ROTR		Rn		*/
    public static final int ROTCL = 0x4024;    /* ROTCL	Rn		*/
    public static final int ROTCR = 0x4025;    /* ROTCR	Rn		*/
    public static final int SHAD = 0x400c;    /* SHAD		Rm,Rn		*/
    public static final int SHAL = 0x4020;    /* SHAL		Rn		*/
    public static final int SHAR = 0x4021;    /* SHAR		Rn		*/
    public static final int SHLD = 0x400d;    /* SHLD		Rm,Rn		*/
    public static final int SHLL = 0x4000;    /* SHLL		Rn		*/
    public static final int SHLR = 0x4001;    /* SHLR		Rn		*/
    public static final int SHLL2 = 0x4008;    /* SHLL2	Rn		*/
    public static final int SHLR2 = 0x4009;    /* SHLR2	Rn		*/
    public static final int SHLL8 = 0x4018;    /* SHLL8	Rn		*/
    public static final int SHLR8 = 0x4019;/* SHLR8	Rn		*/
    public static final int SHLL16 = 0x4028;    /* SHLL16	Rn		*/
    public static final int SHLR16 = 0x4029;    /* SHLR16	Rn		*/
    /* Branch Instructions */
    public static final int BF = 0x8b00;    /* BF		label		*/
    public static final int BFS = 0x8f00;    /* BF/S		label		*/
    public static final int BT = 0x8900;    /* BT		label		*/
    public static final int BTS = 0x8d00;    /* BT/S		label		*/
    public static final int BRA = 0xa000;/* BRA		label		*/
    public static final int BRAF = 0x0023;    /* BRAF		Rn		*/
    public static final int BSR = 0xb000;    /* BSR		label		*/
    public static final int BSRF = 0x0003;    /* BSRF		Rn		*/
    public static final int JMP = 0x402b;    /* JMP		@Rn		*/
    public static final int JSR = 0x400b;    /* JSR		@Rn		*/
    public static final int RTS = 0x000b;    /* RTS				*/
    /* System Control Instructions */
    public static final int CLRMAC = 0x0028;    /* CLRMAC			*/
    public static final int CLRT = 0x0008;    /* CLRT				*/
    public static final int LDC0 = 0x400e;    /* LDC		Rm,SR		*/
    public static final int LDC1 = 0x401e;    /* LDC		Rm,GBR		*/
    public static final int LDC2 = 0x402e;    /* LDC		Rm,VBR		*/
    public static final int LDCL0 = 0x4007;    /* LDC.L	@Rm+,SR		*/
    public static final int LDCL1 = 0x4017;    /* LDC.L	@Rm+,GBR	*/
    public static final int LDCL2 = 0x4027;    /* LDC.L	@Rm+,VBR	*/
    public static final int LDS0 = 0x400a;    /* LDS		Rm,MACH		*/
    public static final int LDS1 = 0x401a;    /* LDS		Rm,MACL		*/
    public static final int LDS2 = 0x402a;    /* LDS		Rm,PR		*/
    public static final int LDSL0 = 0x4006;    /* LDS.L	@Rm+,MACH	*/
    public static final int LDSL1 = 0x4016;    /* LDS.L	@Rm+,MACL	*/
    public static final int LDSL2 = 0x4026;    /* LDS.L	@Rm+,PR		*/
    public static final int NOP = 0x0009;    /* NOP				*/
    public static final int RTE = 0x002b;    /* RTE				*/
    public static final int SETT = 0x0018;    /* SETT				*/
    public static final int SLEEP = 0x001b;    /* SLEEP			*/
    public static final int STC0 = 0x0002;    /* STC		SR,Rn		*/
    public static final int STC1 = 0x0012;    /* STC		GBR,Rn		*/
    public static final int STC2 = 0x0022;    /* STC		VBR,Rn		*/
    public static final int STCL0 = 0x4003;    /* STC.L	SR,@-Rn		*/
    public static final int STCL1 = 0x4013;    /* STC.L	GBR,@-Rn	*/
    public static final int STCL2 = 0x4023;    /* STC.L	VBR,@-Rn	*/
    public static final int STS0 = 0x000a;    /* STS		MACH,Rn		*/
    public static final int STS1 = 0x001a;    /* STS		MACL,Rn		*/
    public static final int STS2 = 0x002a;    /* STS		PR,Rn		*/
    public static final int STSL0 = 0x4002;    /* STS.L	MACH,@-Rn	*/
    public static final int STSL1 = 0x4012;    /* STS.L	MACL,@-Rn	*/
    public static final int STSL2 = 0x4022;    /* STS.L	PR,@-Rn		*/
    public static final int TRAPA = 0xc300;    /* TRAPA	#imm		*/

    public Sh2Disassembler() {
    }

    /* Some bitfields */
    private static final int RM(int opcode) {
        return ((opcode & 0x00f0) >> 4);
    }

    private static final int RN(int opcode) {
        return ((opcode & 0x0f00) >> 8);
    }

    private static final int DISP(int opcode) {
        return (opcode & 0x000f);
    }

    private static final int IMM(int opcode) {
        return (opcode & 0x00ff);
    }

    private static final int LABEL(int opcode) {
        return (opcode & 0x0fff);
    }

    private static final int DRN(int opcode) {
        return ((opcode & 0x0e00) >> 8);
    }

    private static final int BANK(int opcode) {
        return ((opcode & 0x0070) >> 4);
    }

    public String disassemble(int pc, int opcode) {
        int op;
        int temp;
        int reference = 0;


        op = opcode & 0xf000;
        switch (op) {
            case MOV0:
                temp = (IMM(opcode) << 24) >> 24;
                return String.format((temp < 0) ? "mov H'%08x, R%d" : "mov H'%02x, R%d", temp, RN(opcode));


            case MOVW0:
                reference = IMM(opcode) * 2 + pc + 4;
                return String.format("mov.w @(H'%08x), R%d", reference, RN(opcode));


            case MOVL0:
                reference = (IMM(opcode) * 4 + (pc & 0xfffffffc) + 4);
                return String.format("mov.l @(H'%08x), R%d", reference, RN(opcode));


            case MOVL5:
                return String.format("mov.l R%d, @(%d, R%d)", RM(opcode), DISP(opcode), RN(opcode));


            case MOVL6:
                return String.format("mov.l @(%d, R%d), R%d", DISP(opcode), RM(opcode), RN(opcode));


            case ADD1:
                temp = (((IMM(opcode) << 24)) >> 24);
                return String.format("add H'%s%02x, R%d", temp < 0 ? "-" : "", temp & 0xFF, RN(opcode));

            case BRA:
                temp = (((LABEL(opcode) << 20)) >> 20) * 2;
                return String.format("bra H'%08x", temp + (pc + 4));


            case BSR:
                temp = (((LABEL(opcode) << 20)) >> 20) * 2;
                return String.format("bsr H'%08x", temp + (pc + 4));


            /* this seems to be needed, no? */
            default:

        }
        op = opcode & 0xf00f;
        switch (op) {
            case MOV1:
                return String.format("mov R%d, R%d", RM(opcode), RN(opcode));


            case MOVB1:
                return String.format("mov.b R%d, @R%d", RM(opcode), RN(opcode));


            case MOVW1:
                return String.format("mov.w R%d, @R%d", RM(opcode), RN(opcode));


            case MOVL1:
                return String.format("mov.l R%d, @R%d", RM(opcode), RN(opcode));


            case MOVB2:
                return String.format("mov.b @R%d, R%d", RM(opcode), RN(opcode));


            case MOVW2:
                return String.format("mov.w @R%d, R%d", RM(opcode), RN(opcode));


            case MOVL2:
                return String.format("mov.l @R%d, R%d", RM(opcode), RN(opcode));


            case MOVB3:
                return String.format("mov.b R%d, @-R%d", RM(opcode), RN(opcode));


            case MOVW3:
                return String.format("mov.w R%d, @-R%d", RM(opcode), RN(opcode));


            case MOVL3:
                return String.format("mov.l R%d, @-R%d", RM(opcode), RN(opcode));


            case MOVB4:
                return String.format("mov.b @R%d+, R%d", RM(opcode), RN(opcode));


            case MOVW4:
                return String.format("mov.w @R%d+, R%d", RM(opcode), RN(opcode));


            case MOVL4:
                return String.format("mov.l @R%d+, R%d", RM(opcode), RN(opcode));


            case MOVB7:
                return String.format("mov.b R%d, @(R0, R%d)", RM(opcode), RN(opcode));


            case MOVW7:
                return String.format("mov.w R%d, @(R0, R%d)", RM(opcode), RN(opcode));


            case MOVL7:
                return String.format("mov.l R%d, @(R0, R%d)", RM(opcode), RN(opcode));


            case MOVB8:
                return String.format("mov.b @(R0, R%d), R%d", RM(opcode), RN(opcode));


            case MOVW8:
                return String.format("mov.w @(R0, R%d), R%d", RM(opcode), RN(opcode));


            case MOVL8:
                return String.format("mov.l @(R0, R%d), R%d", RM(opcode), RN(opcode));


            case SWAPB:
                return String.format("swap.b R%d, R%d", RM(opcode), RN(opcode));


            case SWAPW:
                return String.format("swap.w R%d, R%d", RM(opcode), RN(opcode));


            case XTRCT:
                return String.format("xtrct R%d, R%d", RM(opcode), RN(opcode));


            case ADD0:
                return String.format("add R%d, R%d", RM(opcode), RN(opcode));


            case ADDC:
                return String.format("addc R%d, R%d", RM(opcode), RN(opcode));


            case ADDV:
                return String.format("addv R%d, R%d", RM(opcode), RN(opcode));


            case CMPEQ1:
                return String.format("cmp/eq R%d, R%d", RM(opcode), RN(opcode));

            case CMPHS:
                return String.format("cmp/hs R%d, R%d", RM(opcode), RN(opcode));


            case CMPGE:
                return String.format("cmp/ge R%d, R%d", RM(opcode), RN(opcode));


            case CMPHI:
                return String.format("cmp/hi R%d, R%d", RM(opcode), RN(opcode));


            case CMPGT:
                return String.format("cmp/gt R%d, R%d", RM(opcode), RN(opcode));


            case CMPSTR:
                return String.format("cmp/str R%d, R%d", RM(opcode), RN(opcode));


            case DIV1:
                return String.format("div1 R%d, R%d", RM(opcode), RN(opcode));


            case DIV0S:
                return String.format("div0s R%d, R%d", RM(opcode), RN(opcode));


            case DMULSL:
                return String.format("dmuls.l R%d, R%d", RM(opcode), RN(opcode));


            case DMULUL:
                return String.format("dmulu.l R%d, R%d", RM(opcode), RN(opcode));


            case EXTSB:
                return String.format("exts.b R%d, R%d", RM(opcode), RN(opcode));


            case EXTSW:
                return String.format("exts.w R%d, R%d", RM(opcode), RN(opcode));


            case EXTUB:
                return String.format("extu.b R%d, R%d", RM(opcode), RN(opcode));


            case EXTUW:
                return String.format("extu.w R%d, R%d", RM(opcode), RN(opcode));


            case MACL:
                return String.format("mac.l @R%d+, @R%d+", RM(opcode), RN(opcode));


            case MACW:
                return String.format("mac.w @R%d+, @R%d+", RM(opcode), RN(opcode));


            case MULL:
                return String.format("mul.l R%d, R%d", RM(opcode), RN(opcode));


            case MULSW:
                return String.format("muls.w R%d, R%d", RM(opcode), RN(opcode));


            case MULUW:
                return String.format("mulu.w R%d, R%d", RM(opcode), RN(opcode));


            case NEG:
                return String.format("neg R%d, R%d", RM(opcode), RN(opcode));


            case NEGC:
                return String.format("negc R%d, R%d", RM(opcode), RN(opcode));


            case SUB:
                return String.format("sub R%d, R%d", RM(opcode), RN(opcode));


            case SUBC:
                return String.format("subc R%d, R%d", RM(opcode), RN(opcode));


            case SUBV:
                return String.format("subv R%d, R%d", RM(opcode), RN(opcode));


            case AND0:
                return String.format("and R%d, R%d", RM(opcode), RN(opcode));


            case NOT:
                return String.format("not R%d, R%d", RM(opcode), RN(opcode));


            case OR0:
                return String.format("or R%d, R%d", RM(opcode), RN(opcode));


            case TST0:
                return String.format("tst R%d, R%d", RM(opcode), RN(opcode));


            case XOR0:
                return String.format("xor R%d, R%d", RM(opcode), RN(opcode));
        }

        op = opcode & 0xff00;
        switch (op) {
            case MOVB5:
                return String.format("mov.b R0, @(%d, R%d)", DISP(opcode), RM(opcode));


            case MOVW5:
                return String.format("mov.w R0, @(%d, R%d)", DISP(opcode), RM(opcode));


            case MOVB6:
                return String.format("mov.b @(%d, R%d), R0", DISP(opcode), RM(opcode));


            case MOVW6:
                return String.format("mov.w @(%d, R%d), R0", DISP(opcode), RM(opcode));


            case MOVB9:
                return String.format("mov.b R0, @(%d, GBR)", IMM(opcode));


            case MOVW9:
                return String.format("mov.w R0, @(%d, GBR)", IMM(opcode));


            case MOVL9:
                return String.format("mov.l R0, @(%d, GBR)", IMM(opcode));


            case MOVB10:
                return String.format("mov.b @(%d, GBR), R0", IMM(opcode));


            case MOVW10:
                return String.format("mov.w @(%d, GBR), R0", IMM(opcode));


            case MOVL10:
                return String.format("mov.l @(%d, GBR), R0", IMM(opcode));


            case MOVA:
                return String.format("mova H'%08x, R0", (IMM(opcode) * 4) + ((pc & 0xfffffffc) + 4));


            case CMPEQ0:
                temp = ((IMM(opcode) << 24)) >> 24;
                return String.format("cmp/eq H'%02x, R0", temp);


            case AND1:
                return String.format("and H'%02x, R0", IMM(opcode));


            case ANDB:
                return String.format("and.b H'%02x, @(R0, GBR)", IMM(opcode));


            case OR1:
                return String.format("or H'%02x, R0", IMM(opcode));


            case ORB:
                return String.format("or.b H'%02x, @(R0, GBR)", IMM(opcode));


            case TST1:
                return String.format("tst H'%02x, R0", IMM(opcode));


            case TSTB:
                return String.format("tst.b H'%02x, @(R0, GBR)", IMM(opcode));


            case XOR1:
                return String.format("xor H'%02x, R0", IMM(opcode));


            case XORB:
                return String.format("xor.b H'%02x, @(R0, GBR)", IMM(opcode));


            case BF:
                temp = (((IMM(opcode) << 24)) >> 24) * 2;
                return String.format("bf H'%08x", temp + (pc + 4));


            case BFS:
                temp = (((IMM(opcode) << 24)) >> 24) * 2;
                return String.format("bf/s H'%08x", temp + (pc + 4));

            case BT:
                temp = (((IMM(opcode) << 24)) >> 24) * 2;
                return String.format("bt H'%08x", temp + (pc + 4));


            case BTS:
                temp = (((IMM(opcode) << 24)) >> 24) * 2;
                return String.format("bt/s H'%08x", temp + (pc + 4));


            case TRAPA:
                return String.format("trapa H'%02x", IMM(opcode) << 2);


            default:

        }

        op = opcode & 0xf0ff;
        switch (op) {
            case MOVT:
                return String.format("movt R%d", RN(opcode));


            case CMPPZ:
                return String.format("cmp/pz R%d", RN(opcode));


            case CMPPL:
                return String.format("cmp/pl R%d", RN(opcode));


            case DT:
                return String.format("dt R%d", RN(opcode));


            case TASB:
                return String.format("tas.b @R%d", RN(opcode));


            case ROTL:
                return String.format("rotl R%d", RN(opcode));


            case ROTR:
                return String.format("rotr R%d", RN(opcode));


            case ROTCL:
                return String.format("rotcl R%d", RN(opcode));


            case ROTCR:
                return String.format("rotcr R%d", RN(opcode));


            case SHAL:
                return String.format("shal R%d", RN(opcode));


            case SHAR:
                return String.format("shar R%d", RN(opcode));


            case SHLL:
                return String.format("shll R%d", RN(opcode));


            case SHLR:
                return String.format("shlr R%d", RN(opcode));


            case SHLL2:
                return String.format("shll2 R%d", RN(opcode));


            case SHLR2:
                return String.format("shlr2 R%d", RN(opcode));


            case SHLL8:
                return String.format("shll8 R%d", RN(opcode));


            case SHLR8:
                return String.format("shlr8 R%d", RN(opcode));


            case SHLL16:
                return String.format("shll16 R%d", RN(opcode));


            case SHLR16:
                return String.format("shlr16 R%d", RN(opcode));


            case BRAF:
                return String.format("braf R%d", RN(opcode));


            case BSRF:
                return String.format("bsrf R%d", RN(opcode));


            case JMP:
                return String.format("jmp @R%d", RN(opcode));


            case JSR:
                return String.format("jsr @R%d", RN(opcode));


            case LDC0:
                return String.format("ldc R%d, SR", RN(opcode));


            case LDC1:
                return String.format("ldc R%d, GBR", RN(opcode));


            case LDC2:
                return String.format("ldc R%d, VBR", RN(opcode));

            case LDCL0:
                return String.format("ldc.l @R%d+, SR", RN(opcode));


            case LDCL1:
                return String.format("ldc.l @R%d+, GBR", RN(opcode));


            case LDCL2:
                return String.format("ldc.l @R%d+, VBR", RN(opcode));

            case LDS0:
                return String.format("lds R%d, MACH", RN(opcode));


            case LDS1:
                return String.format("lds R%d, MACL", RN(opcode));


            case LDS2:
                return String.format("lds R%d, PR", RN(opcode));


            case LDSL0:
                return String.format("lds.l @R%d+, MACH", RN(opcode));


            case LDSL1:
                return String.format("lds.l @R%d+, MACL", RN(opcode));


            case LDSL2:
                return String.format("lds.l @R%d+, PR", RN(opcode));
            case STC0:
                return String.format("stc SR, R%d", RN(opcode));


            case STC1:
                return String.format("stc GBR, R%d", RN(opcode));


            case STC2:
                return String.format("stc VBR, R%d", RN(opcode));

            case STCL0:
                return String.format("stc.l SR, @-R%d", RN(opcode));


            case STCL1:
                return String.format("stc.l GBR, @-R%d", RN(opcode));


            case STCL2:
                return String.format("stc.l VBR, @-R%d", RN(opcode));

            case STS0:
                return String.format("sts MACH, R%d", RN(opcode));


            case STS1:
                return String.format("sts MACL, R%d", RN(opcode));


            case STS2:
                return String.format("sts PR, R%d", RN(opcode));


            case STSL0:
                return String.format("sts.l MACH, @-R%d", RN(opcode));


            case STSL1:
                return String.format("sts.l MACL, @-R%d", RN(opcode));


            case STSL2:
                return String.format("sts.l PR, @-R%d", RN(opcode));
            default:


        }

        op = opcode & 0xffff;
        switch (op) {
            case DIV0U:
                return String.format("div0u");


            case RTS:
                return String.format("rts");


            case CLRMAC:
                return String.format("clrmac");
            case CLRT:
                return String.format("clrt");

            case NOP:
                return String.format("nop");


            case RTE:
                return String.format("rte");

            case SETT:
                return String.format("sett");


            case SLEEP:
                return String.format("sleep");
            default:

        }
        return "??? (" + Integer.toHexString(opcode) + ")";
    }
}