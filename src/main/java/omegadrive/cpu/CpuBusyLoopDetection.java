package omegadrive.cpu;

import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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

    /**
     * TODO does not detect loops of size 5,7,8,9,10?
     */
    private static final int pcHistorySize = 12;

    private final static boolean VERBOSE = false;
    public static final int CK_DELAY_ON_LOOP = 50;

    public static class BusyLoopCtx {
        public int pc;
        public int[] opcodes;
        public boolean isBusy;
    }
    private int FRONT = 0, BACK = 1;
    private final int[][] opcodesHistory = new int[2][pcHistorySize];
    private final int[][] pcHistory = new int[2][pcHistorySize];
    private int pcHistoryPointer = 0, loops;
    private boolean isKnownLoop;
    private int loopsCounter = 0;
    private int delay;

    private final String logHead;

    private final CpuFastDebug cpuFastDebug;

    private final CpuFastDebug.CpuDebugInfoProvider debugInfoProvider;

    private final CpuFastDebug.CpuDebugContext ctx;

    private final BusyLoopCtx busyLoopCtx;

    public CpuBusyLoopDetection(CpuFastDebug cpuFastDebug) {
        this.cpuFastDebug = cpuFastDebug;
        this.logHead = cpuFastDebug.getLogHead();
        this.debugInfoProvider = cpuFastDebug.getDebugInfoProvider();
        this.ctx = cpuFastDebug.getCtx();
        this.busyLoopCtx = new BusyLoopCtx();
    }

    public int isBusyLoop(int pc, int opcode) {
        pcHistory[FRONT][pcHistoryPointer] = pc;
        opcodesHistory[FRONT][pcHistoryPointer] = opcode;
        pcHistoryPointer = (pcHistoryPointer + 1) % pcHistorySize;
        if (pcHistoryPointer == 0) {
            if (Arrays.equals(pcHistory[FRONT], pcHistory[BACK])) {
                if (Arrays.equals(opcodesHistory[FRONT], opcodesHistory[BACK])) {
                    loops++;
                    if (!busyLoopCtx.isBusy && loops > pcHistorySize) {
                        /**
                         * TODO when pc =0 changes from 0xE9(ld (hl)) to 0xF3 (di)
                         * TODO the opcode history still shows 0xE9 -> it thinks we are looping
                         */
                        handleLoop(pc, opcode);
                    }
                } else { //opcodes are different
                    handleStopLoop(pc);
                }
            } else {
                if (busyLoopCtx.isBusy) {
                    handleStopLoop(pc);
                }
                loops = 0;
            }
            FRONT = (FRONT + 1) & 1;
            BACK = (BACK + 1) & 1;
        }
        assert busyLoopCtx.isBusy ? delay > 0 : delay == 0 : busyLoopCtx.isBusy + "," + delay;
        return delay;
    }

    private void handleStopLoop(int pc) {
        loops = 0;
        setBusyLoopCtx(false);
        if (!isKnownLoop) {
            if (VERBOSE) {
                String s = debugInfoProvider.getInstructionOnly();
                LOG.info("{} Stop loop: {}", logHead, s);
                System.out.println(logHead + " Stop loop: " + s);
            }
        }
    }

    private static Set<String> loopDedup = new HashSet<>();
    private static final String jpHlLoop = "00000000            E9    jp (hl)";

    private void handleLoop(int pc, int opcode) {
        int[] pcs = Arrays.stream(pcHistory[FRONT]).distinct().sorted().toArray();
        final int[] opcodes = Arrays.stream(opcodesHistory[FRONT]).distinct().sorted().toArray();
        boolean isBusy = isBusyLoop(ctx.isLoopOpcode, opcodes);
        if (pcs.length > 5) {
            LogHelper.logWarnOnce(LOG, "Ignoring busyLoop of len: " + pcs.length);
            isBusy = false;
        }
        setBusyLoopCtx(opcodes, isBusy);
        final int area = pc >>> ctx.pcAreaShift;
        final int mask = ctx.pcAreasMaskMap[area];
        final int pcMasked = pc & mask;
        CpuFastDebug.PcInfoWrapper piw = cpuFastDebug.pcInfoWrapper[area][pcMasked];
        if (piw != NOT_VISITED && piw.pcLoops > 0) {
            if (!isKnownLoop && isBusy) {
//                printLoopInfo();
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
        loopLogging();
    }

    private void loopLogging() {
        if (busyLoopCtx.isBusy) {
            String str = Arrays.toString(busyLoopCtx.opcodes);
            if (loopDedup.add(str)) {
                String print = str.contains(jpHlLoop) ? getLoopInfo() : getLoopInfoVerbose();
                System.out.println(print);
//                LogHelper.logWarnOnce(LOG, print);
            }
        }
    }

    private void setBusyLoopCtx(boolean isBusy) {
        assert !isBusy;
        setBusyLoopCtx(null, false);
    }

    private void setBusyLoopCtx(int[] opcodes, boolean isBusy) {
        busyLoopCtx.pc = isBusy ? getInitialLoopPc() : -1;
        busyLoopCtx.opcodes = isBusy ? opcodes : null;
        busyLoopCtx.isBusy = isBusy;
        delay = isBusy ? CK_DELAY_ON_LOOP : 0;
//        looping = isBusy;
    }

    public BusyLoopCtx getBusyLoopCtx() {
        return busyLoopCtx;
    }

    public void reset() {
        Arrays.fill(pcHistory[FRONT], 0);
        Arrays.fill(pcHistory[BACK], 1);
        Arrays.fill(opcodesHistory[BACK], 0);
        Arrays.fill(opcodesHistory[FRONT], 1);
        handleStopLoop(0);
        setBusyLoopCtx(false);
        isKnownLoop = false;
        loopsCounter = 0;

    }

    public void printLoopInfo() {
        System.out.println(getLoopInfo());
    }

    public String getLoopInfoVerbose() {
        return getLoopInfo() + "\n\n" + debugInfoProvider.getCpuState("");
    }

    public String getLoopInfo() {
        int[] pcs = Arrays.stream(pcHistory[FRONT]).distinct().sorted().toArray();
        String s = Arrays.stream(pcs).mapToObj(debugInfoProvider::getInstructionOnly).collect(Collectors.joining("\n"));
        return logHead + "\tLoop len: " + pcs.length + ", isBusy: " + busyLoopCtx.isBusy + "\n" + s;
    }

    public String getInstListOnly() {
        return Arrays.toString(Arrays.stream(opcodesHistory[FRONT]).distinct().sorted().toArray());
    }


    public int getInitialLoopPc() {
        return Arrays.stream(pcHistory[FRONT]).distinct().sorted().toArray()[0];
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
