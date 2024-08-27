package mcd.cd;

import mcd.cdd.Cdd;
import mcd.cdd.Cdd.CddCommand;
import mcd.cdd.Cdd.CddRequest;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static mcd.cdd.Cdd.CDD_REG_NUM;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CddLogDecoder {

    public static String basePath = "src/test/resources/megacd/cdd/cd-log";

    public static String subPath =
//            "BI-mcd-tst";
            "sonic-bios";

    public static String fileName =
//            "boot-log-cdd_stat_sonicU_02.txt";
            "boot-log.txt";

    public List<String> lines;

    /**
     * 120: 45.5351F1.53 - 20.053500.00 <-
     * 285: 95.535373.52 - 40.000170.03 <-
     */

    @Test
    public void test01() {
        Path p = Paths.get(basePath, subPath, fileName);
//        Path p = Paths.get(basePath, "cdd_stat_sonicU_01.txt");
        lines = FileUtil.readFileContent(p);

        int cnt = 0;
        for (String line : lines) {
            if (line.startsWith("//")) {
                continue;
            }
            //skip first record
            if (cnt == 0) {
                cnt++;
                continue;
            }
            String[] tkn = line.split(" -");
            int[] status = parseStatCom(tkn[0].trim());
            int[] command = parseStatCom(tkn[1].trim());
            boolean sendCommand = line.contains("<-");
            StringBuilder sb = new StringBuilder("\n" + cnt + ": " + line + "\n");
            sb.append("Status: " + Cdd.statusVals[status[0]]);
            if (status[1] == 0xF) {
                sb.append(" (Busy)");
            } else {
                CddRequest req = Cdd.requestVals[status[1]];
                sb.append("\n\t" + req);
                decodeCommandResultStatus(sb, req, status);
            }
            if (sendCommand) {
                decodeCommand(sb, command);
                sb.append(", ExecCmd");
            }
            System.out.println(sb);
            cnt++;
        }
    }

    private void decodeCommandResultStatus(StringBuilder sb, CddRequest request, int[] status) {
        int rs6 = status[6];

        switch (request) {
            case DiscTracks -> {
                int firstTrack = (status[2] * 10) + status[3];
                int lastTrack = (status[4] * 10) + status[5];
                sb.append(" firstTrack: " + firstTrack + ", lastTrack: " + lastTrack);
            }
            case DiscCompletionTime, RelativeTime, AbsoluteTime -> {
                decodeMsf(sb, status);
            }
            case TrackStartTime -> {
                int[] st = status.clone();
                st[6] &= ~8;
                decodeMsf(sb, st);
                sb.append(", trackType: " + ((status[6] & 8) == 0 ? "Audio" : "Data"));
            }
            case TrackInformation -> {
                int track = (status[2] * 10) + status[3];
                sb.append(", track: " + track);
            }
        }
    }

    private void decodeMsf(StringBuilder sb, int[] status) {
        int lba = CueFileParser.toSector(status[2], status[3], status[4], status[5], status[6], status[7]);
        String str = "" + status[2] + status[3] + "m" + status[4] + status[5] +
                "s" + status[6] + status[7] + "f";
        sb.append(" " + str + ", lba: " + lba);
    }

    private void decodeCommand(StringBuilder sb, int[] command) {
        CddCommand cmd = Cdd.commandVals[command[0]];
        sb.append("\nCommand: " + cmd);
        if (cmd == CddCommand.Request) {
            CddRequest request = Cdd.requestVals[command[3]];
            sb.append(" " + request);
            switch (request) {
                case TrackStartTime -> {
                    int track = command[4] * 10 + command[5];
                    sb.append(" Track 0x" + th(track));
                }
            }
        }
    }

    private int[] parseStatCom(String st) {
        String noDots = st.replace(".", "").trim();
        noDots = noDots.replace("<-", "").trim();
        int[] status = {
                fromAsciiChar(noDots.charAt(0)), fromAsciiChar(noDots.charAt(1)),
                fromAsciiChar(noDots.charAt(2)), fromAsciiChar(noDots.charAt(3)),
                fromAsciiChar(noDots.charAt(4)), fromAsciiChar(noDots.charAt(5)),
                fromAsciiChar(noDots.charAt(6)), fromAsciiChar(noDots.charAt(7)),
                fromAsciiChar(noDots.charAt(8)), fromAsciiChar(noDots.charAt(9))
        };
        String act = "";
        for (int i = 0; i < status.length; i++) {
            act += th(status[i]);
        }
        Assertions.assertEquals(noDots, act.toUpperCase());
        verifyChecksum(status);
        return status;
    }

    private static int fromAsciiChar(char val) {
        int r = val >= 48 && val < 58 ? val - 48 : -1;
        r = r == -1 && val >= 65 && val < 71 ? val - 65 + 10 : r;
        assert r != -1;
        return r;
    }

    private void verifyChecksum(int[] data) {
        int checksum = 0;
        for (int i = 0; i < CDD_REG_NUM - 1; i++) {
            checksum += data[i];
        }
        checksum = ~checksum;
        Assertions.assertEquals(data[9], checksum & 0xF, Arrays.toString(data));
    }

    public static String toTestString(int[] vals) {
        assert vals.length == CDD_REG_NUM;
        String str = "";
        for (int i = 0; i < vals.length; i++) {
            if (i == 2 || i == 8) {
                str += ".";
            }
            str += th(vals[i]);
        }
        return str;
    }
}
