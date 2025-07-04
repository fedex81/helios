package mcd.cdd;

import com.google.common.base.MoreObjects;
import mcd.cdd.CdModel.ExtendedTrackData;
import mcd.cdd.CdModel.TrackDataType;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.system.SysUtil.RomFileType;
import omegadrive.util.LogHelper;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.TrackData;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static mcd.cdd.CdModel.ExtendedTrackData.NO_TRACK;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class ExtendedCueSheet implements Closeable {

    private final static Logger LOG = LogHelper.getLogger(ExtendedCueSheet.class.getSimpleName());

    //placeholder name
    public static final String TEMPLATE_ISO_NAME_PH = "<iso_here>";

    public static final String TEMPLATE_CUE_FOR_ISO =
            "REM CD_ROM, 1 DATA TRACK MAPS TO ISO\n" +
                    "FILE \"<iso_here>\" BINARY\n" +
                    "  TRACK 01 MODE1/2048\n" +
                    "    INDEX 01 00:00:00";
    public final CueSheet cueSheet;
    public final Path cuePath;

    public RomFileType romFileType = RomFileType.UNKNOWN;

    private Optional<Path> isoPathCue = Optional.empty();
    public final List<ExtendedTrackData> extTracks = new ArrayList<>();
    public int numTracks, sectorEnd;
    protected final Map<String, RandomAccessFile> fileCache = new HashMap<>();

    public ExtendedCueSheet(Path discImage, RomFileType rft) {
        assert rft.isDiscImage();
        this.romFileType = rft;
        Path path = discImage;
        Path cp = path;
        if (rft == RomFileType.ISO) {
            AtomicReference<Path> ref = new AtomicReference<>();
            cueSheet = parseCueForIso(discImage, ref);
            cuePath = ref.get();
        } else {
            cueSheet = CueFileParser.parse(cp);
            cuePath = cp;
        }
        parseCueSheet();
        assertReady();
        if (romFileType == null || romFileType == RomFileType.UNKNOWN) {
            throw new IllegalArgumentException("Unsupported rom type: " + path.toAbsolutePath());
        }
    }

    private CueSheet parseCueForIso(Path path, AtomicReference<Path> ref) {
        CueSheet cs = null;
        //first check if we have a matching *.cue file
        Path mcp = Paths.get(path.toAbsolutePath().toString().replace(".iso", ".cue"));
        Path cp = path.resolveSibling(mcp);
        if (Files.exists(cp)) {
            isoPathCue = Optional.of(cp);
            cs = CueFileParser.parse(isoPathCue.get());
            ref.set(isoPathCue.get());
            LOG.info("ISO file detected, using the following CUE file: {}", cp.toAbsolutePath());
        } else {
            //otherwise generate a cue file for the iso
            String fakeCue = TEMPLATE_CUE_FOR_ISO.replace(TEMPLATE_ISO_NAME_PH, path.getFileName().toString());
            ByteArrayInputStream bais = new ByteArrayInputStream(fakeCue.getBytes());
            cs = CueFileParser.parse(bais);
            ref.set(path);
            LOG.info("ISO file detected, generating a synthetic CUE file: \n{}", fakeCue);
        }
        assert cs != null;
        return cs;
    }
    private void parseCueSheet() {
        ExtendedCueSheet extCueSheet = this;
        assert extCueSheet.fileCache.isEmpty();
        List<TrackData> tracks = extCueSheet.cueSheet.getAllTrackData();
        assert !tracks.isEmpty();
        extCueSheet.numTracks = tracks.size();
        for (TrackData track : tracks) {
            parseTrack(extCueSheet, track.getNumber(), cuePath);
        }
    }

    private void parseTrack(ExtendedCueSheet extCueSheet, int trackNumber, Path cuePath) {
        TrackData trackData = getTrack(extCueSheet.cueSheet, trackNumber);
        RandomAccessFile raf = getDataFile(extCueSheet, trackData.getParent().getFile(), cuePath);
        ExtendedTrackData extTrackData = new ExtendedTrackData(trackData, raf);
        extTrackData.trackDataType = TrackDataType.parse(trackData.getDataType());
        try {
            /* read Sega CD image header + security code */
            byte[] sec = new byte[0x200];
            raf.read(sec, 0, sec.length);

            List<ExtendedTrackData> extTracks = extCueSheet.extTracks;
            int sectorStart = 0;
            if (!extTracks.isEmpty()) {
                sectorStart = extTracks.get(extTracks.size() - 1).absoluteSectorEnd;
            }
            CdModel.SectorSize sectorSize = extTrackData.trackDataType.size;
            extTrackData.lenBytes = (int) raf.length();
            extTrackData.absoluteSectorStart = sectorStart;
            extTrackData.absoluteSectorEnd = sectorStart + (extTrackData.lenBytes / sectorSize.s_size);
            extTrackData.trackLenSectors = extTrackData.absoluteSectorEnd - extTrackData.absoluteSectorStart;
            extCueSheet.sectorEnd = extTrackData.absoluteSectorEnd;
            //NOTE projectcd.iso fails this one
            if (romFileType != RomFileType.ISO) {
                //divides with no remainder
                assert sectorSize.s_size * (extTrackData.lenBytes / sectorSize.s_size) == extTrackData.lenBytes;
                /* DATA track length should be at least 2s (BIOS requirement) */
                assert sectorSize.s_size >= Cdd.PREGAP_LEN_LBA;
            }
            extTracks.add(extTrackData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static RandomAccessFile getDataFile(ExtendedCueSheet extCueSheet, String key, Path file, Path cuePath) {
        //TODO close RandomAccessFile
        if (!extCueSheet.fileCache.containsKey(key)) {
            try {
                extCueSheet.fileCache.put(key, new RandomAccessFile(file.toFile(), "r"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return extCueSheet.fileCache.get(key);
    }

    private static RandomAccessFile getDataFile(ExtendedCueSheet extCueSheet, String trackFileName, Path cuePath) {
        Path fp = cuePath.resolveSibling(trackFileName);
        String key = fp.getFileName().toString();
        return getDataFile(extCueSheet, key, fp, cuePath);
    }

    private static TrackData getTrack(CueSheet cueSheet, int number) {
        assert number > 0;
        TrackData td = cueSheet.getAllTrackData().get(number - 1);
        assert td.getNumber() == number;
        return td;
    }

    public static ExtendedTrackData getExtTrack(ExtendedCueSheet extCueSheet, int number) {
        assert number > 0;
        int zeroBased = number - 1;
        ExtendedTrackData td = NO_TRACK;
        if (zeroBased >= 0 && zeroBased < extCueSheet.extTracks.size()) {
            td = extCueSheet.extTracks.get(zeroBased);
            assert td.trackData.getNumber() == number;
        }
        return td;
    }

    public static boolean isAudioTrack(ExtendedCueSheet extCueSheet, int number) {
        assert number > 0;
        return getExtTrack(extCueSheet, number).trackDataType == TrackDataType.AUDIO;
    }

    public byte[] getRomHeader() {
        assert romFileType == RomFileType.BIN_CUE;
        byte[] dest = new byte[0x200];
        try {
            RandomAccessFile raf = extTracks.get(0).file;
            //skip sync pattern(12) + header(4)
            raf.seek(16);
            raf.read(dest);
        } catch (Exception e) {
            LOG.error("Unable to parse MD header", e);
        }
        return dest;
    }

    @Override
    public String toString() {
        String s = "\n\t" + extTracks.stream().map(Objects::toString).collect(Collectors.joining("\n\t")) + "\n";
        return MoreObjects.toStringHelper(this)
                .add("romFileType", romFileType)
                .add("cueSheet", cueSheet)
                .add("cuePath", cuePath)
                .add("isoPath", isoPathCue)
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
        extTracks.forEach(ExtendedTrackData::closeQuietly);
        assert fileCache.values().stream().noneMatch(r -> r.getChannel().isOpen());
    }
}
