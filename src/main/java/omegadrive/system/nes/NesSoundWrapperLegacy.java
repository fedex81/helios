package omegadrive.system.nes;

import com.grapeshot.halfnes.audio.AudioOutInterface;
import omegadrive.sound.fm.FmProvider;
import omegadrive.util.RegionDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;

/**
 * NesSoundWrapper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
@Deprecated
public class NesSoundWrapperLegacy implements AudioOutInterface, FmProvider {

    private static final Logger LOG = LogManager.getLogger(NesSoundWrapperLegacy.class.getSimpleName());

    protected volatile int[] monoBuffer;
    protected volatile int monoBufPtr = 0;
    protected int samplesPerFrame;
    double VOLUME = 13107 / 16384.;

    public NesSoundWrapperLegacy(RegionDetector.Region region, AudioFormat audioFormat) {
        int channels = audioFormat.getChannels();
        int sampleRate = (int) audioFormat.getSampleRate();
        this.samplesPerFrame = (int) Math.ceil((sampleRate * channels) / region.getFps());
        this.monoBuffer = new int[samplesPerFrame];
    }

    @Override
    public int update(int[] buf_lr, int offset, int count) {
        offset <<= 1;
        int end = (count << 1) + offset;

        int res = monoBufPtr;
//        LOG.info("update " + res);

        int k = 0, i = 0;
        for (k = offset; k < end && i < res; k += 2, i++) {
            buf_lr[k] = monoBuffer[i];
            buf_lr[k + 1] = monoBuffer[i];
        }
        monoBufPtr -= i;
        return res;
    }

    @Override
    public void outputSample(int sample) {
        if (monoBufPtr > monoBuffer.length - 1) {
            return;
        }
        sample *= VOLUME;
        if (sample < -32768) {
            sample = -32768;
            //System.err.println("clip");
        }
        if (sample > 32767) {
            sample = 32767;
            //System.err.println("clop");
        }
        //mono
        monoBuffer[monoBufPtr] = sample;
        monoBufPtr++;
    }

    @Override
    public void flushFrame(boolean waitIfBufferFull) {
        //DO NOTHING
    }

    @Override
    public boolean bufferHasLessThan(int samples) {
        return false;
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void destroy() {

    }

    @Override
    public void init(int clock, int rate) {

    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public void tick(double microsPerTick) {

    }

    @Override
    public void reset() {

    }
}
