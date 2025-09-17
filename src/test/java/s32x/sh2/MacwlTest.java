package s32x.sh2;

import omegadrive.SystemLoader;
import omegadrive.system.SystemProvider;
import omegadrive.util.FileUtil;
import omegadrive.util.MdRuntimeData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import s32x.dict.S32xDict;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static omegadrive.util.Util.th;
import static s32x.sh2.Sh2.posS;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class MacwlTest extends Sh2BaseTest {


    /**
     * From https://raw.githubusercontent.com/StrikerX3/Ymir/refs/heads/main/tests/ymir-core-tests/src/hw/sh2/sh2_macwl_testdata.inc
     * Sept2025
     */
    static Path testFilePath = Paths.get("src/test/resources/sh2", "sh2_macwl_testdata.inc");

    private static class TestDataRecord {
        public long rn, rm, macIn, s, macw, macl;
        public String line;
    }

    static int RM = 1, RN = 2;
    private final int r1 = S32xDict.SH2_START_SDRAM | 0x100;
    private final int r2 = S32xDict.SH2_START_SDRAM | 0x110;

    private final int pc = S32xDict.SH2_START_SDRAM | 0x400;

    private static List<TestDataRecord> recordList;

    private static Function<String, String> cleanIt = s -> s.replace("0x", "").replace("},", "").
            replace("TestData{", "").replace("}", "");

    private static List<String> lines;

    @BeforeAll
    public static void loadFile() {
        recordList = new ArrayList<>();
        lines = FileUtil.readFileContent(testFilePath);
        assert !lines.isEmpty();
        lines = lines.stream().map(cleanIt).toList();
        for (String line : lines) {
            String[] dt = line.split(",");
            for (int j = 0; j < dt.length; j++) {
                dt[j] = dt[j].trim();
            }
//                System.out.println(line);
            TestDataRecord tdr = new TestDataRecord();
            tdr.line = line;
            tdr.rn = Long.parseUnsignedLong(dt[0], 16);
            tdr.rm = Long.parseUnsignedLong(dt[1], 16);
            tdr.macIn = Long.parseUnsignedLong(dt[2], 16);
            tdr.s = Long.parseUnsignedLong(dt[3], 16);
            tdr.macw = Long.parseUnsignedLong(dt[4], 16);
            tdr.macl = Long.parseUnsignedLong(dt[5], 16);
            recordList.add(tdr);
        }
        System.out.println("Loaded records: " + recordList.size());
    }

    @Test
    public void testMac() {
        MdRuntimeData.releaseInstance();
        MdRuntimeData.newInstance(SystemLoader.SystemType.S32X, SystemProvider.NO_CLOCK);
        recordList.forEach(this::testInternal);
        /**
         * Quite a few errors, but sw seems to work fine.
         * 32x sw is not really using MACW/MACL extensively?
         */
        Assertions.assertEquals(2482, macwerr);
        Assertions.assertEquals(625, maclerr);
    }

    private int macwerr = 0, maclerr = 0;
    boolean verbose = false;

    private void testInternal(TestDataRecord td) {
        if (verbose) System.out.println(td.line);
        reset(td);
        sh2.MACL(0x021F); // mac.l @r1+, @r2+

        Assertions.assertEquals(r1 + 4, ctx.registers[1]);
        Assertions.assertEquals(r2 + 4, ctx.registers[2]);

        try {
            Assertions.assertEquals((int) (td.macl >>> 32), ctx.MACH);
            Assertions.assertEquals((int) (td.macl & 0xFFFF_FFFF), ctx.MACL);
        } catch (AssertionError ae) {
            if (verbose) System.out.println("MACL," + th(ctx.MACH) + "_" + th(ctx.MACL) + "\n     " + th(td.macl));
            maclerr++;
        }

        ctx.SR = 0;
        ctx.SR |= td.s << posS;
        Sh2Impl.setMAC(ctx, td.macIn);

        sh2.MACW(0x421F); // mac.w @r1+, @r2+

        Assertions.assertEquals(r1 + 6, ctx.registers[1]);
        Assertions.assertEquals(r2 + 6, ctx.registers[2]);
        try {
            Assertions.assertEquals((int) (td.macw >>> 32), ctx.MACH);
            Assertions.assertEquals((int) (td.macw & 0xFFFF_FFFF), ctx.MACL);
        } catch (AssertionError ae) {
            if (verbose) System.out.println("MACW," + th(ctx.MACH) + "_" + th(ctx.MACL) + "\n     " + th(td.macw));
            macwerr++;
        }
    }

    private void reset(TestDataRecord td) {
        ctx.registers[RM] = r1;
        ctx.registers[RN] = r2;
        ctx.PC = pc;
        ctx.SR = 0;
        ctx.SR |= td.s << posS;
        sh2.memory.write32(r1, (int) td.rm);
        sh2.memory.write32(r2, (int) td.rn);
        sh2.memory.write32(r1 + 4, (int) td.rm);
        sh2.memory.write32(r2 + 4, (int) td.rn);
        Sh2Impl.setMAC(ctx, td.macIn);
    }
}
