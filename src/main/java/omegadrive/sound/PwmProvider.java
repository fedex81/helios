package omegadrive.sound;

import omegadrive.system.Genesis;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface PwmProvider extends SoundDevice {

    int NTSC_SH2CLOCK_MHZ = (int) (Genesis.NTSC_MCLOCK_MHZ * 3.0 / 7);
    int PAL_SH2CLOCK_MHZ = (int) (Genesis.PAL_MCLOCK_MHZ * 3.0 / 7);

    void updatePwmCycle(int cycle);

    void playSample(int left, int right);

    default SoundDeviceType getType() {
        return SoundDeviceType.PWM;
    }

    default void newFrame() {
    }

    ;

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
