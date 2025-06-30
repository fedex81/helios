package s32x.sh2.drc;

import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;
import s32x.bus.Sh2Bus;
import s32x.bus.Sh2BusImpl;
import s32x.sh2.prefetch.Sh2Prefetch.BytecodeContext;
import s32x.sh2.prefetch.Sh2Prefetch.Sh2DrcContext;
import s32x.sh2.prefetch.Sh2Prefetcher.Sh2BlockUnit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Sh2BlockRecompiler {

    private final static Logger LOG = LogHelper.getLogger(Sh2BlockRecompiler.class.getSimpleName());
    private static final Path drcFolder = Paths.get("./res/drc_" + System.currentTimeMillis());
    private final static boolean writeClass = false;

    private final OwnClassLoader cl = new OwnClassLoader();

    public static final String drcPackage = Sh2BlockRecompiler.class.getPackageName();

    //detect that memory is Sh2BusImpl vs Sh2Bus and use a Sh2BusImpl field for the class
    //should be faster
    public static Class<?> memoryClass;

    private static Sh2BlockRecompiler current = null;
    private String token;
    private InternalSh2BlockRecompiler recompiler;

    interface InternalSh2BlockRecompiler {
        byte[] createClassBinary(Sh2Block block, Sh2DrcContext drcCtx, String blockClass, Class<?> memoryClass);

        void printSource(Path file, byte[] code);
    }

    public static class OwnClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    /**
     * There is no easy way of releasing/removing a classLoader,
     * GC should take care of it.
     */
    public static Sh2BlockRecompiler newInstance(String token) {
        boolean firstOne = current == null;
        boolean newOne = firstOne || Objects.equals(current.token, token);
        if (newOne) {
            Sh2BlockRecompiler newrec = new Sh2BlockRecompiler();
            newrec.token = token;
            current = newrec;
            LOG.info("New recompiler: {}, with token: {}", newrec.recompiler.getClass().getName(), token);
        }
        return current;
    }

    private Sh2BlockRecompiler() {
        recompiler = new Ow2Sh2BlockRecompiler();
    }

    public static Sh2BlockRecompiler getInstance() {
        assert current != null;
        return current;
    }

    public Runnable createDrcClass(Sh2Block block, Sh2DrcContext drcCtx) {
        String blockClass = drcPackage + "." + drcCtx.sh2Ctx.sh2ShortCode + "_" + th(block.prefetchPc)
                + "_" + th(block.hashCodeWords) + "_" + System.nanoTime();
        memoryClass = drcCtx.memory instanceof Sh2BusImpl ? Sh2BusImpl.class : Sh2Bus.class;
        Runnable r;
        try {
            byte[] binc = recompiler.createClassBinary(block, drcCtx, blockClass, memoryClass);
            writeClassMaybe(blockClass, binc);
            Class<?> clazz = cl.defineClass(blockClass, binc);
            Object b = clazz.getDeclaredConstructor(int[].class, int[].class, Sh2DrcContext.class).
                    newInstance(drcCtx.sh2Ctx.registers, block.prefetchWords, drcCtx);
            assert b instanceof Runnable;
            r = (Runnable) b;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Fatal! ," + blockClass);
        }
        return r;
    }

    public static void setDrcContext(BytecodeContext ctx, Sh2BlockUnit sbu, boolean delaySlot) {
        ctx.opcode = sbu.opcode;
        ctx.pc = sbu.pc;
        ctx.sh2Inst = sbu.inst;
        ctx.delaySlot = delaySlot;
        ctx.delaySlotCtx = null;
    }

    private void writeClassMaybe(String blockClass, byte[] binc) {
        if (!writeClass) {
            return;
        }
        boolean res = drcFolder.toFile().mkdirs();
        if (!res && Files.notExists(drcFolder)) {
            LOG.error("Unable to log files to: {}", drcFolder.toFile());
            return;
        }
        Path p = Paths.get(drcFolder.toAbsolutePath().toString(), (blockClass + ".class"));
        Path bc = Paths.get(drcFolder.toAbsolutePath().toString(), (blockClass + ".bytecode"));
        Util.executorService.submit(() -> {
            recompiler.printSource(bc, binc);
            LOG.info("Bytecode Class written: {}", bc.toAbsolutePath());
            FileUtil.writeFileSafe(p, binc);
            LOG.info("Drc Class written: {}", p.toAbsolutePath());
        });
        LOG.info("{} job submitted", blockClass);
    }
}