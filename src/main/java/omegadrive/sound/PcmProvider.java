package omegadrive.sound;

import omegadrive.util.RegionDetector;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public interface PcmProvider extends SoundDevice, SoundDevice.MutableDevice {

    void playSample(int left, int right);

    default void updateRegion(RegionDetector.Region region) {
        throw new RuntimeException("illegal");
    }

    default void updateRegion(RegionDetector.Region region, int clockRate) {
    }
    default SoundDeviceType getType() {
        return SoundDeviceType.PCM;
    }

    default int updateStereo16(int[] buf_lr, int offset, int count) {
        throw new RuntimeException("Not implemented");
    }

    default void setEnabled(boolean mute) {
    }

    @Override
    default boolean isMute() {
        return false;
    }

    PcmProvider NO_SOUND = new PcmProvider() {
        @Override
        public void playSample(int left, int right) {
        }

        @Override
        public int updateStereo16(int[] buf_lr, int offset, int count) {
            return 0;
        }
    };
}

