package omegadrive.system.gb;

import eu.rekawek.coffeegb.Gameboy;
import eu.rekawek.coffeegb.sound.SoundOutput;
import omegadrive.sound.BlipSoundProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

/**
 * NesSoundWrapper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class BlipGbSoundWrapper implements SoundOutput, FmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipGbSoundWrapper.class.getSimpleName());
    public static final AudioFormat gbAudioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE_HZ, 8, 2, true, false);
    private final static int OUTPUT_RATE_HZ = 740 * 60;

    private final static int MAX_TICKS = 2500;

    private final static int VOLUME_BOOST = 8;
    private final int divider;
    private int tickCnt = 0;

    private int tickLimiter = 0;
    private String name;

    private BlipSoundProvider blipProvider;

    public BlipGbSoundWrapper(RegionDetector.Region region) {
        name = "gb";
        divider = (int) (Gameboy.TICKS_PER_SEC / gbAudioFormat.getSampleRate());
        blipProvider = new BlipSoundProvider(name, RegionDetector.Region.USA, AbstractSoundManager.audioFormat,
                OUTPUT_RATE_HZ);
        System.out.println(blipProvider);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void play(int left, int right) {
        if (tickLimiter++ != 0) {
            tickLimiter %= divider;
            return;
        }
        //should be ~2200 per frame, some early frames are skipping vsync (?) and it gets to 10000
        if (tickCnt < MAX_TICKS) {
            blipProvider.playSample(left << VOLUME_BOOST, right << VOLUME_BOOST);
        }
        tickCnt++;
    }

    @Override
    public void onNewFrame() {
        if (tickCnt > 0) {
            blipProvider.onNewFrame();
            tickCnt = 0;
            tickLimiter = 0;
        } else {
            LogHelper.logWarnOnce(LOG, "newFrame called with tickCnt: {}", tickCnt);
        }
    }

    @Override
    public SampleBufferContext getFrameData() {
//        assert tickCnt == 0;
        return blipProvider.getDataBuffer();
    }

    @Override
    public int updateStereo16(int[] buf_lr, int offset, int count) {
        return 0;
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public void tick() {
    }
}
