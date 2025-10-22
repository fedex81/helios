package omegadrive.sound;

import omegadrive.Device;
import omegadrive.sound.blip.IBlipSoundProvider.BlipBufferContext;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil;
import omegadrive.util.RegionDetector;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface SoundDevice extends Device, SystemProvider.NewFrameListener, BufferUtil.StepDevice {

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

    BlipBufferContext NO_DATA_CTX = new BlipBufferContext();

    default void updateRate(RegionDetector.Region region, int clockRate) {
    }

    default BlipBufferContext getBufferContext() {
        return NO_DATA_CTX;
    }

    @Override
    default void onNewFrame() {
    }

    SoundDeviceType getType();
}
