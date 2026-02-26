package mcd.cdd;

import com.google.common.base.MoreObjects;
import mcd.cdd.CdModel.ExtendedTrackData;
import mcd.cdd.CdModel.TrackDataType;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.system.SysUtil.RomFileType;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import omegadrive.util.ZipUtil;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.TrackData;
import org.slf4j.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static mcd.cdd.CdModel.ExtendedTrackData.NO_TRACK;
import static omegadrive.sound.msumd.MsuMdHandler.CDDA_FORMAT;
import static omegadrive.system.SysUtil.CUE_EXT;
import static omegadrive.system.SysUtil.ISO_EXT;

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
    protected final Map<String, TrackContentHelper> fileCache = new ConcurrentHashMap<>();

    protected final CountDownLatch dataReady = new CountDownLatch(1);

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
        Path mcp = Paths.get(path.toAbsolutePath().toString().replace(ISO_EXT, CUE_EXT));
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
        parseTrack(extCueSheet, 1, cuePath);
        Util.executorService.submit(() -> {
            long start = System.currentTimeMillis();
            tracks.stream().filter(t -> t.getNumber() != 1).parallel().
                    forEachOrdered(t -> parseTrack(extCueSheet, t.getNumber(), cuePath));
            LOG.info("Done parsing tracks from CUE sheet, took {} ms", System.currentTimeMillis() - start);
            dataReady.countDown();
        });
    }

    private void parseTrack(ExtendedCueSheet extCueSheet, int trackNumber, Path cuePath) {
        TrackData trackData = getTrack(extCueSheet.cueSheet, trackNumber);
        TrackContentHelper tca = getDataFile(extCueSheet, trackData.getParent().getFile(), cuePath);
        ExtendedTrackData extTrackData = new ExtendedTrackData(trackData, tca);
        extTrackData.trackDataType = TrackDataType.parse(trackData.getDataType());
        try {
            List<ExtendedTrackData> extTracks = extCueSheet.extTracks;
            int sectorStart = 0;
            if (!extTracks.isEmpty()) {
                int zeroBasedPrevTrackIndex = trackNumber - 1 - 1;
                assert zeroBasedPrevTrackIndex > 0 && extTracks.get(zeroBasedPrevTrackIndex) != null;
                sectorStart = extTracks.get(zeroBasedPrevTrackIndex).absoluteSectorEnd;
                assert sectorStart > 0;
            }
            CdModel.SectorSize sectorSize = extTrackData.trackDataType.size;
            extTrackData.lenBytes = (int) tca.length();
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
            LOG.error("Unable to parse track: {}", trackNumber, e);
        }
    }

    private static TrackContentHelper getDataFile(ExtendedCueSheet extCueSheet, String key, Path file, Path cuePath) {
        if (!extCueSheet.fileCache.containsKey(key)) {
            extCueSheet.fileCache.put(key, decompressContent(key, file));
        }
        return extCueSheet.fileCache.get(key);
    }

    private static TrackContentHelper decompressContent(String key, Path file) {
        TrackContentHelper tca = null;
        try {
            tca = TrackContentHelper.ofFile(file.toFile());
            if (key.endsWith(".ogg")) {
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(file.toFile());
                     AudioInputStream dataIn = AudioSystem.getAudioInputStream(CDDA_FORMAT, ais)) {
                    byte[] b = dataIn.readAllBytes();
                    tca = TrackContentHelper.ofDataArray(b);
                }
            } else if (key.endsWith(".gz")) {
                long start = System.currentTimeMillis();
                assert ZipUtil.isCompressedByteStream(file);
                byte[] b = ZipUtil.readGZipFileContents(file);
                tca = TrackContentHelper.ofDataArray(b);
                LOG.info("Done decompressing track1 ({}), took {} ms", file.getFileName(), System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            LOG.error("Unable to handle content: {}, file: {}", key, file.toAbsolutePath(), e);
        }
        return tca;
    }

    private static TrackContentHelper getDataFile(ExtendedCueSheet extCueSheet, String trackFileName, Path cuePath) {
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
        if (number > 1) {
            Util.waitOnLatch(extCueSheet.dataReady, true);
        }
        int zeroBased = number - 1;
        ExtendedTrackData td = NO_TRACK;
        if (zeroBased >= 0 && zeroBased < extCueSheet.extTracks.size()) {
            td = extCueSheet.extTracks.get(zeroBased);
            assert td.trackData.getNumber() == number;
        }
        assert td != NO_TRACK;
        return td;
    }

    public static boolean isAudioTrack(ExtendedCueSheet extCueSheet, int number) {
        assert number > 0;
        return getExtTrack(extCueSheet, number).trackDataType == TrackDataType.AUDIO;
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
        assert fileCache.values().stream().filter(TrackContentHelper::isFileBased).allMatch(tct ->
                tct.checkOpenIfFileBased(true));
    }

    @Override
    public void close() throws IOException {
        extTracks.forEach(ExtendedTrackData::closeQuietly);
        assert fileCache.values().stream().filter(TrackContentHelper::isFileBased).allMatch(tct ->
                tct.checkOpenIfFileBased(false));
    }
}
