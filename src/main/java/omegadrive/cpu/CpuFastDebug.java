package omegadrive.cpu;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Set;

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
    private final int[][] pcVisited, opcodes;
    private final int pcAreaMask, pcMask, pcAreaShift;
    public DebugMode debugMode = DebugMode.NONE;
    private CpuDebugInfoProvider debugInfoProvider;

    public CpuFastDebug(CpuDebugInfoProvider debugInfoProvider, CpuDebugContext ctx) {
        this.debugInfoProvider = debugInfoProvider;
        this.ctx = ctx;
        this.pcVisited = new int[ctx.pcAreasNumber][];
        this.opcodes = new int[ctx.pcAreasNumber][];
        this.pcAreaMask = ctx.pcAreaSize - 1;
        this.pcMask = ctx.pcMask;
        this.pcAreaShift = ctx.pcAreaShift;
        init();
    }

    public void init() {
        Set<Object> arraySet = ImmutableSet.of(pcVisited, opcodes);
        for (Object o : arraySet) {
            Arrays.stream(ctx.pcAreas).forEach(idx -> ((int[][]) o)[idx] = new int[ctx.pcAreaSize]);
        }
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
        if (prevOpcode == 0 || prevOpcode != opcode) {
            opc[pc & pcAreaMask] = opcode;
            pcv[pc & pcAreaMask] = 1;
            String val = prevOpcode == 0 ? " [NEW]" : " [NEW-R]";
            logNewInst(debugInfoProvider.getInstructionOnly(), val);
        }
    }

    private void logNewInst(String s1, String s2) {
        LOG.info("{}{}", s1, s2);
//        System.out.println(s1 + s2);
    }
}
