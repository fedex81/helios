package mcd.cdd;

import com.google.common.base.MoreObjects;
import mcd.cdd.CdModel.RomFileType;
import mcd.cdd.CdModel.TrackDataType;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.util.LogHelper;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.TrackData;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class ExtendedCueSheet implements Closeable {

    private final static Logger LOG = LogHelper.getLogger(ExtendedCueSheet.class.getSimpleName());

    private final static byte[] SCD_SYS_BYTES = "SEGADISCSYSTEM".getBytes();
    private final static byte ff = (byte) 0xff;
    private final static byte[] CD_SYNC_BYTES = {0x00, ff, ff, ff, ff, ff, ff, ff, ff, ff, ff, 0x00};

    private static final boolean CUE_TEST_MODE = true;
    private static final Path NO_BIN_FILE_PATH = Paths.get(".", "test.bin");
    private static final Path TEMPLATE_ISO_CUE_PATH = Paths.get(".", "template_iso.cue");
    //placeholder name
    private static final String TEMPLATE_ISO_NAME_PH = "<iso_here>";
    public final CueSheet cueSheet;
    public final Path cuePath;

    public RomFileType romFileType = RomFileType.UNKNOWN;

    private Optional<Path> isoPath = Optional.empty();
    public final List<CdModel.ExtendedTrackData> extTracks = new ArrayList<>();
    public int numTracks, sectorEnd;
    protected final Map<String, RandomAccessFile> fileCache = new HashMap<>();
    private static RandomAccessFile NO_BIN_FILE;

    static {
        if (CUE_TEST_MODE) {
            LOG.warn("Cue test mode: {}", CUE_TEST_MODE);
        }
    }

    public ExtendedCueSheet(Path path) {
        Path cp = path;
        if (path.getFileName().toString().endsWith(".iso")) {
            cp = path.resolveSibling(TEMPLATE_ISO_CUE_PATH);
            isoPath = Optional.of(path);
        }
        cuePath = cp;
        cueSheet = CueFileParser.parse(cuePath);
        parseCueSheet();
        assertReady();
        if (romFileType == null || romFileType == RomFileType.UNKNOWN) {
            throw new IllegalArgumentException("Unsupported rom type: " + path.toAbsolutePath());
        }
    }

    private void parseCueSheet() {
        ExtendedCueSheet extCueSheet = this;
        assert extCueSheet.fileCache.isEmpty();
        List<TrackData> tracks = extCueSheet.cueSheet.getAllTrackData();
        assert !tracks.isEmpty();
        extCueSheet.numTracks = tracks.size();
        parseTrack01Header(tracks.get(0));
        for (TrackData track : tracks) {
            parseTrack(extCueSheet, track.getNumber(), cuePath);
        }
    }

    private void parseTrack01Header(TrackData track01) {
        try {
            byte[] header = new byte[16];
            RandomAccessFile raf = getDataFile(this, track01.getParent().getFile(), cuePath);
            TrackDataType trackDataType = TrackDataType.parse(track01.getDataType());
            raf.read(header, 0, header.length);
            if (Arrays.equals(SCD_SYS_BYTES, 0, SCD_SYS_BYTES.length, header, 0, SCD_SYS_BYTES.length)) {
                System.out.println("valid Sega CD image");
                romFileType = RomFileType.ISO;
            } else if (Arrays.equals(CD_SYNC_BYTES, 0, CD_SYNC_BYTES.length, header, 0, CD_SYNC_BYTES.length)) {
                System.out.println("CD-ROM synchro pattern");
                romFileType = RomFileType.BIN_CUE;
            } else if (trackDataType == TrackDataType.AUDIO) {
                System.out.println("CD-AUDIO");
                romFileType = RomFileType.BIN_CUE;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseTrack(ExtendedCueSheet extCueSheet, int trackNumber, Path cuePath) {
        TrackData trackData = getTrack(extCueSheet.cueSheet, trackNumber);
        RandomAccessFile raf = getDataFile(extCueSheet, trackData.getParent().getFile(), cuePath);
        CdModel.ExtendedTrackData extTrackData = new CdModel.ExtendedTrackData(trackData, raf);
        extTrackData.trackDataType = TrackDataType.parse(trackData.getDataType());
        try {
            /* read Sega CD image header + security code */
            byte[] sec = new byte[0x200];
            raf.read(sec, 0, sec.length);

            List<CdModel.ExtendedTrackData> extTracks = extCueSheet.extTracks;
            int sectorStart = 0;
            if (!extTracks.isEmpty()) {
                sectorStart = extTracks.get(extTracks.size() - 1).absoluteSectorEnd;
            }
            CdModel.SectorSize sectorSize = extTrackData.trackDataType.size;
            extTrackData.lenBytes = (int) raf.length();
            extTrackData.absoluteSectorStart = sectorStart;
            extTrackData.absoluteSectorEnd = sectorStart + (extTrackData.lenBytes / sectorSize.s_size);
            extTrackData.lenSector = extTrackData.absoluteSectorEnd - extTrackData.absoluteSectorStart;
            extCueSheet.sectorEnd = extTrackData.absoluteSectorEnd;
            //NOTE projectcd.iso fails this one
            if (romFileType != RomFileType.ISO) {
                //divides with no remainder
                assert sectorSize.s_size * (extTrackData.lenBytes / sectorSize.s_size) == extTrackData.lenBytes;
                /* DATA track length should be at least 2s (BIOS requirement) */
                assert sectorSize.s_size >= 150;
            }
            extTracks.add(extTrackData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static RandomAccessFile getDataFile(ExtendedCueSheet extCueSheet, String key, Path file, Path cuePath) {
        if (!extCueSheet.fileCache.containsKey(key)) {
            try {
                extCueSheet.fileCache.put(key, new RandomAccessFile(file.toFile(), "r"));
            } catch (Exception e) {
                if (CUE_TEST_MODE) {
                    handleTestCueMode(extCueSheet, cuePath, key);
                } else {
                    e.printStackTrace();
                }
            }
        }
        return extCueSheet.fileCache.get(key);
    }

    private static RandomAccessFile getDataFile(ExtendedCueSheet extCueSheet, String trackFileName, Path cuePath) {
        Path fp = cuePath.resolveSibling(trackFileName);
        String key = fp.getFileName().toString();
        if (TEMPLATE_ISO_NAME_PH.equals(key)) {
            assert extCueSheet.isoPath.isPresent();
            fp = extCueSheet.isoPath.orElse(fp);
            key = fp.getFileName().toString();
        }
        return getDataFile(extCueSheet, key, fp, cuePath);
    }

    private static void handleTestCueMode(ExtendedCueSheet extCueSheet, Path cuePath, String key) {
        try {
            if (NO_BIN_FILE == null) {
                Path p = cuePath.resolveSibling(NO_BIN_FILE_PATH);
                NO_BIN_FILE = new RandomAccessFile(p.toFile(), "r");
                LOG.warn("Missing bin file: {}, using instead: {}, cue_test_mode: {}", key, p.toAbsolutePath(), CUE_TEST_MODE);
            }
            extCueSheet.fileCache.put(key, NO_BIN_FILE);
            extCueSheet.romFileType = RomFileType.BIN_CUE;
        } catch (FileNotFoundException ex) {
            LOG.error("Missing bin file: {}", key);
            throw new RuntimeException(ex);
        }
    }

    private static TrackData getTrack(CueSheet cueSheet, int number) {
        assert number > 0;
        TrackData td = cueSheet.getAllTrackData().get(number - 1);
        assert td.getNumber() == number;
        return td;
    }

    public static CdModel.ExtendedTrackData getExtTrack(ExtendedCueSheet extCueSheet, int number) {
        assert number > 0;
        CdModel.ExtendedTrackData td = extCueSheet.extTracks.get(number - 1);
        assert td.trackData.getNumber() == number;
        return td;
    }

    @Override
    public String toString() {
        String s = "\n\t" + extTracks.stream().map(Objects::toString).collect(Collectors.joining("\n\t")) + "\n";
        return MoreObjects.toStringHelper(this)
                .add("romFileType", romFileType)
                .add("cueSheet", cueSheet)
                .add("cuePath", cuePath)
                .add("isoPath", isoPath)
                .add("extTracks", s)
                .add("numTracks", numTracks)
                .add("sectorEnd", sectorEnd)
                .toString();
    }

    public void assertReady() {
        assert romFileType != null && romFileType != RomFileType.UNKNOWN;
        assert cueSheet != null;
        assert !extTracks.isEmpty();
        assert numTracks > 0 && sectorEnd > 0;
        assert !fileCache.isEmpty();
        assert fileCache.values().stream().allMatch(r -> r.getChannel().isOpen());
    }

    @Override
    public void close() throws IOException {
        extTracks.forEach(CdModel.ExtendedTrackData::closeQuietly);
        assert fileCache.values().stream().noneMatch(r -> r.getChannel().isOpen());
    }
}
