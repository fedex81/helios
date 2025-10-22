package omegadrive.sound.fm.ym2612;

import omegadrive.SystemLoader.SystemType;
import omegadrive.sound.fm.MdFmProvider;
import omegadrive.sound.psg.BlipCapableDevice;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.util.Arrays;

import static omegadrive.util.SoundUtil.AF_16bit_Stereo;

/**
 *
 */
public class BlipYM2612Fast extends BlipCapableDevice implements MdFmProvider {

    private final static Logger LOG = LogHelper.getLogger(BlipYM2612Fast.class.getSimpleName());

    private static final AudioFormat audioFormat = AF_16bit_Stereo;

    /**
     * at 44.1khz, one mono sample every 22.68 micros
     * 1000/44.1 = 22.68 micros
     */
    private static final double SAMPLE_TARGET_BASE_MICROS = 1000 / (audioFormat.getSampleRate() / 1000);

    /**
     * mono samples per blip invocation
     */
    private static final int MONO_SAMPLES_BLIP = 10;

    private static final double SAMPLE_TARGET = SAMPLE_TARGET_BASE_MICROS * MONO_SAMPLES_BLIP;

    protected YM2612 fm;

    private final int[] fmOutput = new int[MONO_SAMPLES_BLIP << 1];

    double microAcc = 0, microsPerTick;
    protected int tickCnt = 0, samplePlayed = 0;

    private RegionDetector.Region region;

    public static BlipYM2612Fast createInstance(SystemType systemType,
                                                RegionDetector.Region region, int clock, int sampleRateHz) {
        BlipYM2612Fast s = new BlipYM2612Fast(systemType);
        s.fm = new YM2612(clock, sampleRateHz);
        s.region = region;
        return s;
    }

    private BlipYM2612Fast(SystemType systemType) {
        super(systemType, systemType + "-ym2612-fast", audioFormat);
    }

    @Override
    public void setMicrosPerTick(double microsPerTick) {
        fm.setMicrosPerTick(microsPerTick);
        this.microsPerTick = microsPerTick;
    }

    @Override
    public void write(int addr, int data) {
        fm.write(addr, data);
    }

    protected void playAccumulatedSamples(int num) {
        Arrays.fill(fmOutput, 0);
        int numStereo = updateStereo16(fmOutput, 0, num);
        for (int i = 0; i < numStereo; i += 2) {
            int l = fmOutput[i] << 0;
            int r = fmOutput[i + 1] << 0;
            assert (short) l == l && (short) r == r;
            blipProvider.playSample16((short) l, (short) r);
            samplePlayed++;
        }
    }

    double totMicro = 0;

    @Override
    public void step() {
        fm.step();
        tickCnt++;
        microAcc += microsPerTick;
        checkAndPlay();
        totMicro += microsPerTick;
    }

    private void checkAndPlay() {
        while (microAcc >= SAMPLE_TARGET) {
            playAccumulatedSamples(MONO_SAMPLES_BLIP);
            microAcc -= SAMPLE_TARGET;
        }
    }

    @Override
    public void onNewFrame() {
//        adjustMicrosPerTick();
        totMicro = 0;
        if (tickCnt > 0) {
            checkAndPlay();
            //generate new samples
            blipProvider.onNewFrame();
            tickCnt = 0;
            samplePlayed = 0;
        } else {
            LogHelper.logWarnOnce(LOG, "newFrame called with tickCnt: {}", tickCnt);
        }
    }

    private final double adjustment = 0.0001;

    /**
     * I don't think this is needed?
     */
    private void adjustMicrosPerTick() {
        double target = region.getFrameIntervalMs() * 1000;
        double val = target - totMicro;
        if (Math.abs(val) > 1) {
            double prev = microsPerTick;
            microsPerTick *= 1.0 + Math.signum(val) * adjustment;
            LOG.info("{} adjusting microsPerTick: {} -> {}, target: {}, deltaTarget: {}",
                    region, prev, microsPerTick, target, val);
        }
    }

    @Override
    public void updateRate(RegionDetector.Region region, int clockRate) {
        blipProvider.updateRegion(region, clockRate);
        this.region = region;
    }

    @Override
    protected void fillBuffer(byte[] output, int offset, int end) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int updateStereo16(int[] buf_lr, int offset, int count) {
        return fm.updateStereo16(buf_lr, offset, count);
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return fm.readRegister(type, regNumber);
    }

    @Override
    public int read() {
        return fm.read();
    }
}
