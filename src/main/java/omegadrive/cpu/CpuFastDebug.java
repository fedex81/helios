package omegadrive.cpu;

import com.google.common.base.Objects;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    private int delay;

    private boolean isBusy;

    private final String logHead;
    private final static boolean VERBOSE = false;
    public static final int CK_DELAY_ON_LOOP = 50;

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
        log(logHead + " " + instOnly, tail);
    }

    private PcInfoWrapper createPcWrapper(int pcMasked, int area, int opcode) {
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

    private static final int pcHistorySize = 12;
    private int FRONT = 0, BACK = 1;
    private final int[][] opcodesHistory = new int[2][pcHistorySize];
    private final int[][] pcHistory = new int[2][pcHistorySize];
    private int pcHistoryPointer = 0, loops;
    private boolean looping = false, isKnownLoop;
    private int loopsCounter = 0;

    public int isBusyLoop(int pc, int opcode) {
        pcHistory[FRONT][pcHistoryPointer] = pc;
        opcodesHistory[FRONT][pcHistoryPointer] = opcode;
        pcHistoryPointer = (pcHistoryPointer + 1) % pcHistorySize;
        if (pcHistoryPointer == 0) {
            if (Arrays.equals(pcHistory[FRONT], pcHistory[BACK])) {
                if (Arrays.equals(opcodesHistory[FRONT], opcodesHistory[BACK])) {
                    loops++;
                    if (!looping && loops > pcHistorySize) {
                        handleLoop(pc, opcode);
                        looping = isBusy;
                    }
                } else { //opcodes are different
                    handleStopLoop(pc);
                }
            } else {
                if (looping) {
                    handleStopLoop(pc);
                }
                loops = 0;
            }
            FRONT = (FRONT + 1) & 1;
            BACK = (BACK + 1) & 1;
        }
        assert looping ? delay > 0 : delay == 0 : looping + "," + delay;
        return delay;
    }

    private void handleStopLoop(int pc) {
        looping = false;
        loops = 0;
        delay = 0;
        if (!isKnownLoop) {
            if (VERBOSE) {
                String s = debugInfoProvider.getInstructionOnly();
                LOG.info("{} Stop loop: {}", logHead, s);
                System.out.println(logHead + " Stop loop: " + s);
            }
        }
    }

    private void handleLoop(int pc, int opcode) {
        final int[] opcodes = Arrays.stream(opcodesHistory[FRONT]).distinct().sorted().toArray();
        isBusy = isBusyLoop(ctx.isLoopOpcode, opcodes);
        delay = isBusy ? CK_DELAY_ON_LOOP : 0;
        final int area = pc >>> ctx.pcAreaShift;
        final int mask = ctx.pcAreasMaskMap[area];
        final int pcMasked = pc & mask;
        PcInfoWrapper piw = pcInfoWrapper[area][pcMasked];
        if (piw != NOT_VISITED && piw.pcLoops > 0) {
            if (!isKnownLoop && isBusy) {
                printLoopInfo();
            }
            isKnownLoop = true;
            if (VERBOSE && isBusy) {
                LOG.info("{} Known loop at: {}, busy: {}", logHead, th(pc), isBusy);
                System.out.println("Known loop at: " + th(pc) + ", busy: " + isBusy);
            }
            return;
        } else if (piw == NOT_VISITED) {
            piw = createPcWrapper(pcMasked, area, opcode);
            pcInfoWrapper[area][pcMasked] = piw;
        }
        assert piw != NOT_VISITED : th(pc) + "," + piw;
        isKnownLoop = false;
        loopsCounter++;
        piw.pcLoops = loopsCounter;
        if (false) {
            boolean ignore = isIgnore(ctx.isIgnoreOpcode, opcodes);
            if (!ignore) {
                printLoopInfo();
            }
        }
    }

    private void printLoopInfo() {
        int[] pcs = Arrays.stream(pcHistory[FRONT]).distinct().sorted().toArray();
        String s = Arrays.stream(pcs).mapToObj(debugInfoProvider::getInstructionOnly).collect(Collectors.joining("\n"));
        System.out.println(logHead + "\t" + pcs.length + " Loop, isBusy: " + isBusy + "\n" + s + "\n" + debugInfoProvider.getCpuState(""));
    }

    public static boolean isBusyLoop(final Predicate<Integer> isLoopOpcode, final int[] opcodes) {
        for (int i = 0; i < opcodes.length; i++) {
            if (!isLoopOpcode.test(opcodes[i])) {
                return false;
            }
        }
        return true;
    }

    public static boolean isIgnore(final Predicate<Integer> isIgnoredOpcode, final int[] opcodes) {
        for (int i = 0; i < opcodes.length; i++) {
            if (isIgnoredOpcode.test(opcodes[i])) {
                return true;
            }
        }
        return false;
    }
}