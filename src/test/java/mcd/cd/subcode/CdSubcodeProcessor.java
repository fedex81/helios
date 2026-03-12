package mcd.cd.subcode;

import omegadrive.util.HexUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class CdSubcodeProcessor {
    public final static String basePath = "src/test/resources/subcodes/";

    private static final int Q_DATA_BYTES = 12;
    private static final int SECTOR_SIZE = 96;

    private static final int LEAD_OUT_TRACK_NO = 0xAA;


    public static void main(String[] args) throws IOException {
        qLogger(basePath + "rexrw_lio_fleet.sub");
    }

    public static void qLogger(String path) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(Files.readAllBytes(Path.of(path)));
        byte[] p = new byte[Q_DATA_BYTES];
        StringBuilder sb = new StringBuilder();
        StringBuilder all = new StringBuilder();
        for (int i = Q_DATA_BYTES; i < bb.capacity(); i+=SECTOR_SIZE) {
            bb.position(i);
            bb.get(p);
            if(p[0] == 0){
                continue;
            }
            HexUtil.fillFormattedString_ZeroBased(sb, p, false, false);
//            all.append(sb).append("\n");
            System.out.printf("%8x\t%s\n", i, sb);
            sb.setLength(0);
//            if(((int)p[1] & 0xFF) == LEAD_OUT_TRACK_NO){
//                System.out.print("");
//            }
        }
//        String out = path + ".q.log";
//        Files.write(Path.of(out), all.toString().getBytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
//        Util.sleep(5_000);
    }
}

