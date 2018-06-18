package omegadrive.sound;

import omegadrive.GenesisProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.util.RegionDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface SoundProvider {
    Logger LOG = LogManager.getLogger(SoundProvider.class.getSimpleName());

    long PAL_PSG_CLOCK = GenesisProvider.PAL_MCLOCK_MHZ / 15; // 3546893
    long NTSC_PSG_CLOCK = GenesisProvider.NTSC_MCLOCK_MHZ / 15; //3579545;
    long NTSC_FM_CLOCK = GenesisProvider.NTSC_MCLOCK_MHZ / 7; //7670442;
    long PAL_FM_CLOCK = GenesisProvider.PAL_MCLOCK_MHZ / 7; //7600485;

    int SAMPLE_RATE = 22050;

    PsgProvider getPsg();

    FmProvider getFm();

    static int getPsgBufferByteSize(int fps) {
        return getFmBufferIntSize(fps) / 2;
    }

    static int getFmBufferIntSize(int fps) {
        int res = 2 * SAMPLE_RATE / fps;
        return res % 2 == 0 ? res : res + 1;
    }

    static double getPsgSoundClock(RegionDetector.Region r) {
        return (RegionDetector.Region.USA == r ? NTSC_PSG_CLOCK : PAL_PSG_CLOCK) / 32d;
    }

    static double getFmSoundClock(RegionDetector.Region r) {
        return (RegionDetector.Region.USA == r ? NTSC_FM_CLOCK : PAL_FM_CLOCK);
    }

    void output(int fps);

    void reset();

    default boolean isRecording() {
        return false;
    }

    default void setRecording(boolean recording) {
        //NO OP
    }

    boolean isMute();

    void setMute(boolean mute);

    SoundProvider NO_SOUND = new SoundProvider() {
        @Override
        public PsgProvider getPsg() {
            return PsgProvider.NO_SOUND;
        }

        @Override
        public FmProvider getFm() {
            return FmProvider.NO_SOUND;
        }

        @Override
        public void output(int fps) {

        }

        @Override
        public void reset() {

        }

        @Override
        public boolean isMute() {
            return false;
        }

        @Override
        public void setMute(boolean mute) {
        }
    };

    default boolean isSoundWorking() {
        return false;
    }
}
