package mcd.cdd;

import com.google.common.base.MoreObjects;
import omegadrive.util.LogHelper;
import omegadrive.util.ZipUtil;
import org.digitalmediaserver.cuelib.TrackData;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;

import static mcd.cdd.CdModel.SectorSize.S_2352;
import static mcd.cdd.CdModel.TrackContent.T_AUDIO;
import static mcd.cdd.CdModel.TrackContent.T_DATA;
import static mcd.cdd.CdModel.TrackMode.MODE1;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class CdModel {

    private final static Logger LOG = LogHelper.getLogger(CdModel.class.getSimpleName());

    enum RomFileType {UNKNOWN, BIN_CUE, ISO}

    enum TrackContent {T_AUDIO, T_DATA}

    enum TrackMode {MODE1, MODE2}

    public enum SectorSize {
        S_2048(2048), S_2352(2352);

        public final int s_size;

        SectorSize(int s) {
            this.s_size = s;
        }
    }

    public static final int SECTOR_2352 = S_2352.s_size;

    /**
     * AUDIO	    Audio/Music (2352 — 588 samples)
     * CDG	        Karaoke CD+G (2448)
     * MODE1/2048	CD-ROM Mode 1 Data (cooked)
     * MODE1/2352	CD-ROM Mode 1 Data (raw)
     * MODE2/2048	CD-ROM XA Mode 2 Data (form 1)
     * MODE2/2324	CD-ROM XA Mode 2 Data (form 2)
     * MODE2/2336	CD-ROM XA Mode 2 Data (form mix)
     * MODE2/2352	CD-ROM XA Mode 2 Data (raw)
     * CDI/2336	    CDI Mode 2 Data
     * CDI/2352	    CDI Mode 2 Data
     * <p>
     * https://www.gnu.org/software/ccd2cue/manual/html_node/MODE-_0028Compact-Disc-fields_0029.html
     */
    enum TrackDataType {
        MODE1_2352(MODE1, S_2352, T_DATA),
        AUDIO(MODE1, S_2352, T_AUDIO);

        public final TrackMode mode;
        public final SectorSize size;
        public final TrackContent content;

        TrackDataType(TrackMode mode, SectorSize sectorSize, TrackContent content) {
            this.mode = mode;
            this.size = sectorSize;
            this.content = content;
        }

        public static TrackDataType parse(String spec) {
            String s = spec.toUpperCase();
            if (s.contains(MODE1.name()) && s.contains(S_2352.s_size + "")) {
                return MODE1_2352;
            } else if (s.equals(AUDIO.name())) {
                return AUDIO;
            }
            LOG.error("Unable to parse: {}", spec);
            return null;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this.name())
                    .add("mode", mode)
                    .add("size", size)
                    .add("content", content)
                    .toString();
        }
    }

    static class ExtendedTrackData implements Closeable {

        public static final ExtendedTrackData NO_TRACK = new ExtendedTrackData(null, null);
        public final TrackData trackData;
        public final RandomAccessFile file;
        public TrackDataType trackDataType;
        public int absoluteSectorStart, absoluteSectorEnd, lenBytes, lenSector;

        public ExtendedTrackData(TrackData trackData, RandomAccessFile file) {
            this.trackData = trackData;
            this.file = file;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("trackData", trackData)
                    .add("file", file)
                    .add("trackDataType", trackDataType)
                    .add("absoluteSectorStart", absoluteSectorStart)
                    .add("absoluteSectorEnd", absoluteSectorEnd)
                    .add("lenSector", lenSector)
                    .add("lenBytes", lenBytes)
                    .toString();
        }

        @Override
        public void close() throws IOException {
            closeQuietly();
        }

        public void closeQuietly() {
            ZipUtil.closeQuietly(file);
        }
    }
}