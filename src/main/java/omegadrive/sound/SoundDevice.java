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

    class SampleBufferContext {
        public byte[] lineBuffer;
        //16 bit stereo @ 44100 hz = 44100*4 bytes
        public int stereoBytesLen;
    }

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

    SoundDeviceType getType();

    default SampleBufferContext getFrameData() {
        return null;
    }
}
