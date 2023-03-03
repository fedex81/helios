package s32x.util.blipbuffer;

import s32x.pwm.PwmUtil;

import static s32x.pwm.PwmUtil.setSigned16LE;

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
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i] = new BlipBuffer();
        }
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
        for (int i = bufs.length; --i >= 0; ) {
            bufs[i].endFrame(time);
        }
    }

    public int samplesAvail() {
        return bufs[LEFT].samplesAvail() << 1;
    }

    public void addDelta(int time, int leftDelta, int rightDelta) {
        bufs[LEFT].addDelta(time, leftDelta);
        bufs[RIGHT].addDelta(time, rightDelta);
    }

    static int[] preFilter = new int[0], postFilterL = new int[0], postFilterR = new int[0];
    static int prevSampleL, prevSampleR;

    public int readSamples16bitStereo(byte[] out, int pos, int countMono) {
        if (postFilterL.length < countMono) {
            postFilterL = new int[countMono];
            postFilterR = new int[countMono];
        }
        assert pos == 0;
        int actualMonoLeft = BlipBufferHelper.readSamples16bitMono(left(), postFilterL, pos, countMono);
        int actualMonoRight = BlipBufferHelper.readSamples16bitMono(right(), postFilterR, pos, countMono);
        assert actualMonoRight == actualMonoLeft;
        for (int i = 0; i < countMono; i++) {
            assert (short) postFilterL[i] == postFilterL[i];
            assert (short) postFilterR[i] == postFilterR[i];
            setSigned16LE((short) postFilterL[i], out, pos); //left
            setSigned16LE((short) postFilterR[i], out, pos + 2); //right
            pos += 4;
        }
        return actualMonoLeft;
    }

    public int readSamples16bitStereoFilter(byte[] out, int pos, int countMono) {
        if (preFilter.length < countMono) {
            preFilter = new int[countMono];
            postFilterL = new int[countMono];
            postFilterR = new int[countMono];
        }
        assert pos == 0;
        int actualMonoLeft = BlipBufferHelper.readSamples16bitMono(left(), preFilter, pos, countMono);
        prevSampleL = PwmUtil.dcBlockerLpfMono(preFilter, postFilterL, prevSampleL, actualMonoLeft);
        int actualMonoRight = BlipBufferHelper.readSamples16bitMono(right(), preFilter, pos, countMono);
        assert actualMonoRight == actualMonoLeft;
        prevSampleR = PwmUtil.dcBlockerLpfMono(preFilter, postFilterR, prevSampleR, actualMonoRight);
        for (int i = 0; i < countMono; i++) {
            setSigned16LE((short) postFilterL[i], out, pos); //left
            setSigned16LE((short) postFilterR[i], out, pos + 2); //right
            pos += 4;
        }
        return actualMonoLeft;
    }
}