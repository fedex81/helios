package mcd.asic;

import mcd.util.McdWramCell;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdMemoryContext.MCD_WORD_RAM_1M_MASK;
import static mcd.dict.MegaCdMemoryContext.MCD_WORD_RAM_1M_SIZE;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class McdWramCellTest {

    private static final int BANK_WORDS = MCD_WORD_RAM_1M_SIZE >> 1;

    private static int cellToLinearWord(int cellWord) {
        int lin = McdWramCell.cellToLinear(cellWord << 1, true) & MCD_WORD_RAM_1M_MASK & ~1;
        return lin >> 1;
    }


    @Test
    public void testCellMappingInverseIdentity() {
        int[] og = new int[BANK_WORDS];
        for (int w = 0; w < BANK_WORDS; w++) {
            og[w] = w;
        }

        int[] linear = new int[BANK_WORDS];
        int[] inverse = new int[BANK_WORDS];
        boolean[] written = new boolean[BANK_WORDS];

        for (int c = 0; c < BANK_WORDS; c++) {
            int lin = cellToLinearWord(c);
            Assertions.assertFalse(written[lin]);
            written[lin] = true;
            linear[lin] = og[c];
            inverse[lin] = c;
        }

        for (int l = 0; l < BANK_WORDS; l++) {
            Assertions.assertTrue(written[l]);
        }

        int[] rountrip = new int[BANK_WORDS];
        for (int lin = 0; lin < BANK_WORDS; lin++) {
            rountrip[inverse[lin]] = linear[lin];
        }

        Assertions.assertArrayEquals(og, rountrip);

    }
}
