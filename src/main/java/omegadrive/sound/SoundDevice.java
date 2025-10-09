package omegadrive.sound;

import omegadrive.Device;
import omegadrive.system.SystemProvider;
import omegadrive.util.RegionDetector;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface SoundDevice extends Device, SystemProvider.NewFrameListener {

    interface MutableDevice {
        void setEnabled(boolean mute);

        boolean isMute();
    }

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

    default void updateMono8(byte[] output) {
        updateMono8(output, 0, output.length);
    }

    default void updateRate(RegionDetector.Region region, int clockRate) {
    }

    default SampleBufferContext getFrameData() {
        return null;
    }

    default void tick() {
    }

    @Override
    default void onNewFrame() {
    }

    SoundDeviceType getType();
}
