package mcd.pcm;

import omegadrive.util.*;
import org.slf4j.Logger;
import s32x.util.blipbuffer.BlipBufferIntf;
import s32x.util.blipbuffer.StereoBlipBuffer;

import javax.sound.sampled.SourceDataLine;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static mcd.pcm.McdPcm.pcmSampleRateHz;
import static omegadrive.util.Util.th;
import static s32x.pwm.PwmUtil.pwmAudioFormat;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 */
public class BlipPcmProvider implements McdPcmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipPcmProvider.class.getSimpleName());

    private static final int BUF_SIZE_MS = 50;

    private final AtomicReference<BlipBufferContext> ref = new AtomicReference<>();

    static class BlipBufferContext {
        BlipBufferIntf blipBuffer;
        byte[] lineBuffer;
        int inputClocksForInterval;

        @Override
        public String toString() {
            return new StringJoiner(", ", BlipBufferContext.class.getSimpleName() + "[", "]")
                    .add("inputClocksForInterval=" + inputClocksForInterval)
                    .toString();
        }
    }

    private double deltaTime;

    private int prevLSample, prevRSample;
    private final SourceDataLine dataLine;
    private final RegionDetector.Region region;

    private final ExecutorService exec =
            Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, "pcm"));

    public BlipPcmProvider(RegionDetector.Region region) {
        ref.set(new BlipBufferContext());
        dataLine = SoundUtil.createDataLine(pwmAudioFormat);
        this.region = region;
        setup();
    }

    private void setup() {
        BlipBufferIntf blip = new StereoBlipBuffer("pcm");
        blip.setSampleRate((int) pwmAudioFormat.getSampleRate(), BUF_SIZE_MS);
        blip.setClockRate((int) pcmSampleRateHz);
        BlipBufferContext bbc = new BlipBufferContext();
        bbc.inputClocksForInterval = (int) (1.0 * blip.clockRate() * region.getFrameIntervalMs() / 1000.0);
        bbc.lineBuffer = new byte[0];
        bbc.blipBuffer = blip;
        ref.set(bbc);
        logInfo(bbc);
    }


    @Override
    public void playSample(int lsample, int rsample) {
        if (BufferUtil.assertionsEnabled) {
            if (Math.abs(lsample - prevLSample) > Short.MAX_VALUE) {
                LOG.info("L {} -> {}, absDiff: {}", th(prevLSample), th(lsample), th(Math.abs(lsample - prevLSample)));
            }
            if (Math.abs(rsample - prevRSample) > Short.MAX_VALUE) {
                LOG.info("R {} -> {}, absDiff: {}", th(prevRSample), th(rsample), th(Math.abs(rsample - prevRSample)));
            }
        }
        ref.get().blipBuffer.addDelta((int) deltaTime, lsample - prevLSample, rsample - prevRSample);
        prevLSample = lsample;
        prevRSample = rsample;
        deltaTime++;
    }

    @Override
    public int updateStereo16(int[] buf_lr, int offset, int countMono) {
        LogHelper.logWarnOnce(LOG, "Ignoring sample requests, using its own dataLine");
        return countMono << 1;
    }

    private int prevSampleAvail = 0;
    private final AtomicInteger sync = new AtomicInteger();

    @Override
    public void newFrame() {
        BlipBufferContext context = ref.get();
        BlipBufferIntf blip = context.blipBuffer;
        if (blip == null) {
            return;
        }
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
                        LOG.info("Pcm audio thread too slow: {} vs {}", current, sync.get());
                    }
                }
            });
        }
        prevSampleAvail = availMonoSamples;
    }

    public static void monoStereo16ToByteStereo16Mix(int[] input, byte[] output, int inputLen) {
        for (int i = 0, k = 0; i < inputLen; i += 1, k += 4) {
            output[k + 2] = output[k] = (byte) (input[i] & 0xFF); //lsb
            output[k + 3] = output[k + 1] = (byte) ((input[i] >> 8) & 0xFF); //msb
        }
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