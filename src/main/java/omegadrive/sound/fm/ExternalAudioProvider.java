package omegadrive.sound.fm;

import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.atomic.SpscAtomicArrayQueue;

import javax.sound.sampled.AudioFormat;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ExternalAudioProvider
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class ExternalAudioProvider implements FmProvider {

    private static final Logger LOG = LogManager.getLogger(omegadrive.system.gb.GbSoundWrapper.class.getSimpleName());
    protected AtomicInteger queueLen = new AtomicInteger();
    private Queue<Integer> sampleQueue;
    private volatile boolean running = false;

    public ExternalAudioProvider(RegionDetector.Region region, AudioFormat audioFormat) {
        sampleQueue = new SpscAtomicArrayQueue<>(((int) audioFormat.getSampleRate()) << 1);
    }

    @Override
    public int update(int[] buf_lr, int offset, int count) {
        if (!running) {
            return 0;
        }
        offset <<= 1;
        int end = (count << 1) + offset;

        int res = queueLen.get();
//        LOG.info("update " + res);
        int sampleNum = 0;
        int k = 0, i = 0;
        for (k = offset; k < end && i < res; k += 2, i++) {
            Integer sample = sampleQueue.peek();
            if (sample != null) {
                sampleQueue.poll();
                sampleNum++;
                buf_lr[k] = sample;
                buf_lr[k + 1] = buf_lr[k];
            }
        }
        queueLen.addAndGet(-sampleNum);
        return sampleNum;
    }

    protected void addStereoSample(int left, int right) {
        if (!running) {
            return;
        }
        int mono8 = ((left + right) >> 1);
        boolean res = sampleQueue.offer(Util.getFromIntegerCache(mono8 << 8)); //16 bit
        if (res) {
            queueLen.getAndIncrement();
        } else {
            LOG.info("Sample dropped");
        }
    }

    protected void addMonoSample(int sample) {
        if (!running) {
            return;
        }
        boolean res = sampleQueue.offer(Util.getFromIntegerCache(sample));
        if (res) {
            queueLen.getAndIncrement();
        } else {
            LOG.info("Sample dropped");
        }
    }

    public void start() {
        running = true;
        LOG.debug("Running: {}", running);
    }

    public void stop() {
        running = false;
        LOG.debug("Running: {}", running);
    }

    @Override
    public void init() {
        reset();
    }

    @Override
    public int readRegister(int type, int regNumber) {
        return 0;
    }

    @Override
    public void tick() {

    }

    @Override
    public void reset() {
        sampleQueue.clear();
        queueLen.set(0);
    }
}
