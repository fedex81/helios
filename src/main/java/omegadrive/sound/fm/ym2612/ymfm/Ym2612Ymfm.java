package omegadrive.sound.fm.ym2612.ymfm;

import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.fm.VariableSampleRateSource;
import omegadrive.sound.fm.ym2612.Ym2612RegSupport;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class Ym2612Ymfm extends VariableSampleRateSource implements MdFmProvider {

    private static final Logger LOG = LogHelper.getLogger(Ym2612Ymfm.class);

    private final static int AUDIO_SCALE_BITS = 0;
    private final static int SAMPLE_DIVIDER = 6 * 24;

    private final Ym2612 chip;
    private final YmfmOutput output = new YmfmOutput();
    private final Ym2612RegSupport regSupport;

    private double cycleAccum;
    private int prevL, prevR;

    public Ym2612Ymfm(AudioFormat audioFormat, double fmClock) {
        super(fmClock / SAMPLE_DIVIDER, audioFormat, "Ymfm2612", AUDIO_SCALE_BITS);
        chip = new Ym2612(new YmfmInterface());
        regSupport = new Ym2612RegSupport();
        chip.reset();
    }


    @Override
    public void setMicrosPerTick(double microsPerTick) {
        setMicrosPerInputSample(microsPerTick);
    }

    @Override
    public void reset() {
        super.reset();
        chip.reset();
        cycleAccum = 0;
        prevL = prevR = 0;
        regSupport.reset();
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return regSupport.readRegister(type, regNumber);
    }

    @Override
    public void write(int addr, int data) {
        chip.write(addr, data);
        regSupport.write(addr, data);
    }

    @Override
    public int read() {
        return chip.readStatus();
    }

    @Override
    public void step() {
        spinOnce();
        cycleAccum += microsPerInputSample;
        addSample();
    }

    private void addSample() {
        if (cycleAccum > fmCalcsPerMicros) {
            super.addStereoSample(prevL, prevR);
            cycleAccum -= fmCalcsPerMicros;
        }
    }

    @Override
    protected void spinOnce() {
        chip.generate(output);
        //1st order lpf: p[n]=αp[n−1]+(1−α)pi[n] with α = 0.5
        //The cutoff frequency fco = fs*(1−α)/2πα, where fs is your sampling frequency.
        //fco ~= 8.5 khz
        int l = (prevL + output.data[0]) >> 1;
        int r = (prevR + output.data[1]) >> 1;
        prevL = Ymfm.clamp(l, Short.MIN_VALUE, Short.MAX_VALUE);
        prevR = Ymfm.clamp(r, Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override
    public FmCore getFmCore() {
        return FmCore.YMFM;
    }


    public byte[] getStateBytes() {
        YmfmSavedState state = YmfmSavedState.forSaving();
        chip.saveRestore(state);
        return state.toByteArray();
    }

    public void setStateBytes(byte[] data) {
        if (data == null || data.length == 0) {
            LOG.warn("Unable to restore {} state, FM will not work", getFmCore());
            chip.reset();
            return;
        }

        chip.saveRestore(YmfmSavedState.forRestoring(data));
        chip.invalidateCaches();
    }
}
