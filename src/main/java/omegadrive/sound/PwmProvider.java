package omegadrive.sound;

import omegadrive.util.RegionDetector;
import omegadrive.util.Util;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface PwmProvider extends SoundDevice {

    int NTSC_SH2CLOCK_MHZ = (int) (Util.GEN_NTSC_MCLOCK_MHZ * 3.0 / 7);
    int PAL_SH2CLOCK_MHZ = (int) (Util.GEN_PAL_MCLOCK_MHZ * 3.0 / 7);

    void updatePwmCycle(int cycle);

    void playSample(int left, int right);

    default SoundDeviceType getType() {
        return SoundDeviceType.PWM;
    }

    PwmProvider NO_SOUND = new PwmProvider() {
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
        public void updatePwmCycle(int cycle) {
        }

        @Override
        public void playSample(int left, int right) {
        }
    };
}
