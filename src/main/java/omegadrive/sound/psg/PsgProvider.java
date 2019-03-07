package omegadrive.sound.psg;

import omegadrive.sound.psg.white1.SN76496;
import omegadrive.sound.psg.white2.SN76489Psg;
import omegadrive.util.RegionDetector;

import static omegadrive.sound.SoundProvider.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface PsgProvider {

    int PSG_OUTPUT_SAMPLE_SIZE = 8;
    int PSG_OUTPUT_CHANNELS = 1;

    boolean USE_NEW_PSG = true;

    static PsgProvider createInstance(RegionDetector.Region region, int sampleRate) {
        int clockHz = (int) getPsgSoundClock(region);
        LOG.info("PSG instance, clockHz: " + clockHz + ", sampleRate: " + sampleRate);
        PsgProvider psgProvider = SN76489Psg.createInstance(clockHz, sampleRate);
        if (!USE_NEW_PSG) {
            psgProvider = new SN76496(getPsgSoundClockScaled(region), sampleRate);
            psgProvider.init();
        }
        return psgProvider;
    }

    void init();

    void write(int data);

    void output(byte[] output);

    void output(byte[] output, int offset, int end);

    void reset();

    PsgProvider NO_SOUND = new PsgProvider() {

        @Override
        public void init() {

        }

        @Override
        public void write(int data) {

        }

        @Override
        public void output(byte[] ouput) {

        }

        @Override
        public void output(byte[] output, int offset, int end) {

        }

        @Override
        public void reset() {

        }
    };
}
