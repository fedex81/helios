package omegadrive.system.nes;

import com.grapeshot.halfnes.audio.AudioOutInterface;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.GenericAudioProvider;
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
public class NesSoundWrapper extends GenericAudioProvider implements AudioOutInterface {

    private static final Logger LOG = LogHelper.getLogger(NesSoundWrapper.class.getSimpleName());

    private static final double VOLUME = 13107 / 16384.;
    public static final AudioFormat nesAudioFormat = new AudioFormat(SoundProvider.SAMPLE_RATE_HZ, 16, 1, true, false);

    public NesSoundWrapper(RegionDetector.Region region) {
        super(nesAudioFormat);
        start();
    }

    @Override
    public void outputSample(int sample) {
        sample *= VOLUME;
        sample = sample < Short.MIN_VALUE ? Short.MIN_VALUE :
                (sample > Short.MAX_VALUE ? Short.MAX_VALUE : sample);
        addMonoSample(sample);
    }

    @Override
    public void flushFrame(boolean waitIfBufferFull) {
        //DO NOTHING
//        LOG.info("flush, waitIfFull: {}, samples: {}" ,waitIfBufferFull, queueLen.get());
    }

    @Override
    public boolean bufferHasLessThan(int monoSamples) { //TODO check it is mono
//        LOG.info("bufferHasLessThan: {}, actual: {}", samples, queueLen.get());
        return stereoQueueLen.get() < (monoSamples << 1);
    }

    @Override
    public void pause() {
        stop();
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public void destroy() {
        reset();
    }
}
