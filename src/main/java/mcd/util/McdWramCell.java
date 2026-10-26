package mcd.util;

import omegadrive.util.LogHelper;
import org.slf4j.Logger;

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
}
