package omegadrive.sound;

import omegadrive.util.Util;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface PwmProvider extends SoundDevice, SoundDevice.MutableDevice {

    int NTSC_SH2CLOCK_MHZ = (int) (Util.GEN_NTSC_MCLOCK_MHZ * 3.0 / 7);
    int PAL_SH2CLOCK_MHZ = (int) (Util.GEN_PAL_MCLOCK_MHZ * 3.0 / 7);

    void updatePwmCycle(int cycle);

    void playSample(int left, int right);

    default int updateStereo16(int[] buf_lr, int offset, int count) {
        throw new RuntimeException("Not implemented");
    }

    default SoundDeviceType getType() {
        return SoundDeviceType.PWM;
    }

    @Override
    default void setEnabled(boolean mute) {
    }

    @Override
    default boolean isMute() {
        return false;
    }

    PwmProvider NO_SOUND = new PwmProvider() {

        @Override
        public void updatePwmCycle(int cycle) {
        }

        @Override
        public void playSample(int left, int right) {
        }

        @Override
        public int updateStereo16(int[] buf_lr, int offset, int count) {
            return 0;
        }
    };
}
