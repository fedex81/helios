package s32x.pwm;

import omegadrive.sound.PwmProvider;
import omegadrive.util.*;
import org.slf4j.Logger;
import s32x.util.blipbuffer.BlipBufferHelper;
import s32x.util.blipbuffer.BlipBufferIntf;
import s32x.util.blipbuffer.StereoBlipBuffer;

import javax.sound.sampled.SourceDataLine;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static omegadrive.util.Util.th;
import static s32x.pwm.PwmUtil.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 */
public class BlipPwmProvider implements PwmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipPwmProvider.class.getSimpleName());

    private static final int BUF_SIZE_MS = 50;

    private final AtomicReference<BlipBufferContext> ref = new AtomicReference<>();

    static class BlipBufferContext {
        BlipBufferIntf blipBuffer;
        byte[] lineBuffer;
        int cycle, inputClocksForInterval;
        float scale;

        @Override
        public String toString() {
            return new StringJoiner(", ", BlipBufferContext.class.getSimpleName() + "[", "]")
                    .add("cycle=" + cycle)
                    .add("inputClocksForInterval=" + inputClocksForInterval)
                    .add("scale=" + scale)
                    .toString();
        }
    }

    private final float sh2ClockMhz;
    private final double frameIntervalMs;
    private double deltaTime;

    private int prevLSample, prevRSample;

    int[] preFilter = new int[0];
    int[] pfPrev = new int[2];
    static final double pfAlpha = 0.995;

    private final SourceDataLine dataLine;
    private Warmup warmup = NO_WARMUP;

    private final ExecutorService exec =
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, "pwm"));

    public BlipPwmProvider(RegionDetector.Region region) {
        ref.set(new BlipBufferContext());
        frameIntervalMs = region.getFrameIntervalMs();
        dataLine = SoundUtil.createDataLine(pwmAudioFormat);
        sh2ClockMhz = region == RegionDetector.Region.EUROPE ? PAL_SH2CLOCK_MHZ : NTSC_SH2CLOCK_MHZ;
        updatePwmCycle(1042);
    }

    @Override
    public void updatePwmCycle(int cycle) {
        BlipBufferContext context = ref.get();
        if (cycle == context.cycle) {
            return;
        }
        BlipBufferIntf blip = new StereoBlipBuffer("pwm");
        blip.setSampleRate((int) pwmAudioFormat.getSampleRate(), BUF_SIZE_MS);
        blip.setClockRate((int) (sh2ClockMhz / cycle));

        BlipBufferContext bbc = new BlipBufferContext();
        bbc.cycle = cycle;
        bbc.scale = (Short.MAX_VALUE << 1) / (float) cycle;
        bbc.inputClocksForInterval = (int) (1.0 * blip.clockRate() * frameIntervalMs / 1000.0);

        int outSamplesPerInterval = (int) (pwmAudioFormat.getSampleRate() * BUF_SIZE_MS / 1000.0);
        preFilter = new int[outSamplesPerInterval];
        bbc.lineBuffer = new byte[0];
        bbc.blipBuffer = blip;
        ref.set(bbc);
        warmup = WARMUP;
        warmup.reset();
        warmup.isWarmup = true;
        logInfo(bbc);
        LOG.info("PWM warmup start");
    }

    @Override
    public void playSample(int left, int right) {
        int lsample = scalePwmSample(left);
        int rsample = scalePwmSample(right);
        if (lsample != prevLSample || rsample != prevRSample) {
            ref.get().blipBuffer.addDelta((int) deltaTime, lsample - prevLSample, rsample - prevRSample);
            prevLSample = lsample;
            prevRSample = rsample;
        }
        deltaTime++;
    }

    private int scalePwmSample(int sample) {
        int vsample = (int) ((sample - (ref.get().cycle >> 1)) * ref.get().scale);
        short scaled = (short) vsample;
        if (scaled != vsample) {
            float scale = ref.get().scale;
            ref.get().scale -= 1;
            LOG.warn("PWM value out of range (16 bit signed): {}, scale: {}, " +
                    "pwmVal: {}", th(scaled), scale, sample);
            LOG.warn("Reducing scale: {} -> {}", scale, ref.get().scale);
            scaled = (short) BlipBufferHelper.clampToShort(vsample);
        }
        return scaled;
    }

    private int prevSampleAvail = 0;
    private final AtomicInteger sync = new AtomicInteger();

    //TODO if framerate slows down we get periods where the wave goes back to zero -> poor sound quality
    //TODO S32xPwmProvider fills the gaps and it sounds better
    @Override
    public void newFrame() {
        BlipBufferContext context = ref.get();
        BlipBufferIntf blip = context.blipBuffer;
        blip.endFrame(context.inputClocksForInterval);
        deltaTime = 0;
        int availMonoSamples = blip.samplesAvail();
        if (availMonoSamples + 5 < prevSampleAvail) {
            LOG.info("Audio underrun : {} -> {} samples", prevSampleAvail, availMonoSamples);
        }
        if (context.lineBuffer.length < availMonoSamples << 2) {
            LOG.info("Audio buffer size: {} -> {} bytes", context.lineBuffer.length, availMonoSamples << 2);
            context.lineBuffer = new byte[availMonoSamples << 2];
        }
        final long current = sync.incrementAndGet();
        int stereoBytes = blip.readSamples16bitStereo(context.lineBuffer, 0, availMonoSamples) << 2;
        if (stereoBytes > 0) {
            exec.submit(() -> {
                SoundUtil.writeBufferInternal(dataLine, context.lineBuffer, 0, stereoBytes);
                if (BufferUtil.assertionsEnabled) {
                    if (current != sync.get()) {
                        LOG.info("Pwm audio thread too slow: {} vs {}", current, sync.get());
                    }
                }
            });
        }
        prevSampleAvail = availMonoSamples;
    }

    private void logInfo(BlipBufferContext ctx) {
        int outSamplesPerInterval = (int) (pwmAudioFormat.getSampleRate() * BUF_SIZE_MS / 1000.0);
        int inSamplesPerInterval = (int) (ctx.blipBuffer.clockRate() * BUF_SIZE_MS / 1000.0);
        LOG.info("{}\nOutput sampleRate: {}, Input sampleRate: {}, outputBufLenMs: {}, outputBufLenSamples: {}" +
                        ", inputBufLenSamples: {}", ctx, pwmAudioFormat.getSampleRate(), ctx.blipBuffer.clockRate(), BUF_SIZE_MS,
                outSamplesPerInterval, inSamplesPerInterval);
    }

    @Override
    public void reset() {
        SoundUtil.close(dataLine);
    }
}