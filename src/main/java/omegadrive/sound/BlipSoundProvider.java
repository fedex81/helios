package omegadrive.sound;

import mcd.pcm.McdPcmProvider;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector.Region;
import org.slf4j.Logger;
import s32x.util.blipbuffer.BlipBufferIntf;
import s32x.util.blipbuffer.StereoBlipBuffer;

import javax.sound.sampled.AudioFormat;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 */
public class BlipSoundProvider implements McdPcmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipSoundProvider.class.getSimpleName());

    private static final int BUF_SIZE_MS = 50;

    private final AtomicReference<BlipBufferContext> ref = new AtomicReference<>();

    static class BlipBufferContext extends SampleBufferContext {
        BlipBufferIntf blipBuffer;
        AtomicInteger inputClocksForInterval = new AtomicInteger();

        @Override
        public String toString() {
            return new StringJoiner(", ", BlipBufferContext.class.getSimpleName() + "[", "]")
                    .add("inputClocksForInterval=" + inputClocksForInterval)
                    .toString();
        }
    }

    private double deltaTime;

    private short prevLSample, prevRSample;
    private Region region;

    private final double clockRate;
    private final AudioFormat audioFormat;

    private final String instanceId;

    public BlipSoundProvider(String name, Region region, AudioFormat af, double clockRate) {
        ref.set(new BlipBufferContext());
        this.region = null;
        this.clockRate = clockRate;
        this.instanceId = name + "_" + (int) af.getSampleRate();
        this.audioFormat = af;
        setup(region);
        assert this.region == region;
    }

    private void setup(Region region) {
        BlipBufferIntf blip = new StereoBlipBuffer(instanceId);
        blip.setSampleRate((int) audioFormat.getSampleRate(), BUF_SIZE_MS);
        blip.setClockRate((int) clockRate);
        BlipBufferContext bbc = new BlipBufferContext();
        bbc.lineBuffer = new byte[0];
        bbc.blipBuffer = blip;
        ref.set(bbc);
        updateRegion(region, (int) clockRate);
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
        ref.get().blipBuffer.addDelta((int) deltaTime, lsample - prevLSample, rsample - prevRSample);
        prevLSample = (short) lsample;
        prevRSample = (short) rsample;
        deltaTime++;
    }

    private int prevSampleAvail = 0;

    @Override
    public void newFrame() {
        BlipBufferContext context = ref.get();
        BlipBufferIntf blip = context.blipBuffer;
        if (blip == null) {
            return;
        }
        assert context.inputClocksForInterval.get() > 0;
        blip.endFrame(context.inputClocksForInterval.get());
        deltaTime = 0;
        int availMonoSamples = blip.samplesAvail();
        if (availMonoSamples + 5 < prevSampleAvail) {
            LOG.info("{} Audio underrun : {} -> {} samples", instanceId, prevSampleAvail, availMonoSamples);
        }
        if (context.lineBuffer.length < availMonoSamples << 2) {
            LOG.info("{} Audio buffer size: {} -> {} bytes", instanceId, context.lineBuffer.length, availMonoSamples << 2);
            context.lineBuffer = new byte[availMonoSamples << 2];
        }
        context.stereoBytesLen = blip.readSamples16bitStereo(context.lineBuffer, 0, availMonoSamples) << 2;
        prevSampleAvail = availMonoSamples;
    }

    public SampleBufferContext getDataBuffer() {
        return ref.get();
    }

    @Override
    public void updateRegion(Region region, int clockRate) {
        BlipBufferContext ctx = ref.get();
        if (region != this.region || ctx.blipBuffer.clockRate() != clockRate) {
            this.region = region;
            ctx.blipBuffer.setClockRate(clockRate);
            ctx.inputClocksForInterval.set((int) (1.0 * ctx.blipBuffer.clockRate() * region.getFrameIntervalMs() / 1000.0));
            LOG.info("{} updating region: {} and ticksPerFrame: {}", instanceId, region, ctx.inputClocksForInterval);
        }
    }

    private void logInfo(BlipBufferContext ctx) {
        int outSamplesPerInterval = (int) (audioFormat.getSampleRate() * BUF_SIZE_MS / 1000.0);
        int inSamplesPerInterval = (int) (ctx.blipBuffer.clockRate() * BUF_SIZE_MS / 1000.0);
        LOG.info("{}: {}\nOutput sampleRate: {}, Input sampleRate: {}, outputBufLenMs: {}, outputBufLenSamples: {}" +
                        ", inputBufLenSamples: {}", instanceId, ctx, audioFormat.getSampleRate(), ctx.blipBuffer.clockRate(), BUF_SIZE_MS,
                outSamplesPerInterval, inSamplesPerInterval);
    }

    @Override
    public void close() {
    }

    @Override
    public void reset() {
        LOG.warn("TODO reset");
    }
}