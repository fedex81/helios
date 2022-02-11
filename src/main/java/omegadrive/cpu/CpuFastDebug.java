package omegadrive.cpu;

import com.google.common.collect.ImmutableSet;
import omegadrive.cpu.m68k.MC68000Helper;
import omegadrive.cpu.m68k.debug.MC68000WrapperFastDebug;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class CpuFastDebug {

    private static final Logger LOG = LogManager.getLogger(CpuFastDebug.class.getSimpleName());

    public interface CpuDebugInfoProvider {
        String getInstructionOnly();

        String getCpuState(String head);

        int getPc();

        int getOpcode();
    }

    public static class CpuDebugContext {
        public int[] pcAreas;
        public int pcAreasNumber, pcAreaSize, pcMask, pcAreaShift;
    }

    public enum DebugMode {NONE, INST_ONLY, NEW_INST_ONLY, STATE}

    private final CpuDebugContext ctx;
    private final int[][] pcVisited, opcodes, pcLoops;
    private final int pcAreaMask, pcMask, pcAreaShift;
    public DebugMode debugMode = DebugMode.NONE;
    private CpuDebugInfoProvider debugInfoProvider;

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
    private boolean[] validOpcodes = new boolean[0x10000];

    {
        validOpcodes[0x4e71] = validOpcodes[0x6000] = true; //mars demos
    }

    public void isBusyLoop(int pc, int opcode) {
        pcHistory[FRONT][pcHistoryPointer] = pc;
        opcodesHistory[FRONT][pcHistoryPointer] = opcode;
        pcHistoryPointer = (pcHistoryPointer + 1) % pcHistorySize;
        if (pcHistoryPointer == 0) {
            if (Arrays.equals(pcHistory[FRONT], pcHistory[BACK])) {
                if (Arrays.equals(opcodesHistory[FRONT], opcodesHistory[BACK])) {
                    loops++;
                    if (loops > pcHistorySize) {
                        handleLoop(pc);
                        looping = true;
                    }
                } else { //opcodes are different
                    looping = false;
                    loops = 0;
                }
            } else {
                if (looping) {
                    handleStopLoop(pc);
                    looping = false;
                }
                loops = 0;
            }
            FRONT = (FRONT + 1) & 1;
            BACK = (BACK + 1) & 1;
            if (FRONT == BACK) {
                System.out.println("wtf");
            }
        }
    }

    private void handleStopLoop(int pc) {
        if (!isKnownLoop) {
            MC68000WrapperFastDebug d = (MC68000WrapperFastDebug) debugInfoProvider;
            LOG.info("Stop loop: {}", MC68000Helper.dumpOp(d.getM68k(), pc));
        }
    }

    private void handleLoop(int pc) {
        if (pcLoops[pc >> pcAreaShift][pc >> pcMask] > 0) {
//            LOG.info("Known loop at: {}", th(pc));
            isKnownLoop = true;
            return;
        }
        isKnownLoop = false;
        MC68000WrapperFastDebug d = (MC68000WrapperFastDebug) debugInfoProvider;
        String s = IntStream.range(0, pcHistorySize).
                mapToObj(i -> MC68000Helper.dumpOp(d.getM68k(), pcHistory[FRONT][i])).collect(Collectors.joining("\n"));
        int distinct = (int) Arrays.stream(pcHistory[FRONT]).distinct().count();
        if (distinct > 2) {
            LOG.error("Loop: \n{}\n{}", s, d.getCpuState(""));
        }
//        System.out.println("Loop: \n" + s + "\n" + d.getCpuState(""));
        loopsCounter++;
        for (int i = 0; i < pcHistorySize; i++) {
            int pci = pcHistory[FRONT][i];
            pcLoops[pci >> pcAreaShift][pci >> pcMask] = loopsCounter;
        }
    }
}
