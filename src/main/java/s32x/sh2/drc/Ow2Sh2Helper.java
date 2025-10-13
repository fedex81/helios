package s32x.sh2.drc;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import s32x.sh2.Sh2Context;
import s32x.sh2.prefetch.Sh2Prefetch;

import java.io.PrintStream;
import java.util.Arrays;

import static org.objectweb.asm.Opcodes.*;
import static s32x.sh2.drc.Ow2Sh2Helper.SH2CTX_CLASS_FIELD.SR;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Ow2Sh2Helper {

    private final static Logger LOG = LogHelper.getLogger(Ow2Sh2Helper.class.getSimpleName());

    /**
     * Boundaries between ASM generated code and normal code
     */
    public enum DRC_CLASS_FIELD {regs, opcodes, sh2DrcContext, sh2Context, memory}

    public enum SH2CTX_CLASS_FIELD {PC, PR, SR, GBR, VBR, MACH, MACL, delaySlot, cycles, devices}

    public enum SH2_DRC_CTX_CLASS_FIELD {sh2Ctx, memory}

    public enum SH2_DEVICE_CTX_CLASS_FIELD {sh2MMREG}

    public enum SH2MEMORY_METHOD {read, write}

    //@formatter:off
    public static void createInst(Sh2Prefetch.BytecodeContext ctx) {
//        printString(ctx.mv, ctx.sh2Inst + "," + ctx.opcode);
        switch (ctx.sh2Inst) {
            case ADD -> Ow2Sh2Bytecode.ADD(ctx);
            case ADDC -> Ow2Sh2Bytecode.ADDC(ctx);
            case ADDI -> Ow2Sh2Bytecode.ADDI(ctx);
            case ADDV -> Ow2Sh2Bytecode.ADDV(ctx);
            case AND -> Ow2Sh2Bytecode.AND(ctx);
            case ANDI -> Ow2Sh2Bytecode.ANDI(ctx);
            case ANDM -> Ow2Sh2Bytecode.ANDM(ctx);
            case BF -> Ow2Sh2Bytecode.BF(ctx);
            case BFS -> Ow2Sh2Bytecode.BFS(ctx);
            case BRA -> Ow2Sh2Bytecode.BRA(ctx);
            case BRAF -> Ow2Sh2Bytecode.BRAF(ctx);
            case BSR -> Ow2Sh2Bytecode.BSR(ctx);
            case BSRF -> Ow2Sh2Bytecode.BSRF(ctx);
            case BT -> Ow2Sh2Bytecode.BT(ctx);
            case BTS -> Ow2Sh2Bytecode.BTS(ctx);
            case CLRMAC -> Ow2Sh2Bytecode.CLRMAC(ctx);
            case CLRT -> Ow2Sh2Bytecode.CLRT(ctx);
            case CMPEQ -> Ow2Sh2Bytecode.CMPEQ(ctx);
            case CMPGE -> Ow2Sh2Bytecode.CMPGE(ctx);
            case CMPGT -> Ow2Sh2Bytecode.CMPGT(ctx);
            case CMPHI -> Ow2Sh2Bytecode.CMPHI(ctx);
            case CMPHS -> Ow2Sh2Bytecode.CMPHS(ctx);
            case CMPIM -> Ow2Sh2Bytecode.CMPIM(ctx);
            case CMPPL -> Ow2Sh2Bytecode.CMPPL(ctx);
            case CMPPZ -> Ow2Sh2Bytecode.CMPPZ(ctx);
            case CMPSTR -> Ow2Sh2Bytecode.CMPSTR(ctx);
            case DIV0S -> Ow2Sh2Bytecode.DIV0S(ctx);
            case DIV0U -> Ow2Sh2Bytecode.DIV0U(ctx);
            case DIV1 -> Ow2Sh2Bytecode.DIV1(ctx);
            case DMULS -> Ow2Sh2Bytecode.DMULS(ctx);
            case DMULU -> Ow2Sh2Bytecode.DMULU(ctx);
            case DT -> Ow2Sh2Bytecode.DT(ctx);
            case EXTSB -> Ow2Sh2Bytecode.EXTSB(ctx);
            case EXTSW -> Ow2Sh2Bytecode.EXTSW(ctx);
            case EXTUB -> Ow2Sh2Bytecode.EXTUB(ctx);
            case EXTUW -> Ow2Sh2Bytecode.EXTUW(ctx);
            case ILLEGAL -> Ow2Sh2Bytecode.ILLEGAL(ctx);
            case JMP -> Ow2Sh2Bytecode.JMP(ctx);
            case JSR -> Ow2Sh2Bytecode.JSR(ctx);
            case LDCGBR -> Ow2Sh2Bytecode.LDCGBR(ctx);
            case LDCMGBR -> Ow2Sh2Bytecode.LDCMGBR(ctx);
            case LDCMSR -> Ow2Sh2Bytecode.LDCMSR(ctx);
            case LDCMVBR -> Ow2Sh2Bytecode.LDCMVBR(ctx);
            case LDCSR -> Ow2Sh2Bytecode.LDCSR(ctx);
            case LDCVBR -> Ow2Sh2Bytecode.LDCVBR(ctx);
            case LDSMACH -> Ow2Sh2Bytecode.LDSMACH(ctx);
            case LDSMACL -> Ow2Sh2Bytecode.LDSMACL(ctx);
            case LDSMMACH -> Ow2Sh2Bytecode.LDSMMACH(ctx);
            case LDSMMACL -> Ow2Sh2Bytecode.LDSMMACL(ctx);
            case LDSMPR -> Ow2Sh2Bytecode.LDSMPR(ctx);
            case LDSPR -> Ow2Sh2Bytecode.LDSPR(ctx);
            case MACL -> Ow2Sh2Bytecode.MACL(ctx);
            case MACW -> Ow2Sh2Bytecode.MACW(ctx);
            case MOV -> Ow2Sh2Bytecode.MOV(ctx);
            case MOVA -> Ow2Sh2Bytecode.MOVA(ctx);
            case MOVBL -> Ow2Sh2Bytecode.MOVBL(ctx);
            case MOVBL0 -> Ow2Sh2Bytecode.MOVBL0(ctx);
            case MOVBL4 -> Ow2Sh2Bytecode.MOVBL4(ctx);
            case MOVBLG -> Ow2Sh2Bytecode.MOVBLG(ctx);
            case MOVBM -> Ow2Sh2Bytecode.MOVBM(ctx);
            case MOVBP -> Ow2Sh2Bytecode.MOVBP(ctx);
            case MOVBS -> Ow2Sh2Bytecode.MOVBS(ctx);
            case MOVBS0 -> Ow2Sh2Bytecode.MOVBS0(ctx);
            case MOVBS4 -> Ow2Sh2Bytecode.MOVBS4(ctx);
            case MOVBSG -> Ow2Sh2Bytecode.MOVBSG(ctx);
            case MOVI -> Ow2Sh2Bytecode.MOVI(ctx);
            case MOVLI -> Ow2Sh2Bytecode.MOVLI(ctx);
            case MOVLL -> Ow2Sh2Bytecode.MOVLL(ctx);
            case MOVLL0 -> Ow2Sh2Bytecode.MOVLL0(ctx);
            case MOVLL4 -> Ow2Sh2Bytecode.MOVLL4(ctx);
            case MOVLLG -> Ow2Sh2Bytecode.MOVLLG(ctx);
            case MOVLM -> Ow2Sh2Bytecode.MOVLM(ctx);
            case MOVLP -> Ow2Sh2Bytecode.MOVLP(ctx);
            case MOVLS -> Ow2Sh2Bytecode.MOVLS(ctx);
            case MOVLS0 -> Ow2Sh2Bytecode.MOVLS0(ctx);
            case MOVLS4 -> Ow2Sh2Bytecode.MOVLS4(ctx);
            case MOVLSG -> Ow2Sh2Bytecode.MOVLSG(ctx);
            case MOVT -> Ow2Sh2Bytecode.MOVT(ctx);
            case MOVWI -> Ow2Sh2Bytecode.MOVWI(ctx);
            case MOVWL -> Ow2Sh2Bytecode.MOVWL(ctx);
            case MOVWL0 -> Ow2Sh2Bytecode.MOVWL0(ctx);
            case MOVWL4 -> Ow2Sh2Bytecode.MOVWL4(ctx);
            case MOVWLG -> Ow2Sh2Bytecode.MOVWLG(ctx);
            case MOVWM -> Ow2Sh2Bytecode.MOVWM(ctx);
            case MOVWP -> Ow2Sh2Bytecode.MOVWP(ctx);
            case MOVWS -> Ow2Sh2Bytecode.MOVWS(ctx);
            case MOVWS0 -> Ow2Sh2Bytecode.MOVWS0(ctx);
            case MOVWS4 -> Ow2Sh2Bytecode.MOVWS4(ctx);
            case MOVWSG -> Ow2Sh2Bytecode.MOVWSG(ctx);
            case MULL -> Ow2Sh2Bytecode.MULL(ctx);
            case MULSU -> Ow2Sh2Bytecode.MULSU(ctx);
            case MULSW -> Ow2Sh2Bytecode.MULSW(ctx);
            case NEG -> Ow2Sh2Bytecode.NEG(ctx);
            case NEGC -> Ow2Sh2Bytecode.NEGC(ctx);
            case NOP -> Ow2Sh2Bytecode.NOP(ctx);
            case NOT -> Ow2Sh2Bytecode.NOT(ctx);
            case OR -> Ow2Sh2Bytecode.OR(ctx);
            case ORI -> Ow2Sh2Bytecode.ORI(ctx);
            case ORM -> Ow2Sh2Bytecode.ORM(ctx);
            case ROTCL -> Ow2Sh2Bytecode.ROTCL(ctx);
            case ROTCR -> Ow2Sh2Bytecode.ROTCR(ctx);
            case ROTL -> Ow2Sh2Bytecode.ROTL(ctx);
            case ROTR -> Ow2Sh2Bytecode.ROTR(ctx);
            case RTE -> Ow2Sh2Bytecode.RTE(ctx);
            case RTS -> Ow2Sh2Bytecode.RTS(ctx);
            case SETT -> Ow2Sh2Bytecode.SETT(ctx);
            case SHAL -> Ow2Sh2Bytecode.SHAL(ctx);
            case SHAR -> Ow2Sh2Bytecode.SHAR(ctx);
            case SHLL -> Ow2Sh2Bytecode.SHLL(ctx);
            case SHLL16 -> Ow2Sh2Bytecode.SHLL16(ctx);
            case SHLL2 -> Ow2Sh2Bytecode.SHLL2(ctx);
            case SHLL8 -> Ow2Sh2Bytecode.SHLL8(ctx);
            case SHLR -> Ow2Sh2Bytecode.SHLR(ctx);
            case SHLR16 -> Ow2Sh2Bytecode.SHLR16(ctx);
            case SHLR2 -> Ow2Sh2Bytecode.SHLR2(ctx);
            case SHLR8 -> Ow2Sh2Bytecode.SHLR8(ctx);
            case SLEEP -> Ow2Sh2Bytecode.SLEEP(ctx);
            case STCGBR -> Ow2Sh2Bytecode.STCGBR(ctx);
            case STCMGBR -> Ow2Sh2Bytecode.STCMGBR(ctx);
            case STCMSR -> Ow2Sh2Bytecode.STCMSR(ctx);
            case STCMVBR -> Ow2Sh2Bytecode.STCMVBR(ctx);
            case STCSR -> Ow2Sh2Bytecode.STCSR(ctx);
            case STCVBR -> Ow2Sh2Bytecode.STCVBR(ctx);
            case STSMACH -> Ow2Sh2Bytecode.STSMACH(ctx);
            case STSMACL -> Ow2Sh2Bytecode.STSMACL(ctx);
            case STSMMACH -> Ow2Sh2Bytecode.STSMMACH(ctx);
            case STSMMACL -> Ow2Sh2Bytecode.STSMMACL(ctx);
            case STSMPR -> Ow2Sh2Bytecode.STSMPR(ctx);
            case STSPR -> Ow2Sh2Bytecode.STSPR(ctx);
            case SUB -> Ow2Sh2Bytecode.SUB(ctx);
            case SUBC -> Ow2Sh2Bytecode.SUBC(ctx);
            case SUBV -> Ow2Sh2Bytecode.SUBV(ctx);
            case SWAPB -> Ow2Sh2Bytecode.SWAPB(ctx);
            case SWAPW -> Ow2Sh2Bytecode.SWAPW(ctx);
            case TAS -> Ow2Sh2Bytecode.TAS(ctx);
            case TRAPA -> Ow2Sh2Bytecode.TRAPA(ctx);
            case TST -> Ow2Sh2Bytecode.TST(ctx);
            case TSTI -> Ow2Sh2Bytecode.TSTI(ctx);
            case TSTM -> Ow2Sh2Bytecode.TSTM(ctx);
            case XOR -> Ow2Sh2Bytecode.XOR(ctx);
            case XORI -> Ow2Sh2Bytecode.XORI(ctx);
            case XORM -> Ow2Sh2Bytecode.XORM(ctx);
            case XTRCT -> Ow2Sh2Bytecode.XTRCT(ctx);
            default -> {
                LOG.warn("Fallback: {}", ctx.sh2Inst);
                System.out.println("Fallback: " + ctx.sh2Inst);
                Ow2Sh2Bytecode.fallback(ctx);
            }
        }
    }
    //@formatter:on

    public static void printString(Sh2Prefetch.BytecodeContext ctx, String str) {
        ctx.mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
        ctx.mv.visitLdcInsn(str);
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)));
    }

    public static void printRegValue(Sh2Prefetch.BytecodeContext ctx, int reg) {
        ctx.mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
        Ow2Sh2Bytecode.pushRegRefStack(ctx, reg);
        ctx.mv.visitInsn(IALOAD);
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(int.class)));
    }

    public static void printSh2ContextField(Sh2Prefetch.BytecodeContext ctx, String name, Class<?> clazz) {
        ctx.mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
        pushSh2Context(ctx);
        ctx.mv.visitFieldInsn(GETFIELD, Type.getInternalName(Sh2Context.class), name, Type.getDescriptor(clazz));
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(clazz)));
    }

    public static void popSh2ContextIntField(Sh2Prefetch.BytecodeContext ctx, String name) {
        ctx.mv.visitFieldInsn(PUTFIELD, Type.getInternalName(Sh2Context.class), name, Ow2Sh2BlockRecompiler.intDesc);
    }

    /**
     * Pop the value top of the stack and stores it in Sh2Context::SR
     * A reference to Sh2Context should already be on the stack.
     */
    public static void popSR(Sh2Prefetch.BytecodeContext ctx) {
        ctx.mv.visitFieldInsn(PUTFIELD, Type.getInternalName(Sh2Context.class), SR.name(), Ow2Sh2BlockRecompiler.intDesc);
    }

    /**
     * Push Sh2Context::SR on the stack, a reference to Sh2Context should already be on the stack.
     */
    public static void pushSR(Sh2Prefetch.BytecodeContext ctx) {
        pushField(ctx, Sh2Context.class, SR.name(), int.class);
    }

    public static void pushField(Sh2Prefetch.BytecodeContext ctx, Class<?> refClass, String fieldName, Class<?> fieldClass) {
        ctx.mv.visitFieldInsn(GETFIELD, Type.getInternalName(refClass), fieldName, Type.getDescriptor(fieldClass));
    }

    public static void pushSh2Context(Sh2Prefetch.BytecodeContext ctx) {
        ctx.mv.visitVarInsn(ALOAD, 0); //this
        ctx.mv.visitFieldInsn(GETFIELD, ctx.classDesc, DRC_CLASS_FIELD.sh2Context.name(), Type.getDescriptor(Sh2Context.class));
    }

    public static void pushMemory(Sh2Prefetch.BytecodeContext ctx) {
        ctx.mv.visitVarInsn(ALOAD, 0); //this
        ctx.mv.visitFieldInsn(GETFIELD, ctx.classDesc, DRC_CLASS_FIELD.memory.name(),
                Type.getDescriptor(Sh2BlockRecompiler.memoryClass));
    }

    public static void pushSh2ContextIntField(Sh2Prefetch.BytecodeContext ctx, String name) {
        pushSh2Context(ctx);
        pushField(ctx, Sh2Context.class, name, int.class);
    }

    public static void sh2PushReg15(Sh2Prefetch.BytecodeContext ctx) {
        //NOTE the value to push must be on the top of the stack
        int valIdx = ctx.mv.newLocal(Type.INT_TYPE);
        ctx.mv.visitVarInsn(ISTORE, valIdx);
        Ow2Sh2Bytecode.decReg(ctx, 15, 4);
        pushMemory(ctx);
        Ow2Sh2Bytecode.pushRegValStack(ctx, 15);
        ctx.mv.visitVarInsn(ILOAD, valIdx);
        Ow2Sh2Bytecode.writeMem(ctx, Size.LONG);
    }

    /**
     * NOTE the result of pop will be on the top of the stack
     */
    public static void sh2PopReg15(Sh2Prefetch.BytecodeContext ctx) {
        int resIdx = ctx.mv.newLocal(Type.INT_TYPE);
        pushMemory(ctx);
        Ow2Sh2Bytecode.pushRegValStack(ctx, 15);
        Ow2Sh2Bytecode.readMem(ctx, Size.LONG);
        ctx.mv.visitVarInsn(ISTORE, resIdx);
        Ow2Sh2Bytecode.pushRegRefStack(ctx, 15);
        ctx.mv.visitInsn(DUP2);
        ctx.mv.visitInsn(IALOAD);
        ctx.mv.visitInsn(ICONST_4);
        ctx.mv.visitInsn(IADD);
        ctx.mv.visitInsn(IASTORE);
        ctx.mv.visitVarInsn(ILOAD, resIdx);
    }


    private static void printArrayField(Sh2Prefetch.BytecodeContext ctx, String name, String fieldDesc) {
        if (!Ow2Sh2Bytecode.addPrintStuff) {
            return;
        }
        ctx.mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
        ctx.mv.visitVarInsn(ALOAD, 0); // push `this`
        ctx.mv.visitFieldInsn(GETFIELD, ctx.classDesc, name, fieldDesc);
        ctx.mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Arrays.class), "toString",
                Type.getMethodDescriptor(Type.getType(String.class), Type.getType(int[].class)));
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)));
    }

    private static void emitPrintField(Sh2Prefetch.BytecodeContext ctx, String name, Class<?> clazz) {
        if (!Ow2Sh2Bytecode.addPrintStuff) {
            return;
        }
        ctx.mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
        ctx.mv.visitVarInsn(ALOAD, 0); // push `this`
        ctx.mv.visitFieldInsn(GETFIELD, ctx.classDesc, name, Type.getDescriptor(clazz));
        ctx.mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(clazz)));
    }

    public static void emitPushLongConstToStack(Sh2Prefetch.BytecodeContext ctx, long val) {
        if (val == 0 || val == 1) {
            ctx.mv.visitInsn((int) (LCONST_0 + val));
        } else {
            ctx.mv.visitLdcInsn(val);
        }
    }

    public static void emitPushConstToStack(Sh2Prefetch.BytecodeContext ctx, int val) {
        if (val >= 0 && val <= 5) {
            ctx.mv.visitInsn(ICONST_0 + val);
        } else if (val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE) {
            ctx.mv.visitIntInsn(BIPUSH, val);
        } else if (val >= Short.MIN_VALUE && val <= Short.MAX_VALUE) {
            ctx.mv.visitIntInsn(SIPUSH, val);
        } else {
            ctx.mv.visitLdcInsn(val);
        }
    }

    /**
     * {
     * int d = ...
     * byte b = (byte)d;
     * or
     * short s = (short)d
     * }
     */
    public static void emitCastIntToSize(Sh2Prefetch.BytecodeContext ctx, Size size) {
        switch (size) {
            case BYTE -> ctx.mv.visitInsn(I2B);
            case WORD -> ctx.mv.visitInsn(I2S);
        }
    }
}
