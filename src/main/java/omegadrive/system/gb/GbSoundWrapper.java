package omegadrive.system.gb;

import eu.rekawek.coffeegb.Gameboy;
import eu.rekawek.coffeegb.sound.SoundOutput;
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
public class GbSoundWrapper implements SoundOutput, FmProvider {

    private static final Logger LOG = LogManager.getLogger(GbSoundWrapper.class.getSimpleName());

    protected volatile int[] monoBuffer;
    protected volatile int monoBufPtr = 0;
    protected int samplesPerFrame;
    private int tick, divider;

    public GbSoundWrapper(RegionDetector.Region region, AudioFormat audioFormat) {
        int channels = audioFormat.getChannels();
        int sampleRate = (int) audioFormat.getSampleRate();
        this.samplesPerFrame = (int) Math.ceil((sampleRate * channels) / region.getFps());
        this.monoBuffer = new int[samplesPerFrame];
        this.divider = (int) (Gameboy.TICKS_PER_SEC / audioFormat.getSampleRate());
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
    public void play(int left, int right) {
        if (tick++ != 0) {
            tick %= divider;
            return;
        }
        if (monoBufPtr > monoBuffer.length - 1) {
            return;
        }
        int mono8 = ((left + right) >> 1) + Byte.MIN_VALUE; //signed
        monoBuffer[monoBufPtr++] = mono8 << 8; //16 bit
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

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }
}
