package omegadrive.sound.msumd;

import omegadrive.util.LogHelper;
import org.digitalmediaserver.cuelib.CueParser;
import org.digitalmediaserver.cuelib.CueSheet;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.function.Function;

public class CueFileParser {
    private static final Logger LOG = LogHelper.getLogger(CueFileParser.class.getSimpleName());
    public static final int MAX_TRACKS = 100; //0-99

    public static final int PREGAP_LEN_LBA = 150;
    static final int SECTOR_SIZE_BYTES = 2352;

    public static final Function<Integer, Integer> toBcdByte = n -> {
        assert n >= 0 && n < 100;
        return (((n / 10) << 4) | (n % 10));
    };

    static final NumberFormat msfFormat;

    static {
        msfFormat = NumberFormat.getInstance();
        msfFormat.setMinimumIntegerDigits(2);
        msfFormat.setMaximumFractionDigits(0);
    }

    public static class MsfHolder {
        public int minute, second, frame;

        @Override
        public String toString() {
            return msfFormat.format(minute) + ":" + msfFormat.format(second) + ":" + msfFormat.format(frame);
        }
    }

    public static CueSheet parse(Path cueFilePath) {
        CueSheet cueSheet = null;
        try {
            //NOTE required by msu-md
            cueSheet = CueParser.parse(cueFilePath, Charset.defaultCharset());
        } catch (IOException e) {
            LOG.warn("Unable to open BIN/CUE file: {}, {}", cueFilePath, e.getMessage());
        }
        return cueSheet;
    }

    public static CueSheet parse(InputStream is) {
        CueSheet cueSheet = null;
        try {
            cueSheet = CueParser.parse(is, Charset.defaultCharset());
        } catch (IOException e) {
            LOG.warn("Unable to open BIN/CUE stream: {}", e.getMessage());
        }
        return cueSheet;
    }

    public static int msfToSector(int m, int s, int f) {
        assert s <= 60 && f <= 75 && m <= 99;
        return (m * 60 + s) * 75 + f;
    }

    public static int msfToSectorAdjustPregap(int m, int s, int f) {
        return msfToSector(m, s, f) - PREGAP_LEN_LBA;
    }

    public static int msfToSectorAdjustPregap(int mH, int mL, int sH, int sL, int fH, int fL) {
        return msfToSectorAdjustPregap(mH * 10 + mL, sH * 10 + sL, fH * 10 + fL);
    }

    public static void lbaToMsfAdjustPregap(int sector, MsfHolder h) {
        lbaToMsf(sector + PREGAP_LEN_LBA, h);
    }

    public static void lbaToMsf(int sector, MsfHolder h) {
        // If sectors < 0, we wrap around from 100 minutes (450,000 sectors)
        // This is the standard way to represent lead-in areas before 00:00:00
        if (sector < 0) {
            sector += 450000;
        }
        h.frame = sector % 75;
        sector /= 75;
        h.second = sector % 60;
        sector /= 60;
        h.minute = sector;
    }
}
