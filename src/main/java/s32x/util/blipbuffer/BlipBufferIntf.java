package s32x.util.blipbuffer;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public interface BlipBufferIntf {

    void setSampleRate(int rate, int msec);

    void setClockRate(int rate);

    int clockRate();

    int getBufLen();

    void clear();

    void setVolume(double v);

    int countSamples(int time);

    void addDelta(int time, int deltaL, int deltaR);

    void endFrame(int time);

    int samplesAvail();

    int readSamples16bitStereo(byte[] out, int pos, int countMono);

    default void addDelta(int time, int delta) {
        addDelta(time, delta, delta);
    }
}
