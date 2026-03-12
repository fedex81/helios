package mcd.cd;

import mcd.McdRegTestBase;
import mcd.cdd.Cdd.CddCommand;
import mcd.cdd.Cdd.CddContext;
import mcd.cdd.Cdd.CddRequest;
import mcd.cdd.Cdd.CddStatus;
import mcd.cdd.ExtendedCueSheet;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.system.SysUtil;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static mcd.cd.CddTestHelper.*;
import static mcd.cdd.Cdd.CddCommand.SeekPause;
import static mcd.cdd.Cdd.CddCommand.SeekPlay;
import static mcd.cdd.Cdd.LBA_READAHEAD_LEN;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CddTest extends McdRegTestBase {
    private static ExtendedCueSheet extendedCueSheet;

    /**
     * I don't think the drive can seek in the lead-in.
     * Lead-in state would be seen only on bootstrap.
     */
    public static final int LBA_LEADIN_POINT02 = -350;

    public static final int LBA_LEADIN_END = -151;

    //track 1, Index 00, start of the program area, start of pregap
    public static final int LBA_START_PROGRAM = -150;

    public static final int LBA_PROGRAM_PREGAP_POINT01 = -149;

    public static final int LBA_PROGRAM_PREGAP_POINT02 = -5;
    public static final int LBA_START_TRACK1_PREGAP_END = -1;
    //track 1, Index 01, start of Track1, end of pregap
    public static final int LBA_START_TRACK1_INDEX01 = 0;

    public static final int LBA_START_TRACK1_INDEX01_POINT01 = 1;
    public static final int LBA_START_TRACK1_INDEX01_POINT02 = 45;
    public static final int LBA_START_TRACK1_INDEX01_POINT03 = 345;

    private static final String resFolder = "src/test/resources/megacd/cdd";
    private static final String resName = "test.iso.gz";

    @BeforeAll
    public static void loadIso() {
        Path p = Paths.get(resFolder, resName);
        extendedCueSheet = new ExtendedCueSheet(p, SysUtil.RomFileType.ISO);
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
        //now in lead-in
        checkLeadIn();

        clearCmdReg(lc);
        cddRequest(lc, CddRequest.DiscCompletionTime);
        lc.cdd.step(1);
        Assertions.assertEquals("93.001062.55", getStatusString());
    }

    private void checkLeadIn() {
        //now in lead-in
        clearCmdReg(lc);
        cddRequest(lc, CddRequest.TrackInformation);
        Assertions.assertEquals("92.000000.5f", getStatusString());

        clearCmdReg(lc);
        cddRequest(lc, CddRequest.AbsoluteTime);
        Assertions.assertEquals("90.995800.52", getStatusString());

        clearCmdReg(lc);
        cddRequest(lc, CddRequest.RelativeTime);
        Assertions.assertEquals("9f.995800.53", getStatusString());
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

    /**
     * CD audio contains gaps between tracks called “pregaps”; they’re defined as index 0 within a track,
     * with the track itself beginning at index 1.
     * They throw an interesting edge case for calculating relative timestamps.
     * What does it mean to track the timestamp relative to the start of the track for a time that
     * isn’t part of the track? Since this binary-coded digital format doesn’t support negative numbers,
     * the standard uses a slightly strange but appropriate workaround.
     * Within the pregaps, the relative timestamp instead starts at the length of the pregap and
     * then counts down until it hits 0, which marks the beginning of the track, at which point it
     * begins counting up again. Needless to say, this was the source of a few fun off-by-one bugs.
     */
    @Test
    public void testRelativeTime() {
        Map<Integer, String> m = Map.of(
                LBA_LEADIN_POINT02, "41.995525.52",
                LBA_LEADIN_END, "41.995774.5c",
                LBA_START_PROGRAM, "41.000200.53",
                LBA_PROGRAM_PREGAP_POINT01, "41.000174.59",
                LBA_PROGRAM_PREGAP_POINT02, "41.000005.50",
                LBA_START_TRACK1_PREGAP_END, "41.000001.54",
                LBA_START_TRACK1_INDEX01, "41.000000.55",
                LBA_START_TRACK1_INDEX01_POINT01, "41.000001.54",
                LBA_START_TRACK1_INDEX01_POINT02, "41.000045.5c",
                LBA_START_TRACK1_INDEX01_POINT03, "41.000445.58"
        );

        for (var e : m.entrySet()) {
            CueFileParser.lbaToMsfAdjustPregap(e.getKey(), msfHolder);
            System.out.println(msfHolder);
            testSeekInternal(SeekPause, CddStatus.Paused);
            clearCmdReg(lc);
            cddRequest(lc, CddRequest.RelativeTime);
            Assertions.assertEquals(e.getValue(), getStatusString(), "LBA: " + e.getKey());
        }
    }

    @Test
    public void testAbsoluteTime() {
        Map<Integer, String> m = Map.of(
                LBA_LEADIN_POINT02, "40.995725.51",
                LBA_LEADIN_END, "40.995974.5b",
                LBA_START_PROGRAM, "40.000000.56",
                LBA_PROGRAM_PREGAP_POINT01, "40.000001.55",
                LBA_PROGRAM_PREGAP_POINT02, "40.000170.5e",
                LBA_START_TRACK1_PREGAP_END, "40.000174.5a",
                LBA_START_TRACK1_INDEX01, "40.000200.54",
                LBA_START_TRACK1_INDEX01_POINT01, "40.000201.53",
                LBA_START_TRACK1_INDEX01_POINT02, "40.000245.5b",
                LBA_START_TRACK1_INDEX01_POINT03, "40.000645.57"
        );

        for (var e : m.entrySet()) {
            CueFileParser.lbaToMsfAdjustPregap(e.getKey(), CddTestHelper.msfHolder);
            System.out.println(e.getKey() + "->" + msfHolder);
            testSeekInternal(SeekPause, CddStatus.Paused);
            clearCmdReg(lc);
            cddRequest(lc, CddRequest.AbsoluteTime);
            Assertions.assertEquals(e.getValue(), getStatusString(), "LBA: " + e.getKey());
        }
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
        int[] expStat = {4, -1, 0, 0, 0, 0, 0, -1, 5, -1};
        for (int i = 35; i < 46; i++) {
            System.out.println(i);
            System.out.println(getStatusString());
            CueFileParser.lbaToMsfAdjustPregap(i, CddTestHelper.msfHolder);
            testSeekInternal(SeekPause, CddStatus.Paused);
            clearCmdReg(lc);
            cddRequest(lc, CddRequest.RelativeTime);
            expStat[1] = CddRequest.RelativeTime.ordinal();
            //when seeking the actual lba is decreased by 3
            //150-(i-3) frames away from the actual track start (2s of pregap)
            //TODO can sw see other sectors while seek pause "gets" there??
            int relativeLba = (i - LBA_READAHEAD_LEN);
            String exp = setMsfGetTestString(relativeLba + LBA_READAHEAD_LEN, expStat);
            Assertions.assertEquals(exp, getStatusString());

            cddRequest(lc, CddRequest.AbsoluteTime);
            expStat[1] = CddRequest.AbsoluteTime.ordinal();
            int absoluteLba = i - LBA_READAHEAD_LEN + CueFileParser.PREGAP_LEN_LBA;
            exp = setMsfGetTestString(absoluteLba + LBA_READAHEAD_LEN, expStat);
            Assertions.assertEquals(exp, getStatusString());
        }
    }



    @Test
    public void testTrackInformation() {
        Map<Integer, String> m = Map.of(
                //unable to seek to lead-in MSFs
//                LBA_LEADIN_POINT02, "42.000000.53",
//                LBA_LEADIN_END, "42.000000.53",
                LBA_START_PROGRAM, "42.010000.53",
                LBA_PROGRAM_PREGAP_POINT01, "42.010000.53",
                LBA_PROGRAM_PREGAP_POINT02, "42.010000.53",
                LBA_START_TRACK1_PREGAP_END, "42.010000.53",
                LBA_START_TRACK1_INDEX01, "42.010000.53",
                LBA_START_TRACK1_INDEX01_POINT01, "42.010000.53",
                LBA_START_TRACK1_INDEX01_POINT02, "42.010000.53",
                LBA_START_TRACK1_INDEX01_POINT03, "42.010000.53"
        );

        for (var e : m.entrySet()) {
            CueFileParser.lbaToMsfAdjustPregap(e.getKey(), CddTestHelper.msfHolder);
            System.out.println(e.getKey() + "->" + msfHolder);
            testSeekInternal(SeekPause, CddStatus.Paused);
            clearCmdReg(lc);
            cddRequest(lc, CddRequest.TrackInformation);
            Assertions.assertEquals(e.getValue(), getStatusString(), "LBA: " + e.getKey());
        }
    }

    @Test
    public void testSeekPlay() {
        int[] lbas = {LBA_START_PROGRAM, -5, -1, LBA_START_TRACK1_INDEX01, 45};
        for (var lba : lbas) {
            CueFileParser.lbaToMsfAdjustPregap(lba, CddTestHelper.msfHolder);
            System.out.println(lba + "->" + msfHolder);
            testSeekInternal(SeekPlay, CddStatus.Playing);
            clearCmdReg(lc);
        }
    }

    @Test
    public void testSeekPause() {
        int[] lbas = {LBA_START_PROGRAM, -5, -1, LBA_START_TRACK1_INDEX01, 45};
        for (var lba : lbas) {
            CueFileParser.lbaToMsfAdjustPregap(lba, CddTestHelper.msfHolder);
            System.out.println(lba + "->" + msfHolder);
            testSeekInternal(SeekPause, CddStatus.Paused);
            clearCmdReg(lc);
        }
    }

    private void testSeekInternal(CddCommand command, CddStatus expected) {
        insertDiscEnableHock(lc, extendedCueSheet);
        cddRequest(lc, command);
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
