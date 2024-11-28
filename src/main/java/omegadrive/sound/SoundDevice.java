package omegadrive.sound;

import omegadrive.Device;
import omegadrive.util.RegionDetector.Region;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface SoundDevice extends Device, Device.Tickable, BaseVdpAdapterEventSupport.VdpEventListener {

    enum SoundDeviceType {
        NONE(0), FM(1), PSG(2), PWM(4), PCM(8), CDDA(16);

        private final int bit;

        SoundDeviceType(int b) {
            this.bit = b;
        }

        public int getBit() {
            return bit;
        }
    }

    class SampleBufferContext {
        public byte[] lineBuffer;
        //16 bit stereo @ 44100 hz = 44100*4 bytes
        public int stereoBytesLen;
    }

    SoundDeviceType getType();

    SampleBufferContext getFrameData();

    void updateRate(Region region, int clockRate);
}
