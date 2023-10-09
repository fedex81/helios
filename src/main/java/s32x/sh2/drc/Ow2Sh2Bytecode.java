package s32x.sh2.drc;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.slf4j.Logger;
import s32x.bus.Sh2BusImpl;
import s32x.dict.S32xDict;
import s32x.sh2.*;
import s32x.sh2.prefetch.Sh2Prefetch.BytecodeContext;

import java.util.HashSet;
import java.util.Set;

import static omegadrive.util.Util.th;
import static org.objectweb.asm.Opcodes.*;
import static s32x.sh2.Sh2Impl.RM;
import static s32x.sh2.Sh2Impl.RN;
import static s32x.sh2.drc.Ow2Sh2Helper.DRC_CLASS_FIELD.regs;
import static s32x.sh2.drc.Ow2Sh2Helper.*;
import static s32x.sh2.drc.Ow2Sh2Helper.SH2CTX_CLASS_FIELD.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * NOTES:
 * - asm internally optimizes {visitVarInsn(*LLOAD, varIdx), visitVarInsn(*STORE, varIdx)} to *LOAD_n/*STORE_n when possible
 * - we manually opt ICONST -> {ICONST_n, BIPUSH, SIPUSH}, LCONST -> {LCONST_n}
 */
public class Ow2Sh2Bytecode {

    private final static Logger LOG = LogHelper.getLogger(Ow2Sh2Bytecode.class.getSimpleName());

    public static final boolean addPrintStuff = false, printMissingOpcodes = true;
    private static final Set<String> instSet = new HashSet<>();

    /**
     * @see Sh2Impl#ADD(int)
     */
    public static void ADD(BytecodeContext ctx) {
        opRegToReg(ctx, IADD);
    }


    public static final void ADDC(BytecodeContext ctx) {
        sumWithCarry(ctx, true);
    }

    //ADDC, SUBC
    private static final void sumWithCarry(BytecodeContext ctx, boolean add) {
        int n = RN(ctx.opcode);
        int m = RM(ctx.opcode);
        int tmp0Idx = ctx.mv.newLocal(Type.LONG_TYPE);
        int tmp1Idx = ctx.mv.newLocal(Type.LONG_TYPE);
        int regNIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int tbIdx = ctx.mv.newLocal(Type.BOOLEAN_TYPE);

        //long tmp0 = ctx.registers[n] & 0xFFFF_FFFFL;
        pushRegValStack(ctx, n);
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitVarInsn(LSTORE, tmp0Idx);

        //long tmp1 = (tmp0 + ctx.registers[m]) & 0xFFFF_FFFFL;
        ctx.mv.visitVarInsn(LLOAD, tmp0Idx);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitInsn(add ? LADD : LSUB);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitVarInsn(LSTORE, tmp1Idx);

//        long regN = (tmp1 + (ctx.SR & flagT)) & 0xFFFF_FFFFL;
        ctx.mv.visitVarInsn(LLOAD, tmp1Idx);
        pushSh2ContextIntField(ctx, SR.name());
        emitPushConstToStack(ctx, Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitInsn(add ? LADD : LSUB);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitVarInsn(LSTORE, regNIdx);

        //boolean tb = tmp0 > tmp1 || tmp1 > regN; (add)
        //boolean tb = tmp0 < tmp1 || tmp1 < regN; (sub)
        Label trueLabel = new Label();
        Label falseLabel = new Label();
        Label doneLabel = new Label();
        ctx.mv.visitVarInsn(LLOAD, tmp0Idx);
        ctx.mv.visitVarInsn(LLOAD, tmp1Idx);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(add ? IFGT : IFLT, trueLabel);
        ctx.mv.visitVarInsn(LLOAD, tmp1Idx);
        ctx.mv.visitVarInsn(LLOAD, regNIdx);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(add ? IFLE : IFGE, falseLabel);

        ctx.mv.visitLabel(trueLabel);
        emitPushConstToStack(ctx, 1); //true
        ctx.mv.visitJumpInsn(GOTO, doneLabel);

        ctx.mv.visitLabel(falseLabel);
        emitPushConstToStack(ctx, 0); //false

        ctx.mv.visitLabel(doneLabel);
        ctx.mv.visitVarInsn(ISTORE, tbIdx);

        //ctx.SR &= (~flagT);
        clearSrFlag(ctx, Sh2.flagT);

        //ctx.SR |= tb ? flagT : 0; (ctx.SR |= (int)tb)
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushSR(ctx);
        ctx.mv.visitVarInsn(ILOAD, tbIdx);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);

        //ctx.registers[n] = (int) regN;
        pushRegRefStack(ctx, n);
        ctx.mv.visitVarInsn(LLOAD, regNIdx);
        ctx.mv.visitInsn(L2I);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void ADDI(BytecodeContext ctx) {
        int n = RN(ctx.opcode);
        byte b = (byte) (ctx.opcode & 0xff);
        opRegImm(ctx, IADD, n, b);
    }

    public static final void ADDV(BytecodeContext ctx) {
        sumWithOverflow(ctx, true);
    }

    public static void AND(BytecodeContext ctx) {
        opRegToReg(ctx, IAND);
    }

    public static void ANDI(BytecodeContext ctx) {
        opReg0Imm(ctx, IAND, ((ctx.opcode >> 0) & 0xff));
    }

    public static void ANDM(BytecodeContext ctx) {
        opReg0Mem(ctx, IAND);
    }

    public static void BF(BytecodeContext ctx) {
        int d = (byte) (ctx.opcode & 0xFF) << 1;
        int newPcJump = ctx.pc + 4 + d;
        branchInternal(ctx, newPcJump, true, false);
    }

    public static void BT(BytecodeContext ctx) {
        int d = (byte) (ctx.opcode & 0xFF) << 1;
        int newPcJump = ctx.pc + 4 + d;
        branchInternal(ctx, newPcJump, false, false);
    }

    private static void branchInternal(BytecodeContext ctx, int pcJump, boolean isBF, boolean isDelaySlot) {
        Label elseLbl = new Label();
        Label doneLbl = new Label();
        pushSh2ContextIntField(ctx, SR.name());
        emitPushConstToStack(ctx, Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitJumpInsn(isBF ? IFNE : IFEQ, elseLbl);
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, pcJump);
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cyclesBranch);
        if (isDelaySlot) {
            delaySlot(ctx, pcJump);
        }
        ctx.mv.visitJumpInsn(GOTO, doneLbl);
        ctx.mv.visitLabel(elseLbl);
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, ctx.pc + 2);
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);
        ctx.mv.visitLabel(doneLbl);
    }

    public static void BFS(BytecodeContext ctx) {
        int d = (byte) (ctx.opcode & 0xFF) << 1;
        int newPcJump = ctx.pc + 4 + d;
        branchInternal(ctx, newPcJump, true, true);
    }

    public static void BRA(BytecodeContext ctx) {
        int disp;

        if ((ctx.opcode & 0x800) == 0)
            disp = (0x00000FFF & ctx.opcode);
        else disp = (0xFFFFF000 | ctx.opcode);

        int d = 4 + (disp << 1);

        //PC = PC + d
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, ctx.pc + d);
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);

        delaySlot(ctx, ctx.pc + d);
    }

    public static void BRAF(BytecodeContext ctx) {
        int n = RN(ctx.opcode);

        //PC = reg[n] + (pc + 4)
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, ctx.pc + 4);
        pushRegValStack(ctx, n);
        ctx.mv.visitInsn(IADD);
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);

        delaySlot(ctx);
    }

    public static void BSR(BytecodeContext ctx) {
        int disp = 0;
        if ((ctx.opcode & 0x800) == 0)
            disp = (0x00000FFF & ctx.opcode);
        else disp = (0xFFFFF000 | ctx.opcode);

        int d = (disp << 1) + 4;

        //PR = pc + 4;
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, ctx.pc + 4);
        popSh2ContextIntField(ctx, PR.name());
        //PC = pc + d;
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, ctx.pc + d);
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);

        delaySlot(ctx, ctx.pc + d);
    }

    public static void BSRF(BytecodeContext ctx) {
        int n = RN(ctx.opcode);

        //PR = pc + 4;
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, ctx.pc + 4);
        popSh2ContextIntField(ctx, PR.name());

        //PC = reg[n] + (pc + 4)
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, ctx.pc + 4);
        pushRegValStack(ctx, n);
        ctx.mv.visitInsn(IADD);
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);

        delaySlot(ctx);
    }

    public static void BTS(BytecodeContext ctx) {
        int d = (byte) (ctx.opcode & 0xFF) << 1;
        int newPcJump = ctx.pc + 4 + d;
        branchInternal(ctx, newPcJump, false, true);
    }

    public static void CLRMAC(BytecodeContext ctx) {
        pushSh2Context(ctx);
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, 0);
        ctx.mv.visitInsn(DUP_X1);
        popSh2ContextIntField(ctx, MACH.name());
        popSh2ContextIntField(ctx, MACL.name());
    }

    public static void CLRT(BytecodeContext ctx) {
        clearSrFlag(ctx, Sh2.flagT);
    }

    public static final void CMPEQ(BytecodeContext ctx) {
        cmpRegToReg(ctx, IF_ICMPNE);
    }

    public static final void CMPGE(BytecodeContext ctx) {
        cmpRegToReg(ctx, IF_ICMPLT);
    }

    public static final void CMPGT(BytecodeContext ctx) {
        cmpRegToReg(ctx, IF_ICMPLE);
    }

    public static final void CMPHS(BytecodeContext ctx) {
        cmpRegToRegUnsigned(ctx, IFLT);
    }

    public static final void CMPHI(BytecodeContext ctx) {
        cmpRegToRegUnsigned(ctx, IFLE);
    }

    public static final void CMPIM(BytecodeContext ctx) {
        int i = (byte) (ctx.opcode & 0xFF);
        pushRegValStack(ctx, 0);
        emitPushConstToStack(ctx, i);
        cmpInternal(ctx, IF_ICMPNE);

    }

    public static final void CMPPL(BytecodeContext ctx) {
        cmpRegToZero(ctx, RN(ctx.opcode), IFLE);
    }

    public static final void CMPPZ(BytecodeContext ctx) {
        cmpRegToZero(ctx, RN(ctx.opcode), IFLT);
    }

    public static final void CMPSTR(BytecodeContext ctx) {
        int n = RN(ctx.opcode);
        int m = RM(ctx.opcode);

        int tmpIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int hhIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int hlIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int lhIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int llIdx = ctx.mv.newLocal(Type.INT_TYPE);
        //int tmp = ctx.registers[n] ^ ctx.registers[m];
        pushTwoRegsValsStack(ctx, n, m);
        ctx.mv.visitInsn(IXOR);
        ctx.mv.visitVarInsn(ISTORE, tmpIdx);

//        int HH = (tmp >>> 24) & 0xff;
        ctx.mv.visitVarInsn(ILOAD, tmpIdx);
        emitPushConstToStack(ctx, 24);
        ctx.mv.visitInsn(IUSHR);
        emitPushConstToStack(ctx, 0xFF);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ISTORE, hhIdx);

//        int HL = (tmp >>> 16) & 0xff;
        ctx.mv.visitVarInsn(ILOAD, tmpIdx);
        emitPushConstToStack(ctx, 16);
        ctx.mv.visitInsn(IUSHR);
        emitPushConstToStack(ctx, 0xFF);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ISTORE, hlIdx);
//        int LH = (tmp >>> 8) & 0xff;
        ctx.mv.visitVarInsn(ILOAD, tmpIdx);
        emitPushConstToStack(ctx, 8);
        ctx.mv.visitInsn(IUSHR);
        emitPushConstToStack(ctx, 0xFF);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ISTORE, lhIdx);
//        int LL = tmp & 0xff;
        ctx.mv.visitVarInsn(ILOAD, tmpIdx);
        emitPushConstToStack(ctx, 0xFF);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ISTORE, llIdx);
//        ctx.SR &= ~flagT;
        clearSrFlag(ctx, Sh2.flagT);
//        if ((HH & HL & LH & LL) == 0) {
        Label endLabel = new Label();
        ctx.mv.visitVarInsn(ILOAD, hhIdx);
        ctx.mv.visitVarInsn(ILOAD, hlIdx);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ILOAD, lhIdx);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ILOAD, llIdx);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitJumpInsn(IFNE, endLabel);

        //ctx.SR |= flagT;
        setSrFlag(ctx, Sh2.flagT);

        ctx.mv.visitLabel(endLabel);
    }

    public static void cmpRegToZero(BytecodeContext ctx, int reg, int cmpOpcode) {
        pushRegRefStack(ctx, reg);
        ctx.mv.visitInsn(IALOAD);
        cmpInternal(ctx, cmpOpcode);
    }

    public static void cmpRegToReg(BytecodeContext ctx, int cmpOpcode) {
        int n = RN(ctx.opcode);
        int m = RM(ctx.opcode);
        pushTwoRegsValsStack(ctx, n, m);
        cmpInternal(ctx, cmpOpcode);
    }

    public static void cmpRegToRegUnsigned(BytecodeContext ctx, int cmpOpcode) {
        int n = RN(ctx.opcode);
        int m = RM(ctx.opcode);
        pushRegValStack(ctx, n);
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(LCMP);
        cmpInternal(ctx, cmpOpcode);
    }


    /**
     * if(a cmp b){
     * ctx.SR |= flagT;
     * } else {
     * ctx.SR &= ~flagT;
     * }
     */
    public static void cmpInternal(BytecodeContext ctx, int cmpOpcode) {
        Label elseLabel = new Label();
        Label endLabel = new Label();
        ctx.mv.visitJumpInsn(cmpOpcode, elseLabel);
        setSrFlag(ctx, Sh2.flagT); //then branch
        ctx.mv.visitJumpInsn(GOTO, endLabel); //then branch end
        ctx.mv.visitLabel(elseLabel); //else branch
        clearSrFlag(ctx, Sh2.flagT); //else branch end
        ctx.mv.visitLabel(endLabel);
    }

    //ctx.SR |= flag;
    private static void setSrFlag(BytecodeContext ctx, int flag) {
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushSR(ctx);
        emitPushConstToStack(ctx, flag);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);
    }

    // ctx.SR &= ~flag;
    private static void clearSrFlag(BytecodeContext ctx, int flag) {
        clearSrFlag(ctx, flag, true);
    }

    private static void clearSrFlag(BytecodeContext ctx, int flag, boolean popSr) {
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushSR(ctx);
        emitPushConstToStack(ctx, ~flag);
        ctx.mv.visitInsn(IAND);
        if (popSr) {
            popSR(ctx);
        }
    }

    public static void DIV0S(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);

        //SR &= ~(flagQ | flagM | flagT);
        clearSrFlag(ctx, Sh2.flagM | Sh2.flagQ | Sh2.flagT);

        // (((regs[10] >>> 31) & 1) << posQ)
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushSR(ctx);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, 31);
        ctx.mv.visitInsn(IUSHR);
//        pushIntConstStack(ctx ,1);  // &1 can be skipped
//        ctx.mv.visitInsn(IAND);
        emitPushConstToStack(ctx, Sh2.posQ);
        ctx.mv.visitInsn(ISHL);

        //  OR (((regs[11] >>> 31) & 1) << posM)
        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, 31);
        ctx.mv.visitInsn(IUSHR);
        emitPushConstToStack(ctx, Sh2.posM);
        ctx.mv.visitInsn(ISHL);
        ctx.mv.visitInsn(IOR);

        // OR ((((regs[11] ^ regs[10]) >>> 31) & 1) << posT);
        pushTwoRegsValsStack(ctx, n, m);
        ctx.mv.visitInsn(IXOR);
        emitPushConstToStack(ctx, 31);
        ctx.mv.visitInsn(IUSHR);
//        pushIntConstStack(ctx , posT); // << 0 can be skipped
//        ctx.mv.visitInsn(ISHL);
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitInsn(IOR);

        popSR(ctx);
    }

    public static void DIV1(BytecodeContext ctx) {
        int dvd = RN(ctx.opcode);
        int dvsr = RM(ctx.opcode);

        int udvdIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int udvsrIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int rIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int oldQIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int qmIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int qIdx = ctx.mv.newLocal(Type.INT_TYPE);

        //long udvd = ctx.registers[dvd] & 0xFFFF_FFFFL;
        pushRegRefStack(ctx, dvd);
        ctx.mv.visitInsn(IALOAD);
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitVarInsn(LSTORE, udvdIdx);
        //long udvsr = ctx.registers[dvsr] & 0xFFFF_FFFFL
        pushRegRefStack(ctx, dvsr);
        ctx.mv.visitInsn(IALOAD);
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitVarInsn(LSTORE, udvsrIdx);

//        //int old_q = (ctx.SR >> posQ) & 1;
        pushSh2Context(ctx);
        pushSR(ctx);
        emitPushConstToStack(ctx, Sh2.posQ);
        ctx.mv.visitInsn(ISHR);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ISTORE, oldQIdx);

        //ctx.SR &= ~flagQ;
        //ctx.SR |= ((udvd >> 31) & 1) << posQ;
        clearSrFlag(ctx, Sh2.flagQ);

        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushSR(ctx);
        ctx.mv.visitVarInsn(LLOAD, udvdIdx);
        emitPushConstToStack(ctx, 31);
        ctx.mv.visitInsn(LSHR);
        ctx.mv.visitInsn(L2I);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        emitPushConstToStack(ctx, Sh2.posQ);
        ctx.mv.visitInsn(ISHL);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);

        //long r = (udvd << 1) & 0xFFFF_FFFFL;
        ctx.mv.visitVarInsn(LLOAD, udvdIdx);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(LSHL);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitVarInsn(LSTORE, rIdx);

        //r |= (ctx.SR & flagT);
        ctx.mv.visitVarInsn(LLOAD, rIdx);
        pushSh2Context(ctx);
        pushSR(ctx);
        emitPushConstToStack(ctx, Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitInsn(LOR);
        ctx.mv.visitVarInsn(LSTORE, rIdx);

        //if (old_q == ((ctx.SR >> posM) & 1))
        Label elseLabel = new Label();
        Label endLabel = new Label();
        ctx.mv.visitVarInsn(ILOAD, oldQIdx);
        pushSh2Context(ctx);
        pushSR(ctx);
        emitPushConstToStack(ctx, Sh2.posM);
        ctx.mv.visitInsn(ISHR);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitJumpInsn(IF_ICMPNE, elseLabel);

        //{ r -= udvsr; }
        ctx.mv.visitVarInsn(LLOAD, rIdx);
        ctx.mv.visitVarInsn(LLOAD, udvsrIdx);
        ctx.mv.visitInsn(LSUB);
        ctx.mv.visitVarInsn(LSTORE, rIdx);
        ctx.mv.visitJumpInsn(GOTO, endLabel);

        //else { r += udvsr; }
        ctx.mv.visitLabel(elseLabel);
        ctx.mv.visitVarInsn(LLOAD, rIdx);
        ctx.mv.visitVarInsn(LLOAD, udvsrIdx);
        ctx.mv.visitInsn(LADD);
        ctx.mv.visitVarInsn(LSTORE, rIdx);

        //ctx.registers[dvd] = (int) r;
        ctx.mv.visitLabel(endLabel);
        pushRegRefStack(ctx, dvd);
        ctx.mv.visitVarInsn(LLOAD, rIdx);
        ctx.mv.visitInsn(L2I);
        ctx.mv.visitInsn(IASTORE);

        //int qm = ((ctx.SR >> posQ) & 1) ^ ((ctx.SR >> posM) & 1);
        pushSh2Context(ctx);
        pushSR(ctx);
        emitPushConstToStack(ctx, Sh2.posQ);
        ctx.mv.visitInsn(ISHR);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        pushSh2Context(ctx);
        pushSR(ctx);
        emitPushConstToStack(ctx, Sh2.posM);
        ctx.mv.visitInsn(ISHR);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IXOR);
        ctx.mv.visitVarInsn(ISTORE, qmIdx);

        //int q = qm ^ (int) ((r >> 32) & 1);
        ctx.mv.visitVarInsn(ILOAD, qmIdx);
        ctx.mv.visitVarInsn(LLOAD, rIdx);
        emitPushConstToStack(ctx, 32);
        ctx.mv.visitInsn(LSHR);
        emitPushLongConstToStack(ctx, 1);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(L2I);
        ctx.mv.visitInsn(IXOR);
        ctx.mv.visitVarInsn(ISTORE, qIdx);

        //qm = q ^ ((ctx.SR >> posM) & 1);
        ctx.mv.visitVarInsn(ILOAD, qIdx);
        pushSh2Context(ctx);
        pushSR(ctx);
        emitPushConstToStack(ctx, Sh2.posM);
        ctx.mv.visitInsn(ISHR);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IXOR);
        ctx.mv.visitVarInsn(ISTORE, qmIdx);

        //ctx.SR &= ~(flagQ | flagT);
        clearSrFlag(ctx, Sh2.flagQ | Sh2.flagT);

        //ctx.SR |= (q << posQ) | (1 - qm);
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushSR(ctx);
        ctx.mv.visitVarInsn(ILOAD, qIdx);
        emitPushConstToStack(ctx, Sh2.posQ);
        ctx.mv.visitInsn(ISHL);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitVarInsn(ILOAD, qmIdx);
        ctx.mv.visitInsn(ISUB);
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);
    }

    public static void DIV0U(BytecodeContext ctx) {
        clearSrFlag(ctx, Sh2.flagQ | Sh2.flagM | Sh2.flagT);
    }

    public static void DMULS(BytecodeContext ctx) {
        assert LocalVariablesSorter.class.isInstance(ctx.mv);
        int longVarIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushRegValStack(ctx, n);
        ctx.mv.visitInsn(I2L);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitInsn(LMUL);
        ctx.mv.visitVarInsn(LSTORE, longVarIdx);
        storeToMAC(ctx, longVarIdx);

    }

    public static void DMULU(BytecodeContext ctx) {
        assert LocalVariablesSorter.class.isInstance(ctx.mv);
        int longVarIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushRegValStack(ctx, n);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitLdcInsn(0xffffffffL);
        ctx.mv.visitInsn(LAND);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitLdcInsn(0xffffffffL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(LMUL);
        ctx.mv.visitVarInsn(LSTORE, longVarIdx);
        storeToMAC(ctx, longVarIdx);
    }

    public static void DT(BytecodeContext ctx) {
        int n = RN(ctx.opcode);
        decReg(ctx, n, 1);
        cmpRegToZero(ctx, n, IFNE);
    }

    public static void EXTSB(BytecodeContext ctx) {
        extSigned(ctx, Size.BYTE);
    }

    public static void EXTSW(BytecodeContext ctx) {
        extSigned(ctx, Size.WORD);
    }

    public static void EXTUB(BytecodeContext ctx) {
        extUnsigned(ctx, Size.BYTE);
    }

    public static void EXTUW(BytecodeContext ctx) {
        extUnsigned(ctx, Size.WORD);
    }

    public static void ILLEGAL(BytecodeContext ctx) {
        LOG.error("{} illegal instruction: {}\n{}", ctx.drcCtx.sh2Ctx.cpuAccess, th(ctx.opcode),
                Sh2Helper.toDebuggingString(ctx.drcCtx.sh2Ctx));
        pushSh2ContextIntField(ctx, PC.name());
        pushSh2ContextIntField(ctx, PC.name());
        pushSh2ContextIntField(ctx, SR.name());
        sh2PushReg15(ctx);
        sh2PushReg15(ctx);
        pushSh2ContextIntField(ctx, VBR.name());
        emitPushConstToStack(ctx, Sh2.ILLEGAL_INST_VN << 2);
        ctx.mv.visitInsn(IADD);
        readMem(ctx, Size.LONG);
        ctx.mv.visitFieldInsn(PUTFIELD, Type.getInternalName(Sh2Context.class), PC.name(), Ow2Sh2BlockRecompiler.intDesc);
    }

    public static void JMP(BytecodeContext ctx) {
        int n = RN(ctx.opcode);

        //PC = reg[n]
        pushSh2Context(ctx);
        pushRegValStack(ctx, n);
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);

        delaySlot(ctx);
    }

    public static void JSR(BytecodeContext ctx) {
        int n = RN(ctx.opcode);
        //PR = PC + 4
        pushSh2Context(ctx);
        emitPushConstToStack(ctx, ctx.pc + 4);
        popSh2ContextIntField(ctx, PR.name());

        //PC = reg[n]
        pushSh2Context(ctx);
        pushRegValStack(ctx, n);
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);

        delaySlot(ctx);
    }

    public static void LDCGBR(BytecodeContext ctx) {
        ldcReg(ctx, GBR.name(), -1);
    }

    public static void LDCSR(BytecodeContext ctx) {
        ldcReg(ctx, SR.name(), Sh2.SR_MASK);
    }

    public static void LDCVBR(BytecodeContext ctx) {
        ldcReg(ctx, VBR.name(), -1);
    }

    public static final void LDCMGBR(BytecodeContext ctx) {
        ldcmReg(ctx, GBR.name(), -1);
    }

    public static final void LDCMSR(BytecodeContext ctx) {
        ldcmReg(ctx, SR.name(), Sh2.SR_MASK);
    }

    public static final void LDCMVBR(BytecodeContext ctx) {
        ldcmReg(ctx, VBR.name(), -1);
    }

    public static void LDSMACH(BytecodeContext ctx) {
        ldcReg(ctx, MACH.name(), -1);
    }

    public static void LDSMACL(BytecodeContext ctx) {
        ldcReg(ctx, MACL.name(), -1);
    }

    public static void LDSPR(BytecodeContext ctx) {
        ldcReg(ctx, PR.name(), -1);
    }

    public static final void LDSMMACH(BytecodeContext ctx) {
        ldcmReg(ctx, MACH.name(), -1);
    }

    public static final void LDSMMACL(BytecodeContext ctx) {
        ldcmReg(ctx, MACL.name(), -1);
    }

    public static final void LDSMPR(BytecodeContext ctx) {
        ldcmReg(ctx, PR.name(), -1);
    }

    public static final void MACL(BytecodeContext ctx) {
        int n = RN(ctx.opcode);
        int m = RM(ctx.opcode);
        int regNIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int regMIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int resIdx = ctx.mv.newLocal(Type.LONG_TYPE);

//        long regN = memory.read32(ctx.registers[n]);
//        ctx.registers[n] += 4;
        pushMemory(ctx);
        pushRegValStack(ctx, n);
        readMem(ctx, Size.LONG);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitVarInsn(LSTORE, regNIdx);
        pushRegRefStack(ctx, n);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        emitPushConstToStack(ctx, 4);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitInsn(IASTORE);

//        long regM = memory.read32(ctx.registers[m]);
//        ctx.registers[m] += 4;
        pushMemory(ctx);
        pushRegValStack(ctx, m);
        readMem(ctx, Size.LONG);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitVarInsn(LSTORE, regMIdx);
        pushRegRefStack(ctx, m);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        emitPushConstToStack(ctx, 4);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitInsn(IASTORE);

        //long res = regM * regN;
        ctx.mv.visitVarInsn(LLOAD, regMIdx);
        ctx.mv.visitVarInsn(LLOAD, regNIdx);
        ctx.mv.visitInsn(LMUL);
        ctx.mv.visitVarInsn(LSTORE, resIdx);

        //res += ((ctx.MACH & 0xFFFF_FFFFL) << 32) + (ctx.MACL & 0xFFFF_FFFFL);
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        pushSh2ContextIntField(ctx, MACH.name());
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        emitPushConstToStack(ctx, 32);
        ctx.mv.visitInsn(LSHL);
        pushSh2ContextIntField(ctx, MACL.name());
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(LADD);
        ctx.mv.visitInsn(LADD);
        ctx.mv.visitVarInsn(LSTORE, resIdx);

        //if ((ctx.SR & flagS) > 0) {
        Label endLabel = new Label();
        Label elseIfLabel = new Label();
        pushSh2Context(ctx);
        pushSR(ctx);
        emitPushConstToStack(ctx, Sh2.flagS);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitJumpInsn(IFLE, endLabel);

//        if (res > 0x7FFF_FFFF_FFFFL) {
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, 0x7FFF_FFFF_FFFFL);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFLE, elseIfLabel);

        //res = 0x7FFF_FFFF_FFFFL;
        emitPushLongConstToStack(ctx, 0x7FFF_FFFF_FFFFL);
        ctx.mv.visitVarInsn(LSTORE, resIdx);
        ctx.mv.visitJumpInsn(GOTO, endLabel);

        //} else if (res < 0xFFFF_8000_0000_0000L) {
        ctx.mv.visitLabel(elseIfLabel);
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, 0xFFFF_8000_0000_0000L);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFGE, endLabel);

        //res = 0xFFFF_8000_0000_0000L;
        emitPushLongConstToStack(ctx, 0xFFFF_8000_0000_0000L);
        ctx.mv.visitVarInsn(LSTORE, resIdx);

        ctx.mv.visitLabel(endLabel);

//        ctx.MACH = (int) (res >> 32);
        pushSh2Context(ctx);
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushConstToStack(ctx, 32);
        ctx.mv.visitInsn(LSHR);
        ctx.mv.visitInsn(L2I);
        popSh2ContextIntField(ctx, MACH.name());

        //        ctx.MACL = (int) (res & 0xFFFF_FFFF);
        pushSh2Context(ctx);
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, -1L);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(L2I);
        popSh2ContextIntField(ctx, MACL.name());
    }

    public static final void MACW(BytecodeContext ctx) {
        int n = RN(ctx.opcode);
        int m = RM(ctx.opcode);
        int regNIdx = ctx.mv.newLocal(Type.SHORT_TYPE);
        int regMIdx = ctx.mv.newLocal(Type.SHORT_TYPE);
        Label returnLabel = new Label();

//        final short rn = (short) memory.read16(ctx.registers[n]);
//        ctx.registers[n] += 2;
        pushMemory(ctx);
        pushRegValStack(ctx, n);
        readMem(ctx, Size.WORD);
        ctx.mv.visitInsn(I2S);
        ctx.mv.visitVarInsn(ISTORE, regNIdx);
        pushRegRefStack(ctx, n);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        emitPushConstToStack(ctx, 2);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitInsn(IASTORE);

//        final short rm = (short) memory.read16(ctx.registers[m]);
//        ctx.registers[m] += 2;
        pushMemory(ctx);
        pushRegValStack(ctx, m);
        readMem(ctx, Size.WORD);
        ctx.mv.visitInsn(I2S);
        ctx.mv.visitVarInsn(ISTORE, regMIdx);
        pushRegRefStack(ctx, m);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        emitPushConstToStack(ctx, 2);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitInsn(IASTORE);
        // if ((this.sh2Context.SR & flagS) > 0) {
        Label macw64Label = new Label();
        pushSh2ContextIntField(ctx, SR.name());
        emitPushConstToStack(ctx, Sh2.flagS);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitJumpInsn(IFLE, macw64Label);
        // { macw32 block}
        macw32Block(ctx, regNIdx, regMIdx, returnLabel);
        // else { macw64 block}
        macw64Block(ctx, regNIdx, regMIdx, macw64Label, returnLabel);
        ctx.mv.visitLabel(returnLabel);
    }

    //part of MACW
    private static void macw32Block(BytecodeContext ctx, int regNIdx, int regMIdx, Label returnLabel) {
        int resIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        Label elseIfLabel = new Label();
        Label endLabel = new Label();

//        long res = rm * rn + (long) ctx.MACL;
        ctx.mv.visitVarInsn(ILOAD, regMIdx);
        ctx.mv.visitVarInsn(ILOAD, regNIdx);
        ctx.mv.visitInsn(IMUL);
        ctx.mv.visitInsn(I2L);
        pushSh2ContextIntField(ctx, MACL.name());
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitInsn(LADD);
        ctx.mv.visitVarInsn(LSTORE, resIdx);

//        if (res > 0x7FFF_FFFFL) {
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, 0x7FFF_FFFFL);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFLE, elseIfLabel);

        //res = 0x7FFF_FFFFL;
        //ctx.MACH |= 1;
        emitPushLongConstToStack(ctx, 0x7FFF_FFFFL);
        ctx.mv.visitVarInsn(LSTORE, resIdx);
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushField(ctx, Sh2Context.class, MACH.name(), int.class);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IOR);
        popSh2ContextIntField(ctx, MACH.name());
        ctx.mv.visitJumpInsn(GOTO, endLabel);

        //} else if (res < 0xFFFF_FFFF_8000_0000L) {
        ctx.mv.visitLabel(elseIfLabel);
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFF_8000_0000L);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFGE, endLabel);

        //	res = 0xFFFF_FFFF_8000_0000L;
        //	ctx.MACH |= 1;
        emitPushLongConstToStack(ctx, 0xFFFF_FFFF_8000_0000L);
        ctx.mv.visitVarInsn(LSTORE, resIdx);
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushField(ctx, Sh2Context.class, MACH.name(), int.class);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IOR);
        popSh2ContextIntField(ctx, MACH.name());
        ctx.mv.visitJumpInsn(GOTO, endLabel);

//        ctx.MACL = (int) (res & 0xFFFF_FFFF);
        //return
        ctx.mv.visitLabel(endLabel);
        pushSh2Context(ctx);
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, -1L);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(L2I);
        popSh2ContextIntField(ctx, MACL.name());
        ctx.mv.visitJumpInsn(GOTO, returnLabel);
    }

    //part of MACW
    private static void macw64Block(BytecodeContext ctx, int regNIdx, int regMIdx, Label macw64Label, Label returnLabel) {
        int prodIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int macIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        int resIdx = ctx.mv.newLocal(Type.LONG_TYPE);
        ctx.mv.visitLabel(macw64Label);
        //long prod = rm * rn;
        ctx.mv.visitVarInsn(ILOAD, regMIdx);
        ctx.mv.visitVarInsn(ILOAD, regNIdx);
        ctx.mv.visitInsn(IMUL);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitVarInsn(LSTORE, prodIdx);

        //long mac = ((ctx.MACH & 0xFFFF_FFFFL) << 32) + (ctx.MACL & 0xFFFF_FFFFL);
        pushSh2ContextIntField(ctx, MACH.name());
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        emitPushConstToStack(ctx, 32);
        ctx.mv.visitInsn(LSHL);
        pushSh2ContextIntField(ctx, MACL.name());
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(LADD);
        ctx.mv.visitVarInsn(LSTORE, macIdx);

        //long res = prod + mac;
        ctx.mv.visitVarInsn(LLOAD, prodIdx);
        ctx.mv.visitVarInsn(LLOAD, macIdx);
        ctx.mv.visitInsn(LADD);
        ctx.mv.visitVarInsn(LSTORE, resIdx);

        //ctx.MACH = (int) (res >> 32);
        //ctx.MACL = (int) (res & 0xFFFF_FFFF);
        pushSh2Context(ctx);
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushConstToStack(ctx, 32);
        ctx.mv.visitInsn(LSHR);
        ctx.mv.visitInsn(L2I);
        popSh2ContextIntField(ctx, MACH.name());
        pushSh2Context(ctx);
        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, -1L);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(L2I);
        popSh2ContextIntField(ctx, MACL.name());

        //if ((prod > 0 && mac > 0 && res < 0) || ...
        Label ifLabel64 = new Label();
        Label orSecondPartLabel = new Label();
        ctx.mv.visitVarInsn(LLOAD, prodIdx);
        emitPushLongConstToStack(ctx, 0);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFLE, orSecondPartLabel);

        ctx.mv.visitVarInsn(LLOAD, macIdx);
        emitPushLongConstToStack(ctx, 0);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFLE, orSecondPartLabel);

        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, 0);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFLT, ifLabel64);

        //|| (mac < 0 && prod < 0 && res > 0)) {
        ctx.mv.visitLabel(orSecondPartLabel);

        ctx.mv.visitVarInsn(LLOAD, macIdx);
        emitPushLongConstToStack(ctx, 0);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFGE, returnLabel);

        ctx.mv.visitVarInsn(LLOAD, prodIdx);
        emitPushLongConstToStack(ctx, 0);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFGE, returnLabel);

        ctx.mv.visitVarInsn(LLOAD, resIdx);
        emitPushLongConstToStack(ctx, 0);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFLE, returnLabel);

//        ctx.MACH |= 1;
        ctx.mv.visitLabel(ifLabel64);
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushField(ctx, Sh2Context.class, MACH.name(), int.class);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IOR);
        popSh2ContextIntField(ctx, MACH.name());
    }

    public static void MOV(BytecodeContext ctx) {
        int n = RN(ctx.opcode);
        int m = RM(ctx.opcode);
        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void MOVA(BytecodeContext ctx) {
        int pcBase = ctx.pc + 4;
        int d = ctx.opcode & 0xff;
        if (ctx.delaySlot) { //TODO test
            LOG.warn("MOVA delaySlot at PC: {}, branchPc: {}", th(ctx.pc), th(ctx.branchPc));
            System.out.println("MOVA delaySlot");
            assert ctx.branchPc != 0;
            pcBase = ctx.branchPc + 2;
        }
        int memAddr = (pcBase & 0xfffffffc) + (d << 2);
        pushRegRefStack(ctx, 0);
        emitPushConstToStack(ctx, memAddr);
        ctx.mv.visitInsn(IASTORE);
    }

    public final static void MOVBL(BytecodeContext ctx) {
        movMemToReg(ctx, Size.BYTE);
    }

    public final static void MOVWL(BytecodeContext ctx) {
        movMemToReg(ctx, Size.WORD);
    }

    public final static void MOVLL(BytecodeContext ctx) {
        movMemToReg(ctx, Size.LONG);
    }

    public final static void MOVBL0(BytecodeContext ctx) {
        movMemWithReg0ShiftToReg(ctx, Size.BYTE);
    }

    public final static void MOVWL0(BytecodeContext ctx) {
        movMemWithReg0ShiftToReg(ctx, Size.WORD);
    }

    public final static void MOVLL0(BytecodeContext ctx) {
        movMemWithReg0ShiftToReg(ctx, Size.LONG);
    }

    public final static void MOVBL4(BytecodeContext ctx) {
        movMemToRegShift(ctx, Size.BYTE);
    }

    public final static void MOVWL4(BytecodeContext ctx) {
        movMemToRegShift(ctx, Size.WORD);
    }

    public final static void MOVLL4(BytecodeContext ctx) {
        movMemToRegShift(ctx, Size.LONG);
    }

    public final static void MOVBLG(BytecodeContext ctx) {
        movMemWithGBRShiftToReg0(ctx, Size.BYTE);
    }

    public final static void MOVWLG(BytecodeContext ctx) {
        movMemWithGBRShiftToReg0(ctx, Size.WORD);
    }

    public final static void MOVLLG(BytecodeContext ctx) {
        movMemWithGBRShiftToReg0(ctx, Size.LONG);
    }

    public static final void MOVBP(BytecodeContext ctx) {
        movMemToRegPostInc(ctx, Size.BYTE);
    }

    public static final void MOVWP(BytecodeContext ctx) {
        movMemToRegPostInc(ctx, Size.WORD);
    }

    public static final void MOVLP(BytecodeContext ctx) {
        movMemToRegPostInc(ctx, Size.LONG);
    }

    public final static void MOVBM(BytecodeContext ctx) {
        movRegPredecToMem(ctx, Size.BYTE);
    }

    public final static void MOVWM(BytecodeContext ctx) {
        movRegPredecToMem(ctx, Size.WORD);
    }

    public final static void MOVLM(BytecodeContext ctx) {
        movRegPredecToMem(ctx, Size.LONG);
    }

    public final static void MOVBS(BytecodeContext ctx) {
        movRegToReg(ctx, Size.BYTE);
    }

    public final static void MOVWS(BytecodeContext ctx) {
        movRegToReg(ctx, Size.WORD);
    }

    public final static void MOVLS(BytecodeContext ctx) {
        movRegToReg(ctx, Size.LONG);
    }

    public final static void MOVBS0(BytecodeContext ctx) {
        movRegToMemWithReg0Shift(ctx, Size.BYTE);
    }

    public final static void MOVWS0(BytecodeContext ctx) {
        movRegToMemWithReg0Shift(ctx, Size.WORD);
    }

    public final static void MOVLS0(BytecodeContext ctx) {
        movRegToMemWithReg0Shift(ctx, Size.LONG);
    }

    public static final void MOVBS4(BytecodeContext ctx) {
        movRegToRegShift(ctx, Size.BYTE);
    }

    public static final void MOVWS4(BytecodeContext ctx) {
        movRegToRegShift(ctx, Size.WORD);
    }

    public static final void MOVLS4(BytecodeContext ctx) {
        movRegToRegShift(ctx, Size.LONG);
    }

    public static final void MOVBSG(BytecodeContext ctx) {
        movGBRShiftToReg0(ctx, Size.BYTE);
    }

    public static final void MOVWSG(BytecodeContext ctx) {
        movGBRShiftToReg0(ctx, Size.WORD);
    }

    public static final void MOVLSG(BytecodeContext ctx) {
        movGBRShiftToReg0(ctx, Size.LONG);
    }

    public static void MOVI(BytecodeContext ctx) {
        int reg = (ctx.opcode >> 8) & 0xF;
        storeToReg(ctx, reg, ctx.opcode, Size.BYTE);

    }

    public static void MOVT(BytecodeContext ctx) {
        int n = RN(ctx.opcode);
        pushRegRefStack(ctx, n);
        pushSh2ContextIntField(ctx, SR.name());
        emitPushConstToStack(ctx, Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IASTORE);

    }

    public static final void MOVWI(BytecodeContext ctx) {
        movMemWithPcOffsetToReg(ctx, Size.WORD);
    }

    public static final void MOVLI(BytecodeContext ctx) {
        movMemWithPcOffsetToReg(ctx, Size.LONG);
    }

    public static void MULL(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushSh2Context(ctx);
        pushTwoRegsValsStack(ctx, n, m);
        ctx.mv.visitInsn(IMUL);
        ctx.mv.visitInsn(ICONST_M1);
        ctx.mv.visitInsn(IAND);
        popSh2ContextIntField(ctx, MACL.name());

    }

    public static void MULSW(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushSh2Context(ctx);
        pushRegValStack(ctx, n);
        ctx.mv.visitInsn(I2S);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(I2S);
        ctx.mv.visitInsn(IMUL);
        popSh2ContextIntField(ctx, MACL.name());

    }

    public static void MULSU(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushSh2Context(ctx);
        pushRegValStack(ctx, n);
        ctx.mv.visitLdcInsn(0xffff);
        ctx.mv.visitInsn(IAND);
        pushRegValStack(ctx, m);
        ctx.mv.visitLdcInsn(0xffff);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IMUL);
        popSh2ContextIntField(ctx, MACL.name());

    }

    public static void NEG(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushRegRefStack(ctx, n);
        ctx.mv.visitInsn(ICONST_0);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(ISUB);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void NEGC(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        int temp0Idx = ctx.mv.newLocal(Type.LONG_TYPE);
        int regNIdx = ctx.mv.newLocal(Type.LONG_TYPE);

        //long tmp = (0 - ctx.registers[m]) & 0xFFFF_FFFFL;
        emitPushConstToStack(ctx, 0);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(ISUB);
        ctx.mv.visitInsn(I2L);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitVarInsn(LSTORE, temp0Idx);

//        long regN = (tmp - (ctx.SR & flagT)) & 0xFFFF_FFFFL;
        ctx.mv.visitVarInsn(LLOAD, temp0Idx);
        pushSh2Context(ctx);
        pushSR(ctx);
        emitPushConstToStack(ctx, Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(I2L);
        ctx.mv.visitInsn(LSUB);
        emitPushLongConstToStack(ctx, 0xFFFF_FFFFL);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitVarInsn(LSTORE, regNIdx);

//        ctx.registers[n] = (int) regN;
        pushRegRefStack(ctx, n);
        ctx.mv.visitVarInsn(LLOAD, regNIdx);
        ctx.mv.visitInsn(L2I);
        ctx.mv.visitInsn(IASTORE);

//        ctx.SR &= ~flagT;
        clearSrFlag(ctx, Sh2.flagT);

//        if(tmp > 0 || tmp < regN){
//            ctx.SR |= flagT;
//        }
        Label endLabel = new Label();
        Label ifLabel = new Label();
        ctx.mv.visitVarInsn(LLOAD, temp0Idx);
        ctx.mv.visitInsn(LCONST_0);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFGT, ifLabel);   //skip eval tmp < regN
        ctx.mv.visitVarInsn(LLOAD, temp0Idx);
        ctx.mv.visitVarInsn(LLOAD, regNIdx);
        ctx.mv.visitInsn(LCMP);
        ctx.mv.visitJumpInsn(IFGE, endLabel);

        ctx.mv.visitLabel(ifLabel);
        setSrFlag(ctx, Sh2.flagT);

        ctx.mv.visitLabel(endLabel);
    }

    public static void NOP(BytecodeContext ctx) {
        //nop
    }

    public static void NOT(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(ICONST_M1);
        ctx.mv.visitInsn(IXOR);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void OR(BytecodeContext ctx) {
        opRegToReg(ctx, IOR);
    }

    public static void ORI(BytecodeContext ctx) {
        opReg0Imm(ctx, IOR, ((ctx.opcode >> 0) & 0xff));
    }

    public static void ORM(BytecodeContext ctx) {
        opReg0Mem(ctx, IOR);
    }

    public static final void ROTCL(BytecodeContext ctx) {
        rotateRegWithCarry(ctx, true);
    }

    public static final void ROTCR(BytecodeContext ctx) {
        rotateRegWithCarry(ctx, false);
    }

    public static final void ROTL(BytecodeContext ctx) {
        rotateReg(ctx, true);
    }

    public static final void ROTR(BytecodeContext ctx) {
        rotateReg(ctx, false);
    }

    public static final void RTE(BytecodeContext ctx) {
        pushSh2Context(ctx);
        sh2PopReg15(ctx);
        popSh2ContextIntField(ctx, PC.name());

        pushSh2Context(ctx);
        sh2PopReg15(ctx);
        emitPushConstToStack(ctx, Sh2.SR_MASK);
        ctx.mv.visitInsn(IAND);
        popSh2ContextIntField(ctx, SR.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);

        delaySlot(ctx);
    }

    public static final void RTS(BytecodeContext ctx) {
        //store PR to PC
        pushSh2Context(ctx);
        pushSh2ContextIntField(ctx, PR.name());
        popSh2ContextIntField(ctx, PC.name());
        subCyclesExt(ctx, ctx.sh2Inst.cycles);

        delaySlot(ctx);
    }

    private static void delaySlot(BytecodeContext ctx) {
        delaySlot(ctx, 0);
    }

    private static void delaySlot(BytecodeContext ctx, int branchPc) {
        assert ctx.delaySlotCtx != null && ctx.delaySlotCtx.delaySlot && !ctx.delaySlot;
        assert ctx.drcCtx.sh2Ctx == ctx.delaySlotCtx.drcCtx.sh2Ctx;
        ctx.delaySlotCtx.branchPc = branchPc;
        createInst(ctx.delaySlotCtx);
    }

    public final static void SETT(BytecodeContext ctx) {
        setSrFlag(ctx, Sh2.flagT);
    }

    public final static void SLEEP(BytecodeContext ctx) {
        LOG.warn("SLEEP");
        System.out.println("SLEEP");
    }

    public final static void STCSR(BytecodeContext ctx) {
        stsToReg(ctx, SR.name());
    }

    public static void STCVBR(BytecodeContext ctx) {
        stsToReg(ctx, VBR.name());
    }

    public static void STCGBR(BytecodeContext ctx) {
        stsToReg(ctx, GBR.name());
    }

    public static final void STCMGBR(BytecodeContext ctx) {
        stsMem(ctx, GBR.name());
    }

    public static final void STCMSR(BytecodeContext ctx) {
        stsMem(ctx, SR.name());
    }

    public static final void STCMVBR(BytecodeContext ctx) {
        stsMem(ctx, VBR.name());
    }

    public static final void STSMMACH(BytecodeContext ctx) {
        stsMem(ctx, MACH.name());
    }

    public static final void STSMMACL(BytecodeContext ctx) {
        stsMem(ctx, MACL.name());
    }

    public static final void STSMPR(BytecodeContext ctx) {
        stsMem(ctx, PR.name());
    }

    public static void STSMACH(BytecodeContext ctx) {
        stsToReg(ctx, MACH.name());
    }

    public static void STSMACL(BytecodeContext ctx) {
        stsToReg(ctx, MACL.name());
    }

    public static void STSPR(BytecodeContext ctx) {
        stsToReg(ctx, PR.name());
    }

    public static void SUB(BytecodeContext ctx) {
        opRegToReg(ctx, ISUB);
    }

    public static void SUBC(BytecodeContext ctx) {
        sumWithCarry(ctx, false);
    }

    public static void SUBV(BytecodeContext ctx) {
        sumWithOverflow(ctx, false);
    }

    private static void sumWithOverflow(BytecodeContext ctx, boolean add) {
        int n = RN(ctx.opcode);
        int m = RM(ctx.opcode);
        int dIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int sIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int rIdx = ctx.mv.newLocal(Type.INT_TYPE);

        //int d = (ctx.registers[n] >> 31) & 1;
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, 31);
        ctx.mv.visitInsn(ISHR);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ISTORE, dIdx);

        //int s = ((ctx.registers[m] >> 31) & 1) + d;
        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, 31);
        ctx.mv.visitInsn(ISHR);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ILOAD, dIdx);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitVarInsn(ISTORE, sIdx);

        //ctx.registers[n] -= ctx.registers[m];
        pushRegRefStack(ctx, n);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(add ? IADD : ISUB);
        ctx.mv.visitInsn(IASTORE);

//        int r = ((ctx.registers[n] >> 31) & 1) + dest;
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, 31);
        ctx.mv.visitInsn(ISHR);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ILOAD, dIdx);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitVarInsn(ISTORE, rIdx);

//        ctx.SR &= (~flagT);
        clearSrFlag(ctx, Sh2.flagT);

//        ctx.SR |= (s & r) & 1; (sub)
//        ctx.SR |= ((s+1) & r) & 1; (sub)
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        pushSR(ctx);
        ctx.mv.visitVarInsn(ILOAD, rIdx);
        ctx.mv.visitVarInsn(ILOAD, sIdx);
        if (add) {
            emitPushConstToStack(ctx, 1);
            ctx.mv.visitInsn(IADD);
        }
        ctx.mv.visitInsn(IAND);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);
    }

    public static final void SHAL(BytecodeContext ctx) {
        shiftArithmetic(ctx, true);
    }

    public static final void SHAR(BytecodeContext ctx) {
        shiftArithmetic(ctx, false);
    }

    public static final void SHLL(BytecodeContext ctx) {
        shiftLogical(ctx, true);
    }

    public static final void SHLR(BytecodeContext ctx) {
        shiftLogical(ctx, false);
    }

    public static void SHLL2(BytecodeContext ctx) {
        shiftConst(ctx, ISHL, 2);
    }

    public static void SHLL8(BytecodeContext ctx) {
        shiftConst(ctx, ISHL, 8);
    }

    public static void SHLL16(BytecodeContext ctx) {
        shiftConst(ctx, ISHL, 16);
    }

    public static void SHLR2(BytecodeContext ctx) {
        shiftConst(ctx, IUSHR, 2);
    }

    public static void SHLR8(BytecodeContext ctx) {
        shiftConst(ctx, IUSHR, 8);
    }

    public static void SHLR16(BytecodeContext ctx) {
        shiftConst(ctx, IUSHR, 16);
    }

    public static void SWAPB(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        int temp0Idx = ctx.mv.newLocal(Type.INT_TYPE);
        int temp1Idx = ctx.mv.newLocal(Type.INT_TYPE);

        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, 0xFFFF0000);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ISTORE, temp0Idx);

        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, 0xFF);
        ctx.mv.visitInsn(IAND);
        emitPushConstToStack(ctx, 8);
        ctx.mv.visitInsn(ISHL);
        ctx.mv.visitVarInsn(ISTORE, temp1Idx);

        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, 0xFF00);
        ctx.mv.visitInsn(IAND);
        emitPushConstToStack(ctx, 8);
        ctx.mv.visitInsn(ISHR);
        ctx.mv.visitInsn(IASTORE);

        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, n);
        ctx.mv.visitVarInsn(ILOAD, temp1Idx);
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitVarInsn(ILOAD, temp0Idx);
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void SWAPW(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);

        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, 16);
        ctx.mv.visitInsn(ISHL);
        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, 16);
        ctx.mv.visitInsn(IUSHR);
        emitPushConstToStack(ctx, 0xFFFF);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void TAS(BytecodeContext ctx) {
        int n = RN(ctx.opcode);

        int valIdx = ctx.mv.newLocal(Type.INT_TYPE);

        pushMemory(ctx);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, S32xDict.SH2_CACHE_THROUGH_OFFSET);
        ctx.mv.visitInsn(IOR);
        readMem(ctx, Size.BYTE);
        ctx.mv.visitInsn(DUP);
        ctx.mv.visitVarInsn(ISTORE, valIdx);

        cmpInternal(ctx, IFNE);

        pushMemory(ctx);
        pushRegValStack(ctx, n);
        ctx.mv.visitVarInsn(ILOAD, valIdx);
        emitPushConstToStack(ctx, 0x80);
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitInsn(I2B);
        writeMem(ctx, Size.BYTE);
    }

    public static void TRAPA(BytecodeContext ctx) {
        int imm = (0xFF & ctx.opcode);
        pushSh2Context(ctx);
        pushSR(ctx);
        sh2PushReg15(ctx);
        emitPushConstToStack(ctx, ctx.pc + 2);
        sh2PushReg15(ctx);
        //ctx.PC = memory.read32(ctx.VBR + (imm << 2));
        pushSh2Context(ctx);
        pushMemory(ctx);
        pushSh2ContextIntField(ctx, VBR.name());
        emitPushConstToStack(ctx, imm << 2);
        ctx.mv.visitInsn(IADD);
        readMem(ctx, Size.LONG);
        popSh2ContextIntField(ctx, PC.name());
    }

    public static void TST(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushTwoRegsValsStack(ctx, n, m);
        ctx.mv.visitInsn(IAND);
        cmpInternal(ctx, IFNE);
    }

    public static void TSTI(BytecodeContext ctx) {
        int i = ((ctx.opcode >> 0) & 0xff);
        pushRegValStack(ctx, 0);
        emitPushConstToStack(ctx, i);
        ctx.mv.visitInsn(IAND);
        cmpInternal(ctx, IFNE);

    }

    public static void TSTM(BytecodeContext ctx) {
        int i = ((ctx.opcode >> 0) & 0xff);
        pushMemory(ctx);
        pushSh2ContextIntField(ctx, GBR.name());
        pushRegValStack(ctx, 0);
        ctx.mv.visitInsn(IADD);
        readMem(ctx, Size.BYTE);
        emitPushConstToStack(ctx, i);
        ctx.mv.visitInsn(IAND);
        cmpInternal(ctx, IFNE);
    }

    public static void XOR(BytecodeContext ctx) {
        opRegToReg(ctx, IXOR);
    }

    public static void XORI(BytecodeContext ctx) {
        opReg0Imm(ctx, IXOR, ((ctx.opcode >> 0) & 0xff));
    }

    public static void XORM(BytecodeContext ctx) {
        opReg0Mem(ctx, IXOR);
    }

    public static void XTRCT(BytecodeContext ctx) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);

        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, 0xffff0000);
        ctx.mv.visitInsn(IAND);
        emitPushConstToStack(ctx, 16);
        ctx.mv.visitInsn(IUSHR);
        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, 0xffff);
        ctx.mv.visitInsn(IAND);
        emitPushConstToStack(ctx, 16);
        ctx.mv.visitInsn(ISHL);
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void fallback(BytecodeContext ctx) {
        System.out.println("Fallback block: " + th(ctx.drcCtx.sh2Ctx.PC) + "," + ctx.sh2Inst);
        if (printMissingOpcodes) {
            if (instSet.add(ctx.sh2Inst.name())) {
                LOG.warn("DRC unimplemented: {},{}", ctx.sh2Inst, ctx.opcode);
                System.out.println("DRC unimplemented: " + ctx.sh2Inst + "," + ctx.opcode);
            }
        }
        //if the delaySlot inst is a fallback the PC gets corrupted
        assert !ctx.delaySlot;
        setContextPcFallback(ctx);
        ctx.mv.visitFieldInsn(GETSTATIC, Type.getInternalName(Sh2Instructions.class), "instOpcodeMap",
                Type.getDescriptor(Sh2Instructions.Sh2InstructionWrapper[].class));
        ctx.mv.visitLdcInsn(ctx.opcode);
        ctx.mv.visitInsn(AALOAD);
        ctx.mv.visitFieldInsn(GETFIELD, Type.getInternalName(Sh2Instructions.Sh2InstructionWrapper.class), "runnable",
                Type.getDescriptor(Runnable.class));
        ctx.mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Runnable.class), "run", Ow2Sh2BlockRecompiler.noArgsNoRetDesc);
    }

    public static void shiftConst(BytecodeContext ctx, int shiftBytecode, int shift) {
        assert shift == 2 || shift == 8 || shift == 16;
        int n = RN(ctx.opcode);
        pushRegRefStack(ctx, n);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        switch (shift) {
            case 2 -> ctx.mv.visitInsn(ICONST_2);
            case 8 -> ctx.mv.visitIntInsn(BIPUSH, 8);
            case 16 -> ctx.mv.visitIntInsn(BIPUSH, 16);
        }
        ctx.mv.visitInsn(shiftBytecode);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void opRegToReg(BytecodeContext ctx, int opBytecode) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushRegRefStack(ctx, n);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        pushRegValStack(ctx, m);
        ctx.mv.visitInsn(opBytecode);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void opReg0Imm(BytecodeContext ctx, int opBytecode, int i) {
        opRegImm(ctx, opBytecode, 0, i);
    }

    public static void opRegImm(BytecodeContext ctx, int opBytecode, int reg, int i) {
        pushRegRefStack(ctx, reg);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        emitPushConstToStack(ctx, i);
        ctx.mv.visitInsn(opBytecode);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void opReg0Mem(BytecodeContext ctx, int opBytecode) {
        int i = (byte) ((ctx.opcode >> 0) & 0xff);
        int memValIdx = ctx.mv.newLocal(Type.INT_TYPE);
        int memAddrIdx = ctx.mv.newLocal(Type.INT_TYPE);

        pushSh2ContextIntField(ctx, GBR.name());
        pushRegValStack(ctx, 0);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitVarInsn(ISTORE, memAddrIdx);

        pushMemory(ctx);
        ctx.mv.visitVarInsn(ILOAD, memAddrIdx);
        readMem(ctx, Size.BYTE);
        emitCastIntToSize(ctx, Size.BYTE);
        ctx.mv.visitVarInsn(ISTORE, memValIdx);

        pushMemory(ctx);
        ctx.mv.visitVarInsn(ILOAD, memAddrIdx);
        ctx.mv.visitVarInsn(ILOAD, memValIdx);
        emitPushConstToStack(ctx, i);
        ctx.mv.visitInsn(opBytecode);
        writeMem(ctx, Size.BYTE);
    }

    public static void stsToReg(BytecodeContext ctx, String source) {
        pushRegRefStack(ctx, RN(ctx.opcode));
        pushSh2ContextIntField(ctx, source);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void stsMem(BytecodeContext ctx, String source) {
        int n = RN(ctx.opcode);
        decReg(ctx, n, 4);
        pushMemory(ctx);
        pushRegValStack(ctx, n);
        pushSh2ContextIntField(ctx, source);
        writeMem(ctx, Size.LONG);
    }

    public static void ldcReg(BytecodeContext ctx, String dest, int mask) {
        pushSh2Context(ctx);
        int m = RN(ctx.opcode);
        pushRegValStack(ctx, m);
        if (mask > 0) {
            emitPushConstToStack(ctx, Sh2.SR_MASK);
            ctx.mv.visitInsn(IAND);
        }
        popSh2ContextIntField(ctx, dest);
    }

    public static void ldcmReg(BytecodeContext ctx, String src, int mask) {
        int m = RN(ctx.opcode);
        pushSh2Context(ctx);
        pushMemory(ctx);
        pushRegValStack(ctx, m);
        readMem(ctx, Size.LONG);
        if (mask > 0) {
            emitPushConstToStack(ctx, mask);
            ctx.mv.visitInsn(IAND);
        }
        popSh2ContextIntField(ctx, src);

        pushRegRefStack(ctx, m);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        ctx.mv.visitInsn(ICONST_4);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitInsn(IASTORE);
    }


    /**
     * LEFT
     * ctx.SR &= ~flagT;
     * ctx.SR |= (ctx.registers[n] >>> 31) & flagT;
     * ctx.registers[n] = (ctx.registers[n] << 1) | (ctx.registers[n] >>> 31);
     * <p>
     * RIGHT
     * ctx.SR &= ~flagT;
     * ctx.SR |= ctx.registers[n] & flagT;
     * ctx.registers[n] = (ctx.registers[n] >>> 1) | (ctx.registers[n] << 31);
     */
    public static void rotateReg(BytecodeContext ctx, boolean left) {
        int n = RN(ctx.opcode);

        clearSrFlag(ctx, Sh2.flagT, false);
        pushRegValStack(ctx, n);
        if (left) {
            emitPushConstToStack(ctx, 31);
            ctx.mv.visitInsn(IUSHR);
        }
        emitPushConstToStack(ctx, Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);

        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(left ? ISHL : IUSHR);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, 31);
        ctx.mv.visitInsn(left ? IUSHR : ISHL);
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitInsn(IASTORE);


    }

    /**
     * LEFT
     * int msbit = (ctx.registers[n] >>> 31) & 1;
     * ctx.registers[n] = (ctx.registers[n] << 1) | (ctx.SR & flagT);
     * ctx.SR &= ~flagT;
     * ctx.SR |= msbit;
     * <p>
     * RIGHT
     * int lsbit = ctx.registers[n] & 1;
     * ctx.registers[n] = (ctx.registers[n] >>> 1) | ((ctx.SR & flagT) << 31);
     * ctx.SR &= ~flagT;
     * ctx.SR |= lsbit;
     */
    public static void rotateRegWithCarry(BytecodeContext ctx, boolean left) {
        int n = RN(ctx.opcode);
        int highLowBit = ctx.mv.newLocal(Type.INT_TYPE);
        pushRegValStack(ctx, n);
        if (left) {
            emitPushConstToStack(ctx, 31);
            ctx.mv.visitInsn(IUSHR);
        }
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitVarInsn(ISTORE, highLowBit);

        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(left ? ISHL : IUSHR);
        pushSh2ContextIntField(ctx, SR.name());
        emitPushConstToStack(ctx, Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        if (!left) {
            emitPushConstToStack(ctx, 31);
            ctx.mv.visitInsn(ISHL);
        }
        ctx.mv.visitInsn(IOR);
        ctx.mv.visitInsn(IASTORE);

        // ctx.SR = (ctx.SR & ~flagT) | highLowBit;
        clearSrFlag(ctx, Sh2.flagT, false);
        ctx.mv.visitVarInsn(ILOAD, highLowBit);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);
    }

    /**
     * RIGHT
     * ctx.SR &= (~flagT);
     * ctx.SR |= ctx.registers[n] & 1;
     * ctx.registers[n] = ctx.registers[n] >> 1;
     * <p>
     * LEFT
     * ctx.SR &= (~flagT);
     * ctx.SR |= (ctx.registers[n] >>> 31) & 1;
     * ctx.registers[n] = ctx.registers[n] << 1;
     */
    public static void shiftArithmetic(BytecodeContext ctx, boolean left) {
        int n = RN(ctx.opcode);

        clearSrFlag(ctx, Sh2.flagT, false);
        pushRegValStack(ctx, n);
        if (left) {
            emitPushConstToStack(ctx, 31);
            ctx.mv.visitInsn(IUSHR);
        }
        emitPushConstToStack(ctx, Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);

        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(left ? ISHL : ISHR);
        ctx.mv.visitInsn(IASTORE);

    }

    /**
     * LEFT
     * ctx.SR = (ctx.SR & ~flagT) | ((ctx.registers[n] >>> 31) & 1);
     * ctx.registers[n] <<= 1;
     * RIGHT
     * ctx.SR = (ctx.SR & ~flagT) | (ctx.registers[n] & 1);
     * ctx.registers[n] >>>= 1;
     */
    public static void shiftLogical(BytecodeContext ctx, boolean left) {
        int n = RN(ctx.opcode);

        pushSh2Context(ctx);
        pushSh2ContextIntField(ctx, SR.name());
        emitPushConstToStack(ctx, ~Sh2.flagT);
        ctx.mv.visitInsn(IAND);
        pushRegValStack(ctx, n);
        if (left) {
            emitPushConstToStack(ctx, 31);
            ctx.mv.visitInsn(IUSHR);
        }
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IOR);
        popSR(ctx);

        pushRegRefStack(ctx, n);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        emitPushConstToStack(ctx, 1);
        ctx.mv.visitInsn(left ? ISHL : IUSHR);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void extSigned(BytecodeContext ctx, Size size) {
        assert size != Size.LONG;
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, m);
        emitCastIntToSize(ctx, size);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void extUnsigned(BytecodeContext ctx, Size size) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushRegRefStack(ctx, n);
        pushRegValStack(ctx, m);
        ctx.mv.visitLdcInsn(size.getMask());
        ctx.mv.visitInsn(IAND);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void movMemToReg(BytecodeContext ctx, Size size) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);

        pushRegRefStack(ctx, n);
        pushMemory(ctx);
        pushRegValStack(ctx, m);
        readMem(ctx, size);
        emitCastIntToSize(ctx, size);
        ctx.mv.visitInsn(IASTORE);

    }

    public static void movMemToRegShift(BytecodeContext ctx, Size size) {
        int d = ((ctx.opcode >> 0) & 0x0f);
        int n = size == Size.LONG ? RN(ctx.opcode) : 0;
        int m = RM(ctx.opcode);
        pushRegRefStack(ctx, n);
        pushMemory(ctx);
        pushRegValStack(ctx, m);
        emitPushConstToStack(ctx, d << size.ordinal());
        ctx.mv.visitInsn(IADD);
        readMem(ctx, size);
        emitCastIntToSize(ctx, size);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void movRegToReg(BytecodeContext ctx, Size size) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushMemory(ctx);
        pushTwoRegsValsStack(ctx, n, m);
        writeMem(ctx, size);
    }

    public static void movRegToRegShift(BytecodeContext ctx, Size size) {
        int d = ((ctx.opcode >> 0) & 0x0f);
        int n = size == Size.LONG ? RN(ctx.opcode) : RM(ctx.opcode);
        int m = size == Size.LONG ? RM(ctx.opcode) : 0;
        pushMemory(ctx);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, d << size.ordinal());
        ctx.mv.visitInsn(IADD);
        pushRegValStack(ctx, m);
        writeMem(ctx, size);

    }

    public static void movRegToMemWithReg0Shift(BytecodeContext ctx, Size size) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushMemory(ctx);
        pushTwoRegsValsStack(ctx, n, 0);
        ctx.mv.visitInsn(IADD);
        pushRegValStack(ctx, m);
        emitCastIntToSize(ctx, size);
        writeMem(ctx, size);

    }

    public static void movMemWithReg0ShiftToReg(BytecodeContext ctx, Size size) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);
        pushRegRefStack(ctx, n);
        pushMemory(ctx);
        pushTwoRegsValsStack(ctx, m, 0);
        ctx.mv.visitInsn(IADD);
        readMem(ctx, size);
        emitCastIntToSize(ctx, size);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void movMemWithPcOffsetToReg(BytecodeContext ctx, Size size) {
        assert size != Size.BYTE;
        int d = (ctx.opcode & 0xff);
        int n = ((ctx.opcode >> 8) & 0x0f);
        int pcBase = ctx.pc + 4;
        //If this instruction is placed immediately after a delayed branch instruction, the PC must
        //point to an address specified by (the starting address of the branch destination) + 2.
        if (ctx.delaySlot) {
            assert ctx.branchPc != 0;
            pcBase = ctx.branchPc + 2;
        }
        int memAddr = size == Size.WORD ? pcBase + (d << 1) : (pcBase & 0xfffffffc) + (d << 2);
        pushRegRefStack(ctx, n);
        pushMemory(ctx);
        emitPushConstToStack(ctx, memAddr);
        readMem(ctx, size);
        emitCastIntToSize(ctx, size);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void movRegPredecToMem(BytecodeContext ctx, Size size) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);

        int predecVal = size.getByteSize();
        //write32(r[n] - predecVal, r[m])
        pushMemory(ctx);
        pushRegValStack(ctx, n);
        emitPushConstToStack(ctx, predecVal);
        ctx.mv.visitInsn(ISUB);
        pushRegValStack(ctx, m);
        writeMem(ctx, size);
        //r[n] -= predecVal
        decReg(ctx, n, predecVal);
    }

    public static void movMemToRegPostInc(BytecodeContext ctx, Size size) {
        int m = RM(ctx.opcode);
        int n = RN(ctx.opcode);

        int postIncVal = size.getByteSize();
        pushRegRefStack(ctx, n);
        pushMemory(ctx);
        pushRegValStack(ctx, m);
        readMem(ctx, size);
        emitCastIntToSize(ctx, size);
        ctx.mv.visitInsn(IASTORE);
        if (n != m) {
            pushRegRefStack(ctx, m);
            ctx.mv.visitInsn(DUP2);
            ctx.mv.visitInsn(IALOAD);
            emitPushConstToStack(ctx, postIncVal);
            ctx.mv.visitInsn(IADD);
            ctx.mv.visitInsn(IASTORE);
        }

    }

    public static void movMemWithGBRShiftToReg0(BytecodeContext ctx, Size size) {
        int d = ((ctx.opcode >> 0) & 0xff);
        //ctx.registers[0] = memory.read32(ctx.GBR + (d << n));
        int addVal = d << size.ordinal(); //{0,1,2}
        pushRegRefStack(ctx, 0);
        pushMemory(ctx);
        pushSh2ContextIntField(ctx, GBR.name());
        emitPushConstToStack(ctx, addVal);
        ctx.mv.visitInsn(IADD);
        readMem(ctx, size);
        emitCastIntToSize(ctx, size);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void movGBRShiftToReg0(BytecodeContext ctx, Size size) {
        int v = size.ordinal();
        int gbrOffset = (ctx.opcode & 0xff) << v;
        pushMemory(ctx);
        pushSh2ContextIntField(ctx, GBR.name());
        emitPushConstToStack(ctx, gbrOffset);
        ctx.mv.visitInsn(IADD);
        pushRegValStack(ctx, 0);
        writeMem(ctx, size);

    }

    public static void writeMem(BytecodeContext ctx, Size size) {
        ctx.mv.visitFieldInsn(GETSTATIC, Type.getInternalName(Size.class), size.name(), Type.getDescriptor(Size.class));
        int invoke = Ow2Sh2BlockRecompiler.memoryClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
        ctx.mv.visitMethodInsn(invoke, Type.getInternalName(Sh2BusImpl.class), SH2MEMORY_METHOD.write.name(),
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.getType(Size.class)));
    }

    public static void readMem(BytecodeContext ctx, Size size) {
        ctx.mv.visitFieldInsn(GETSTATIC, Type.getInternalName(Size.class), size.name(), Type.getDescriptor(Size.class));
        int invoke = Ow2Sh2BlockRecompiler.memoryClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
        ctx.mv.visitMethodInsn(invoke, Type.getInternalName(Sh2BusImpl.class), SH2MEMORY_METHOD.read.name(),
                Type.getMethodDescriptor(Type.INT_TYPE, Type.INT_TYPE, Type.getType(Size.class)));
    }

    /**
     * {
     * regs[n] -= val
     * }
     */
    public static void decReg(BytecodeContext ctx, int reg, int val) {
        pushRegRefStack(ctx, reg);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        emitPushConstToStack(ctx, val);
        ctx.mv.visitInsn(ISUB);
        ctx.mv.visitInsn(IASTORE);
    }

    public static void pushTwoRegsValsStack(BytecodeContext ctx, int reg1, int reg2) {
        pushRegValStack(ctx, reg1);
        if (reg2 == reg1) {
            ctx.mv.visitInsn(DUP);
            return;
        }
        pushRegValStack(ctx, reg2);
    }

    public static void pushRegRefStack(BytecodeContext ctx, int reg) {
        ctx.mv.visitVarInsn(ALOAD, 0); //this
        ctx.mv.visitFieldInsn(GETFIELD, ctx.classDesc, regs.name(), Ow2Sh2BlockRecompiler.intArrayDesc);
        emitPushConstToStack(ctx, reg);
    }

    public static void pushRegValStack(BytecodeContext ctx, int reg) {
        pushRegRefStack(ctx, reg);
        ctx.mv.visitInsn(IALOAD);
    }


    public static void storeToReg(BytecodeContext ctx, int reg, int val, Size size) {
        pushRegRefStack(ctx, reg);
        switch (size) {
            case BYTE -> ctx.mv.visitIntInsn(BIPUSH, val);
            case WORD, LONG -> ctx.mv.visitLdcInsn(val);
        }
        ctx.mv.visitInsn(IASTORE);
    }

    //store the long at varIndex to MACH, MACL
    public static void storeToMAC(BytecodeContext ctx, int varIndex) {
        pushSh2Context(ctx);
        ctx.mv.visitVarInsn(LLOAD, varIndex);
        ctx.mv.visitLdcInsn(-1L);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(L2I);
        popSh2ContextIntField(ctx, MACL.name());

        pushSh2Context(ctx);
        ctx.mv.visitVarInsn(LLOAD, varIndex);
        emitPushConstToStack(ctx, 32);
        ctx.mv.visitInsn(LUSHR);
        ctx.mv.visitLdcInsn(-1L);
        ctx.mv.visitInsn(LAND);
        ctx.mv.visitInsn(L2I);
        popSh2ContextIntField(ctx, MACH.name());
    }

    /**
     * Set the context.PC to the current PC
     * NOTE: Only for testing fallback mode.
     */
    private static void setContextPcFallback(BytecodeContext ctx) {
        assert ctx.pc != 0;
        pushSh2Context(ctx);
        ctx.mv.visitLdcInsn(ctx.pc);
        ctx.mv.visitFieldInsn(PUTFIELD, Type.getInternalName(Sh2Context.class), PC.name(), Ow2Sh2BlockRecompiler.intDesc);
    }

    /**
     * {
     * ctx.cycles -= val;
     * }
     */
    public static void subCyclesExt(BytecodeContext ctx, int cycles) {
        if (cycles == 0)
            return;
        pushSh2Context(ctx);
        ctx.mv.visitInsn(DUP);
        ctx.mv.visitFieldInsn(GETFIELD, Type.getInternalName(Sh2Context.class), SH2CTX_CLASS_FIELD.cycles.name(), Ow2Sh2BlockRecompiler.intDesc);
        emitPushConstToStack(ctx, cycles);
        ctx.mv.visitInsn(ISUB);
        popSh2ContextIntField(ctx, SH2CTX_CLASS_FIELD.cycles.name());
    }

    /**
     * {
     * ctx.PC = val;
     * }
     */
    public static void setPcExt(BytecodeContext ctx, int pc) {
        pushSh2Context(ctx);
        ctx.mv.visitLdcInsn(pc);
        ctx.mv.visitFieldInsn(PUTFIELD, Type.getInternalName(Sh2Context.class), PC.name(), Ow2Sh2BlockRecompiler.intDesc);
    }
}