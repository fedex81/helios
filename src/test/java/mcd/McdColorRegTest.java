package mcd;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.RegSpecMcd.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdColorRegTest extends McdRegTestBase {

    @Test
    public void testCommRegs() {
        writeSubRegWord(MCD_FONT_COLOR, 0x10);

        writeSubRegWord(MCD_FONT_BIT, 0x2);
        checkFontDataRegs(0, 0, 0, 0x10);

        writeSubRegEvenByte(MCD_FONT_BIT, 0xF0);
        checkFontDataRegs(0x1111, 0, 0, 0x10);

        writeSubRegWord(MCD_FONT_BIT, 0x5A5A);
        checkFontDataRegs(0x0101, 0x1010, 0x0101, 0x1010);

        writeSubRegWord(MCD_FONT_BIT, 0xA5A5);
        checkFontDataRegs(0x1010, 0x0101, 0x1010, 0x0101);

        //change colors
        writeSubRegWord(MCD_FONT_COLOR, 0xF5);
        checkFontDataRegs(0xF5F5, 0x5F5F, 0xF5F5, 0x5F5F);

        //only word access to color reg
        writeSubRegEvenByte(MCD_FONT_COLOR, 0xA8);
        Assertions.assertEquals(0xA8A8, readSubRegWord(MCD_FONT_DATA0));
        Assertions.assertEquals(0xA8, readSubRegWord(MCD_FONT_COLOR));

        //read back pixel reg
        writeSubRegWord(MCD_FONT_BIT, 0x1234);
        Assertions.assertEquals(0x1234, readSubRegWord(MCD_FONT_BIT));
    }

    private void checkFontDataRegs(int... vals) {
        Assertions.assertEquals(vals[0], readSubRegWord(MCD_FONT_DATA0));
        Assertions.assertEquals(vals[1], readSubRegWord(MCD_FONT_DATA1));
        Assertions.assertEquals(vals[2], readSubRegWord(MCD_FONT_DATA2));
        Assertions.assertEquals(vals[3], readSubRegWord(MCD_FONT_DATA3));
    }
}
