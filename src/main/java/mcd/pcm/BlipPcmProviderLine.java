package mcd.pcm;

import omegadrive.sound.blip.BlipBufferHelper;
import omegadrive.sound.blip.BlipBufferIntf;
import omegadrive.sound.blip.StereoBlipBuffer;
import omegadrive.util.*;
import org.slf4j.Logger;

import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static omegadrive.sound.javasound.AbstractSoundManager.audioFormat;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 */
public class BlipPcmProviderLine extends BlipPcmProvider {

    private static final Logger LOG = LogHelper.getLogger(BlipPcmProviderLine.class.getSimpleName());

    private final SourceDataLine dataLine;
    private final ExecutorService exec;

    //TODO hack
    @Deprecated
    public static boolean mute = false;

    public BlipPcmProviderLine(String name, RegionDetector.Region region, double clockRate) {
        super(name, region, (int) clockRate);
        dataLine = SoundUtil.createDataLine(audioFormat);
        exec = Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, instanceId));
    }

    @Override
    public int updateStereo16(int[] buf_lr, int offset, int countMono) {
        LogHelper.logWarnOnce(LOG, "{} Ignoring sample requests, using its own dataLine", instanceId);
        return countMono << 1;
    }

    private final AtomicInteger sync = new AtomicInteger();
    private byte[] outputStereo16 = new byte[0];

    @Override
    public void onNewFrame() {
        BlipBufferContext context = ref.get();
        BlipBufferIntf blip = context.blipBuffer;
        assert blip != null;
        assert context.inputClocksForInterval.get() > 0;
        blip.endFrame(context.inputClocksForInterval.get());
        int availMonoSamples = getBufferContext().blipBuffer.samplesAvail();
        if (outputStereo16.length < availMonoSamples) {
            outputStereo16 = new byte[availMonoSamples << 2];
        }
        deltaTime = 0;
        int stereoBytes = BlipBufferHelper.readSamples16bitStereo((StereoBlipBuffer) blip, outputStereo16, 0, availMonoSamples);
        final long current = sync.incrementAndGet();

        if (stereoBytes > 0 && !mute) {
            exec.submit(Util.wrapRunnableEx(() -> {
                SoundUtil.writeBufferInternal(dataLine, outputStereo16, 0, stereoBytes);
                if (BufferUtil.assertionsEnabled) {
                    if (current != sync.get()) {
                        LOG.info("{} Blip audio thread too slow: {} vs {}", instanceId, current, sync.get());
                    }
                }
            }));
        }
        prevSampleAvail = availMonoSamples;
    }

    @Override
    public void setEnabled(boolean mute) {
        super.setEnabled(mute);
        BlipPcmProviderLine.mute = mute;
    }

    @Override
    public void close() {
        SoundUtil.close(dataLine);
        exec.shutdown();
    }

    @Override
    public void reset() {
        super.reset();
    }
}