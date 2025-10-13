package s32x.sh2.drc;

import omegadrive.util.LogHelper;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.slf4j.Logger;
import s32x.bus.Sh2Bus;
import s32x.sh2.Sh2Context;
import s32x.sh2.prefetch.Sh2Prefetch.BytecodeContext;
import s32x.sh2.prefetch.Sh2Prefetch.Sh2DrcContext;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;

import static org.objectweb.asm.Opcodes.*;
import static s32x.sh2.drc.Ow2Sh2Helper.DRC_CLASS_FIELD.*;
import static s32x.sh2.drc.Ow2Sh2Helper.SH2_DRC_CTX_CLASS_FIELD.sh2Ctx;

/**
 * Ow2Sh2BlockRecompiler
 * <p>
 * uses objectWeb ASM to create Sh2 blocks as Java Classes.
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Ow2Sh2BlockRecompiler implements Sh2BlockRecompiler.InternalSh2BlockRecompiler {

    private final static Logger LOG = LogHelper.getLogger(Ow2Sh2BlockRecompiler.class.getSimpleName());
    public static final String intArrayDesc = Type.getDescriptor(int[].class);
    public static final String intDesc = Type.getDescriptor(int.class);
    public static final String noArgsNoRetDesc = "()V";
    public static final String classConstructor = "<init>";
    public static final String runMethodName = "run";

    @Override
    public byte[] createClassBinary(Sh2Block block, Sh2DrcContext drcCtx, String blockClass, Class<?> memoryClass) {
        String blockClassDesc = blockClass.replace('.', '/');
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V11, ACC_PUBLIC | ACC_FINAL, blockClassDesc, null, Type.getInternalName(Object.class),
                new String[]{Type.getInternalName(Runnable.class)});
        {
            //fields
            cw.visitField(ACC_PRIVATE | ACC_FINAL, regs.name(), intArrayDesc, null, null).visitEnd();
            cw.visitField(ACC_PRIVATE | ACC_FINAL, opcodes.name(), intArrayDesc, null, null).visitEnd();
            cw.visitField(ACC_PRIVATE | ACC_FINAL, sh2DrcContext.name(), Type.getDescriptor(Sh2DrcContext.class), null, null).visitEnd();
            cw.visitField(ACC_PRIVATE | ACC_FINAL, sh2Context.name(), Type.getDescriptor(Sh2Context.class), null, null).visitEnd();
            cw.visitField(ACC_PRIVATE | ACC_FINAL, memory.name(), Type.getDescriptor(memoryClass), null, null).visitEnd();
        }
        {

            // constructor
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, classConstructor,
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(int[].class), Type.getType(int[].class),
                            Type.getType(Sh2DrcContext.class)), null, null);
            mv.visitVarInsn(ALOAD, 0); // push `this` to the operand stack
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), classConstructor, noArgsNoRetDesc, false);// call the constructor of super class
            {
                //set fields
                setClassField(mv, blockClassDesc, 1, regs, intArrayDesc);
                setClassField(mv, blockClassDesc, 2, opcodes, intArrayDesc);
                setClassField(mv, blockClassDesc, 3, sh2DrcContext, Type.getDescriptor(Sh2DrcContext.class));

                //set sh2Context
                mv.visitVarInsn(ALOAD, 0); // push `this`
                mv.visitVarInsn(ALOAD, 3); // push `sh2DrcContext`
                mv.visitFieldInsn(GETFIELD, Type.getInternalName(Sh2DrcContext.class), sh2Ctx.name(), Type.getDescriptor(Sh2Context.class));
                mv.visitFieldInsn(PUTFIELD, blockClassDesc, sh2Context.name(), Type.getDescriptor(Sh2Context.class));

                //set memory
                mv.visitVarInsn(ALOAD, 0); // push `this`
                mv.visitVarInsn(ALOAD, 3); // push `sh2DrcContext`
                mv.visitFieldInsn(GETFIELD, Type.getInternalName(Sh2DrcContext.class),
                        Ow2Sh2Helper.SH2_DRC_CTX_CLASS_FIELD.memory.name(),
                        Type.getDescriptor(Sh2Bus.class));
                if (memoryClass != Sh2Bus.class) {
                    mv.visitTypeInsn(CHECKCAST, Type.getInternalName(memoryClass));
                }
                mv.visitFieldInsn(PUTFIELD, blockClassDesc, memory.name(), Type.getDescriptor(memoryClass));
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_FINAL, runMethodName, noArgsNoRetDesc, null, null);
            LocalVariablesSorter lvs = new LocalVariablesSorter(ACC_PUBLIC | ACC_FINAL, noArgsNoRetDesc, mv);
            int limit = block.prefetchWords.length;
            BytecodeContext ctx = new BytecodeContext();
            BytecodeContext dsCtx = new BytecodeContext();
            ctx.classDesc = dsCtx.classDesc = blockClassDesc;
            ctx.drcCtx = dsCtx.drcCtx = drcCtx;
            ctx.mv = dsCtx.mv = lvs;
            int totCycles = 0;
            for (int i = 0; i < block.prefetchLenWords; i++) {
                Sh2BlockRecompiler.setDrcContext(ctx, block.inst[i], false);
                if (ctx.sh2Inst.isBranchDelaySlot()) {
                    Sh2BlockRecompiler.setDrcContext(dsCtx, block.inst[i + 1], true);
                    ctx.delaySlotCtx = dsCtx;
                    //LOG.info("Block at PC {}, setting delaySlot ctx\nctx {}\nds  {}", th(block.prefetchPc), ctx, dsCtx);
                }
                Ow2Sh2Helper.createInst(ctx);
                //branch inst cycles taken are not known at this point
                if (!ctx.sh2Inst.isBranch()) {
                    totCycles += ctx.sh2Inst.cycles;
                }
                //delay slot will be run within
                if (ctx.sh2Inst.isBranchDelaySlot()) {
                    break;
                }
            }
            if (block.isNoJump()) {
                Ow2Sh2Bytecode.setPcExt(ctx, block.inst[limit - 1].pc + 2);
            }
            Ow2Sh2Bytecode.subCyclesExt(ctx, totCycles);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void setClassField(MethodVisitor mv, String classDesc, int varIndex, Ow2Sh2Helper.DRC_CLASS_FIELD field, String typeDesc) {
        mv.visitVarInsn(ALOAD, 0); // push `this`
        mv.visitVarInsn(ALOAD, varIndex); // push field
        mv.visitFieldInsn(PUTFIELD, classDesc, field.name(), typeDesc);
    }

    @Override
    public void printSource(Path file, byte[] code) {
        ClassReader reader;
        try (
                FileWriter fileWriter = new FileWriter(file.toFile());
                PrintWriter pw = new PrintWriter(fileWriter);
        ) {
            reader = new ClassReader(code);
            ClassVisitor visitor = new TraceClassVisitor(null, new Textifier(), pw);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) {
            LOG.error(file.toString(), e);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        String p = "./res/drc_1652793435182/sh2.sh2.drc.S_6000390_19768421105194.class";
        ASMifier.main(new String[]{p});
    }
}