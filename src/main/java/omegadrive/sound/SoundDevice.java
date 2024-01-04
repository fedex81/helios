package omegadrive.sound;

import omegadrive.Device;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface SoundDevice extends Device {

    enum SoundDeviceType {
        NONE(0), FM(1), PSG(2), PWM(4), PCM(8);

        private final int bit;

        SoundDeviceType(int b) {
            this.bit = b;
        }

        public int getBit() {
            return bit;
        }
    }

    SoundDevice NO_SOUND = () -> SoundDeviceType.NONE;

    /**
     * Typical FM output
     */
    default int updateStereo16(int[] buf_lr, int offset, int count) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Typical PSG output
     */
    default void updateMono8(byte[] output, int offset, int end) {
        throw new RuntimeException("Not implemented");
    }

    default void updateMono8(byte[] output) {
        updateMono8(output, 0, output.length);
    }

    SoundDeviceType getType();
}
