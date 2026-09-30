package mcd.pcm;

import omegadrive.sound.PcmProvider;
import omegadrive.sound.blip.BlipBufferHelper;
import omegadrive.sound.blip.BlipBufferIntf;
import omegadrive.sound.blip.StereoBlipBuffer;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import org.slf4j.Logger;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static omegadrive.sound.javasound.AbstractSoundManager.audioFormat;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 * <p>
 */
public class BlipPcmProvider implements PcmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipPcmProvider.class.getSimpleName());

    private static final int BUF_SIZE_MS = 50;

    protected final AtomicReference<BlipBufferContext> ref = new AtomicReference<>();

    static class BlipBufferContext {
        BlipBufferIntf blipBuffer;
        AtomicInteger inputClocksForInterval = new AtomicInteger();

        @Override
        public String toString() {
            return new StringJoiner(", ", BlipBufferContext.class.getSimpleName() + "[", "]")
                    .add("inputClocksForInterval=" + inputClocksForInterval)
                    .toString();
        }
    }

    protected double deltaTime;

    private short prevLSample, prevRSample;
    protected int prevSampleAvail = 0;

    private RegionDetector.Region region;

    private final double clockRate;

    protected final String instanceId;

    public BlipPcmProvider(String name, RegionDetector.Region region) {
        ref.set(new BlipBufferContext());
        this.region = region;
        this.clockRate = audioFormat.getSampleRate();
        this.instanceId = name + "_" + (int) clockRate;
        setup();
    }

    public BlipPcmProvider(String name, RegionDetector.Region region, int sampleRateHz) {
        ref.set(new BlipBufferContext());
        this.region = region;
        this.clockRate = sampleRateHz;
        this.instanceId = name + "_" + (int) clockRate;
        setup();
    }

    private void setup() {
        BlipBufferIntf blip = new StereoBlipBuffer(instanceId);
        blip.setSampleRate((int) audioFormat.getSampleRate(), BUF_SIZE_MS);
        blip.setClockRate((int) clockRate);
        BlipBufferContext bbc = new BlipBufferContext();
        bbc.blipBuffer = blip;
        ref.set(bbc);
        updateRegion(region);
        logInfo(bbc);
    }


    @Override
    public void playSample(int lsample, int rsample) {
        if (BufferUtil.assertionsEnabled) {
            if (Math.abs(lsample - prevLSample) > 0xD000) {
                LOG.info("{} L {} -> {}, absDiff: {}", instanceId, th(prevLSample), th((short) lsample), th(Math.abs(lsample - prevLSample)));
            }
            if (Math.abs(rsample - prevRSample) > 0xD000) {
                LOG.info("{} R {} -> {}, absDiff: {}", instanceId, th(prevRSample), th((short) rsample), th(Math.abs(rsample - prevRSample)));
            }
        }
        ref.get().blipBuffer.addDelta((int) deltaTime, (short) (lsample - prevLSample), (short) (rsample - prevRSample));
        prevLSample = (short) lsample;
        prevRSample = (short) rsample;
        deltaTime++;
    }

    @Override
    public int updateStereo16(int[] buf_lr, int offset, int countMono) {
        BlipBufferContext context = ref.get();
        BlipBufferIntf blip = context.blipBuffer;
        assert blip != null;
        assert context.inputClocksForInterval.get() > 0;
        blip.endFrame(context.inputClocksForInterval.get());
        deltaTime = 0;
        return BlipBufferHelper.readSamples16bitStereo((StereoBlipBuffer) blip, buf_lr, 0, countMono);
    }

    @Override
    public void updateRegion(RegionDetector.Region region) {
        this.region = region;
        BlipBufferContext ctx = ref.get();
        ctx.inputClocksForInterval.set((int) (1.0 * ctx.blipBuffer.clockRate() * region.getFrameIntervalMs() / 1000.0));
    }

    private void logInfo(BlipBufferContext ctx) {
        int outSamplesPerInterval = (int) (audioFormat.getSampleRate() * BUF_SIZE_MS / 1000.0);
        int inSamplesPerInterval = (int) (ctx.blipBuffer.clockRate() * BUF_SIZE_MS / 1000.0);
        LOG.info("{}: {}\nOutput sampleRate: {}, Input sampleRate: {}, outputBufLenMs: {}, outputBufLenSamples: {}" +
                        ", inputBufLenSamples: {}", instanceId, ctx, audioFormat.getSampleRate(), ctx.blipBuffer.clockRate(), BUF_SIZE_MS,
                outSamplesPerInterval, inSamplesPerInterval);
    }

    @Override
    public void reset() {
        deltaTime = prevLSample = prevRSample = 0;
        var bb = ref.get().blipBuffer;
        bb.endFrame(ref.get().inputClocksForInterval.get());
        bb.clear();
    }
}