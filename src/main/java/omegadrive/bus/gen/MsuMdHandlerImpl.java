package omegadrive.bus.gen;

import com.google.common.io.Files;
import omegadrive.util.Size;
import omegadrive.util.SoundUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.TrackData;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * MsuMdHandlerImpl
 * TODO fadeOut, volume
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class MsuMdHandlerImpl implements MsuMdHandler {

    private static final Logger LOG = LogManager.getLogger(MsuMdHandlerImpl.class.getSimpleName());

    private MsuCommandArg commandArg = new MsuCommandArg();
    private int clock = 0;
    private boolean init;
    private volatile Clip clip;
    private volatile long clipPosition;
    private volatile CueSheet cueSheet;
    private Path romPath;
    private volatile byte[] buffer = new byte[0];
    private RandomAccessFile binFile;
    private TrackDataHolder[] trackDataHolders = new TrackDataHolder[CueFileParser.MAX_TRACKS];


    private MsuMdHandlerImpl(Path romPath, CueSheet cueSheet, RandomAccessFile binFile) {
        this.romPath = romPath;
        this.cueSheet = cueSheet;
        this.binFile = binFile;
        LOG.info("Enabling MSU-MD handling, using cue sheet: {}", cueSheet.getFile().toAbsolutePath());
    }

    public static MsuMdHandler createInstance(Path romPath) {
        if (romPath == null) {
            return NO_OP_HANDLER;
        }
        CueSheet cueSheet = MsuMdHandlerImpl.initCueSheet(romPath);
        if (cueSheet == null) {
            LOG.error("Disabling MSU-MD handling, unable to find CUE file.");
            return NO_OP_HANDLER;
        }
        RandomAccessFile binFile = MsuMdHandlerImpl.initBinFile(romPath, cueSheet);
        if (binFile == null) {
            LOG.error("Disabling MSU-MD handling, unable to find BIN file");
            return NO_OP_HANDLER;
        }
        MsuMdHandlerImpl h = new MsuMdHandlerImpl(romPath, cueSheet, binFile);
        h.initTrackData(cueSheet);
        return h;
    }

    protected void initTrackData(CueSheet cueSheet) {
        List<TrackData> trackDataList = cueSheet.getAllTrackData();
        for (TrackData td : trackDataList) {
            TrackDataHolder h = new TrackDataHolder();
            h.type = CueFileDataType.getFileType(td.getParent().getFileType());
            switch (h.type) {
                case BINARY:
                    if (trackDataList.size() == td.getNumber()) {
                        continue; //TODO
                    }
                    TrackData trackDataNext = trackDataList.get(td.getNumber());
                    int startFrame = td.getFirstIndex().getPosition().getTotalFrames();
                    int endFrame = trackDataNext.getFirstIndex().getPosition().getTotalFrames();
                    int numBytes = (endFrame - startFrame) * CueFileParser.SECTOR_SIZE_BYTES;
                    h.numBytes = Optional.of(numBytes);
                    h.startFrame = Optional.of(startFrame);
                    break;
                case WAVE:
                    h.type = CueFileDataType.WAVE;
                    h.waveFile = Optional.ofNullable(cueSheet.getFile().resolveSibling(td.getParent().getFile()).toFile());
                    break;
            }
            trackDataHolders[td.getNumber()] = h;
        }
    }

    private static CueSheet initCueSheet(Path romPath) {
        String romName = romPath.getFileName().toString();
        String cueFileName = romName.replace("." + Files.getFileExtension(romName), ".cue");
        return CueFileParser.parse(romPath.resolveSibling(cueFileName));
    }

    private static RandomAccessFile initBinFile(Path romPath, CueSheet sheet) {
        RandomAccessFile binFile = null;
        try {
            Path binPath = romPath.resolveSibling(sheet.getFileData().get(0).getFile());
            binFile = new RandomAccessFile(binPath.toFile(), "r");
        } catch (Exception e) {
            LOG.error(e);
        }
        return binFile;
    }

    @Override
    public int handleMsuMdRead(int address, Size size) {
        switch (address) {
            case MCD_STATUS_ADDR:
                int val = init ? 0 : 1;
                init = true;
                LOG.info("Read MCD_STATUS: {}", val);
                return val;
            case CLOCK_ADDR:
                return clock;
            case MCD_GATE_ARRAY_START:
                return 0xFF; //ignore
            default:
                LOG.warn("Unexpected MegaCD address range read at: {}, {}",
                        Long.toHexString(address), size);
                return 0xFF;
        }
    }

    @Override
    public void handleMsuMdWrite(int address, int data, Size size) {
        switch (size) {
            case BYTE:
                handleMsuMdWriteByte(address, data);
                break;
            case WORD:
                handleMsuMdWriteByte(address, data >> 8);
                handleMsuMdWriteByte(address + 1, data & 0xFF);
                break;
            case LONG:
                handleMsuMdWriteByte(address, (data >> 24) & 0xFF);
                handleMsuMdWriteByte(address + 1, (data >> 16) & 0xFF);
                handleMsuMdWriteByte(address + 2, (data >> 8) & 0xFF);
                handleMsuMdWriteByte(address + 3, data & 0xFF);
                break;
        }
    }

    private void handleMsuMdWriteByte(int address, int data) {
        switch (address) {
            case CLOCK_ADDR:
                LOG.debug("Cmd clock: {} -> {}", clock, data);
                processCommand(commandArg);
                clock = data;
                break;
            case CMD_ADDR:
                commandArg.command = MsuCommand.getMsuCommand(data);
                LOG.debug("Cmd: {}, arg {}", commandArg.command, commandArg.arg);
                break;
            case CMD_ARG_ADDR:
                commandArg.arg = data;
                LOG.debug("Cmd: {}, arg {}", commandArg.command, commandArg.arg);
                break;
        }
    }

    boolean paused;

    private void processCommand(MsuCommandArg commandArg) {
        int arg = commandArg.arg;
        Runnable r = null;
        switch (commandArg.command) {
            case PLAY:
                LOG.info("Play track: {}", arg);
                r = playTrack(arg, false);
                break;
            case PLAY_LOOP:
                LOG.info("PlayLoop track: {}", arg);
                r = playTrack(arg, true);
                break;
            case PAUSE:
                LOG.info("Pause: {}", arg);
                r = pauseTrack(arg / 75d);
                break;
            case RESUME:
                LOG.info("Resume: {}", arg);
                r = resumeTrack();
                break;
            case VOL:
                LOG.info("Volume: {}", arg);
                break;
        }
        if (r != null) {
//            Util.executorService.submit(r);
            r.run();
        }
    }

    private Runnable pauseTrack(final double fadeMs) {
        return () -> {
            if (clip != null) {
                clipPosition = clip.getMicrosecondPosition();
                clip.stop();
            }
            paused = true;
        };
    }

    private Runnable resumeTrack() {
        return () -> {
            if (clip != null) {
                clip.setMicrosecondPosition(clipPosition);
                clip.start();
            }
            paused = false;
        };
    }

    private Runnable stopTrack() {
        return () -> {
            SoundUtil.close(clip);
            clipPosition = 0;
            LOG.info("Track stopped");
        };
    }

    @Override
    public void close() {
        stopTrack().run();
        LOG.info("Closing");
    }

    private Runnable playTrack(final int track, boolean loop) {
        return () -> {
            try {
                TrackDataHolder h = trackDataHolders[track];
                stopTrack();
                clip = AudioSystem.getClip();
                switch (h.type) {
                    case WAVE:
                        AudioInputStream ais = AudioSystem.getAudioInputStream(h.waveFile.get());
                        clip.open(ais);
                        break;
                    case BINARY:
                        prepareBuffer(h);
                        clip.open(CDDA_FORMAT, buffer, 0, h.numBytes.get());
                        break;
                    default:
                        LOG.error("Unable to parse track type: {}", h.type);
                }
                clip.loop(loop ? Clip.LOOP_CONTINUOUSLY : 0);
                if (!paused) {
                    clip.start();
                } else {
                    clipPosition = 0;
                }
                LOG.info("Track started: {}", track);
            } catch (Exception e) {
                LOG.error(e);
            }
        };
    }

    private void prepareBuffer(TrackDataHolder h) {
        int numBytes = h.numBytes.get();
        if (buffer.length < numBytes) {
            buffer = new byte[numBytes];
        }
        Arrays.fill(buffer, (byte) 0);
        try {
            binFile.seek(h.startFrame.get() * CueFileParser.SECTOR_SIZE_BYTES);
            binFile.readFully(buffer, 0, numBytes);
        } catch (IOException e) {
            LOG.error(e);
        }
    }
}