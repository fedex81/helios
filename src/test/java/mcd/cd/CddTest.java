package mcd.cd;

import mcd.McdRegTestBase;
import mcd.cdd.Cdd;
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
import static mcd.cdd.Cdd.CddCommand.*;
import static mcd.cdd.Cdd.CddStatus.Paused;
import static mcd.cdd.Cdd.CddStatus.Playing;
import static mcd.cdd.Cdd.LBA_READAHEAD_LEN;
import static mcd.cdd.Cdd.LEADOUTNUM;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 *
 *
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

        request(CddRequest.DiscCompletionTime);
        lc.cdd.step(1);
        assertEquals("03.000862.1b", getStatusString());
    }

    private void checkLeadIn() {
        //now in lead-in
        request(CddRequest.TrackInformation);
        assertEquals("02.000000.1c", getStatusString());

        request(CddRequest.AbsoluteTime);
        assertEquals("00.995800.1f", getStatusString());

        request(CddRequest.RelativeTime);
        assertEquals("0f.995800.10", getStatusString());
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
        assertEquals("05.000280.1f", getStatusString());
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
            testSeekInternal(SeekPause, Paused);
            request(CddRequest.RelativeTime);
            assertEquals(e.getValue(), getStatusString(), "LBA: " + e.getKey());
        }
    }

    @Test
    public void testAbsoluteTimeSimple() {
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
            testSeekInternal(SeekPause, Paused);
            request(CddRequest.AbsoluteTime);
            assertEquals(e.getValue(), getStatusString(), "LBA: " + e.getKey());
        }
    }

    @Test
    public void testAbsoluteTime() {
        for (int target : new int[]{45, 345}) {
            CueFileParser.lbaToMsfAdjustPregap(target, msfHolder);
            testSeekInternal(SeekPlay, Playing);
            CddContext ctx = ctx();

            //landed LBA_READAHEAD_LEN sectors early
            assertEquals(target - LBA_READAHEAD_LEN, ctx.io.sector);

            for (int s = 0; s < LBA_READAHEAD_LEN; s++) {
                lc.cdd.step(1);
            }
            assertEquals(target, ctx.io.sector);

            request(CddRequest.AbsoluteTime);

            CueFileParser.lbaToMsfAdjustPregap(target, msfHolder);
            String exp = CddLogDecoder.toTestString(new int[]{0, 0,
                    msfHolder.minute / 10, msfHolder.minute % 10, msfHolder.second / 10,
                    msfHolder.second % 10, msfHolder.frame / 10, msfHolder.frame % 10, 0, 0});
            assertEquals(exp.substring(3, 9), getStatusString().substring(3, 9));
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
//    @Disabled
    @Test
    public void testRelativeTime2() {
        //indexed by i-35, i in [35.46); SeekPause lands exactly on the requested sector
        String[] expRel = {
                "41.000035.5d", "41.000036.5c", "41.000037.5b", "41.000038.5a", "41.000039.59",
                "41.000040.51", "41.000041.50", "41.000042.5f", "41.000043.5e", "41.000044.5d",
                "41.000045.5c"
        };
        String[] expAbs = {
                "40.000235.5c", "40.000236.5b", "40.000237.5a", "40.000238.59", "40.000239.58",
                "40.000240.50", "40.000241.5f", "40.000242.5e", "40.000243.5d", "40.000244.5c",
                "40.000245.5b"
        };
        int sectStart = 35, sectEnd = 46;

        for (int i = sectStart; i < sectEnd; i++) {
            CueFileParser.lbaToMsfAdjustPregap(i, CddTestHelper.msfHolder);
            testSeekInternal(SeekPause, Paused);
            request(CddRequest.RelativeTime);
            assertEquals(expRel[i - sectStart], getStatusString());

            request(CddRequest.AbsoluteTime);
            assertEquals(expAbs[i - sectStart], getStatusString());
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
            testSeekInternal(SeekPause, Paused);
            request(CddRequest.TrackInformation);
            assertEquals(e.getValue(), getStatusString(), "LBA: " + e.getKey());
        }
    }

    @Test
    public void testSeekPlay() {
        int[] lbas = {LBA_START_PROGRAM, -5, -1, LBA_START_TRACK1_INDEX01, 45};
        for (var lba : lbas) {
            CueFileParser.lbaToMsfAdjustPregap(lba, CddTestHelper.msfHolder);
            testSeekInternal(SeekPlay, CddStatus.Playing);
            clearCmdReg(lc);
        }
    }

    @Test
    public void testSeekPause() {
        int[] lbas = {LBA_START_PROGRAM, -5, -1, LBA_START_TRACK1_INDEX01, 45};
        for (var lba : lbas) {
            CueFileParser.lbaToMsfAdjustPregap(lba, CddTestHelper.msfHolder);
            testSeekInternal(SeekPause, Paused);
            clearCmdReg(lc);
        }
    }

    @Test
    public void testSeekPlayAdvancesToTarget() {
        int target = 45;
        CueFileParser.lbaToMsfAdjustPregap(target, msfHolder);
        testSeekInternal(SeekPlay, CddStatus.Playing);
        CddContext ctx = lc.cdd.getCddContext();

        assertEquals(target - LBA_READAHEAD_LEN, ctx.io.sector);

        for (int s = 0; s < LBA_READAHEAD_LEN; s++) {
            lc.cdd.step(1);
        }

        assertEquals(target, ctx.io.sector);
        assertEquals(CddStatus.Playing, ctx.io.status);

        lc.cdd.step(1);

        //keeps advancing while playing
        assertEquals(target + 1, ctx.io.sector);
    }

    @Test
    public void testSeekPauseFreezesPosition() {
        int target = 45;
        CueFileParser.lbaToMsfAdjustPregap(target, msfHolder);
        testSeekInternal(SeekPause, Paused);
        CddContext ctx = lc.cdd.getCddContext();

        assertEquals(target, ctx.io.sector);

        for (int s = 0; s < 8; s++) {
            lc.cdd.step(1);
            assertEquals(target, ctx.io.sector);
            assertEquals(Paused, ctx.io.status);
        }
    }

    @Test
    public void testBiosSeekPauseSettlesOnTarget() {
        int target = 45;
        //BIOS: issue SeekPause, poll until settles
        CddContext ctx = insertAndPauseAt(target);

        request(CddRequest.AbsoluteTime);
        assertEquals("40.000245.5b", getStatusString());
        assertEquals(target, ctx.io.sector);
    }

    @Test
    public void testBiosSeekPlayReadsFromTarget() {
        int target = 45;
        CddContext ctx = insertAndPlayTo(target);
        assertEquals(target, ctx.io.sector);
        assertEquals(CddStatus.Playing, ctx.io.status);
    }

    @Test
    public void testBiosPauseResumeWhilePlaying() {
        int target = 45;
        CddContext ctx = insertAndPlayTo(target);

        //BIOS pause: freeze at the current sector
        command(Pause);
        waitCddStatus(lc, Paused);

        int frozen = ctx.io.sector;
        for (int i = 0; i < 5; i++) {
            lc.cdd.step(1);
            assertEquals(frozen, ctx.io.sector);
        }

        //BIOS resume (Play): drive advances, from the frozen sector
        command(Play);
        waitCddStatus(lc, CddStatus.Playing); //this already steps
        int afterResume = ctx.io.sector;
        Assertions.assertTrue(afterResume > frozen);
        lc.cdd.step(1);
        assertEquals(afterResume + 1, ctx.io.sector);
    }


    @Test
    public void testBiosStop() {
        int target = 45;
        //start playing and reach the target
        CddContext ctx = insertAndPlayTo(target);
        command(Stop);
        //RS0 reports Stopped immediately
        assertEquals(CddStatus.Stopped.ordinal(), ctx.statusRegs[0]);
        //internal drive state is ReadingTOC, drive no longer advances
        assertEquals(CddStatus.ReadingTOC, ctx.io.status);
        int frozen = ctx.io.sector;
        for (int i = 0; i < 5; i++) {
            lc.cdd.step(1);
            assertEquals(frozen, ctx.io.sector);
        }
    }

    @Test
    public void testBiosForwardReverseScan() {
        int target = 200;
        //start playing and reach the target
        CddContext ctx = insertAndPlayTo(target);
        //Forward scan: position jumps forward by CD_SCAN_SPEED per step
        command(Forward);
        waitCddStatus(lc, CddStatus.Scanning);
        int before = ctx.io.sector;
        lc.cdd.step(1);
        assertEquals(before + Cdd.CD_SCAN_SPEED, ctx.io.sector);

        //Reverse scan
        command(Reverse);
        waitCddStatus(lc, CddStatus.Scanning);
        before = ctx.io.sector;
        lc.cdd.step(1);
        assertEquals(before - Cdd.CD_SCAN_SPEED, ctx.io.sector);
    }

    /**
     * TrackSkip moves the laser and reports Paused; it does not change the reported position.
     */
    @Test
    public void testBiosTrackSkip() {
        int target = 45;
        CddContext ctx = insertAndPauseAt(target);
        int before = ctx.io.sector;

        command(TrackSkip);
        assertEquals(Paused, ctx.io.status);
        assertEquals(before, ctx.io.sector);
    }

    @Test
    public void testBiosDoorOpenClose() {
        insertDiscEnableHock(lc, extendedCueSheet);
        CddContext ctx = lc.cdd.getCddContext();

        command(DoorOpen);
        assertEquals(CddStatus.DoorOpened.ordinal(), ctx.statusRegs[0]);
        assertEquals(CddStatus.DoorOpened, ctx.io.status);

        command(DoorClose);
        assertEquals(CddStatus.Stopped.ordinal(), ctx.statusRegs[0]);
        assertEquals(CddStatus.ReadingTOC, ctx.io.status);
    }

    @Test
    public void testBiosDriveStatus() {
        int target = 45;
        CddContext ctx = insertAndPauseAt(target);
        int before = ctx.io.sector;

        command(DriveStatus);
        lc.cdd.step(1);
        assertEquals(Paused.ordinal(), ctx.statusRegs[0]);
        assertEquals(before, ctx.io.sector);
    }

    @Test
    public void testFader() {
        CddContext ctx = lc.cdd.getCddContext();
        assertEquals(Cdd.FADER_MAX, ctx.faderVol);
        assertEquals(Cdd.FADER_MAX, ctx.faderTarget);

        lc.cdd.write(MCD_CD_FADER, MCD_CD_FADER.addr, 0x4000, Size.WORD); // >> 4 = 0x400 full
        assertEquals(0x400, ctx.faderTarget);
        lc.cdd.write(MCD_CD_FADER, MCD_CD_FADER.addr, 0, Size.WORD); // fade to mute
        assertEquals(0, ctx.faderTarget);
        lc.cdd.write(MCD_CD_FADER, MCD_CD_FADER.addr, 0x2000, Size.WORD); // fade to mute
        assertEquals(0x200, ctx.faderTarget);

        //per-sample fade stepping
        ctx.faderVol = 0x200;
        ctx.faderTarget = 0x204;

        for (int i = 0; i < 4; i++) {
            lc.cdd.stepCdda();
        }

        assertEquals(ctx.faderTarget, ctx.faderVol);
        ctx.faderTarget = 0x200;
        for (int i = 0; i < 4; i++) {
            lc.cdd.stepCdda();
        }
        assertEquals(ctx.faderTarget, ctx.faderVol);

        //at target, no further movement
        lc.cdd.stepCdda();
        assertEquals(ctx.faderTarget, ctx.faderVol);

    }

    @Test
    public void testRelativeTimeLeadOut() {
        insertDiscEnableHock(lc, extendedCueSheet);
        CddContext ctx = lc.cdd.getCddContext();

        //place the drive in the lead-out, after the lead-out start
        int offset = 100;
        ctx.io.track = LEADOUTNUM;
        ctx.io.sector = extendedCueSheet.sectorEnd + offset;

        clearCmdReg(lc);
        cddRequest(lc, CddRequest.RelativeTime);

        //relative time must count up from the lead-out start (end of last track)
        int relLba = relativeLbaFromStatus(ctx);
        assertEquals(offset, relLba);
        Assertions.assertNotEquals(extendedCueSheet.sectorEnd + offset, relLba);
    }



    /**
     * TODO not implemented
     *
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
    public void testPauseWhileReadingFreezesImmediately() {
        int target = 203;
        CddContext ctx = insertAndPlayTo(target);

        //Pause while reading: status -> Paused(0x4) immediately, position frozen at the current
        //sector (no hw 2-sector overshoot implemented)
        command(Pause);
        assertEquals(Paused.ordinal(), ctx.statusRegs[0]);
        assertEquals(Paused, ctx.io.status);
        assertEquals(target, ctx.io.sector);

        for (int i = 0; i < 4; i++) {
            lc.cdd.step(1);
            assertEquals(target, ctx.io.sector);
            assertEquals(Paused, ctx.io.status);
        }

    }

    /**
     * HELPERS
     */

    private void testSeekInternal(CddCommand command, CddStatus expected) {
        insertDiscEnableHock(lc, extendedCueSheet);
        cddRequest(lc, command);
        waitCddStatus(lc, expected);
    }

    private String getStatusString() {
        CddContext context = lc.cdd.getCddContext();
        return CddLogDecoder.toTestString(context.statusRegs);
    }

    private int relativeLbaFromStatus(CddContext ctx) {
        int[] s = ctx.statusRegs;
        return CueFileParser.msfToSector(s[2] * 10 + s[3], s[4] * 10 + s[5], s[6] * 10 + s[7]);
    }

    private CddContext ctx() {
        return lc.cdd.getCddContext();
    }

    private void seekTo(CddCommand c, int targetLba) {
        CueFileParser.lbaToMsfAdjustPregap(targetLba, msfHolder);
        cddRequest(lc, c);
    }

    private CddContext insertAndPlayTo(int targetLba) {
        insertDiscEnableHock(lc, extendedCueSheet);
        seekTo(SeekPlay, targetLba);
        waitCddStatus(lc, CddStatus.Playing);
        playForwardTo(targetLba);
        return ctx();
    }

    private CddContext insertAndPauseAt(int targetLba) {
        insertDiscEnableHock(lc, extendedCueSheet);
        seekTo(SeekPause, targetLba);
        waitCddStatus(lc, Paused);
        return ctx();
    }

    private void playForwardTo(int targetLba) {
        int guard = 0;
        while (ctx().io.sector < targetLba && guard++ < 1000) {
            lc.cdd.step(1);
        }
        assertEquals(targetLba, ctx().io.sector);
    }

    private void command(CddCommand command) {
        clearCmdReg(lc);
        cddRequest(lc, command);
    }

    private void request(CddRequest request) {
        clearCmdReg(lc);
        cddRequest(lc, request);
    }

}
