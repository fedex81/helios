package omegadrive.system.nes;

import com.grapeshot.halfnes.audio.AudioOutInterface;
import omegadrive.sound.BlipSoundProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundUtil;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

/**
 * NesSoundWrapperFM_RATE
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class BlipNesSoundWrapper implements AudioOutInterface, FmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipNesSoundWrapper.class.getSimpleName());

    private static final double VOLUME = 13107 / 16384.;
    public static final AudioFormat nesAudioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE_HZ, 16, 1, true, false);

    private int tickCnt = 0;
    private String name;

    private BlipSoundProvider blipProvider;

    public BlipNesSoundWrapper(RegionDetector.Region region) {
        name = "nes";
        assert VOLUME <= 1.0;
        blipProvider = new BlipSoundProvider(name, RegionDetector.Region.USA, AbstractSoundManager.audioFormat,
                SoundProvider.SAMPLE_RATE_HZ);
    }

    @Override
    public void outputSample(int sample) {
        sample *= VOLUME; //VOLUME <= 1.0
        sample = SoundUtil.clampToShort(sample);
        blipProvider.playSample(sample, sample);
        tickCnt++;
    }

    @Override
    public void flushFrame(boolean waitIfBufferFull) {
        //DO NOTHING
//        LOG.info("flush, waitIfFull: {}, samples: {}" ,waitIfBufferFull, queueLen.get());
    }

    @Override
    public boolean bufferHasLessThan(int monoSamples) { //TODO check it is mono
//        LOG.info("bufferHasLessThan: {}, actual: {}", samples, queueLen.get());
        return tickCnt < monoSamples;
    }

    @Override
    public void onNewFrame() {
        if (tickCnt > 0) {
            blipProvider.onNewFrame();
            tickCnt = 0;
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
        throw new UnsupportedOperationException();
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public void tick() {
        //DO NOTHING
    }

    @Override
    public void pause() {
        //TODO
    }

    @Override
    public void resume() {
        //TODO
    }

    @Override
    public void destroy() {
        reset();
    }
}
