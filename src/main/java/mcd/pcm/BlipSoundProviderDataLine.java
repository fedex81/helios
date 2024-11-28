package mcd.pcm;

import omegadrive.sound.BlipSoundProvider;
import omegadrive.util.*;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 */
@Deprecated
public class BlipSoundProviderDataLine extends BlipSoundProvider.BlipSoundProviderImpl {

    private static final Logger LOG = LogHelper.getLogger(BlipSoundProviderDataLine.class.getSimpleName());

    private final SourceDataLine dataLine;
    private final ExecutorService exec;
    private final String instanceId;

    private final AtomicInteger sync = new AtomicInteger();

    public BlipSoundProviderDataLine(String name, RegionDetector.Region region, AudioFormat audioFormat, double clockRate) {
        super(name, region, audioFormat, clockRate);
        dataLine = SoundUtil.createDataLine(audioFormat);
        this.instanceId = name + "_" + (int) clockRate;
        exec = Executors.newSingleThreadExecutor(new PriorityThreadFactory(Thread.MAX_PRIORITY, instanceId));
        super.updateRegion(region, (int) clockRate);
    }

    @Override
    public void newFrame() {
        super.newFrame();
        SampleBufferContext ctx = getDataBuffer();
        if (ctx.stereoBytesLen > 0) {
            final long current = sync.incrementAndGet();
            exec.submit(() -> {
                SoundUtil.writeBufferInternal(dataLine, ctx.lineBuffer, 0, ctx.stereoBytesLen);
                if (BufferUtil.assertionsEnabled) {
                    if (current != sync.get()) {
                        LOG.info("{} Blip audio thread too slow: {} vs {}", instanceId, current, sync.get());
                    }
                }
            });
        }
    }
    @Override
    public void close() {
        SoundUtil.close(dataLine);
        exec.shutdown();
    }

    @Override
    public void reset() {
        LOG.warn("TODO reset");
    }
}