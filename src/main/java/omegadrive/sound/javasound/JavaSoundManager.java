package omegadrive.sound.javasound;

import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.persist.FileSoundPersister;
import omegadrive.sound.persist.SoundPersister;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * TODO mixing sound volumes?
 */
public class JavaSoundManager implements SoundProvider {
    private static Logger LOG = LogManager.getLogger(JavaSoundManager.class.getSimpleName());

    private static SoundPersister.SoundType DEFAULT_SOUND_TYPE = SoundPersister.SoundType.BOTH;

    private static ExecutorService executorService =
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, JavaSoundManager.class.getSimpleName()));

    private PsgProvider psg;
    private FmProvider fm;

    private static int OUTPUT_SAMPLE_SIZE = 16;
    private static int OUTPUT_CHANNELS = 1;
    private AudioFormat audioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE, OUTPUT_SAMPLE_SIZE, OUTPUT_CHANNELS, true, false);
    private SourceDataLine dataLine;
    private SoundPersister soundPersister;
    private boolean mute = false;
    public volatile boolean close;
    public volatile boolean hasOutput = false;
    private volatile AtomicInteger micros = new AtomicInteger(0);
    private volatile boolean isSoundWorking = false;

    public static JavaSoundManager createSoundProvider(RegionDetector.Region region) {
        PsgProvider psgProvider = PsgProvider.createInstance(region);
        FmProvider fmProvider = FmProvider.createInstance(region);
        JavaSoundManager jsm = new JavaSoundManager();
        jsm.setFm(fmProvider);
        jsm.setPsg(psgProvider);
        jsm.init(region);
        return jsm;
    }

    private void init(RegionDetector.Region region) {
        dataLine = SoundUtil.createDataLine(audioFormat);
        soundPersister = new FileSoundPersister();
        executorService.submit(getRunnable(dataLine, region));
//        executorService.submit(SoundHandler.getSoundRunnable(this, dataLine, region));
        LOG.info("Creating instance, output audioFormat: " + audioFormat);
    }

    private Runnable getRunnable(SourceDataLine dataLine, RegionDetector.Region region) {
        int fmSize = SoundProvider.getFmBufferIntSize(region.getFps());
        int psgSize = SoundProvider.getPsgBufferByteSize(region.getFps());
        return () -> {
            int[] fm_buf_ints = new int[fmSize];
            byte[] mix_buf_bytes16 = new byte[fm_buf_ints.length];
            byte[] psg_buf_bytes = new byte[psgSize];
            do {
//                while (!hasOutput) {
//                    int ms = micros.getAndSet(0);
//                    fm.synchronizeTimers(ms);
//                }
                fm.synchronizeTimers(micros.getAndSet(0));
                hasOutput = false;
                psg.output(psg_buf_bytes);
                fm.output(fm_buf_ints);
                try {
                    Arrays.fill(mix_buf_bytes16, SoundUtil.ZERO_BYTE);
                    SoundUtil.intStereo14ToByteMono16Mix(fm_buf_ints, mix_buf_bytes16, psg_buf_bytes);
                    updateSoundWorking(mix_buf_bytes16);
                    if (!isMute()) {
                        SoundUtil.writeBufferInternal(dataLine, mix_buf_bytes16, mix_buf_bytes16.length);
                    }
                    if (isRecording()) {
                        soundPersister.persistSound(DEFAULT_SOUND_TYPE, mix_buf_bytes16);
                    }
                } catch (Exception e) {
                    LOG.error("Unexpected sound error", e);
                }
                Arrays.fill(fm_buf_ints, 0);
                Arrays.fill(psg_buf_bytes, SoundUtil.ZERO_BYTE);
            } while (!close);
            LOG.info("Stopping sound thread");
            psg.reset();
            fm.reset();
        };
    }

    protected void updateSoundWorking(byte[] b) {
        if (isSoundWorking) {
            return;
        }
        for (int i = 0; i < b.length; i++) {
            isSoundWorking |= b[i] != 0;
        }
    }

    public void setPsg(PsgProvider psg) {
        this.psg = psg;
    }

    public void setFm(FmProvider fm) {
        this.fm = fm;
    }

    @Override
    public void updateElapsedMicros(int micros) {
        this.micros.getAndAdd(micros);
    }

    @Override
    public PsgProvider getPsg() {
        return psg;
    }

    @Override
    public FmProvider getFm() {
        return fm;
    }

    @Override
    public void output(int micros) {
        hasOutput = true;
    }

    @Override
    public void reset() {
        LOG.info("Resetting sound");
        close = true;
        if (dataLine != null) {
            dataLine.drain();
            dataLine.close();
        }
    }

    @Override
    public boolean isRecording() {
        return soundPersister.isRecording();
    }

    @Override
    public void setRecording(boolean recording) {
        if (isRecording() && !recording) {
            soundPersister.stopRecording();
        } else if (!isRecording() && recording) {
            soundPersister.startRecording(DEFAULT_SOUND_TYPE);
        }
    }

    @Override
    public boolean isMute() {
        return mute;
    }

    @Override
    public void setMute(boolean mute) {
        this.mute = mute;
        LOG.info("Set mute: " + mute);
    }

    @Override
    public boolean isSoundWorking() {
        return isSoundWorking;
    }
}
