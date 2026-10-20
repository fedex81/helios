package mcd.cd;

import mcd.McdRegTestBase;
import mcd.cdd.Cdd;
import mcd.cdd.ExtendedCueSheet;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.system.SysUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static mcd.cdd.Cdd.CddStatus.Playing;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class CddRealignSeekTest extends McdRegTestBase {

    static final String TEST_ISO = "src/test/resources/megacd/cdd/test.iso.gz";
    private static final int TARGET_LBA = 900;
    private ExtendedCueSheet cue;

    @BeforeEach
    public void insertDisc() {
        Path p = Paths.get(TEST_ISO);
        cue = new ExtendedCueSheet(p, SysUtil.RomFileType.ISO);
        CddTestHelper.insertDiscEnableHock(lc, cue);
    }

    @Test
    public void testColdSeekAppliesReadAhead() {
        Cdd.CddContext ctx = lc.cdd.getCddContext();
        Assertions.assertNotEquals(Playing, ctx.io.status);

        seekPlayTo(TARGET_LBA);

        Assertions.assertEquals(TARGET_LBA - Cdd.LBA_READAHEAD_LEN, ctx.io.sector);
    }

    //segacd-fmv
    @Test
    public void testRealigneSeekWhilePlayingLandsExact() {
        Cdd.CddContext ctx = lc.cdd.getCddContext();
        ctx.io.status = Playing;

        seekPlayTo(TARGET_LBA);

        Assertions.assertEquals(TARGET_LBA, ctx.io.sector);
    }


    private void seekPlayTo(int lba) {
        CueFileParser.lbaToMsfAdjustPregap(lba, CddTestHelper.msfHolder);
        CddTestHelper.cddRequest(lc, Cdd.CddCommand.SeekPlay);
    }

}
