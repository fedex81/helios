package s32x.sh2;

import omegadrive.cpu.CpuFastDebug;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import s32x.dict.S32xDict;
import s32x.sh2.drc.Sh2Block;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static omegadrive.util.Util.th;
import static s32x.sh2.Sh2Debug.createContext;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class Sh2Helper {

    private static final Logger LOG = LogHelper.getLogger(Sh2Helper.class.getSimpleName());
    public static final Sh2Disassembler disasm = new Sh2Disassembler();
    private static final String simpleFormat = "%s %08x\t%04x\t%s";

    private static final Sh2PcInfoWrapper[] EMPTY_WRAPPER = new Sh2PcInfoWrapper[0];
    public static final Sh2PcInfoWrapper SH2_NOT_VISITED = new Sh2PcInfoWrapper(0, 0);
    private static Sh2PcInfoWrapper[][] piwArr = createPcInfoWrapper();


    /**
     * Even indexes -> MASTER pc
     * Odd indexes  -> SLAVE pc, actual PC is pc & ~1
     */
    public final static class Sh2PcInfoWrapper extends CpuFastDebug.PcInfoWrapper {

        public Sh2Block block = Sh2Block.INVALID_BLOCK;
        public Map<Integer, Sh2Block> knownBlocks = Collections.emptyMap();
        private static final boolean verbose = false;

        //reduce boxing by using a 16-bit hash (key)
        public static final int HASH_CODE_MASK = 0xFFFF;

        public Sh2PcInfoWrapper(int area, int pcMasked) {
            super(area, pcMasked);
        }

        public void setBlock(Sh2Block block) {
            assert this != SH2_NOT_VISITED;
            this.block = block;
            if (verbose && block == Sh2Block.INVALID_BLOCK) {
                LOG.info("set invalid block: {} {}", th(area), th(pcMasked));
            }
        }

        public void invalidateBlock() {
            if (verbose) LOG.info("Invalidate pc area: {}, pcMasked: {}", th(area), th(pcMasked));
            if (block != Sh2Block.INVALID_BLOCK) {
                if (verbose) LOG.info("{} Block pc: {}, {}", block.drcContext.cpu, th(block.prefetchPc), block);
                block.invalidate();
                block = Sh2Block.INVALID_BLOCK;
            }
        }

        public Sh2Block addToKnownBlocks(Sh2Block b) {
            assert this != SH2_NOT_VISITED;
            if (knownBlocks == Collections.EMPTY_MAP) {
                knownBlocks = new HashMap<>(1);
            }
            Sh2Block prev = knownBlocks.putIfAbsent(b.hashCodeWords & HASH_CODE_MASK, b);
            //check for hash collisions
            assert prev != null ? prev == b : true;
            return prev;
        }
    }


    public static void clear() {
        piwArr = createWrapper(createContext());
    }

    /**
     * Even indexes -> MASTER pc
     * Odd indexes  -> SLAVE pc, actual PC is pc & ~1
     */
    private static Sh2PcInfoWrapper[][] createPcInfoWrapper() {
        if (piwArr == null) {
            piwArr = createWrapper(createContext());
        }
        return piwArr;
    }

    public static Sh2PcInfoWrapper[][] getPcInfoWrapper() {
        assert piwArr != null;
        return piwArr;
    }

    private static Sh2PcInfoWrapper[][] createWrapper(CpuFastDebug.CpuDebugContext ctx) {
        Sh2PcInfoWrapper[][] pcInfoWrapper = new Sh2PcInfoWrapper[ctx.pcAreasNumber][0];
        assert EMPTY_WRAPPER != null;
        Arrays.fill(pcInfoWrapper, EMPTY_WRAPPER);

        for (int i = 0; i < ctx.pcAreasMaskMap.length; ++i) {
            int pcAreaSize = ctx.pcAreasMaskMap[i] + 1;
            if (pcAreaSize > 1) {
                pcInfoWrapper[i] = new Sh2PcInfoWrapper[pcAreaSize];
                Arrays.fill(pcInfoWrapper[i], SH2_NOT_VISITED);
            }
        }
        return pcInfoWrapper;
    }

    public static boolean isValidPc(int pc, CpuDeviceAccess cpu) {
        assert (pc & 1) == 0 : th(pc);
        final int piwPc = pc | cpu.ordinal();
        return piwArr[piwPc >>> S32xDict.SH2_PC_AREA_SHIFT].length > 0;
    }

    public static Sh2PcInfoWrapper getOrDefault(int pc, CpuDeviceAccess cpu) {
        assert (pc & 1) == 0 : th(pc);
        final int piwPc = pc | cpu.ordinal();
        final Sh2PcInfoWrapper[] piwSubArr = piwArr[piwPc >>> S32xDict.SH2_PC_AREA_SHIFT];
        if (piwSubArr.length == 0) {
            return SH2_NOT_VISITED;
        }
        //TODO cache-through vs cached
        Sh2PcInfoWrapper piw = piwSubArr[piwPc & Sh2Debug.pcAreaMaskMap[piwPc >>> S32xDict.SH2_PC_AREA_SHIFT]];
        assert (piw != SH2_NOT_VISITED
                ? piw.pcMasked == (pc & Sh2Debug.pcAreaMaskMap[pc >>> S32xDict.SH2_PC_AREA_SHIFT]) : true) : th(piwPc) + "," + th(piw.pcMasked);
        return piw;
    }

    /**
     * area = pc >>> SH2_PC_AREA_SHIFT;
     * pcMasked = pc & pcAreaMaskMap[area]
     */
    public static Sh2PcInfoWrapper get(int pc, CpuDeviceAccess cpu) {
        assert (pc & 1) == 0 : th(pc);
        final int piwPc = pc | cpu.ordinal();
        //TODO cache-through vs cached
        Sh2PcInfoWrapper piw = piwArr[piwPc >>> S32xDict.SH2_PC_AREA_SHIFT][piwPc & Sh2Debug.pcAreaMaskMap[piwPc >>> S32xDict.SH2_PC_AREA_SHIFT]];
        assert (piw != SH2_NOT_VISITED
                ? piw.pcMasked == (pc & Sh2Debug.pcAreaMaskMap[pc >>> S32xDict.SH2_PC_AREA_SHIFT]) : true) : th(piwPc) + "," + th(piw.pcMasked);
        return piw;
    }

    /**
     * area = pc >>> SH2_PC_AREA_SHIFT;
     * pcMasked = pc & pcAreaMaskMap[area]
     */
    public static Sh2PcInfoWrapper getOrCreate(int pc, CpuDeviceAccess cpu) {
        Sh2PcInfoWrapper piw = get(pc, cpu);
        assert piw != null;
        if (piw == SH2_NOT_VISITED) {
            final int piwPc = pc | cpu.ordinal();
            piw = new Sh2PcInfoWrapper(pc >>> S32xDict.SH2_PC_AREA_SHIFT, pc & Sh2Debug.pcAreaMaskMap[pc >>> S32xDict.SH2_PC_AREA_SHIFT]);
            piwArr[piw.area][piw.pcMasked | cpu.ordinal()] = piw;
        }
        assert piw.pcMasked == (pc & Sh2Debug.pcAreaMaskMap[pc >>> S32xDict.SH2_PC_AREA_SHIFT]);
        return piw;
    }

    public static void printInst(Sh2Context ctx) {
        System.out.println(getInstString(ctx));
    }

    public static String getInstString(Sh2Context ctx) {
        assert ctx.opcode > 0;
        return String.format(simpleFormat, ctx.sh2TypeCode, ctx.PC, ctx.opcode, disasm.disassemble(ctx.PC, ctx.opcode));
    }

    public static String getInstString(int pc, int opcode) {
        return disasm.disassemble(pc, opcode);
    }

    public static String getInstString(String sh2Type, int pc, int opcode) {
        return String.format(simpleFormat, sh2Type, pc, opcode, disasm.disassemble(pc, opcode));
    }

    public static void printState(Sh2Context ctx) {
        System.out.println(toDebuggingString(ctx));
    }

    public static StringBuilder toListOfInst(int pc, int... opcodes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < opcodes.length; i++) {
            sb.append(Sh2Helper.getInstString("", pc + (i << 1), opcodes[i])).append("\n");
        }
        return sb;
    }

    public static StringBuilder toListOfInst(Sh2Block ctx) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.prefetchWords.length; i++) {
            int pc = ctx.start + (i << 1);
            pc = pc != ctx.pcMasked ? pc : ctx.prefetchPc;
            sb.append(Sh2Helper.getInstString("", pc, ctx.prefetchWords[i])).append("\n");
        }
        return sb;
    }

    public static String toDebuggingString(Sh2Context ctx) {
        StringBuilder sb = new StringBuilder("\n");
        sb.append(getInstString(ctx.sh2TypeCode, ctx.PC, ctx.opcode)).append("\n");
        sb.append(String.format("PC : %08x\t", ctx.PC));
        sb.append(String.format("GBR: %08x\t", ctx.GBR));
        sb.append(String.format("VBR: %08x\t", ctx.VBR));
        sb.append(String.format("SR : %08x\t", ctx.SR));

        sb.append(((ctx.SR & Sh2.flagT) != 0 ? "T" : "-") + ((ctx.SR & Sh2.flagS) != 0 ? "S" : "-") +
                ((ctx.SR & Sh2.flagQ) != 0 ? "Q" : "-") + (((ctx.SR & Sh2.flagM) != 0 ? "M" : "-")));
        sb.append("\n");


        for (int i = 0; i < 16; i++) {
            sb.append(String.format("R%02d: %08x\t", i, ctx.registers[i]));
            if (i == 7) {
                sb.append("\n");
            }
        }
        sb.append("\n");
        sb.append(String.format("MCH: %08x\t", ctx.MACH));
        sb.append(String.format("MCL: %08x\t", ctx.MACL));
        sb.append(String.format("PR : %08x\t", ctx.PR));
        sb.append("\n");
        return sb.toString();
    }

    public static class Sh2Config {
        public final static Sh2Config DEFAULT_CONFIG = new Sh2Config();
        private static final AtomicReference<Sh2Config> instance = new AtomicReference<>(DEFAULT_CONFIG);
        public final boolean prefetchEn, drcEn, pollDetectEn, ignoreDelays, tasQuirk;

        private Sh2Config() {
            tasQuirk = true;
            prefetchEn = drcEn = pollDetectEn = ignoreDelays = false;
            LOG.info("Default config: {}", this);
        }

        public Sh2Config(boolean prefetchEn, boolean drcEn, boolean pollDetectEn, boolean ignoreDelays) {
            this(prefetchEn, drcEn, pollDetectEn, ignoreDelays, 1);
        }


        public Sh2Config(boolean prefetchEn, boolean drcEn, boolean pollDetectEn, boolean ignoreDelays, int tasQuirk) {
            this.prefetchEn = prefetchEn;
            this.drcEn = drcEn;
            this.pollDetectEn = pollDetectEn;
            this.ignoreDelays = ignoreDelays;
            this.tasQuirk = tasQuirk > 0;
            if (instance.compareAndSet(DEFAULT_CONFIG, this)) {
                LOG.info("Using config: {}", this);
            } else {
                LOG.error("Ignoring config: {}, current: {}", this, instance);
            }
        }

        public static Sh2Config get() {
            return instance.get();
        }

        //force config, test only
        public static void reset(Sh2Config config) {
            instance.set(config);
            LOG.warn("Overriding config: {}", config);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Sh2Config.class.getSimpleName() + "[", "]")
                    .add("prefetchEn=" + prefetchEn)
                    .add("drcEn=" + drcEn)
                    .add("pollDetectEn=" + pollDetectEn)
                    .add("ignoreDelays=" + ignoreDelays)
                    .toString();
        }
    }

    public static class FetchResult implements Serializable {
        @Serial
        private static final long serialVersionUID = 6381334821051653354L;

        public int pc, opcode;
        public transient Sh2Block block;
        //TODO
//        public Sh2Helper.Sh2PcInfoWrapper piw = Sh2Helper.SH2_NOT_VISITED;

        @Override
        public String toString() {
            return new StringJoiner(", ", FetchResult.class.getSimpleName() + "[", "]")
                    .add("pc=" + pc)
                    .add("opcode=" + opcode)
                    .add("block=" + block)
                    .toString();
        }
    }
}