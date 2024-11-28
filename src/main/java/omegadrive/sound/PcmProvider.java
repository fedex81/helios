package omegadrive.sound;

import omegadrive.util.RegionDetector;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public interface PcmProvider extends SoundDevice {

    void playSample(int left, int right);

    default SoundDeviceType getType() {
        return SoundDeviceType.PCM;
    }

    PcmProvider NO_SOUND = new PcmProvider() {
        @Override
        public void tick() {
        }

        @Override
        public SampleBufferContext getFrameData() {
            return null;
        }

        @Override
        public void updateRate(RegionDetector.Region region, int clockRate) {
        }

        @Override
        public void playSample(int left, int right) {
        }
    };
}

