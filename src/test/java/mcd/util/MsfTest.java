package mcd.util;

import omegadrive.sound.msumd.CueFileParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
                    int lba = CueFileParser.toSector(m, s, f);
                    CueFileParser.toMSF(lba, msh);
                    int lba3 = CueFileParser.toSector(msh.minute / 10, msh.minute % 10,
                            msh.second / 10, msh.second % 10, msh.frame / 10, msh.frame % 10);
                    int lba2 = CueFileParser.toSector(msh.minute, msh.second, msh.frame);
                    Assertions.assertEquals(lba, lba2);
                    Assertions.assertEquals(lba3, lba2);
                }
            }
        }
    }

}
