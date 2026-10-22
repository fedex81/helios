package mcd.asic;

import mcd.McdRegTestBase;
import mcd.wram.McdWordRamTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.asic.AsicModel.StampPriorityMode.OVERWRITE;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_MEM_MODE;

/**
 *
 * AsicReg2WriteTest
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class AsicReg2WriteTest extends McdRegTestBase {

    private static final int PM1 = 1 << 4;   //OVERWRITE = PM1 set, PM0 clear (SUB only)
    private static final int DMNA = 1 << 1;
    private static final int RET = 1 << 0;
    private static final int BK0 = 1 << 6;  //PRG_RAM bank bit0 (MAIN-only)
    private static final int BK1 = 1 << 7;  //PRG_RAM bank bit1 (MAIN-only)

    @Test
    public void testMainWriteDoesNotClobberSubPriorityMode() {
        McdWordRamTest.setWramSub2M(lc);

        int subLsb = readSubRegOdd(MCD_MEM_MODE);
        writeSubRegOddByte(MCD_MEM_MODE, subLsb | PM1);

        Assertions.assertEquals(OVERWRITE, lc.asic.getStampPriorityMode());
        Assertions.assertTrue((readSubRegOdd(MCD_MEM_MODE) & PM1) != 0);

        writeMainRegOddByte(MCD_MEM_MODE, DMNA);
        Assertions.assertTrue((readSubRegOdd(MCD_MEM_MODE) & PM1) != 0);
        Assertions.assertEquals(OVERWRITE, lc.asic.getStampPriorityMode());

        int subLsb2 = readSubRegOdd(MCD_MEM_MODE);
        writeSubRegOddByte(MCD_MEM_MODE, subLsb2 | RET);
        Assertions.assertEquals(OVERWRITE, lc.asic.getStampPriorityMode());
    }

    @Test
    public void testSubWriteDoesNotClobberMainPrgRamBank() {
        McdWordRamTest.setWramSub2M(lc);

        int mainLsb = readMainRegOdd(MCD_MEM_MODE);
        writeMainRegOddByte(MCD_MEM_MODE, mainLsb | BK0 | BK1);
        int afterMain = readMainRegOdd(MCD_MEM_MODE);
        Assertions.assertEquals(BK0 | BK1, afterMain & (BK0 | BK1));

        writeSubRegOddByte(MCD_MEM_MODE, readSubRegOdd(MCD_MEM_MODE) | RET);

        Assertions.assertEquals(BK0 | BK1, readMainRegOdd(MCD_MEM_MODE) & (BK0 | BK1));
    }

}
