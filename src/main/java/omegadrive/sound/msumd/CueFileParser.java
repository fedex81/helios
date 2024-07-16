/*
 * Copyright (C) 2003, 2014 Graham Sanderson
 *
 * This file is part of JPSX.
 *
 * JPSX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPSX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JPSX.  If not, see <http://www.gnu.org/licenses/>.
 */
package omegadrive.sound.msumd;

import omegadrive.util.LogHelper;
import org.digitalmediaserver.cuelib.CueParser;
import org.digitalmediaserver.cuelib.CueSheet;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
            cueSheet = parse(Files.newInputStream(cueFilePath, StandardOpenOption.READ));
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
