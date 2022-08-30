package omegadrive.cpu.m68k;

import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class M68kCycles {

    private final static Logger LOG = LogHelper.getLogger(M68kCycles.class.getSimpleName());
    private static final int NUM_OPCODES = 0x10_000;
    public static final int[] m68k_cycles;

    /**
     * true - use opcode-cycle from external file
     * false - use m68k internal cycle compute
     * <p>
     * TODO MOVEM.L et al. cycle table only lists base-case cycles, less accurate timing
     */
    public static final boolean USE_CYCLES_TABLE = Boolean.parseBoolean(System.getProperty("68k.use.cycle.table", "false"));

    static {
        long ns = System.nanoTime();
        List<String> l = FileUtil.readFileContent("res/m68kCycles.dat");
        assert l != null && l.size() > 0;
        m68k_cycles = l.stream().filter(st -> !st.startsWith("#")).flatMap(st -> Arrays.stream(st.split(","))).
                mapToInt(st -> Integer.parseInt(st.trim())).toArray();
        assert m68k_cycles.length == NUM_OPCODES;
        LOG.info("M68kCycle table loaded in: {} ms", Duration.ofNanos(System.nanoTime() - ns).toMillis());
    }

    public static final int getCycles(int opcode, int cycles) {
        return USE_CYCLES_TABLE ? m68k_cycles[opcode] : cycles;
    }
}