package mcd.util;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.FileUtil;
import omegadrive.util.HexUtil;
import omegadrive.util.LogHelper;
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

    public static final int[] CELL_LUT = new int[0x8000];

    static {
        //22_0000 - 22_FFFF <- 20_0000 - 20_FFFF (cell y 0-31)
        for (int i = 0; i < 0x4000; i++) {
            int offset = (i & 7) << 8; //cell vline (0-7)
            offset |= ((i >> 8) & 0x3F) << 2; // cell x offset (0-63)
            offset |= ((i >> 3) & 0x1F) << 11; // cell y offset (0-31)
            CELL_LUT[i] = offset;
        }
        //23_0000 - 23_7FFF <- 21_0000 - 21_7FFF (cell y 0-15)
        for (int i = 0x4000; i < 0x6000; i++) {
            int offset = (i & 7) << 8; //cell vline (0-7)
            offset |= ((i >> 7) & 0x3F) << 2; // cell x offset
            offset |= ((i >> 3) & 0xF) << 11; // cell y offset (0-15)
            CELL_LUT[i] = offset;
        }
        //23_8000 - 23_BFFF <- 21_8000 - 21_BFFF (cell y 0-7)
        for (int i = 0x6000; i < 0x7000; i++) {
            int offset = (i & 7) << 8; //cell vline (0-7)
            offset |= ((i >> 6) & 0x3F) << 2; // cell x offset
            offset |= ((i >> 3) & 0x7) << 11; // cell y offset (0-7)
            CELL_LUT[i] = offset | 0x8000;
        }
        //23_C000 - 23_DFFF <- 21_C000 - 21_DFFF (cell y 0-3)
        for (int i = 0x7000; i < 0x7800; i++) {
            int offset = (i & 7) << 8; //cell vline (0-7)
            offset |= ((i >> 5) & 0x3F) << 2; // cell x offset
            offset |= ((i >> 3) & 0x3) << 11; // cell y offset (0-3)
            CELL_LUT[i] = offset | 0xC000;
        }
        //23_E000 - 23_FFFF <- 21_E000 - 21_FFFF (cell y 0-3)
        for (int i = 0x7800; i < 0x8000; i++) {
            int offset = (i & 7) << 8; //cell vline (0-7)
            offset |= ((i >> 5) & 0x3F) << 2; // cell x offset
            offset |= ((i >> 3) & 0x3) << 11; // cell y offset (0-3)
            CELL_LUT[i] = offset | 0xE000;
        }
    }

    public static int cellToLinear(int cellOffset, boolean wordAccess) {
        int lowMask = wordAccess ? 0x10002 : 0x10003;
        return CELL_LUT[(cellOffset >> 2) & 0x7fff] | (cellOffset & lowMask);
    }

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
        SortedMap<Integer, Integer> sm = new TreeMap<>(m);
        int size = sm.size() * 8;
        ByteBuffer bb = ByteBuffer.allocate(size);
        for (var entry : sm.entrySet()) {
            bb.putInt(entry.getKey());
            bb.putInt(entry.getValue());
        }
        FileUtil.writeFileSafeAsync(Paths.get("./test.bin"), bb.array());
    }

    public static void printMemoryBank(MegaCdMemoryContext ctx, int bank) {
        StringBuilder sb = new StringBuilder();
        HexUtil.fillFormattedString_ZeroBased(sb, ctx.wordRam01[bank]);
        System.out.println("\n\n" + sb);
    }

    public static void main(String[] args) {
        System.out.println("test");
    }
}
