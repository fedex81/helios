package mcd.asic;

import mcd.McdRegTestBase;
import mcd.McdWordRamTest;
import mcd.dict.MegaCdDict;
import mcd.dict.MegaCdDict.RegSpecMcd;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.FileUtil;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static mcd.dict.MegaCdDict.END_MCD_SUB_WORD_RAM_2M;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static mcd.dict.MegaCdDict.START_MCD_SUB_WORD_RAM_2M;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class AsicTest extends McdRegTestBase {

    static String folderBase = "src/test/resources/megacd/asic/";

    static Path testFilePathBefore = Paths.get(folderBase, "wram_before.txt.zip");
    static Path testFilePathAfter = Paths.get(folderBase, "wram_after.txt.zip");

    @Test
    public void testAsic() {
        McdWordRamTest.setWramSub2M(lc);
        copyFileToWram(testFilePathBefore);

        //set ASIC registers
        asicRegWordWrite(MCD_IMG_STAMP_SIZE, (0 << 8) | 7);  //make sure GRON=0
        asicRegWordWrite(MCD_IMG_STAMP_MAP_ADDR, (64 << 8) | 0);
        asicRegWordWrite(MCD_IMG_VCELL, 0 | 13);
        asicRegWordWrite(MCD_IMG_START_ADDR, (-64 << 8) | 0);
        asicRegWordWrite(MCD_IMG_OFFSET, 0 | 0);
        asicRegWordWrite(MCD_IMG_HDOT, (1 << 8) | 0);
        asicRegWordWrite(MCD_IMG_VDOT, 0 | 112);
        asicRegWordWrite(MCD_IMG_TRACE_VECTOR_ADDR, (-128 << 8) | 0);

        int linesLeft;
        do {
            lc.asic.step(0);
            linesLeft = lc.asic.read(MCD_IMG_VDOT, MCD_IMG_VDOT.addr, Size.WORD);
        } while (linesLeft > 0);
        compareFileToWram(testFilePathAfter);
    }

    private List<Integer> toListOf16bitWords(Path path) {
        byte[] content = FileUtil.readBinaryFile(path, ".txt");
        String str = new String(content).replace("\n", "");
        List<Integer> il = new ArrayList<>();
        Arrays.stream(str.split(",")).filter(v -> v.trim().length() > 0).forEach(v -> il.add(Integer.valueOf(v)));
        return il;
    }

    private void copyFileToWram(Path path) {
        List<Integer> il = toListOf16bitWords(path);
        for (int i = START_MCD_SUB_WORD_RAM_2M; i < END_MCD_SUB_WORD_RAM_2M; i += 2) {
            int index = (i - START_MCD_SUB_WORD_RAM_2M) >> 1;
            lc.memoryContext.wramHelper.writeWordRam(SUB_M68K, i, il.get(index), Size.WORD);
        }
        //        printWram(lc.memoryContext);
    }

    private void compareFileToWram(Path path) {
        List<Integer> il = toListOf16bitWords(path);
        for (int i = START_MCD_SUB_WORD_RAM_2M; i < END_MCD_SUB_WORD_RAM_2M; i += 2) {
            int index = (i - START_MCD_SUB_WORD_RAM_2M) >> 1;
            int actual = il.get(index);
            int exp = lc.memoryContext.wramHelper.readWordRam(SUB_M68K, i, Size.WORD);
            Assertions.assertEquals(exp, actual);
        }
        //        printWram(lc.memoryContext);
    }

    private void asicRegWordWrite(RegSpecMcd reg, int word) {
        assert reg.deviceType == MegaCdDict.McdRegType.ASIC;
        lc.asic.write(reg, reg.addr, word, Size.WORD);
    }

    public static void printWram(MegaCdMemoryContext mc) {
        StringBuilder sb = new StringBuilder();
        for (int i = START_MCD_SUB_WORD_RAM_2M; i < END_MCD_SUB_WORD_RAM_2M; i += 2) {
            sb.append((mc.wramHelper.readWordRam(SUB_M68K, i, Size.WORD) & 0xFFFF) + ",");
            if (((i + 2) >> 1) % 16 == 0) {
                sb.append("\n");
            }
        }
        System.out.println(sb);
    }
}
