package omegadrive.sound.fm;

import omegadrive.util.RegionDetector;

import static omegadrive.sound.SoundProvider.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface FmProvider {

    static FmProvider createInstance(RegionDetector.Region region) {
        double clock = getFmSoundClock(region);
        YM2612 fmProvider = new YM2612();
        fmProvider.init((int) clock, SAMPLE_RATE);
        LOG.info("FM instance, clock: " + clock + ", sampleRate: " + SAMPLE_RATE);
        return fmProvider;
    }

    int reset();

    int read();

    void write0(int addr, int data);

    void write1(int addr, int data);

    void update(int[] buf_lr, int offset, int end);

    default void output(int[] buf_lr) {
        update(buf_lr, 0, buf_lr.length / 2);
    }

    void synchronizeTimers(int length);

    FmProvider NO_SOUND = new FmProvider() {
        @Override
        public int reset() {
            return 0;
        }

        @Override
        public int read() {
            return 0;
        }

        @Override
        public void write0(int addr, int data) {

        }

        @Override
        public void write1(int addr, int data) {

        }

        @Override
        public void update(int[] buf_lr, int offset, int end) {

        }

        @Override
        public void synchronizeTimers(int length) {

        }

        @Override
        public void output(int[] buf_lr) {

        }
    };


}
