package mcd.util;

import omegadrive.sound.msumd.CueFileParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static omegadrive.sound.msumd.CueFileParser.PREGAP_LEN_LBA;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class MsfTest {

    @Test
    public void testMsf() {
        CueFileParser.MsfHolder msh = new CueFileParser.MsfHolder();
        for (int m = 0; m <= 60; m++) {
            for (int s = 0; s <= 60; s++) {
                for (int f = 0; f <= 75; f++) {
                    int lba = CueFileParser.msfToSectorAdjustPregap(m, s, f);
                    CueFileParser.lbaToMsfAdjustPregap(lba, msh);
                    int lba3 = CueFileParser.msfToSectorAdjustPregap(msh.minute / 10, msh.minute % 10,
                            msh.second / 10, msh.second % 10, msh.frame / 10, msh.frame % 10);
                    int lba2 = CueFileParser.msfToSectorAdjustPregap(msh.minute, msh.second, msh.frame);
                    Assertions.assertEquals(lba, lba2);
                    Assertions.assertEquals(lba3, lba2);
                }
            }
        }
    }

    @Test
    public void testPregapShift() {
        CueFileParser.MsfHolder msh = new CueFileParser.MsfHolder();
        int[] lba = {-5, 0, 50};
        for (int i = 0; i < lba.length; i++) {
            CueFileParser.lbaToMsfAdjustPregap(lba[i], msh);
            int lba1 = CueFileParser.msfToSector(msh.minute, msh.second, msh.frame);
            Assertions.assertEquals(lba[i], lba1 - PREGAP_LEN_LBA);

            int lba2 = CueFileParser.msfToSectorAdjustPregap(msh.minute, msh.second, msh.frame);
            Assertions.assertEquals(lba[i], lba2);
        }
    }

}
