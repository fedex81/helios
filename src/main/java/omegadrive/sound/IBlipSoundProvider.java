package omegadrive.sound;

import omegadrive.system.SystemProvider;
import omegadrive.util.RegionDetector;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public interface IBlipSoundProvider extends SystemProvider.NewFrameListener {

    SoundDevice.SampleBufferContext getDataBuffer();

    void updateRegion(RegionDetector.Region region, int clockRate);

    /**
     * 16 bit signed samples
     */
    void playSample(int lsample, int rsample);
}
