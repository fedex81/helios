package omegadrive.cpu;

import com.google.common.base.Objects;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Predicate;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class CpuFastDebug {

    private static final Logger LOG = LogHelper.getLogger(CpuFastDebug.class.getSimpleName());

    private static final boolean logToSysOut = Boolean.parseBoolean(System.getProperty("helios.logToSysOut", "true"));

    public interface CpuDebugInfoProvider {

        String getInstructionOnly(int pc, int opcode);
        String getInstructionOnly(int pc);

        String getCpuState(String head);

        int getPc();

        int getOpcode();

        default String getInstructionOnly() {
            return getInstructionOnly(getPc(), getOpcode());
        }
    }

    public static class CpuDebugContext {
        public final int[] pcAreasMaskMap;
        public final int pcAreasNumber;
        public int pcAreaShift;
        public Predicate<Integer> isLoopOpcode = i -> false;
        public Predicate<Integer> isIgnoreOpcode = i -> true;
        public int debugMode;

        public String cpuCode;

        public CpuDebugContext(Map<Integer, Integer> areaMaskMap) {
            pcAreasNumber = 1 + areaMaskMap.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
            pcAreasMaskMap = new int[pcAreasNumber];
            for (var e : areaMaskMap.entrySet()) {
                pcAreasMaskMap[e.getKey()] = e.getValue();
            }
        }
    }

    public static class PcInfoWrapper {
        public final int area, pcMasked, hc;
        public int opcode, pcLoops;
        public String str;

        public PcInfoWrapper(int area, int pcMasked) {
            this.area = area;
            this.pcMasked = pcMasked;
            this.hc = Objects.hashCode(area, pcMasked);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", PcInfoWrapper.class.getSimpleName() + "[", "]")
                    .add("area=" + area)
                    .add("pcMasked=" + pcMasked)
                    .add("opcode=" + opcode)
                    .add("pcLooops=" + pcLoops)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PcInfoWrapper that = (PcInfoWrapper) o;
            return area == that.area && pcMasked == that.pcMasked;
        }

        @Override
        public int hashCode() {
            return hc;
        }
    }

    public enum DebugMode {NONE, INST_ONLY, NEW_INST_ONLY, STATE}

    public final static DebugMode[] debugModeVals = DebugMode.values();
    public final static PcInfoWrapper NOT_VISITED = new PcInfoWrapper(0, 0);

    private final CpuDebugContext ctx;
    public final PcInfoWrapper[][] pcInfoWrapper;
    public DebugMode debugMode = DebugMode.NONE;
    private final CpuDebugInfoProvider debugInfoProvider;
    private final String logHead;

    public CpuFastDebug(CpuDebugInfoProvider debugInfoProvider, CpuDebugContext ctx) {
        this.debugInfoProvider = debugInfoProvider;
        this.ctx = ctx;
        this.pcInfoWrapper = new PcInfoWrapper[ctx.pcAreasNumber][0];
        logHead = Optional.ofNullable(ctx.cpuCode).orElse("");
        init();
    }

    public void init() {
        assert ctx.debugMode < debugModeVals.length : ctx.debugMode;
        debugMode = debugModeVals[ctx.debugMode];
        resetWrapper();
    }

    public void resetWrapper() {
        LOG.warn("{} Resetting known and visited PCs!!", logHead);
        for (int i = 0; i < ctx.pcAreasMaskMap.length; i++) {
            int pcAreaSize = ctx.pcAreasMaskMap[i] + 1;
            if (pcAreaSize > 1) {
                pcInfoWrapper[i] = new PcInfoWrapper[pcAreaSize];
                Arrays.fill(pcInfoWrapper[i], NOT_VISITED);
            }
        }
    }

    public void printDebugMaybe() {
        switch (debugMode) {
            case STATE:
                log(debugInfoProvider.getCpuState(ctx.cpuCode), "");
                break;
            case INST_ONLY:
                doPrintInst("", debugInfoProvider.getInstructionOnly());
                break;
            case NEW_INST_ONLY:
                printNewInstruction();
                break;
            default:
                break;
        }
    }

    /**
     * TODO 68k,z80
     * 00ff1928   13fc 0009 00a13005      move.b   #$0009,$00a13005 [NEW]
     * vs
     * 00ff1928   13fc 0001 00a13005      move.b   #$0001,$00a13005 [NEW]
     * <p>
     * we don't detect the change as we only compare 13fc
     */
    private void printNewInstruction() {
        final int pc = debugInfoProvider.getPc();

        final int area = pc >>> ctx.pcAreaShift;
        final int mask = ctx.pcAreasMaskMap[area];
        assert mask > 0 : th(pc);
        final int pcMasked = pc & mask;
        final int opcode = debugInfoProvider.getOpcode();

        PcInfoWrapper piw = pcInfoWrapper[area][pcMasked];
        if (piw == NOT_VISITED) {
            String instOnly = debugInfoProvider.getInstructionOnly(pc, opcode);
            pcInfoWrapper[area][pcMasked] = createPcWrapper(pcMasked, area, opcode);
            pcInfoWrapper[area][pcMasked].str = instOnly;
            doPrintInst(" [NEW]", instOnly);
            return;
        }
        boolean isReplaced = piw != NOT_VISITED && piw.opcode != opcode;
        if (isReplaced) {
            piw.opcode = opcode;
            piw.str = debugInfoProvider.getInstructionOnly(pc, opcode);
            doPrintInst(" [NEW-R]", piw.str);
        }
    }

    private void doPrintInst(String tail, String instOnly) {
        log((logHead.isEmpty() ? "" : logHead + " ") + instOnly, tail);
    }

    protected PcInfoWrapper createPcWrapper(int pcMasked, int area, int opcode) {
        PcInfoWrapper piw = new PcInfoWrapper(area, pcMasked);
        piw.opcode = opcode;
        return piw;
    }

    private void log(String s1, String s2) {
        if (logToSysOut) {
            System.out.println(s1 + s2);
        } else {
            LOG.info("{}{}", s1, s2);
        }
    }

    public String getLogHead() {
        return logHead;
    }

    public CpuDebugInfoProvider getDebugInfoProvider() {
        return debugInfoProvider;
    }

    public CpuDebugContext getCtx() {
        return ctx;
    }
}