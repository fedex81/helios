package omegadrive.sound.psg;

import omegadrive.SystemLoader;
import omegadrive.sound.SoundDevice;
import omegadrive.sound.blip.BlipSoundProvider;
import omegadrive.sound.blip.IBlipSoundProvider;
import omegadrive.sound.blip.IBlipSoundProvider.BlipBufferContext;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public abstract class BlipCapableDevice implements SoundDevice {

    private final static Logger LOG = LogHelper.getLogger(BlipCapableDevice.class.getSimpleName());

    protected static int DEFAULT_CLOCK_RATE = AbstractSoundManager.SAMPLE_RATE_HZ;

    protected final IBlipSoundProvider blipProvider;

    protected final SystemLoader.SystemType type;

    protected final int VOLUME_ATTENUATION;
    protected byte[] output = new byte[0];

    protected int ticksPerFrame = 0;

    private final AudioFormat af;

    protected BlipCapableDevice(SystemLoader.SystemType type, String name, AudioFormat audioFormat) {
        this(type, name, audioFormat, DEFAULT_CLOCK_RATE);
    }

    protected BlipCapableDevice(SystemLoader.SystemType type, String name, AudioFormat audioFormat, int clockRate) {
        this.type = type;
        blipProvider = new BlipSoundProvider(name, RegionDetector.Region.USA, audioFormat,
                clockRate);
        af = audioFormat;
        VOLUME_ATTENUATION = type.isMdBased() ? 3 : 0;
    }

    protected byte[] getOutputBuffer() {
        return getBufferContext().lineBuffer;
    }

    protected abstract void fillBuffer(byte[] output, int offset, int end);

    @Override
    public void updateRate(RegionDetector.Region region, int clockRate) {
        blipProvider.updateRegion(region, clockRate);
        this.ticksPerFrame = (int) (1.0 * clockRate / region.getFps());
        if (ticksPerFrame != output.length) {
            output = new byte[ticksPerFrame];
        }
    }

    @Override
    public BlipBufferContext getBufferContext() {
        return blipProvider.getBufferContext();
    }

    @Override
    public void onNewFrame() {
        playAccumulatedSamples();
        //generate new samples
        blipProvider.onNewFrame();
    }

    protected void playAccumulatedSamples() {
        fillBuffer(getOutputBuffer());
        for (int i = 0; i < output.length; i++) {
            blipProvider.playSample8(output[i] >> VOLUME_ATTENUATION);
        }
    }

    private void fillBuffer(byte[] outputBuffer) {
        fillBuffer(outputBuffer, 0, outputBuffer.length);
    }
}
