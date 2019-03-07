package omegadrive.sound.javasound;

import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.persist.FileSoundPersister;
import omegadrive.sound.persist.SoundPersister;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.PriorityThreadFactory;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * TODO mixing sound volumes?
 */
public class JavaSoundManager2 implements SoundProvider {
    private static Logger LOG = LogManager.getLogger(JavaSoundManager2.class.getSimpleName());

    private static SoundPersister.SoundType DEFAULT_SOUND_TYPE = SoundPersister.SoundType.BOTH;

    private static ExecutorService executorService =
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, JavaSoundManager.class.getSimpleName()));

    private final Object blocker = new Object();
    private volatile boolean hasOutput;

    private PsgProvider psg;
    private FmProvider fm;

    private static int OUTPUT_SAMPLE_SIZE = 16;
    private static int OUTPUT_CHANNELS = 1;
    private AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE_HZ, OUTPUT_SAMPLE_SIZE, OUTPUT_CHANNELS, true, false);
    private SourceDataLine dataLine;
    private SoundPersister soundPersister;
    private boolean mute = false;
    public volatile boolean close;

    int samplesPerFrame = 0;
    private byte[] psgBuffer = new byte[0];
    private int[] fmBuffer = new int[0];
    private byte[] mixBuffer = new byte[0];

    public static JavaSoundManager2 createSoundProvider(RegionDetector.Region region) {
        PsgProvider psgProvider = PsgProvider.createInstance(region, SAMPLE_RATE_HZ);
        FmProvider fmProvider = FmProvider.createInstance(region, SAMPLE_RATE_HZ);
        JavaSoundManager2 jsm = new JavaSoundManager2();
        jsm.setFm(fmProvider);
        jsm.setPsg(psgProvider);
        jsm.init(region);
        return jsm;
    }

    private void init(RegionDetector.Region region) {
        dataLine = SoundUtil.createDataLine(audioFormat);
        soundPersister = new FileSoundPersister();
        samplesPerFrame = (int) (region.getFrameIntervalMs() * (SAMPLE_RATE_HZ / 1000d));
        psgBuffer = new byte[samplesPerFrame];  //8 bit mono
        fmBuffer = new int[samplesPerFrame * 2];  //16 bit stereo
        mixBuffer = new byte[samplesPerFrame * 2];  //16 bit mono
        executorService.submit(getRunnable(dataLine, region));
        LOG.info("Output audioFormat: " + audioFormat);
    }

    private Runnable getRunnable(SourceDataLine dataLine, RegionDetector.Region region) {
        return new Runnable() {
            @Override
            public void run() {
                do {
                    do {
                        Util.waitOnObject(blocker);
                    } while (!hasOutput);
                    hasOutput = false;
                    //FM: stereo 16 bit, PSG: mono 8 bit, OUT: mono 16 bit
                    SoundUtil.intStereo14ToByteMono16Mix(fmBuffer, mixBuffer, psgBuffer);
                    Arrays.fill(fmBuffer, 0);
                    SoundUtil.writeBufferInternal(dataLine, mixBuffer, mixBuffer.length);
                } while (!close);
            }
        };
    }

    public void setPsg(PsgProvider psg) {
        this.psg = psg;
    }

    public void setFm(FmProvider fm) {
        this.fm = fm;
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
    public void output(int oneFrame) {
        psg.output(psgBuffer, 0, samplesPerFrame);
        fm.update(fmBuffer, 0, samplesPerFrame);
        synchronized (blocker) {
            hasOutput = true;
            blocker.notifyAll();
        }
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
    public void close() {
        reset();
        List<Runnable> list = executorService.shutdownNow();
        LOG.info("Closing sound, stopping background tasks: #" + list.size());
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
        return false;
    }
}
