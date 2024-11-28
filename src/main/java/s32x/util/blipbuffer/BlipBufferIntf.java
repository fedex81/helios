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

    /**
     * @return mono 16 bit samples read, byte[] out.len == val << 2
     */
    @Deprecated
    int readSamples16bitStereo(byte[] out, int pos, int countMono);

    /**
     * @return mono 16 bit samples read, byte[] out.len == val << 1
     * as the out buffer is bigger (usually double) than one frame worth of data
     */
    default int readSamples16bitStereoLen(byte[] out, int pos, int countMono) {
        return readSamples16bitStereo(out, pos, countMono) << 2;
    }

    default void addDelta(int time, int delta) {
        addDelta(time, delta, delta);
    }
}
