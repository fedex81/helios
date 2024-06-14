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
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.text.NumberFormat;

public class CueFileParser {
    private static final Logger LOG = LogHelper.getLogger(CueFileParser.class.getSimpleName());
    public static final int MAX_TRACKS = 100; //0-99
    static final int SECTOR_SIZE_BYTES = 2352;

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
            cueSheet = CueParser.parse(cueFilePath.toFile(), Charset.defaultCharset());
        } catch (IOException e) {
            LOG.warn("Unable to open BIN/CUE file {}: {}", cueFilePath, e.getMessage());
        }
        return cueSheet;
    }

    public static int toSector(int m, int s, int f) {
        return (m * 60 + s) * 75 + f;
    }

    public static int toMSF_BCD(int sector) {
        int f = sector % 75;
        sector /= 75;
        int s = sector % 60;
        sector /= 60;
        int m = sector;
        return (toBCD(m) << 16) | (toBCD(s) << 8) | toBCD(f);
    }

    public static void toMSF(int sector, MsfHolder h) {
        h.frame = sector % 75;
        sector /= 75;
        h.second = sector % 60;
        sector /= 60;
        h.minute = sector;
    }

    public static int toBCD(int x) {
        return (x % 10) + ((x / 10) << 4);
    }

}
