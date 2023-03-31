package s32x.sh2.drc;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.junit.jupiter.api.Assertions;
import s32x.sh2.Sh2;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Helper;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.util.S32xUtil;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static omegadrive.util.Util.th;
import static s32x.sh2.drc.Sh2Block.SH2_DRC_MAX_BLOCK_LEN_BYTES;
import static s32x.sh2.prefetch.Sh2PrefetchSimple.prefetchContexts;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class DrcUtil {

    private static final String sentinelPath = "src/test/resources/sentinel";

    public static final boolean RUNNING_IN_GITHUB;

    static {
        System.out.println(new File(".").getAbsolutePath());
        Path p = Paths.get(".", sentinelPath);
        RUNNING_IN_GITHUB = !p.toFile().exists();
        System.err.println("Ignore tests failing in GitHub: " + RUNNING_IN_GITHUB);
    }

    public static void loopUntilDrc(Sh2 sh2, Sh2Context sh2Context, Sh2Helper.Sh2PcInfoWrapper wrapper) {
        int cnt = 0;
        do {
            sh2.run(sh2Context);
            cnt++;
        } while (wrapper.block.stage2Drc == null && cnt < 1_000);
        Assertions.assertNotEquals(1_000, cnt);
    }

    private static Table<S32xUtil.CpuDeviceAccess, Integer, Sh2Block> blockTable = HashBasedTable.create();

    public static void triggerDrcBlocks(Sh2 sh2, Sh2Context context, int... blockPcs) {
        Assertions.assertNotEquals(0, blockPcs.length);
        blockTable.clear();
        boolean stop = false;
        int maxSpin = 0x1000;
        int spin = 0;
        do {
            sh2.run(context);
            spin++;
            stop = allBlocksDrc(context, blockPcs) || spin > maxSpin;
        } while (!stop);
        Assertions.assertFalse(spin > maxSpin);
    }

    private static boolean allBlocksDrc(Sh2Context sh2Context, int... blockPcs) {
        S32xUtil.CpuDeviceAccess cpu = sh2Context.cpuAccess;
        for (int i = 0; i < blockPcs.length; i++) {
            int blockPc = blockPcs[i];
            if (!blockTable.contains(cpu, blockPc)) {
                Collection<Sh2Block> l = getPrefetchBlocksAt(cpu, blockPc);
                l.forEach(b -> {
                    if (b != Sh2Block.INVALID_BLOCK) {
                        blockTable.put(cpu, blockPc, b);
                        System.out.println(cpu + " Detected block: " + th(blockPc));
                    }
                });
            }
        }
        boolean atLeastOneNoDrc = blockTable.row(cpu).values().stream().anyMatch(b -> b.stage2Drc == null);
        return !atLeastOneNoDrc;
    }

    public static Collection<Sh2Block> getPrefetchBlocksAt(S32xUtil.CpuDeviceAccess cpu, int address) {
        Set<Sh2Block> l = new HashSet<>();
        if (Sh2Config.get().drcEn) {
            for (int i = address - SH2_DRC_MAX_BLOCK_LEN_BYTES; i <= address; i += 2) {
                Sh2Block b = Sh2Helper.getOrDefault(i, cpu).block;
                if (b != Sh2Block.INVALID_BLOCK) {
                    int end = b.prefetchPc + (b.end - b.start);
                    boolean include = b.prefetchPc <= address && end > address;
                    if (include) {
                        l.add(b);
                    }
                }
            }
        } else {
            assert Sh2Config.get().prefetchEn;
            if (prefetchContexts[cpu.ordinal()].prefetchPc == address) {
                Sh2Block block = new Sh2Block(address, cpu);
                block.start = address;
                //TODO mismatch between block.end and prefetch.end
                block.end = prefetchContexts[cpu.ordinal()].end - 2;
                block.prefetchWords = prefetchContexts[cpu.ordinal()].prefetchWords;
                l.add(block);
            }
        }
        return l;
    }
}
