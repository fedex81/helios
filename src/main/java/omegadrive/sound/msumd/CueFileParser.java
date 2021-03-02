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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.digitalmediaserver.cuelib.CueParser;
import org.digitalmediaserver.cuelib.CueSheet;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

public class CueFileParser {
    private static final Logger LOG = LogManager.getLogger(CueFileParser.class.getSimpleName());
    public static final int MAX_TRACKS = 100; //0-99
    static int SECTOR_SIZE_BYTES = 2352;

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
        return (m * 60 + s) * 75 + f - 150;
    }

    public static int toMSF(int sector) {
        int f = sector % 75;
        sector /= 75;
        int s = sector % 60;
        sector /= 60;
        int m = sector;
        return (toBCD(m) << 16) | (toBCD(s) << 8) | toBCD(f);
    }

    public static int toBCD(int x) {
        return (x % 10) + ((x / 10) << 4);
    }

}
