package omegadrive.sound.fm;

import omegadrive.util.RegionDetector;

import static omegadrive.sound.SoundProvider.LOG;
import static omegadrive.sound.SoundProvider.getFmSoundClock;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface FmProvider {

    static FmProvider createInstance(RegionDetector.Region region, int sampleRate) {
        double clock = getFmSoundClock(region);
        FmProvider fmProvider = new YM2612();
        fmProvider.init((int) clock, sampleRate);
//        FmProvider fmProvider = new Ym2612Nuke();
        LOG.info("FM instance, clock: " + clock + ", sampleRate: " + sampleRate);
        return fmProvider;
    }

    int reset();

    int read();

    int init(int clock, int rate);

    void write0(int addr, int data);

    void write1(int addr, int data);

    int readRegister(int type, int regNumber);

    void update(int[] buf_lr, int offset, int count);

    default void output(int[] buf_lr) {
        update(buf_lr, 0, buf_lr.length / 2);
    }

    void tick(double microsPerTick);

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
        public int init(int clock, int rate) {
            return 0;
        }

        @Override
        public void write0(int addr, int data) {

        }

        @Override
        public void write1(int addr, int data) {

        }

        @Override
        public int readRegister(int type, int regNumber) {
            return 0;
        }

        @Override
        public void update(int[] buf_lr, int offset, int end) {

        }

        @Override
        public void tick(double microsPerTick) {

        }

        @Override
        public void output(int[] buf_lr) {

        }
    };


}
