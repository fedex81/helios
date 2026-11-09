package mcd.cd;

import mcd.McdRegTestBase;
import mcd.cdd.Cdd;
import mcd.cdd.Cdd.CddCommand;
import mcd.cdd.ExtendedCueSheet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static mcd.cd.CddTestHelper.*;
import static mcd.cdd.Cdd.CddStatus.LeadOut;
import static mcd.cdd.Cdd.CddStatus.Playing;
import static omegadrive.sound.msumd.CueFileParser.lbaToMsfAdjustPregap;
import static omegadrive.system.SysUtil.*;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class CddFormatScanTest extends McdRegTestBase {

    private static final Path FORMAT_ROOT = Paths.get("src/test/resources/megacd/cdd/format");

    static Stream<Path> discImages() throws IOException {
        try (Stream<Path> dirs = Files.list(FORMAT_ROOT)) {
            return dirs.filter(Files::isDirectory)
                    .map(CddFormatScanTest::discImageIn)
                    .filter(Objects::nonNull).sorted().toList().stream();
        }
    }

    private static Path discImageIn(Path p1) {
        try (Stream<Path> files = Files.list(p1)) {
            List<Path> all = files.toList();
            return all.stream().filter(p -> p.toString().endsWith(CUE_EXT)).findFirst()
                    .or(() -> all.stream().filter(p -> p.toString().endsWith(ISO_EXT) || p.toString().endsWith(ISO_GZ_EXT)).findFirst())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @MethodSource("discImages")
    @ParameterizedTest(name = "{0}")
    public void testImage(Path discImage) {
        RomFileType rft = discImage.toString().toLowerCase().endsWith(CUE_EXT) ? RomFileType.BIN_CUE :
                RomFileType.ISO;
        ExtendedCueSheet cue = new ExtendedCueSheet(discImage, rft);

        insertDiscEnableHock(lc, cue);
        Cdd.CddContext ctx = lc.cdd.getCddContext();
        Assertions.assertTrue(ctx.io.sector < 0);
        Assertions.assertNotEquals(LeadOut, ctx.io.status);

        lbaToMsfAdjustPregap(0, CddTestHelper.msfHolder);
        cddRequest(lc, CddCommand.SeekPlay);
        waitCddStatus(lc, Playing);

        final int end = cue.sectorEnd;
        long guard = 0;
        long guardMax = (end + 300) * 1000;
        int prevMsf = -1;

        while (ctx.io.status != LeadOut && guard++ < guardMax) {
            Assertions.assertEquals(Playing, ctx.io.status);
            String tts = CddLogDecoder.toTestString(ctx.statusRegs);
            Assertions.assertEquals(Cdd.getCddChecksum(ctx.statusRegs), ctx.statusRegs[Cdd.CDD_CHECKSUM_BYTE]);
            int msf = Integer.parseInt(tts.substring(3, 9)); //MMSSFF
            Assertions.assertTrue(msf >= prevMsf);
            prevMsf = msf;

            int before = ctx.io.sector;
            lc.cdd.step(1);
            if (ctx.io.sector == before && ctx.io.status == Playing) {
                lc.cdd.stepCdda();
            }
        }
        Assertions.assertEquals(LeadOut, ctx.io.status);
        Assertions.assertTrue(ctx.io.sector >= end);
    }
}
