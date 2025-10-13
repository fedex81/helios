package omegadrive.system.gb;

import eu.rekawek.coffeegb.Gameboy;
import eu.rekawek.coffeegb.sound.SoundOutput;
import omegadrive.sound.BlipSoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

import static omegadrive.util.SoundUtil.AF_8bit_Stereo;

/**
 * NesSoundWrapper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class BlipGbSoundWrapper implements SoundOutput, FmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipGbSoundWrapper.class.getSimpleName());
    private static final AudioFormat audioFormat = AF_8bit_Stereo;
    private final static int OUTPUT_RATE_HZ = 740 * 60;

    private final static int MAX_TICKS = 2500;
    private final int divider;
    private int tickCnt = 0;

    private int tickLimiter = 0;
    private String name;

    private BlipSoundProvider blipProvider;

    public BlipGbSoundWrapper(RegionDetector.Region region) {
        name = "gb";
        divider = (int) (Gameboy.TICKS_PER_SEC / audioFormat.getSampleRate());
        blipProvider = new BlipSoundProvider(name, RegionDetector.Region.USA, audioFormat,
                OUTPUT_RATE_HZ);
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
            blipProvider.playSample8(left, right);
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
