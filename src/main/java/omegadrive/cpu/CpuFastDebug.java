package omegadrive.cpu;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class CpuFastDebug {

    private static final Logger LOG = LogManager.getLogger(CpuFastDebug.class.getSimpleName());

    public interface CpuDebugInfoProvider {
        String getInstructionOnly(int pc);

        String getCpuState(String head);

        int getPc();

        int getOpcode();

        default String getInstructionOnly() {
            return getInstructionOnly(getPc());
        }
    }

    public static class CpuDebugContext {
        public int[] pcAreas;
        public int pcAreasNumber, pcAreaSize, pcMask, pcAreaShift;
        public Predicate<Integer> isLoopOpcode = i -> false;
        public Predicate<Integer> isIgnoreOpcode = i -> true;
    }

    public enum DebugMode {NONE, INST_ONLY, NEW_INST_ONLY, STATE}

    private final CpuDebugContext ctx;
    private final int[][] pcVisited, opcodes, pcLoops;
    private final int pcAreaMask, pcMask, pcAreaShift;
    public DebugMode debugMode = DebugMode.NONE;
    private CpuDebugInfoProvider debugInfoProvider;
    private int delay;
    private final static boolean VERBOSE = false;
    public static int CK_DELAY_ON_LOOP = 50;

    public CpuFastDebug(CpuDebugInfoProvider debugInfoProvider, CpuDebugContext ctx) {
        this.debugInfoProvider = debugInfoProvider;
        this.ctx = ctx;
        this.pcVisited = new int[ctx.pcAreasNumber][];
        this.pcLoops = new int[ctx.pcAreasNumber][];
        this.opcodes = new int[ctx.pcAreasNumber][];
        this.pcAreaMask = ctx.pcAreaSize - 1;
        this.pcMask = ctx.pcMask;
        this.pcAreaShift = ctx.pcAreaShift;
        init();
    }

    public void init() {
        Set<Object> arraySet = ImmutableSet.of(pcVisited, pcLoops, opcodes);
        for (Object o : arraySet) {
            Arrays.stream(ctx.pcAreas).forEach(idx -> ((int[][]) o)[idx] = new int[ctx.pcAreaSize]);
        }
        Arrays.stream(ctx.pcAreas).forEach(i -> Arrays.fill(opcodes[i], -1));
    }

    public void printDebugMaybe() {
        switch (debugMode) {
            case STATE:
                LOG.info(debugInfoProvider.getCpuState(""));
                break;
            case INST_ONLY:
                LOG.info(debugInfoProvider.getInstructionOnly());
                break;
            case NEW_INST_ONLY:
                printNewInstruction();
                break;
            default:
                break;
        }
    }

    private void printNewInstruction() {
        final int pc = debugInfoProvider.getPc() & pcMask;
        final int opcode = debugInfoProvider.getOpcode();
        final int area = pc >> pcAreaShift;
        final int[] pcv = pcVisited[area];
        final int[] opc = opcodes[area];
        final int prevOpcode = opc[pc & pcAreaMask];
        if (prevOpcode == -1 || prevOpcode != opcode) {
            opc[pc & pcAreaMask] = opcode;
            pcv[pc & pcAreaMask] = 1;
            String val = prevOpcode == -1 ? " [NEW]" : " [NEW-R]";
            logNewInst(debugInfoProvider.getInstructionOnly(), val);
        }
    }

    private void logNewInst(String s1, String s2) {
        LOG.info("{}{}", s1, s2);
//        System.out.println(s1 + s2);
    }

    private static int pcHistorySize = 12;
    private int FRONT = 0, BACK = 1;
    private int[][] opcodesHistory = new int[2][pcHistorySize];
    private int[][] pcHistory = new int[2][pcHistorySize];
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
                        handleLoop(pc);
                        looping = true;
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
        return delay;
    }

    private void handleStopLoop(int pc) {
        looping = false;
        loops = 0;
        delay = 0;
        if (!isKnownLoop) {
            if (VERBOSE) {
                String s = debugInfoProvider.getInstructionOnly();
                LOG.info("Stop loop: {}", s);
                System.out.println("Stop loop: " + s);
            }
        }
    }

    private void handleLoop(int pc) {
        final int[] opcodes = Arrays.stream(opcodesHistory[FRONT]).distinct().sorted().toArray();
        final boolean isBusy = isBusyLoop(ctx.isLoopOpcode, opcodes);
        delay = isBusy ? CK_DELAY_ON_LOOP : 0;
        if (pcLoops[pc >> pcAreaShift][pc & pcMask] > 0) {
            isKnownLoop = true;
            if (VERBOSE && isBusy) {
                LOG.info("Known loop at: {}, busy: {}", th(pc), isBusy);
                System.out.println("Known loop at: " + th(pc) + ", busy: " + isBusy);
            }
            return;
        }
        isKnownLoop = false;
        loopsCounter++;
        for (int i = 0; i < pcHistorySize; i++) {
            int pci = pcHistory[FRONT][i];
            pcLoops[pci >> pcAreaShift][pci & pcMask] = loopsCounter;
        }
        if (VERBOSE) {
            boolean ignore = isIgnore(ctx.isIgnoreOpcode, opcodes);
            if (!ignore) {
                int[] pcs = Arrays.stream(pcHistory[FRONT]).distinct().sorted().toArray();
                String s = Arrays.stream(pcs).mapToObj(debugInfoProvider::getInstructionOnly).collect(Collectors.joining("\n"));
//                if(pcs.length < 4 && !isBusy) {
                System.out.println(pcs.length + " Loop, isBusy: " + isBusy + "\n" + s + "\n" + debugInfoProvider.getCpuState(""));
//                }
            }
        }
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
