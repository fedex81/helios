package omegadrive.sound;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public interface PcmProvider extends SoundDevice {

    void updateFreqDelta(int freqDelta);

    void playSample(int left, int right);

    default SoundDeviceType getType() {
        return SoundDeviceType.PCM;
    }

    default void newFrame() {
    }

    PcmProvider NO_SOUND = new PcmProvider() {
        @Override
        public void playSample(int left, int right) {
        }

        @Override
        public void updateFreqDelta(int freqDelta) {
        }
    };
}

