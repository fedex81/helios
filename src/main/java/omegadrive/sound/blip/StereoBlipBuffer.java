package omegadrive.sound.blip;

/**
 * StereoBuffer
 *
 * @Copyright Shay Greeen
 * @Copyright Federico Berti
 */
public final class StereoBlipBuffer implements BlipBufferIntf {
    private final BlipBuffer[] bufs = new BlipBuffer[2];
    private final String name;
    private static final int LEFT = 0, RIGHT = 1;


    // Same behavior as in BlipBuffer unless noted

    public StereoBlipBuffer(String name) {
        bufs[LEFT] = new BlipBuffer();
        bufs[RIGHT] = new BlipBuffer();
        this.name = name;
    }

    public void setSampleRate(int rate, int msec) {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].setSampleRate(rate, msec);
        }
    }

    public void setClockRate(int rate) {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].setClockRate(rate);
        }
    }

    public int clockRate() {
        assert bufs[LEFT].clockRate() == bufs[RIGHT].clockRate();
        return bufs[LEFT].clockRate();
    }

    @Override
    public int getBufLen() {
        return bufs[LEFT].getBufLen();
    }

    public int countSamples(int time) {
        return bufs[LEFT].countSamples(time);
    }

    public void clear() {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].clear();
        }
    }

    public void setVolume(double v) {
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].setVolume(v);
        }
    }

    public BlipBuffer left() {
        return bufs[LEFT];
    }

    public BlipBuffer right() {
        return bufs[RIGHT];
    }

    public void endFrame(int time) {
        bufs[LEFT].endFrame(time);
        bufs[RIGHT].endFrame(time);
    }

    public int samplesAvail() {
        assert bufs[LEFT].samplesAvail() == bufs[RIGHT].samplesAvail();
        return bufs[LEFT].samplesAvail();
    }

    public void addDelta(int time, int leftDelta, int rightDelta) {
        bufs[LEFT].addDelta(time, leftDelta);
        bufs[RIGHT].addDelta(time, rightDelta);
    }

    public int readSamples16bitStereo(byte[] out, int pos, int countMono) {
        return BlipBufferHelper.readSamples16bitStereo(this, out, pos, countMono);
    }
}