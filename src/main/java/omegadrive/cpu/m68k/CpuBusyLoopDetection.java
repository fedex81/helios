package omegadrive.cpu.m68k;

import omegadrive.cpu.CpuFastDebug;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static omegadrive.cpu.CpuFastDebug.NOT_VISITED;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class CpuBusyLoopDetection {

    private static final Logger LOG = LogHelper.getLogger(CpuBusyLoopDetection.class.getSimpleName());

    private static final int pcHistorySize = 12;

    private final static boolean VERBOSE = false;
    public static final int CK_DELAY_ON_LOOP = 50;
    private int FRONT = 0, BACK = 1;
    private final int[][] opcodesHistory = new int[2][pcHistorySize];
    private final int[][] pcHistory = new int[2][pcHistorySize];
    private int pcHistoryPointer = 0, loops;
    private boolean looping = false, isKnownLoop;
    private int loopsCounter = 0;
    private int delay;

    private boolean isBusy;

    private final String logHead;

    private final CpuFastDebug cpuFastDebug;

    private final CpuFastDebug.CpuDebugInfoProvider debugInfoProvider;

    private final CpuFastDebug.CpuDebugContext ctx;

    public CpuBusyLoopDetection(CpuFastDebug cpuFastDebug) {
        this.cpuFastDebug = cpuFastDebug;
        this.logHead = cpuFastDebug.getLogHead();
        this.debugInfoProvider = cpuFastDebug.getDebugInfoProvider();
        this.ctx = cpuFastDebug.getCtx();
    }

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
        CpuFastDebug.PcInfoWrapper piw = cpuFastDebug.pcInfoWrapper[area][pcMasked];
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
            piw = cpuFastDebug.createPcWrapper(pcMasked, area, opcode);
            cpuFastDebug.pcInfoWrapper[area][pcMasked] = piw;
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
