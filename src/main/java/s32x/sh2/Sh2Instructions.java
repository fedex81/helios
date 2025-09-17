package s32x.sh2;

import s32x.bus.Sh2Bus;
import s32x.sh2.prefetch.Sh2Prefetcher;

import java.util.*;
import java.util.function.Predicate;

import static omegadrive.util.Util.th;
import static s32x.sh2.Sh2Instructions.Sh2BaseInstruction.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Sh2Instructions {

    static class OpcodeCreateCtx {
        int opcode;
        Sh2Bus memory;
    }

    public static class Sh2InstructionWrapper {
        public final Runnable runnable;
        public final Sh2BaseInstruction inst;
        public final int opcode;

        public Sh2InstructionWrapper(int opcode, Sh2BaseInstruction inst, Runnable r) {
            assert inst == sh2OpcodeMap[opcode] : th(opcode) + "-" + inst + "-" + sh2OpcodeMap[opcode];
            this.opcode = opcode;
            this.inst = inst;
            this.runnable = r;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Sh2InstructionWrapper.class.getSimpleName() + "[", "]")
                    .add("inst=" + inst)
                    .add("opcode=" + opcode)
                    .toString();
        }
    }

    public static final int NUM_OPCODES = 0x10000;
    public static Sh2InstructionWrapper[] instOpcodeMap;
    public static Sh2BaseInstruction[] sh2OpcodeMap;

    //interrupts cannot be triggered during these instructions, I think it can be ignored
    public static Sh2BaseInstruction[] intDisabledOpcodes = {
            LDCGBR, LDSPR, LDCMGBR, LDCMSR, LDCMVBR, LDCSR,
            LDCVBR, LDSMACH, LDSMACL, LDSMMACH, LDSMMACH, LDSMPR,
            STCSR, STCGBR, STCVBR, STCMSR, STCMGBR, STCMVBR,
            STSMACH, STSMACL, STSPR, STSMMACH, STSMMACL, STSMPR
    };

    public static Sh2BaseInstruction[] illegalSlotOpcodes = {
            JMP, JSR, BRA, BSR, RTS, RTE, BT, BF, TRAPA, BFS, BTS, BSRF, BRAF
    };

    static {
        Arrays.sort(intDisabledOpcodes);
        Arrays.sort(illegalSlotOpcodes);
    }

    public static Sh2InstructionWrapper[] createOpcodeMap(Sh2Impl sh2) {
        instOpcodeMap = new Sh2InstructionWrapper[NUM_OPCODES];
        sh2OpcodeMap = new Sh2BaseInstruction[NUM_OPCODES];
        for (int i = 0; i < instOpcodeMap.length; i++) {
            sh2OpcodeMap[i] = getInstruction(i);
            instOpcodeMap[i] = getInstruction(sh2, i);
        }
        return instOpcodeMap;
    }

    private static String methodName() {
        StackWalker walker = StackWalker.getInstance();
        Optional<String> methodName = walker.walk(frames -> frames
                .skip(2).findFirst()
                .map(StackWalker.StackFrame::getMethodName));
        return methodName.orElse("ERROR");
    }

    public static Sh2Prefetcher.Sh2BlockUnit[] generateInst(int[] opcodes) {
        return Arrays.stream(opcodes).mapToObj(op -> new Sh2Prefetcher.Sh2BlockUnit(instOpcodeMap[op])).toArray(Sh2Prefetcher.Sh2BlockUnit[]::new);
    }

    /**
     * Generates opcode -> Sh2BaseInstruction mapping
     */
    private final static Sh2BaseInstruction getInstruction(final int opcode) {
        switch ((opcode >>> 12) & 0xf) {
            case 0:
                switch ((opcode >>> 0) & 0xf) {
                    case 2:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return STCSR;
                            case 1:
                                return STCGBR;
                            case 2:
                                return STCVBR;
                            default:
                                return ILLEGAL;
                        }
                    case 3:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return BSRF;
                            case 2:
                                return BRAF;
                            default:
                                return ILLEGAL;
                        }
                    case 4:
                        return MOVBS0;
                    case 5:
                        return MOVWS0;
                    case 6:
                        return MOVLS0;
                    case 7:
                        return MULL;
                    case 8:
                        switch ((opcode >>> 4) & 0xfff) {
                            case 0:
                                return CLRT;
                            case 1:
                                return SETT;
                            case 2:
                                return CLRMAC;
                            default:
                                return ILLEGAL;
                        }
                    case 9:
                        switch ((opcode >>> 4) & 0xfff) {
                            case 0:
                                return NOP;
                            case 1:
                                return DIV0U;
                            case 2:
                                return MOVT;
                            default:
                                switch ((opcode >>> 4) & 0xf) {
                                    case 2:
                                        return MOVT;
                                    default:
                                        return ILLEGAL;
                                }
                        }
                    case 10:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return STSMACH;
                            case 1:
                                return STSMACL;
                            case 2:
                                return STSPR;
                            default:
                                return ILLEGAL;
                        }
                    case 11:
                        switch ((opcode >>> 4) & 0xfff) {
                            case 0:
                                return RTS;
                            case 1:
                                return SLEEP;
                            case 2:
                                return RTE;
                            default:
                                return ILLEGAL;
                        }
                    case 12:
                        return MOVBL0;
                    case 13:
                        return MOVWL0;
                    case 14:
                        return MOVLL0;
                    case 15:
                        return MACL;
                    default:
                        return ILLEGAL;
                }
            case 1:
                return MOVLS4;
            case 2:
                switch ((opcode >>> 0) & 0xf) {
                    case 0:
                        return MOVBS;
                    case 1:
                        return MOVWS;
                    case 2:
                        return MOVLS;
                    case 4:
                        return MOVBM;
                    case 5:
                        return MOVWM;
                    case 6:
                        return MOVLM;
                    case 7:
                        return DIV0S;
                    case 8:
                        return TST;
                    case 9:
                        return AND;
                    case 10:
                        return XOR;
                    case 11:
                        return OR;
                    case 12:
                        return CMPSTR;
                    case 13:
                        return XTRCT;
                    case 14:
                        return MULSU;
                    case 15:
                        return MULSW;
                    default:
                        return ILLEGAL;
                }
            case 3:
                switch ((opcode >>> 0) & 0xf) {
                    case 0:
                        return CMPEQ;
                    case 2:
                        return CMPHS;
                    case 3:
                        return CMPGE;
                    case 4:
                        return DIV1;
                    case 5:
                        return DMULU;
                    case 6:
                        return CMPHI;
                    case 7:
                        return CMPGT;
                    case 8:
                        return SUB;
                    case 10:
                        return SUBC;
                    case 11:
                        return SUBV;
                    case 12:
                        return ADD;
                    case 13:
                        return DMULS;
                    case 14:
                        return ADDC;
                    case 15:
                        return ADDV;
                    default:
                        return ILLEGAL;
                }
            case 4:
                switch ((opcode >>> 0) & 0xf) {
                    case 0:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return SHLL;
                            case 1:
                                return DT;
                            case 2:
                                return SHAL;
                            default:
                                return ILLEGAL;
                        }
                    case 1:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return SHLR;
                            case 1:
                                return CMPPZ;
                            case 2:
                                return SHAR;
                            default:
                                return ILLEGAL;
                        }
                    case 2:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return STSMMACH;
                            case 1:
                                return STSMMACL;
                            case 2:
                                return STSMPR;
                            default:
                                return ILLEGAL;
                        }
                    case 3:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return STCMSR;
                            case 1:
                                return STCMGBR;
                            case 2:
                                return STCMVBR;
                            default:
                                return ILLEGAL;
                        }
                    case 4:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return ROTL;
                            case 2:
                                return ROTCL;
                            default:
                                return ILLEGAL;
                        }
                    case 5:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return ROTR;
                            case 1:
                                return CMPPL;
                            case 2:
                                return ROTCR;
                            default:
                                return ILLEGAL;
                        }
                    case 6:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return LDSMMACH;
                            case 1:
                                return LDSMMACL;
                            case 2:
                                return LDSMPR;
                            default:
                                return ILLEGAL;
                        }
                    case 7:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return LDCMSR;
                            case 1:
                                return LDCMGBR;
                            case 2:
                                return LDCMVBR;
                            default:
                                return ILLEGAL;
                        }
                    case 8:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return SHLL2;
                            case 1:
                                return SHLL8;
                            case 2:
                                return SHLL16;
                            default:
                                return ILLEGAL;
                        }
                    case 9:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return SHLR2;
                            case 1:
                                return SHLR8;
                            case 2:
                                return SHLR16;
                            default:
                                return ILLEGAL;
                        }
                    case 10:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return LDSMACH;
                            case 1:
                                return LDSMACL;
                            case 2:
                                return LDSPR;
                            default:
                                return ILLEGAL;
                        }
                    case 11:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return JSR;
                            case 1:
                                return TAS;
                            case 2:
                                return JMP;
                            default:
                                return ILLEGAL;
                        }
                    case 14:
                        switch ((opcode >>> 4) & 0xf) {
                            case 0:
                                return LDCSR;
                            case 1:
                                return LDCGBR;
                            case 2:
                                return LDCVBR;
                            default:
                                return ILLEGAL;
                        }
                    case 15:
                        return MACW;
                    default:
                        return ILLEGAL;
                }
            case 5:
                return MOVLL4;
            case 6:
                switch ((opcode >>> 0) & 0xf) {
                    case 0:
                        return MOVBL;
                    case 1:
                        return MOVWL;
                    case 2:
                        return MOVLL;
                    case 3:
                        return MOV;
                    case 4:
                        return MOVBP;
                    case 5:
                        return MOVWP;
                    case 6:
                        return MOVLP;
                    case 7:
                        return NOT;
                    case 8:
                        return SWAPB;
                    case 9:
                        return SWAPW;
                    case 10:
                        return NEGC;
                    case 11:
                        return NEG;
                    case 12:
                        return EXTUB;
                    case 13:
                        return EXTUW;
                    case 14:
                        return EXTSB;
                    case 15:
                        return EXTSW;
                }
            case 7:
                return ADDI;
            case 8:
                switch ((opcode >>> 8) & 0xf) {
                    case 0:
                        return MOVBS4;
                    case 1:
                        return MOVWS4;
                    case 4:
                        return MOVBL4;
                    case 5:
                        return MOVWL4;
                    case 8:
                        return CMPIM;
                    case 9:
                        return BT;
                    case 11:
                        return BF;
                    case 13:
                        return BTS;
                    case 15:
                        return BFS;
                    default:
                        return ILLEGAL;
                }
            case 9:
                return MOVWI;
            case 10:
                return BRA;
            case 11:
                return BSR;
            case 12:
                switch ((opcode >>> 8) & 0xf) {
                    case 0:
                        return MOVBSG;
                    case 1:
                        return MOVWSG;
                    case 2:
                        return MOVLSG;
                    case 3:
                        return TRAPA;
                    case 4:
                        return MOVBLG;
                    case 5:
                        return MOVWLG;
                    case 6:
                        return MOVLLG;
                    case 7:
                        return MOVA;
                    case 8:
                        return TSTI;
                    case 9:
                        return ANDI;
                    case 10:
                        return XORI;
                    case 11:
                        return ORI;
                    case 12:
                        return TSTM;
                    case 13:
                        return ANDM;
                    case 14:
                        return XORM;
                    case 15:
                        return ORM;
                }
            case 13:
                return MOVLI;
            case 14:
                return MOVI;
        }
        return ILLEGAL;
    }

    public final static Sh2InstructionWrapper getInstruction(final Sh2Impl sh2, final int opcode) {
        Sh2BaseInstruction sh2Inst = getInstruction(opcode);
        Runnable r;
        switch (sh2Inst) {
            case ADD:
                r = () -> sh2.ADD(opcode);
                break;
            case ADDC:
                r = () -> sh2.ADDC(opcode);
                break;
            case ADDI:
                r = () -> sh2.ADDI(opcode);
                break;
            case ADDV:
                r = () -> sh2.ADDV(opcode);
                break;
            case AND:
                r = () -> sh2.AND(opcode);
                break;
            case ANDI:
                r = () -> sh2.ANDI(opcode);
                break;
            case ANDM:
                r = () -> sh2.ANDM(opcode);
                break;
            case BF:
                r = () -> sh2.BF(opcode);
                break;
            case BFS:
                r = () -> sh2.BFS(opcode);
                break;
            case BRA:
                r = () -> sh2.BRA(opcode);
                break;
            case BRAF:
                r = () -> sh2.BRAF(opcode);
                break;
            case BSR:
                r = () -> sh2.BSR(opcode);
                break;
            case BSRF:
                r = () -> sh2.BSRF(opcode);
                break;
            case BT:
                r = () -> sh2.BT(opcode);
                break;
            case BTS:
                r = () -> sh2.BTS(opcode);
                break;
            case CLRMAC:
                r = () -> sh2.CLRMAC(opcode);
                break;
            case CLRT:
                r = () -> sh2.CLRT(opcode);
                break;
            case CMPEQ:
                r = () -> sh2.CMPEQ(opcode);
                break;
            case CMPGE:
                r = () -> sh2.CMPGE(opcode);
                break;
            case CMPGT:
                r = () -> sh2.CMPGT(opcode);
                break;
            case CMPHI:
                r = () -> sh2.CMPHI(opcode);
                break;
            case CMPHS:
                r = () -> sh2.CMPHS(opcode);
                break;
            case CMPIM:
                r = () -> sh2.CMPIM(opcode);
                break;
            case CMPPL:
                r = () -> sh2.CMPPL(opcode);
                break;
            case CMPPZ:
                r = () -> sh2.CMPPZ(opcode);
                break;
            case CMPSTR:
                r = () -> sh2.CMPSTR(opcode);
                break;
            case DIV0S:
                r = () -> sh2.DIV0S(opcode);
                break;
            case DIV0U:
                r = () -> sh2.DIV0U(opcode);
                break;
            case DIV1:
                r = () -> sh2.DIV1(opcode);
                break;
            case DMULS:
                r = () -> sh2.DMULS(opcode);
                break;
            case DMULU:
                r = () -> sh2.DMULU(opcode);
                break;
            case DT:
                r = () -> sh2.DT(opcode);
                break;
            case EXTSB:
                r = () -> sh2.EXTSB(opcode);
                break;
            case EXTSW:
                r = () -> sh2.EXTSW(opcode);
                break;
            case EXTUB:
                r = () -> sh2.EXTUB(opcode);
                break;
            case EXTUW:
                r = () -> sh2.EXTUW(opcode);
                break;
            case ILLEGAL:
                r = () -> sh2.ILLEGAL(opcode);
                break;
            case JMP:
                r = () -> sh2.JMP(opcode);
                break;
            case JSR:
                r = () -> sh2.JSR(opcode);
                break;
            case LDCGBR:
                r = () -> sh2.LDCGBR(opcode);
                break;
            case LDCMGBR:
                r = () -> sh2.LDCMGBR(opcode);
                break;
            case LDCMSR:
                r = () -> sh2.LDCMSR(opcode);
                break;
            case LDCMVBR:
                r = () -> sh2.LDCMVBR(opcode);
                break;
            case LDCSR:
                r = () -> sh2.LDCSR(opcode);
                break;
            case LDCVBR:
                r = () -> sh2.LDCVBR(opcode);
                break;
            case LDSMACH:
                r = () -> sh2.LDSMACH(opcode);
                break;
            case LDSMACL:
                r = () -> sh2.LDSMACL(opcode);
                break;
            case LDSMMACH:
                r = () -> sh2.LDSMMACH(opcode);
                break;
            case LDSMMACL:
                r = () -> sh2.LDSMMACL(opcode);
                break;
            case LDSMPR:
                r = () -> sh2.LDSMPR(opcode);
                break;
            case LDSPR:
                r = () -> sh2.LDSPR(opcode);
                break;
            case MACL:
                r = () -> sh2.MACL(opcode);
                break;
            case MACW:
                r = () -> sh2.MACW(opcode);
                break;
            case MOV:
                r = () -> sh2.MOV(opcode);
                break;
            case MOVA:
                r = () -> sh2.MOVA(opcode);
                break;
            case MOVBL:
                r = () -> sh2.MOVBL(opcode);
                break;
            case MOVBL0:
                r = () -> sh2.MOVBL0(opcode);
                break;
            case MOVBL4:
                r = () -> sh2.MOVBL4(opcode);
                break;
            case MOVBLG:
                r = () -> sh2.MOVBLG(opcode);
                break;
            case MOVBM:
                r = () -> sh2.MOVBM(opcode);
                break;
            case MOVBP:
                r = () -> sh2.MOVBP(opcode);
                break;
            case MOVBS:
                r = () -> sh2.MOVBS(opcode);
                break;
            case MOVBS0:
                r = () -> sh2.MOVBS0(opcode);
                break;
            case MOVBS4:
                r = () -> sh2.MOVBS4(opcode);
                break;
            case MOVBSG:
                r = () -> sh2.MOVBSG(opcode);
                break;
            case MOVI:
                r = () -> sh2.MOVI(opcode);
                break;
            case MOVLI:
                r = () -> sh2.MOVLI(opcode);
                break;
            case MOVLL:
                r = () -> sh2.MOVLL(opcode);
                break;
            case MOVLL0:
                r = () -> sh2.MOVLL0(opcode);
                break;
            case MOVLL4:
                r = () -> sh2.MOVLL4(opcode);
                break;
            case MOVLLG:
                r = () -> sh2.MOVLLG(opcode);
                break;
            case MOVLM:
                r = () -> sh2.MOVLM(opcode);
                break;
            case MOVLP:
                r = () -> sh2.MOVLP(opcode);
                break;
            case MOVLS:
                r = () -> sh2.MOVLS(opcode);
                break;
            case MOVLS0:
                r = () -> sh2.MOVLS0(opcode);
                break;
            case MOVLS4:
                r = () -> sh2.MOVLS4(opcode);
                break;
            case MOVLSG:
                r = () -> sh2.MOVLSG(opcode);
                break;
            case MOVT:
                r = () -> sh2.MOVT(opcode);
                break;
            case MOVWI:
                r = () -> sh2.MOVWI(opcode);
                break;
            case MOVWL:
                r = () -> sh2.MOVWL(opcode);
                break;
            case MOVWL0:
                r = () -> sh2.MOVWL0(opcode);
                break;
            case MOVWL4:
                r = () -> sh2.MOVWL4(opcode);
                break;
            case MOVWLG:
                r = () -> sh2.MOVWLG(opcode);
                break;
            case MOVWM:
                r = () -> sh2.MOVWM(opcode);
                break;
            case MOVWP:
                r = () -> sh2.MOVWP(opcode);
                break;
            case MOVWS:
                r = () -> sh2.MOVWS(opcode);
                break;
            case MOVWS0:
                r = () -> sh2.MOVWS0(opcode);
                break;
            case MOVWS4:
                r = () -> sh2.MOVWS4(opcode);
                break;
            case MOVWSG:
                r = () -> sh2.MOVWSG(opcode);
                break;
            case MULL:
                r = () -> sh2.MULL(opcode);
                break;
            case MULSU:
                r = () -> sh2.MULSU(opcode);
                break;
            case MULSW:
                r = () -> sh2.MULSW(opcode);
                break;
            case NEG:
                r = () -> sh2.NEG(opcode);
                break;
            case NEGC:
                r = () -> sh2.NEGC(opcode);
                break;
            case NOP:
                r = () -> sh2.NOP(opcode);
                break;
            case NOT:
                r = () -> sh2.NOT(opcode);
                break;
            case OR:
                r = () -> sh2.OR(opcode);
                break;
            case ORI:
                r = () -> sh2.ORI(opcode);
                break;
            case ORM:
                r = () -> sh2.ORM(opcode);
                break;
            case ROTCL:
                r = () -> sh2.ROTCL(opcode);
                break;
            case ROTCR:
                r = () -> sh2.ROTCR(opcode);
                break;
            case ROTL:
                r = () -> sh2.ROTL(opcode);
                break;
            case ROTR:
                r = () -> sh2.ROTR(opcode);
                break;
            case RTE:
                r = () -> sh2.RTE(opcode);
                break;
            case RTS:
                r = () -> sh2.RTS(opcode);
                break;
            case SETT:
                r = () -> sh2.SETT(opcode);
                break;
            case SHAL:
                r = () -> sh2.SHAL(opcode);
                break;
            case SHAR:
                r = () -> sh2.SHAR(opcode);
                break;
            case SHLL:
                r = () -> sh2.SHLL(opcode);
                break;
            case SHLL16:
                r = () -> sh2.SHLL16(opcode);
                break;
            case SHLL2:
                r = () -> sh2.SHLL2(opcode);
                break;
            case SHLL8:
                r = () -> sh2.SHLL8(opcode);
                break;
            case SHLR:
                r = () -> sh2.SHLR(opcode);
                break;
            case SHLR16:
                r = () -> sh2.SHLR16(opcode);
                break;
            case SHLR2:
                r = () -> sh2.SHLR2(opcode);
                break;
            case SHLR8:
                r = () -> sh2.SHLR8(opcode);
                break;
            case SLEEP:
                r = () -> sh2.SLEEP(opcode);
                break;
            case STCGBR:
                r = () -> sh2.STCGBR(opcode);
                break;
            case STCMGBR:
                r = () -> sh2.STCMGBR(opcode);
                break;
            case STCMSR:
                r = () -> sh2.STCMSR(opcode);
                break;
            case STCMVBR:
                r = () -> sh2.STCMVBR(opcode);
                break;
            case STCSR:
                r = () -> sh2.STCSR(opcode);
                break;
            case STCVBR:
                r = () -> sh2.STCVBR(opcode);
                break;
            case STSMACH:
                r = () -> sh2.STSMACH(opcode);
                break;
            case STSMACL:
                r = () -> sh2.STSMACL(opcode);
                break;
            case STSMMACH:
                r = () -> sh2.STSMMACH(opcode);
                break;
            case STSMMACL:
                r = () -> sh2.STSMMACL(opcode);
                break;
            case STSMPR:
                r = () -> sh2.STSMPR(opcode);
                break;
            case STSPR:
                r = () -> sh2.STSPR(opcode);
                break;
            case SUB:
                r = () -> sh2.SUB(opcode);
                break;
            case SUBC:
                r = () -> sh2.SUBC(opcode);
                break;
            case SUBV:
                r = () -> sh2.SUBV(opcode);
                break;
            case SWAPB:
                r = () -> sh2.SWAPB(opcode);
                break;
            case SWAPW:
                r = () -> sh2.SWAPW(opcode);
                break;
            case TAS:
                r = () -> sh2.TAS(opcode);
                break;
            case TRAPA:
                r = () -> sh2.TRAPA(opcode);
                break;
            case TST:
                r = () -> sh2.TST(opcode);
                break;
            case TSTI:
                r = () -> sh2.TSTI(opcode);
                break;
            case TSTM:
                r = () -> sh2.TSTM(opcode);
                break;
            case XOR:
                r = () -> sh2.XOR(opcode);
                break;
            case XORI:
                r = () -> sh2.XORI(opcode);
                break;
            case XORM:
                r = () -> sh2.XORM(opcode);
                break;
            case XTRCT:
                r = () -> sh2.XTRCT(opcode);
                break;
            default:
                r = () -> sh2.ILLEGAL(opcode);
                break;
        }
        return new Sh2InstructionWrapper(opcode, sh2Inst, r);
    }

    public enum Sh2BaseInstruction {
        ADD(0, 1),
        ADDC(0, 1),
        ADDI(0, 1),
        ADDV(0, 1),
        AND(0, 1),
        ANDI(0, 1),
        ANDM(0, 3),
        BF(9, 1, 3), //isBranch,illegalDelaySlot
        BFS(11, 1, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        BRA(11, 2, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        BRAF(11, 2, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        BSR(11, 2, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        BSRF(11, 2, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        BT(9, 1, 3), //isBranch,illegalDelaySlot
        BTS(11, 1, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        CLRMAC(0, 1),
        CLRT(0, 1),
        CMPEQ(0, 1),
        CMPGE(0, 1),
        CMPGT(0, 1),
        CMPHI(0, 1),
        CMPHS(0, 1),
        CMPIM(0, 1),
        CMPPL(0, 1),
        CMPPZ(0, 1),
        CMPSTR(0, 1),
        DIV0S(0, 1),
        DIV0U(0, 1),
        DIV1(0, 1),
        DMULS(0, 2),
        DMULU(0, 2),
        DT(0, 1),
        EXTSB(0, 1),
        EXTSW(0, 1),
        EXTUB(0, 1),
        EXTUW(0, 1),
        ILLEGAL(4, 5), //illegal,
        JMP(11, 2, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        JSR(11, 2, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        LDCGBR(0, 1),
        LDCMGBR(0, 3),
        LDCMSR(0, 3),
        LDCMVBR(0, 3),
        LDCSR(0, 1),
        LDCVBR(0, 1),
        LDSMACH(0, 2),
        LDSMACL(0, 2),
        LDSMMACH(0, 1),
        LDSMMACL(0, 1),
        LDSMPR(0, 1),
        LDSPR(0, 1),
        MACL(0, 2),
        MACW(0, 2),
        MOV(0, 1),
        MOVA(0, 1),
        MOVBL(0, 1),
        MOVBL0(0, 1),
        MOVBL4(0, 1),
        MOVBLG(0, 1),
        MOVBM(0, 1),
        MOVBP(0, 1),
        MOVBS(0, 1),
        MOVBS0(0, 1),
        MOVBS4(0, 1),
        MOVBSG(0, 1),
        MOVI(0, 1),
        MOVLI(0, 1),
        MOVLL(0, 1),
        MOVLL0(0, 1),
        MOVLL4(0, 1),
        MOVLLG(0, 1),
        MOVLM(0, 1),
        MOVLP(0, 1),
        MOVLS(0, 1),
        MOVLS0(0, 1),
        MOVLS4(0, 1),
        MOVLSG(0, 1),
        MOVT(0, 1),
        MOVWI(0, 1),
        MOVWL(0, 1),
        MOVWL0(0, 1),
        MOVWL4(0, 1),
        MOVWLG(0, 1),
        MOVWM(0, 1),
        MOVWP(0, 1),
        MOVWS(0, 1),
        MOVWS0(0, 1),
        MOVWS4(0, 1),
        MOVWSG(0, 1),
        MULL(0, 2),
        MULSU(0, 2),
        MULSW(0, 2),
        NEG(0, 1),
        NEGC(0, 1),
        NOP(0, 1),
        NOT(0, 1),
        OR(0, 1),
        ORI(0, 1),
        ORM(0, 3),
        ROTCL(0, 1),
        ROTCR(0, 1),
        ROTL(0, 1),
        ROTR(0, 1),
        RTE(11, 4, 4), //isBranch,isBranchDelaySlot,illegalDelaySlot
        RTS(11, 2, 2), //isBranch,isBranchDelaySlot,illegalDelaySlot
        SETT(0, 1),
        SHAL(0, 1),
        SHAR(0, 1),
        SHLL(0, 1),
        SHLL16(0, 1),
        SHLL2(0, 1),
        SHLL8(0, 1),
        SHLR(0, 1),
        SHLR16(0, 1),
        SHLR2(0, 1),
        SHLR8(0, 1),
        SLEEP(0, 4),
        STCGBR(0, 2),
        STCMGBR(0, 2),
        STCMSR(0, 2),
        STCMVBR(0, 2),
        STCSR(0, 2),
        STCVBR(0, 2),
        STSMACH(0, 1),
        STSMACL(0, 1),
        STSMMACH(0, 1),
        STSMMACL(0, 1),
        STSMPR(0, 1),
        STSPR(0, 2),
        SUB(0, 1),
        SUBC(0, 1),
        SUBV(0, 1),
        SWAPB(0, 1),
        SWAPW(0, 1),
        TAS(0, 1),
        TRAPA(9, 8), //isBranch, illegalDelaySlot
        TST(0, 1),
        TSTI(0, 1),
        TSTM(0, 3),
        XOR(0, 1),
        XORI(0, 1),
        XORM(0, 3),
        XTRCT(0, 1);

        public static final int FLAG_BRANCH = 0;
        public static final int FLAG_BRANCH_DELAY_SLOT = 1;
        public static final int FLAG_ILLEGAL = 2;
        public static final int FLAG_ILLEGAL_SLOT = 3;
        public static final int FLAG_BRANCH_MASK = 1 << FLAG_BRANCH;
        public static final int FLAG_BRANCH_DELAY_SLOT_MASK = 1 << FLAG_BRANCH_DELAY_SLOT;
        public static final int FLAG_ILLEGAL_MASK = 1 << FLAG_ILLEGAL;
        public static final int FLAG_ILLEGAL_SLOT_MASK = 1 << FLAG_ILLEGAL_SLOT;

        public final int cycles, cyclesBranch;

        /**
         * Bitfield:
         * bit0 : isBranch
         * bit1 : isBranchDelaySlot
         * bit2 : isIllegal
         * bit3 : isIllegalDelaySlot
         */
        public final int flags;

        Sh2BaseInstruction(int flags, int cycles) {
            this(flags, cycles, 0);
        }

        Sh2BaseInstruction(int flags, int cycles, int cyclesBranch) {
            this.flags = flags;
            this.cycles = cycles;
            this.cyclesBranch = cyclesBranch;
        }

        public boolean isBranch() {
            return (flags & FLAG_BRANCH_MASK) > 0;
        }

        public boolean isBranchDelaySlot() {
            return (flags & FLAG_BRANCH_DELAY_SLOT_MASK) > 0;
        }

        public boolean isIllegal() {
            return (flags & FLAG_ILLEGAL_MASK) > 0;
        }

        public boolean isIllegalSlot() {
            return (flags & FLAG_ILLEGAL_SLOT_MASK) > 0;
        }

        /**
         * Generate Sh2Inst list, cycle timing needs to be adjusted.
         */
        public static void main(String[] args) {
            Predicate<String> isBranchPred = n -> n.startsWith("B") || n.startsWith("J") || n.startsWith("RT") || n.startsWith("BRA")
                    || n.startsWith("TRAP");
            //Delayed branch instructions: JMP, JSR, BRA, BSR, RTS, RTE, BF/S, BT/S, BSRF,BRAF
            Predicate<String> isBranchDelaySlotPred = n -> n.startsWith("J") || n.startsWith("BRA") ||
                    n.startsWith("BSR") || n.startsWith("RT") || n.startsWith("BTS") || n.startsWith("BFS");
            //Illegal slot instruction: JMP, JSR, BRA, BSR, RTS, RTE, BT, BF, TRAPA, BFS, BTS, BSRF, BRAF
            Predicate<String> illegalDelaySlotPred = n -> n.startsWith("J") || n.startsWith("BRA") ||
                    n.startsWith("BSR") || n.startsWith("RT") || n.startsWith("BT") || n.startsWith("BF") || n.startsWith("TRAPA");
            Map<Sh2BaseInstruction, String> instSet = new EnumMap<>(Sh2BaseInstruction.class);
            Map<Sh2BaseInstruction, String> explain = new EnumMap<>(Sh2BaseInstruction.class);
            for (Sh2BaseInstruction i : Sh2BaseInstruction.values()) {
                boolean isBranch = isBranchPred.test(i.name());
                int cycles = i.cycles;
                String s = (isBranch ? "isBranch," : "") +
                        (isBranchDelaySlotPred.test(i.name()) ? "isBranchDelaySlot," : "") +
                        (i == ILLEGAL ? "illegal," : "") +
                        (illegalDelaySlotPred.test(i.name()) ? "illegalDelaySlot" : "");
                explain.put(i, s);

                int flags = ((isBranch ? 1 : 0) << FLAG_BRANCH) |
                        ((isBranchDelaySlotPred.test(i.name()) ? 1 : 0) << FLAG_BRANCH_DELAY_SLOT) |
                        ((i == ILLEGAL ? 1 : 0) << FLAG_ILLEGAL) |
                        ((illegalDelaySlotPred.test(i.name()) ? 1 : 0) << FLAG_ILLEGAL_SLOT);
                String val = i + "(" + flags + "," + cycles + (isBranch ? "," + i.cyclesBranch : "") + "), " +
                        (s.length() > 0 ? "//" + s : "");
                instSet.put(i, val);
            }
            String header = "inst,isBranch,isBranchDelaySlot,isIllegal,cycles,cyclesBranchTaken";
            String res = String.join("\n", explain.values());
            System.out.println(header + "\n" + res);

            header = "inst,flags,cycles,cyclesBranchTaken, explain";
            res = String.join("\n", instSet.values());
            System.out.println(header + "\n" + res);
        }
    }

}