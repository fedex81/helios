package mcd.util;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.FileUtil;
import omegadrive.util.LogHelper;
import omegadrive.vdp.util.MemView;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static mcd.dict.MegaCdMemoryContext.MCD_WORD_RAM_1M_SIZE;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdWramCell {

    private static final Logger LOG = LogHelper.getLogger(McdWramCell.class.getSimpleName());

    /**
     * 200000->220000
     * 200002->220002
     * 200100->220004
     * 200102->220006
     * 200200->220008
     * 200202->22000a
     */
    public final static int[] linearCellMap = new int[MCD_WORD_RAM_1M_SIZE];
    public static int[] slotBaseAddr = {0x0_0000, 0x1_0000, 0x1_8000, 0x1_C000, 0x1_E000};

    static {
        //for mcd-ver
        int[] limit = {0x8000, 0xC000, 0xE000, 0xF000, 0x10_000};
        for (int j = 0; j < 5; j++) {
            int baseAddr = slotBaseAddr[j];
            int start = baseAddr >> 1;
            int size = 0x10_000 >> j;
            size = Math.max(size, 0x2000);
            int acc = start;
            int lineStart = start;
            for (int i = 0; i < size - 3; i += 4) {
                if (acc >= limit[j]) {
                    lineStart += 2;
                    acc = lineStart;
                }
                int val1 = acc;
                int val2 = acc + 1;
                int src1 = slotBaseAddr[j] | (val1 << 1);
                int dest1 = slotBaseAddr[j] | (baseAddr + i);
                int src2 = slotBaseAddr[j] | (val2 << 1);
                int dest2 = slotBaseAddr[j] | (baseAddr + i + 2);
                assert linearCellMap[src1] == 0 && linearCellMap[src2] == 0;
                linearCellMap[src1] = dest1;
                linearCellMap[src2] = dest2;
//                System.out.println(j + "s," +  th(baseAddr + i) + "," + th(val1) + "," + th(0x20_0000 | src1)
//                + "->" + th(0x22_0000 | dest1));
//                System.out.println(j + "s," +  th(baseAddr + i + 2) + "," + th(val2)
//                        + "," + th(0x20_0000 | src2) + "->" + th(0x22_0000 |dest2));
                acc += 0x80;
            }
        }
//        writeToFile(linearCellMap);
    }

    private static void writeToFile(Map<Integer, Integer> m) {
        SortedMap<Integer, Integer> sm = new TreeMap(m);
        int size = sm.size() * 8;
        ByteBuffer bb = ByteBuffer.allocate(size);
        for (var entry : sm.entrySet()) {
            bb.putInt(entry.getKey());
            bb.putInt(entry.getValue());
        }
        FileUtil.writeFileSafe(Paths.get("./test.bin"), bb.array());
    }

    public static void printMemoryBank(MegaCdMemoryContext ctx, int bank) {
        StringBuilder sb = new StringBuilder();
        MemView.fillFormattedString(sb, ctx.wordRam01[bank], 0, ctx.wordRam01[bank].length);
        System.out.println("\n\n" + sb);
    }

    public static void main(String[] args) {
        System.out.println("test");
    }
}
