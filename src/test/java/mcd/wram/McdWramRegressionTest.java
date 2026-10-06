package mcd.wram;

import mcd.McdRegTestBase;
import mcd.bus.McdWordRamHelper.UpdateImpl;
import mcd.dict.MegaCdMemoryContext.WramSetup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static mcd.dict.MegaCdMemoryContext.WramSetup.W_2M_MAIN;
import static mcd.dict.MegaCdMemoryContext.WramSetup.W_2M_SUB;
import static mcd.wram.McdWordRamTest.mainSetLsbFn;
import static mcd.wram.McdWordRamTest.subSetLsbFn;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class McdWramRegressionTest extends McdRegTestBase {

    final static int MAIN_IDX = 1;
    final static int SUB_IDX = 0;
    private static final int[] REG3_VALS = {0, 1, 2, 3, 4, 5, 6};
    private static final int[] REG3_CPUS = {MAIN_IDX, SUB_IDX};

    private static final int REG3_MAX_LEN = 4;

    private static final Path CSV_PATH = Path.of("src", "test", "resources", "mcd",
            "wram_gpgx_expected.csv");

    @BeforeEach
    public void setup() {
        setupBase();
    }

    @Test
    public void testUpdateWramAll() {
        int[] vals = {0, 1, 2, 3, 4, 5, 6};
        int[] cpus = {MAIN_IDX, SUB_IDX}; //1 = MAIN, 0 = SUB
        int steps = cpus.length * vals.length;

        for (int len = 1; len <= 4; len++) {
            long combos = (long) Math.pow(steps, len);
            for (long idx = 0; idx < combos; idx++) {
                int[][] seq = decodeReg3Seq(len, idx);
                WramSetup b = runReg3Seq(UpdateImpl.BLASTEM, seq);
                WramSetup g = runReg3Seq(UpdateImpl.GPGX, seq);
                if (b != g)
                    System.out.printf("reg$03 seq [%s] -> BLASTEM=%s GPGX=%s %s%n", formatReg3Seq(seq), b, g, b == g ? "(same)" : "(diff)");
            }
        }
    }

    /**
     * Differ on a redundant SUB RET write while already in 1M:
     * - GPGX gates its SUB handshake on "(data ^ reg1) & 0x05" (MODE/RET changed).
     * A SUB write that re-asserts the same RET/MODE is a no-op, so the pending-swap flag (scd.dmna=1 from
     * the earlier MAIN DMNA write) survives, and the later 1M->2M switch computes RET=~dmna=0 -> SUB owns.
     * - BLASTEM has no change gate: it reprocesses the RET=1 write, re-setting main_has_word2m=1,
     * so the 1M->2M switch -> MAIN owns.
     * <p>
     * Minimal diverge sequence:
     * SUB=5 (1M,bank1,RET=1) -> MAIN=2 (DMAN swap req in 1M) -> SUB=5 (redundant RET=1) -> SUB=0 (2M)
     */
    @Test
    public void testBlastemVsGpgxDiverge() {
        //1 = MAIN, 0 = SUB
        int[][] seq = {
                {SUB_IDX, 5}, //SUB=5 (1M,bank1,RET=1)
                {MAIN_IDX, 2},     //MAIN=2 (DMAN swap req in 1M)
                {SUB_IDX, 5}, //SUB=5 (redundant RET=1)
                {SUB_IDX, 0}, //SUB=0 (2M)
        };
        WramSetup blastem = runReg3Seq(UpdateImpl.BLASTEM, seq);
        WramSetup gpgx = runReg3Seq(UpdateImpl.GPGX, seq);
        System.out.println("BLASTEM=" + blastem + " GPGX=" + gpgx);

        Assertions.assertEquals(W_2M_MAIN, blastem);
        Assertions.assertEquals(W_2M_SUB, gpgx);
        Assertions.assertNotEquals(blastem, gpgx);
    }

    @Test
    public void testGpgxRegressions() throws IOException {
        List<String> lines = Files.readAllLines(CSV_PATH);
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", 3);
            WramSetup exp = WramSetup.valueOf(cols[2].trim());
            WramSetup actual = runReg3Seq(UpdateImpl.GPGX, parseReg3Seq(cols[1]));
            if (exp != actual) {
                mismatches.add("[" + cols[1] + "] expected " + exp + " but was " + actual);
            }
            checked++;
        }
        System.out.println("Validated " + checked + " rows, " + mismatches.size() + " mismatches");
        Assertions.assertTrue(mismatches.isEmpty(), "GPGX regressed on " +
                mismatches.size() + " sequences: " + mismatches.stream().limit(10).toList());

    }

    @Disabled("generator")
    @Test
    public void generateGpgxExpectedCsv() throws IOException {
        int steps = REG3_CPUS.length * REG3_VALS.length;
        Files.createDirectories(CSV_PATH.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("len,sequence,result\n");
        for (int len = 1; len < REG3_MAX_LEN; len++) {
            long combos = (long) Math.pow(steps, len);
            for (long idx = 0; idx < combos; idx++) {
                int[][] seq = decodeReg3Seq(len, idx);
                WramSetup g = runReg3Seq(UpdateImpl.GPGX, seq);
                sb.append(len).append(",").append(formatReg3Seq(seq)).append(",").append(g).append("\n");
            }
        }
        Files.writeString(CSV_PATH, sb.toString());
        System.out.println("Wrote " + CSV_PATH.toAbsolutePath());
    }

    private static String formatReg3Seq(int[][] seq) {
        StringBuilder sb = new StringBuilder();
        for (int[] w : seq) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(w[0] == MAIN_IDX ? "MAIN" : "SUB").append("=").append(th(w[1]));
        }
        return sb.toString();
    }

    private static int[][] decodeReg3Seq(int len, long idx) {
        int[][] seq = new int[len][2];
        long t = idx;
        for (int s = 0; s < len; s++) {
            int cpuIdx = (int) (t % REG3_CPUS.length);
            t /= REG3_CPUS.length;
            int valIdx = (int) (t % REG3_VALS.length);
            t /= REG3_VALS.length;
            seq[s][0] = REG3_CPUS[cpuIdx];
            seq[s][1] = REG3_VALS[valIdx];
        }
        return seq;
    }


    private WramSetup runReg3Seq(UpdateImpl impl, int[][] seq) {
        ctx.wramHelper.resetState();
        ctx.wramHelper.setUpdateImpl(impl);
        for (int[] w : seq) {
            if (w[0] == MAIN_IDX) {
                mainSetLsbFn.accept(lc.mainBus, w[1]);
            } else {
                assert w[0] == SUB_IDX;
                subSetLsbFn.accept(lc.subBus, w[1]);
            }
        }
        return ctx.wramSetup;
    }

    private static int[][] parseReg3Seq(String s) {
        String[] tokens = s.trim().split("\\s+");
        List<int[]> out = new ArrayList<>();
        for (String tok : tokens) {
            String[] kv = tok.split("=");
            int cpu = kv[0].equals("MAIN") ? MAIN_IDX : SUB_IDX;
            int val = Integer.parseInt(kv[1], 16);
            out.add(new int[]{cpu, val});
        }
        return out.toArray(new int[0][]);
    }


}
