package omegadrive.sound.msumd;

import com.google.common.io.Files;
import omegadrive.SystemLoader;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.Message;
import org.digitalmediaserver.cuelib.TrackData;
import org.slf4j.Logger;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static omegadrive.util.Util.th;

/**
 * MsuMdHandlerImpl
 * TODO fadeOut
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class MsuMdHandlerImpl implements MsuMdHandler {

    private static final Logger LOG = LogHelper.getLogger(MsuMdHandlerImpl.class.getSimpleName());
    private static final boolean verbose = true;

    private final MsuCommandArg commandArg = new MsuCommandArg();
    private int clock = 0;
    private boolean init;
    private volatile Clip clip;
    private volatile byte[] buffer = new byte[0];
    private final AtomicReference<LineListener> lineListenerRef = new AtomicReference<>();
    private final RandomAccessFile binFile;
    private final TrackDataHolder[] trackDataHolders = new TrackDataHolder[CueFileParser.MAX_TRACKS];
    private final ClipContext clipContext = new ClipContext();

    //CDDA: clipFrames are 44100/sec, each frame has 4 bytes (16 bit stereo)
    private static int sectorsToClipFrames(int val) {
        int mins = val / 75 / 60;
        int sec = val / 75 % 60;
        int frames = val % 75;
        return (int) (CDDA_SAMPLE_RATE / 1000d * (mins * 60 * 1000 + sec * 1000 + frames / 75d));
    }

    protected void initTrackData(CueSheet cueSheet, long binLen) {
        List<TrackData> trackDataList = cueSheet.getAllTrackData();
        Arrays.fill(trackDataHolders, NO_TRACK);
        for (int i = 0; i < trackDataList.size(); i++) {
            TrackData td = trackDataList.get(i);
            TrackDataHolder h = new TrackDataHolder();
            h.type = CueFileDataType.getFileType(td.getParent().getFileType());

            switch (h.type) {
                case BINARY:
                    int numBytes = 0;
                    int startFrame = td.getFirstIndex().getPosition().getTotalFrames();
                    if (trackDataList.size() == td.getNumber()) {
                        numBytes = (int) binLen - (startFrame * CueFileParser.SECTOR_SIZE_BYTES);
                    } else {
                        TrackData trackDataNext = trackDataList.get(td.getNumber());
                        int endFrame = trackDataNext.getFirstIndex().getPosition().getTotalFrames();
                        numBytes = (endFrame - startFrame) * CueFileParser.SECTOR_SIZE_BYTES;
                    }
                    h.numBytes = Optional.of(numBytes);
                    h.startFrame = Optional.of(startFrame);
                    break;
                case WAVE:
                    h.waveFile = Optional.ofNullable(cueSheet.getFile().resolveSibling(td.getParent().getFile()).toFile());
                    break;
            }
            initCueLoopPoints(cueSheet.getMessages(), i, h);
            trackDataHolders[td.getNumber()] = h;
        }
    }

    private TrackDataHolder initCueLoopPoints(List<Message> loopInfoList, int index, TrackDataHolder h) {
        if (loopInfoList == null || loopInfoList.size() <= index) {
            return h;
        }
        String str = Optional.ofNullable(loopInfoList.get(index)).map(Message::getInput).orElse("");
//        Legal commands are: REM LOOP; REM LOOP xxxxx; REM NOLOOP
        if (str.contains("LOOP")) {
            h.cueLoop = Optional.of(!str.contains("NOLOOP"));
            if (h.cueLoop.get()) {
                try {
                    int loopPoint = Integer.parseInt(str.split("\\s+")[2].trim());
                    h.cueLoopPoint = Optional.of(loopPoint);
                } catch (Exception e) {
                    LOG.warn("Unable to parse loop point for pos #{}: {}", index, str);
                }
            }
            if (verbose) LOG.info("CueFile has loop info for pos #{}: {}", index, str);
        }
        return h;
    }


    private MsuMdHandlerImpl(CueSheet cueSheet, RandomAccessFile binFile) {
        this.binFile = binFile;
        LOG.info("Enabling MSU-MD handling, using cue sheet: {}", cueSheet.getFile().toAbsolutePath());
    }

    public static MsuMdHandler createInstance(SystemLoader.SystemType systemType, Path romPath) {
        if (romPath == null) {
            return NO_OP_HANDLER;
        }
        if (systemType == SystemLoader.SystemType.MEGACD) {
            LOG.info("Disabling MSU-MD handling, {} detected.", systemType);
            return NO_OP_HANDLER;
        }
        CueSheet cueSheet = MsuMdHandlerImpl.initCueSheet(romPath);
        if (cueSheet == null) {
            LOG.info("Disabling MSU-MD handling, unable to find CUE file.");
            return NO_OP_HANDLER;
        }
        RandomAccessFile binFile = MsuMdHandlerImpl.initBinFile(romPath, cueSheet);
        if (binFile == null) {
            LOG.error("Disabling MSU-MD handling, unable to find BIN file");
            return NO_OP_HANDLER;
        }
        long binLen = 0;
        try {
            if ((binLen = binFile.length()) == 0) {
                throw new Exception("Zero length file: " + romPath);
            }
        } catch (Exception e) {
            LOG.error("Disabling MSU-MD handling, unable to find BIN file");
            return NO_OP_HANDLER;
        }
        MsuMdHandlerImpl h = new MsuMdHandlerImpl(cueSheet, binFile);
        h.initTrackData(cueSheet, binLen);
        return h;
    }

    private int handleFirstRead() {
        init = true;
        if (verbose) LOG.info("Read MCD_STATUS: {}", MCD_STATE.INIT);
        return MCD_STATE.INIT.ordinal();
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
            LOG.error("Error", e);
        }
        return binFile;
    }

    private volatile boolean busy = false;
    private int lastPlayed = -1;

    public void setBusy(boolean busy) {
        if (verbose) LOG.info("Busy: {} -> {}", this.busy ? 1 : 0, busy ? 1 : 0);
        this.busy = busy;
    }

    @Override
    public void handleMsuMdWrite(int address, int data, Size size) {
        //write to sub-cpu memory/wram, ignore
        if (address >= MCD_WRAM_START && address <= MCD_WRAM_END) {
            return;
        }
        handleMsuMdWriteByte(address, data, size);
    }

    private void handleMsuMdWriteByte(int address, int data, Size size) {
        switch (address) {
            case CLOCK_ADDR:
                if (verbose) LOG.info("Cmd clock: {} -> {}", clock, data);
                processCommand(commandArg);
                clock = data;
                break;
            case CMD_ADDR:
                int cmdData = data >> (size == Size.WORD ? 8 : (size == Size.LONG ? 24 : 0));
                commandArg.command = MsuCommand.getMsuCommand(cmdData);
                if (size == Size.WORD) {
                    commandArg.arg = data & 0xFF;
                }
                if (verbose) LOG.info("Cmd: {}, arg {}, arg1 {}", commandArg.command, commandArg.arg,
                        commandArg.arg1);
                break;
            case CMD_ARG_ADDR:
                commandArg.arg1 = data;
                if (verbose) LOG.info("Cmd Arg: {}, arg {}, arg1 {}", commandArg.command, commandArg.arg,
                        commandArg.arg1);
                break;
            default:
                handleIgnoredMcdWrite(address, data);
                break;
        }
    }

    @Override
    public int handleMsuMdRead(int address, Size size) {
        switch (address) {
            case MCD_STATUS_ADDR:
                if (!init) { //first read needs to return INIT
                    return handleFirstRead();
                }
                MCD_STATE m = busy ? MCD_STATE.CMD_BUSY : MCD_STATE.READY;
                if (verbose) LOG.info("Read MCD_STATUS: {}, busy: {}", m, busy);
                return m.ordinal();
            case CLOCK_ADDR:
                if (verbose) LOG.info("Read CLOCK_ADDR: {}", clock);
                return clock;
            default:
                return handleIgnoredMcdRead(address, size);
        }
    }

    private void handleIgnoredMcdWrite(int address, int data) {
        switch (address) {
            case MCD_GATE_ARRAY_START:
                if (verbose) LOG.info("Write MCD_GATE_ARRAY_START: {}", data);
                break;
            case MCD_MMOD:
                if (verbose) LOG.info("Write MCD_MMOD: {}", data);
                break;
            case MCD_COMF:
                if (verbose) LOG.info("Write MCD_COMF: {}", data);
                break;
            case MCD_MEMWP:
                if (verbose) LOG.info("Write MCD_MEMWP: {}", data);
                break;
            default:
                LOG.error("Unexpected bus write: {}, data {} {}",
                        th(address), th(data), Size.BYTE);
                break;
        }
    }

    private int handleIgnoredMcdRead(int address, Size size) {
        switch (address) {
            case MCD_GATE_ARRAY_START:
                if (verbose) LOG.info("Read MCD_GATE_ARRAY_START: {}", 0xFF);
                return size.getMask(); //ignore
            default:
                LOG.warn("Unexpected MegaCD address range read at: {}, {}",
                        th(address), size);
                return size.getMask();
        }
    }

    private MsuCommandArg injectCueLoopSetup(MsuCommandArg commandArg) {
        if (commandArg.command == MsuCommand.PLAY && trackDataHolders[commandArg.arg].cueLoop.orElse(false)) {
            TrackDataHolder tdh = trackDataHolders[commandArg.arg];
            String str = String.format("Using loop info from CUE file to override: %s %X %s", commandArg.command,
                    commandArg.arg, commandArg.arg1);
            if (tdh.cueLoopPoint.orElse(0) > 0) {
                commandArg.command = MsuCommand.PLAY_OFFSET;
                commandArg.arg1 = tdh.cueLoopPoint.get();
            } else {
                commandArg.command = MsuCommand.PLAY_LOOP;
            }
            if (verbose) LOG.info("{} to: {} 0x{} 0x{}", str, commandArg.command, commandArg.arg, commandArg.arg1);
        }
        return commandArg;
    }

    private void processCommand(MsuCommandArg commandArg) {
        int arg = commandArg.arg;
        Runnable r = null;
        if (verbose) LOG.info("{} track: {}", commandArg.command, arg);
        commandArg = injectCueLoopSetup(commandArg);
        switch (commandArg.command) {
            case PLAY:
                clipContext.track = arg;
                clipContext.loop = false;
                clipContext.loopOffsetCDDAFrames = 0;
                r = playTrack(clipContext);
                break;
            case PLAY_LOOP:
                clipContext.track = arg;
                clipContext.loop = true;
                clipContext.loopOffsetCDDAFrames = 0;
                r = playTrack(clipContext);
                break;
            case PAUSE:
                r = pauseTrack(arg / 75d);
                break;
            case RESUME:
                r = resumeTrack();
                break;
            case VOL:
                r = volumeTrack(arg);
                break;
            case PLAY_OFFSET:
                clipContext.track = arg;
                clipContext.loop = true;
                clipContext.loopOffsetCDDAFrames = commandArg.arg1;
                r = playTrack(clipContext);
                break;
            default:
                LOG.warn("Unknown command: {}", commandArg.command);
        }
        if (r != null) {
            Util.executorService.submit(r);
        }
    }

    private Runnable volumeTrack(int val) {
        return () -> {
            if (clip != null) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                double gainAbs = Math.max(val, 0.1) / 255.0; //(0;1]
                float gain_dB = (float) (Math.log(gainAbs) / Math.log(10.0) * 20.0);
                float prevDb = gain.getValue();
                //0.0db is the line's normal gain (baseline), any value < 0xFF attenuates the volume
                gain.setValue(gain_dB);
                LOG.info("Volume: {}, gain_db changed from: {}, to: {}", val, prevDb, gain_dB);
            }
        };
    }

    private void stopTrackInternal(boolean busy) {
        SoundUtil.close(clip);
        if (clip != null) {
            clip.removeLineListener(lineListenerRef.getAndSet(null));
        }
        setBusy(busy);
        if (verbose) LOG.info("Track stopped");
    }

    private Runnable pauseTrack(final double fadeMs) {
        return () -> {
            if (clip != null) {
                clipContext.positionMicros = clip.getMicrosecondPosition();
                clip.stop();
                clipContext.paused = true;
            }
        };
    }

    private Runnable resumeTrack() {
        return () -> startClipInternal(clip, clipContext);
    }

    private Runnable playTrack(ClipContext context) {
        int track = context.track;
        //TODO HACK
        if (lastPlayed == track && track == 20) { //sonic 1 hack
            LOG.warn("Trying to play again track: {}, ignoring", track);
            return () -> {
            };
        }
        lastPlayed = track;
        setBusy(true);
        //TODO HACK
        return () -> {
            try {
                TrackDataHolder h = trackDataHolders[track];
                stopTrackInternal(true);
                clip = AudioSystem.getClip();
                lineListenerRef.set(this::handleClipEvent);
                clip.addLineListener(lineListenerRef.get());
                switch (h.type) {
                    case WAVE:
                    case OGG:
                        AudioInputStream ais = AudioSystem.getAudioInputStream(h.waveFile.get());
                        AudioInputStream dataIn = AudioSystem.getAudioInputStream(CDDA_FORMAT, ais);
                        clip.open(dataIn);
                        break;
                    case BINARY:
                        prepareBuffer(h);
                        clip.open(CDDA_FORMAT, buffer, 0, h.numBytes.get());
                        break;
                    default:
                        LOG.error("Unable to parse track {}, type: {}", track, h.type);
                        return;
                }
                if (!clipContext.paused) {
                    startClipInternal(clip, context);
                } else {
                    clipContext.positionMicros = 0;
                }
                if (verbose) LOG.info("Track started: {}", track);
            } catch (Exception e) {
                LOG.error("Error", e);
                e.printStackTrace();
            } finally {
                setBusy(false);
            }
        };
    }

    @Override
    public void close() {
        stopTrackInternal(false);
        LOG.info("Closing");
    }

    private void handleClipEvent(LineEvent event) {
        if (verbose) LOG.info("Clip event: {}", event.getType());
    }

    private void startClipInternal(Clip clip, ClipContext context) {
        if (clip == null) {
            return;
        }
        if (verbose) LOG.info("Playing clip: {}", context);
        if (clipContext.positionMicros > 0) {
            clip.setMicrosecondPosition(clipContext.positionMicros);
        }
        if (clipContext.loopOffsetCDDAFrames > 0) {
            int startLoop = Math.min(clip.getFrameLength(), sectorsToClipFrames(context.loopOffsetCDDAFrames));
            clip.setLoopPoints(Math.max(startLoop, 0), -1);
        }
        clip.loop(context.loop ? Clip.LOOP_CONTINUOUSLY : 0);
        clipContext.paused = false;
        clipContext.positionMicros = 0;
    }

    enum MCD_STATE {READY, INIT, CMD_BUSY}

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
            LOG.error("Error", e);
        }
    }

    static class ClipContext {
        int track;
        int loopOffsetCDDAFrames; //see MSF
        long positionMicros;
        boolean loop;
        boolean paused;

        @Override
        public String toString() {
            return "ClipContext{" +
                    "track=" + track +
                    ", loopOffsetCDDAFrames=" + loopOffsetCDDAFrames +
                    ", positionMicros=" + positionMicros +
                    ", loop=" + loop +
                    ", paused=" + paused +
                    '}';
        }
    }
}