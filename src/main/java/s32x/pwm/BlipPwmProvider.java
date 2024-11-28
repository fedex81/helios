package s32x.pwm;

import omegadrive.sound.BlipBaseSound;
import omegadrive.sound.PwmProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector.Region;
import org.slf4j.Logger;
import s32x.util.blipbuffer.BlipBufferHelper;

import javax.sound.sampled.AudioFormat;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 * <p>
 */
public class BlipPwmProvider extends BlipBaseSound.BlipBaseSoundImpl implements PwmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipPwmProvider.class.getSimpleName());

    public static final int DEFAULT_PWM_CYCLE = 1042;

    @Override
    public int getSample16bit(boolean left) {
        throw new RuntimeException("Invalid");
    }

    private final float sh2ClockMhz;
    private int cycle = DEFAULT_PWM_CYCLE;
    private double scale = 1.0;
//    int[] preFilter = new int[0];
//    int[] pfPrev = new int[2];
//    static final double pfAlpha = 0.995;
//    private Warmup warmup = NO_WARMUP;

    public BlipPwmProvider(Region region, AudioFormat audioFormat) {
        super("pwm", region,
                (region == Region.EUROPE ? PAL_SH2CLOCK_MHZ : NTSC_SH2CLOCK_MHZ) / DEFAULT_PWM_CYCLE, Channel.STEREO,
                audioFormat);
        sh2ClockMhz = region == Region.EUROPE ? PAL_SH2CLOCK_MHZ : NTSC_SH2CLOCK_MHZ;
        updatePwmCycle(DEFAULT_PWM_CYCLE);
    }

    @Override
    public void updatePwmCycle(int cycle) {
        if (this.cycle != cycle) {
            LOG.info("PWM cycle: {}", cycle);
            this.cycle = cycle;
            if (cycle != 0) {
                blipProvider.updateRegion(region, (int) (sh2ClockMhz / cycle));
                scale = (Short.MAX_VALUE << 1) / (double) cycle;
            }
        }
//        BlipBufferContext context = blipProvider.getBlipContextReference().get();
//        if (cycle == context.cycle) {
//            return;
//        }
//        BlipBufferIntf blip = new StereoBlipBuffer("pwm");
//        blip.setSampleRate((int) pwmAudioFormat.getSampleRate(), BUF_SIZE_MS);
//        blip.setClockRate((int) (sh2ClockMhz / cycle));
//
//        BlipBufferContext bbc = new BlipBufferContext();
//        bbc.cycle = cycle;
//        bbc.scale = (Short.MAX_VALUE << 1) / (float) cycle;
//        bbc.inputClocksForInterval = (int) (1.0 * blip.clockRate() * frameIntervalMs / 1000.0);
//
//        int outSamplesPerInterval = (int) (pwmAudioFormat.getSampleRate() * BUF_SIZE_MS / 1000.0);
//        preFilter = new int[outSamplesPerInterval];
//        bbc.lineBuffer = new byte[0];
//        bbc.blipBuffer = blip;
//        ref.set(bbc);
//        warmup = WARMUP;
//        warmup.reset();
//        warmup.isWarmup = true;
//        logInfo(bbc);
//        LOG.info("PWM warmup start");
    }

    //TODO if framerate slows down we get periods where the wave goes back to zero -> poor sound quality
    //TODO S32xPwmProvider fills the gaps and it sounds better
    @Override
    public void playSample(int left, int right) {
        blipProvider.playSample(scalePwmSample(left), scalePwmSample(right));
        tickCnt++;
    }

    private int scalePwmSample(int sample) {
        int vsample = (int) ((sample - (cycle >> 1)) * scale);
        short scaled = (short) vsample;
        if (scaled != vsample) {
            float scalef = (float) scale;
            scale -= 1;
            LOG.warn("PWM value out of range (16 bit signed): {}, scale: {}, " +
                    "pwmVal: {}", th(scaled), scalef, sample);
            LOG.warn("Reducing scale: {} -> {}", scalef, scale);
            scaled = (short) BlipBufferHelper.clampToShort(vsample);
        }
        return scaled;
    }

//    private void logInfo(BlipBufferContext ctx) {
//        int outSamplesPerInterval = (int) (pwmAudioFormat.getSampleRate() * BUF_SIZE_MS / 1000.0);
//        int inSamplesPerInterval = (int) (ctx.blipBuffer.clockRate() * BUF_SIZE_MS / 1000.0);
//        LOG.info("{}\nOutput sampleRate: {}, Input sampleRate: {}, outputBufLenMs: {}, outputBufLenSamples: {}" +
//                        ", inputBufLenSamples: {}", ctx, pwmAudioFormat.getSampleRate(), ctx.blipBuffer.clockRate(), BUF_SIZE_MS,
//                outSamplesPerInterval, inSamplesPerInterval);
//    }

    @Override
    public void reset() {
    }
}