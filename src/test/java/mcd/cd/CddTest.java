package mcd.cd;

import mcd.McdRegTestBase;
import mcd.cdd.Cdd.CddCommand;
import mcd.cdd.Cdd.CddContext;
import mcd.cdd.Cdd.CddRequest;
import mcd.cdd.Cdd.CddStatus;
import mcd.cdd.ExtendedCueSheet;
import omegadrive.system.SysUtil;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static mcd.cd.CddTestHelper.*;
import static mcd.cdd.Cdd.CddCommand.SeekPause;
import static mcd.cdd.Cdd.CddCommand.SeekPlay;
import static mcd.cdd.Cdd.LBA_READAHEAD_LEN;
import static mcd.cdd.Cdd.PREGAP_LEN_LBA;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CddTest extends McdRegTestBase {
    private static ExtendedCueSheet extendedCueSheet;

    @BeforeAll
    public static void loadIso() {
        extendedCueSheet = new ExtendedCueSheet(Paths.get("src/test/resources/megacd/cdd", "test.iso"),
                SysUtil.RomFileType.ISO);
    }

    @Test
    public void testNoDisc() {
        cddRequest(lc, CddRequest.DiscTracks);
        lc.cdd.step(1);
        lc.cdd.step(1);
    }

    @Test
    public void testDiscCompletionTime() {
        insertDiscEnableHock(lc, extendedCueSheet);
        cddRequest(lc, CddRequest.DiscCompletionTime);
        lc.cdd.step(1);
        Assertions.assertEquals("93.000248.05", getStatusString());
    }

    @Test
    public void testTrackStartTime() {
        insertDiscEnableHock(lc, extendedCueSheet);
        int track = 1;
        //
        lc.cdd.write(MCD_CDD_COMM5, MCD_CDD_COMM5.addr, CddCommand.Request.ordinal(), Size.BYTE);
        lc.cdd.write(MCD_CDD_COMM6, MCD_CDD_COMM6.addr + 1, CddRequest.TrackStartTime.ordinal(), Size.BYTE);
        lc.cdd.write(MCD_CDD_COMM7, MCD_CDD_COMM7.addr, track / 10, Size.BYTE);
        lc.cdd.write(MCD_CDD_COMM7, MCD_CDD_COMM7.addr + 1, track % 10, Size.BYTE);
        setCommandChecksum(lc);
        //
        lc.cdd.step(1);
        Assertions.assertEquals("95.000280.16", getStatusString());
    }

    @Test
    public void testRelativeTime() {
        testSeekInternal(SeekPause, CddStatus.Paused, 145);
        clearCmdReg(lc);
        cddRequest(lc, CddRequest.RelativeTime);
        Assertions.assertEquals("41.000008.4e", getStatusString());
    }

    /**
     * 210: 20.000160.51 - 20.010000.0C <-
     * Status: Seeking
     * AbsoluteTime 00m01s60f, lba: 135
     * Command: Request RelativeTime, ExecCmd
     * <p>
     * 211: 21.000013.53 - 20.020000.0B <-
     * Status: Seeking
     * RelativeTime 00m00s13f, lba: 13
     * Command: Request TrackInformation, ExecCmd
     * <p>
     * 212: 22.010050.50 - 20.000000.0D <-
     * Status: Seeking
     * TrackInformation, track: 1
     * Command: Request AbsoluteTime, ExecCmd
     * <p>
     * 213: 20.000163.5E - 20.010000.0C <-
     * Status: Seeking
     * AbsoluteTime 00m01s63f, lba: 138
     * Command: Request RelativeTime, ExecCmd
     * <p>
     * 214: 21.000010.56 - 20.020000.0B <-
     * Status: Seeking
     * RelativeTime 00m00s10f, lba: 10
     * Command: Request TrackInformation, ExecCmd
     */
    @Test
    public void testRelativeTime2() {
        int[] expStat = {4, -1, 0, 0, 0, 0, 0, -1, 4, -1};
        for (int i = 135; i < 156; i++) {
            testSeekInternal(SeekPause, CddStatus.Paused, i);
            clearCmdReg(lc);
            cddRequest(lc, CddRequest.RelativeTime);
            expStat[1] = CddRequest.RelativeTime.ordinal();
            //when seeking the actual lba is decreased by 3
            //150-(i-3) frames away from the actual track start (2s of pregap)
            int relativeLba = Math.abs(PREGAP_LEN_LBA - (i - 3));
            String exp = setMsfGetTestString(relativeLba, expStat);
            Assertions.assertEquals(exp, getStatusString());

            cddRequest(lc, CddRequest.AbsoluteTime);
            expStat[1] = CddRequest.AbsoluteTime.ordinal();
            int absoluteLba = i - LBA_READAHEAD_LEN;
            exp = setMsfGetTestString(absoluteLba, expStat);
            Assertions.assertEquals(exp, getStatusString());
        }
    }

    @Test
    public void testAbsoluteTime() {
        testSeekInternal(SeekPause, CddStatus.Paused, 145);
        clearCmdReg(lc);
        cddRequest(lc, CddRequest.AbsoluteTime);
        Assertions.assertEquals("40.000167.49", getStatusString());
    }

    @Test
    public void testTrackInformation() {
        testSeekInternal(SeekPause, CddStatus.Paused, 145);
        clearCmdReg(lc);
        cddRequest(lc, CddRequest.TrackInformation);
        Assertions.assertEquals("42.010000.08", getStatusString());
    }

    @Test
    public void testSeekPlay() {
        testSeekInternal(SeekPlay, CddStatus.Playing, 151);
    }

    @Test
    public void testSeekPause() {
        testSeekInternal(SeekPause, CddStatus.Paused, 145);
    }

    private void testSeekInternal(CddCommand command, CddStatus expected, int lba) {
        insertDiscEnableHock(lc, extendedCueSheet);
        cddRequest(lc, command, lba);
        setCommandChecksum(lc);
        waitCddStatus(lc, expected);
    }

    private String getStatusString() {
        CddContext context = lc.cdd.getCddContext();
        return CddLogDecoder.toTestString(context.statusRegs);
    }

    /**
     * TODO
     * when a pause command is received by CDD from SUB-CPU while the disc is being read (cdd status = 0x1),
     * cdd status changes imediately to paused status (0x4) but the reported position (in other cdd status nibbles)
     * continues to increase for two sectors before it seeks back to a few sectors before the paused position ,
     * which means CDD continues to receive valid subcode data from CD-DSP for two sectors
     * before seeking really starts (indicated by report mode switching to 0xf), and by extension,
     * CDC probably also has time to receive the two next sectors.
     * while playing
     * <p>
     * status - command
     * 10.000203.45 - 60.000000.09 <-
     * 40.000204.50 - 20.010000.0C <-
     * 41.000005.50 - 20.020000.0B <-
     * 4F.000000.57 - 00.000000.0F
     */
    @Test
    public void test01() {
//        insertDiscEnableHock();
//        cddRequest(CddRequest.DiscTracks, 0);
//        setCommandChecksum();
//        waitCddStatus(expected);
    }

}
