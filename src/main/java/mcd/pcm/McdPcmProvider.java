package mcd.pcm;

import omegadrive.sound.SoundDevice;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface McdPcmProvider extends SoundDevice {

    int NTSC_SH2CLOCK_MHZ = (int) (Util.GEN_NTSC_MCLOCK_MHZ * 3.0 / 7);
    int PAL_SH2CLOCK_MHZ = (int) (Util.GEN_PAL_MCLOCK_MHZ * 3.0 / 7);

    void playSample(int left, int right);

    void updateRegion(RegionDetector.Region region);

    default SoundDeviceType getType() {
        return SoundDeviceType.PCM;
    }

    default void newFrame() {
    }

    McdPcmProvider NO_SOUND = new McdPcmProvider() {
        @Override
        public void playSample(int left, int right) {
        }

        @Override
        public void updateRegion(RegionDetector.Region region) {
        }

        @Override
        public int updateStereo16(int[] buf_lr, int offset, int count) {
            return 0;
        }
    };
}
