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
    static final int SECTOR_SIZE_BYTES = 2352;

    public static final Function<Integer, Integer> toBcdByte = n -> {
        assert n >= 0 && n < 80;
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

    public static int toSector(int m, int s, int f) {
        return (m * 60 + s) * 75 + f;
    }

    public static int toSector(int mH, int mL, int sH, int sL, int fH, int fL) {
        return toSector(mH * 10 + mL, sH * 10 + sL, fH * 10 + fL);
    }
    public static void toMSF(int sector, MsfHolder h) {
        h.frame = sector % 75;
        sector /= 75;
        h.second = sector % 60;
        sector /= 60;
        h.minute = sector;
    }
}
