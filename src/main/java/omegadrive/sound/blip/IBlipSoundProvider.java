package omegadrive.sound.blip;

import omegadrive.sound.SoundDevice;
import omegadrive.system.SystemProvider;
import omegadrive.util.RegionDetector;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public interface IBlipSoundProvider extends SystemProvider.NewFrameListener {

    BlipBufferContext getBufferContext();

    void updateRegion(RegionDetector.Region region, int clockRate);

    /**
     * 16 bit signed samples - stereo
     */
    void playSample16(int lsample, int rsample);

    /**
     * 16 bit signed samples - mono
     */
    default void playSample16(int sample) {
        playSample16(sample, sample);
    }

    /**
     * 8 bit signed samples - mono
     */
    default void playSample8(int lsample, int rsample) {
        playSample16(lsample << 8, rsample << 8);
    }

    /**
     * 8 bit signed samples - mono
     */
    default void playSample8(int sample) {
        playSample16(sample << 8, sample << 8);
    }

    class BlipBufferContext extends SoundDevice.SampleBufferContext {
        public BlipBufferIntf blipBuffer;
        public final AtomicInteger inputClocksForInterval = new AtomicInteger();

        @Override
        public String toString() {
            return new StringJoiner(", ", BlipBufferContext.class.getSimpleName() + "[", "]")
                    .add("inputClocksForInterval=" + inputClocksForInterval)
                    .toString();
        }
    }
}
