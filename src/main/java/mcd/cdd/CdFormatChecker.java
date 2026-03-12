package mcd.cdd;

import mcd.cdc.CdcModel;
import omegadrive.sound.msumd.CueFileParser;
import omegadrive.util.LogHelper;
import omegadrive.util.ZipUtil;
import org.slf4j.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static mcd.cdd.CdModel.SectorSize.S_2352;
import static omegadrive.sound.msumd.MsuMdHandler.CDDA_FORMAT;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class CdFormatChecker {

    public static final byte[] expSync = {0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0};

    private final static Logger LOG = LogHelper.getLogger(CdFormatChecker.class.getSimpleName());

    //Silpheed (Demo) JP has issues, see http://redump.org/disc/39378/
    public static boolean checkTrack1Sectors(String str, CdModel.ExtendedTrackData etd) {
        if (etd.trackDataType == CdModel.TrackDataType.AUDIO || etd.trackDataType.size != S_2352) {
            return true;
        }
        byte[] sync = new byte[12];
        TrackContentHelper data = etd.data;
        List<Integer> invalidSectors = new ArrayList<>();
        int len = etd.trackLenSectors - CueFileParser.PREGAP_LEN_LBA;
        for (int i = 0; i < len; i++) {
            try {
                data.seek(i * S_2352.s_size);
                int r = data.read(sync, 0, sync.length);
                assert r == sync.length : etd;
                boolean ok = Arrays.equals(expSync, sync);
                if (!ok) {
                    invalidSectors.add(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (invalidSectors.size() > 0) {
            LOG.warn("Invalid sectors, missing sync, {} out of {}, list: {}, {}", invalidSectors.size(), etd.trackLenSectors,
                    Arrays.toString(invalidSectors.toArray()), str);
        }
        return true;
    }

    public static boolean checkMode1Data(CdModel.ExtendedTrackData track, CueFileParser.MsfHolder holder,
                                         CdcModel.CdcHeader cdcHeader, int dataSeekPos) {
        byte[] header = new byte[4];
        byte[] syncHeader = new byte[12];
        boolean ok = false;
        try {
            track.data.seek(dataSeekPos - 16);
            int readN = track.data.read(syncHeader, 0, syncHeader.length);
            try {
                assert readN == syncHeader.length : track + ",seekPos:" + th(dataSeekPos); //SurgicalStrike_E
            } catch (Error e) {
                //try-catch for debugging
                e.printStackTrace();
            }
            readN = track.data.read(header, 0, header.length);
            assert readN == header.length;
            ok = Integer.parseInt("" + holder.minute, 16) == header[0] &&
                    Integer.parseInt("" + holder.second, 16) == header[1] &&
                    Integer.parseInt("" + holder.frame, 16) == header[2];
            ok &= header[0] == cdcHeader.minute &&
                    header[1] == cdcHeader.second &&
                    header[2] == cdcHeader.frame;
            ok &= header[3] == cdcHeader.mode; //MODE1
            ok &= Arrays.equals(expSync, syncHeader);
        } catch (Exception e) {
            LOG.error("decode error: {}", e.getMessage());
            e.printStackTrace();
            assert false;
        }
        return ok;
    }

    public static TrackContentHelper decompressContent(String key, Path file) {
        TrackContentHelper tca = null;
        try {
            tca = TrackContentHelper.ofFile(file.toFile());
            if (key.endsWith(".ogg")) {
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(file.toFile());
                     AudioInputStream dataIn = AudioSystem.getAudioInputStream(CDDA_FORMAT, ais)) {
                    byte[] b = padToCdSector(dataIn.readAllBytes());
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

    private static byte[] padToCdSector(byte[] b) {
        int rem = b.length % S_2352.s_size;
        if (rem != 0) {
            int newLength = b.length + (S_2352.s_size - rem);
            b = java.util.Arrays.copyOf(b, newLength);
        }
        return b;
    }
}
