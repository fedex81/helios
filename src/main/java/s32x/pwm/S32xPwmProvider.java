package s32x.pwm;

import omegadrive.sound.PwmProvider;
import omegadrive.sound.fm.GenericAudioProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.SoundFilterUtil;
import omegadrive.util.SoundFilterUtil.DcBlockLpfHistory;
import org.slf4j.Logger;

import java.util.Arrays;

import static omegadrive.sound.javasound.AbstractSoundManager.audioFormat;
import static omegadrive.util.SoundFilterUtil.dcBlockerLpf;
import static omegadrive.util.SoundUtil.clampToShort;
import static omegadrive.util.Util.th;
import static s32x.pwm.Pwm.CYCLE_LIMIT;
import static s32x.pwm.PwmUtil.*;
import static s32x.pwm.PwmUtil.PwmStats.NO_STATS;

/**
 * S32xPwmProvider
 *
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 *
 * Most sw uses cycle ~= 1045 for 22Khz
 * - Bad Apple 32x, cycle 719*60 -> 43khz
 * - Space Harrier, cycle 1474*60 -> 88khz
 * - OutRom.bin, pwm only
 *
 * TODO: check?? other problematic stuff: Mars Test #2 (should be mute?)
 */
public class S32xPwmProvider extends GenericAudioProvider implements PwmProvider {

    private static final Logger LOG = LogHelper.getLogger(S32xPwmProvider.class.getSimpleName());
    private static final boolean collectStats = Boolean.parseBoolean(System.getProperty("helios.32x.pwm.stats", "false"));

    private final float sh2ClockMhz;
    private float scale = 0;
    private int cycle;
    private final int fps;

    private boolean shouldPlay;
    private Warmup warmup = NO_WARMUP;
    private PwmStats stats = NO_STATS;

    public static class PwmProcessingData {
        int[] rawBuffer = new int[0];
        int[] interpBuffer = new int[0];
        DcBlockLpfHistory filterHistory = new DcBlockLpfHistory();

        public void reset() {
            Arrays.fill(rawBuffer, 0);
            Arrays.fill(interpBuffer, 0);
            filterHistory.reset();
        }
    }

    private PwmProcessingData ppd = new PwmProcessingData();

    public S32xPwmProvider(RegionDetector.Region region) {
        super(audioFormat);
        this.fps = region.getFps();
        this.sh2ClockMhz = region == RegionDetector.Region.EUROPE ? PAL_SH2CLOCK_MHZ : NTSC_SH2CLOCK_MHZ;
        if (collectStats) this.stats = new PwmStats();
    }

    @Override
    public void updatePwmCycle(int cycle) {
        float c = cycle;
        int pwmSamplesPerFrame = (int) (sh2ClockMhz / (fps * cycle));
        scale = (Short.MAX_VALUE << 1) / c;
        shouldPlay = cycle >= CYCLE_LIMIT;
        this.cycle = cycle;
        start();
        if (!shouldPlay) {
            LOG.error("Unsupported cycle setting: {}, limit: {}, pwmSamplesPerFrame: {}",
                    cycle, CYCLE_LIMIT, pwmSamplesPerFrame);
            stop();
        } else {
            LOG.info("PWM cycle setting: {}, limit: {}, pwmSamplesPerFrame: {}",
                    cycle, CYCLE_LIMIT, pwmSamplesPerFrame);
            warmup = WARMUP;
            warmup.reset();
            warmup.isWarmup = true;
            LOG.info("PWM warmup start");
        }
    }

    //NOTE source is running ~22khz
    @Override
    public void playSample(int left, int right) {
        if (!shouldPlay) {
            return;
        }
        int vleft = (int) ((left - (cycle >> 1)) * scale);
        int vright = (int) ((right - (cycle >> 1)) * scale);
        short sleft = (short) vleft;
        short sright = (short) vright;
        if (sleft != vleft || sright != vright) {
            float sc = scale;
            scale -= 1;
            LOG.warn("PWM value out of range (16 bit signed), L/R: {}/{}, scale: {}, " +
                    "pwmVal: {}/{}", th(sleft), th(sright), sc, left, right);
            LOG.warn("Reducing scale: {} -> {}", sc, scale);
            sleft = clampToShort(vleft);
            sright = clampToShort(vright);
        }
        addStereoSample(sleft, sright);
    }

    /**
     * TODO all the post processing should be done in the SoundManager
     */
    @Override
    public int updateStereo16(int[] buf_lr, int offset, int countMono) {
        int expStereoSamples = countMono << 1;
        if (countMono == 0 || !running) {
            return expStereoSamples;
        }
        if (ppd.rawBuffer.length < buf_lr.length) {
            ppd.rawBuffer = buf_lr.clone();
        }
        int rawSamplesStereo = super.updateStereo16(ppd.rawBuffer, offset, countMono);
        if (collectStats) stats.monoSamplesPull += rawSamplesStereo >> 1;
        if (rawSamplesStereo == 0) {
            if (collectStats) stats.monoSamplesFiller += expStereoSamples >> 1;
            LogHelper.logWarnOnce(LOG, "Sample requested {}, zero available!", expStereoSamples >> 1);
            fillWithLatestValues(buf_lr, expStereoSamples);  //TODO not great
            return expStereoSamples;

        }
        int[] srcBuffer = ppd.rawBuffer;
        if (rawSamplesStereo < expStereoSamples) {
            if (ppd.interpBuffer.length < expStereoSamples) {
                ppd.interpBuffer = buf_lr.clone();
            }
            SoundFilterUtil.interpolateInterleavedStereo(ppd.rawBuffer, rawSamplesStereo, ppd.interpBuffer, expStereoSamples);
            srcBuffer = ppd.interpBuffer;
        }
        //this is needed, see Tempo
        dcBlockerLpf(srcBuffer, buf_lr, ppd.filterHistory, expStereoSamples);
        warmup.doWarmup(buf_lr, expStereoSamples);
        return expStereoSamples;
    }

    private void fillWithLatestValues(int[] buf_lr, int stereoSamples) {
        for (int i = 0; i < stereoSamples; i += 2) {
            buf_lr[i] = (int) ppd.filterHistory.lastOutL;
            buf_lr[i + 1] = (int) ppd.filterHistory.lastOutR;
        }
    }

    @Override
    public SoundDeviceType getType() {
        return SoundDeviceType.PWM;
    }

    @Override
    public void reset() {
        shouldPlay = false;
        WARMUP.reset();
        ppd.reset();
        super.reset();
    }
}
