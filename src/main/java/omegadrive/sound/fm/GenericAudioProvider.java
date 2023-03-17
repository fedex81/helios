package omegadrive.sound.fm;

import omegadrive.system.BaseSystem;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.jctools.queues.atomic.SpscAtomicArrayQueue;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static omegadrive.util.Util.th;

/**
 * GenericAudioProvider
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class GenericAudioProvider implements FmProvider {

    private static final Logger LOG = LogHelper.getLogger(GenericAudioProvider.class.getSimpleName());
    protected final AtomicInteger stereoQueueLen = new AtomicInteger();
    //NOTE: each element represent a 16 bit sample for one channel
    protected final Queue<Integer> sampleQueue;
    protected volatile boolean running = false;
    private final Integer[] stereoSamples = new Integer[2]; //[0] left, [1] right
    private final int audioScaleBits;
    private final int sampleShift;

    public GenericAudioProvider(AudioFormat inputAudioFormat) {
        //2 frames maxQueueLen
        this(inputAudioFormat, 0, ((int) inputAudioFormat.getSampleRate()) << 2);
    }

    public GenericAudioProvider(AudioFormat inputAudioFormat, int audioScaleBits, int maxQueueLen) {
        sampleQueue = new SpscAtomicArrayQueue<>(maxQueueLen);
        sampleShift = 16 - inputAudioFormat.getSampleSizeInBits();
        this.audioScaleBits = audioScaleBits;
        LOG.info("Input sound source format: {}, audioScaleBits: {}", inputAudioFormat, audioScaleBits);
    }

    @Override
    public int updateStereo16(int[] buf_lr, int offset, int count) {
        if (!running) {
            return 0;
        }
        offset <<= 1;
        int end = (count << 1) + offset;
        int queueIndicativeLen = stereoQueueLen.get();
        int i = offset;
        for (; i < end && queueIndicativeLen > 0; i += 2) {
            //when using mono we process two samples
            if (!fillStereoSamples(stereoSamples)) {
                LOG.warn("Null left sample QL{} P{}", queueIndicativeLen, i);
                break;
            }
            queueIndicativeLen = stereoQueueLen.addAndGet(-2);
            //Integer -> short -> int
            buf_lr[i] = ((short) (stereoSamples[0] & 0xFFFF)) << audioScaleBits;
            buf_lr[i + 1] = ((short) (stereoSamples[1] & 0xFFFF)) << audioScaleBits;
        }
        return i;
    }

    private boolean fillStereoSamples(Integer[] stereoSamples) {
        stereoSamples[0] = sampleQueue.peek();
        if (stereoSamples[0] == null) {
            return false;
        }
        sampleQueue.poll();
        stereoSamples[1] = sampleQueue.poll();
        if (stereoSamples[1] == null) {
            stereoSamples[1] = stereoSamples[0];
            LOG.warn("Null right sample, left: {}", stereoSamples[0]);
        }
        //sanity check
        assert (stereoSamples[0] & 1) == 1 && (stereoSamples[1] & 1) == 0;
        return true;
    }

    protected void addStereoSample(int left, int right) {
        if (!running) {
            return;
        }
        boolean res = sampleQueue.offer(Util.getFromIntegerCache((left << sampleShift) | 1)); //sampleL is always odd
        boolean res2 = sampleQueue.offer(Util.getFromIntegerCache((right << sampleShift) & ~1)); //sampleR is always even
        stereoQueueLen.addAndGet(2);
        if (!res) {
            //NOTE when running at > 60 fps we expect to drop samples
            if (!BaseSystem.fullThrottle) LOG.warn("Left sample dropped: {}", th(left));
            stereoQueueLen.decrementAndGet();
        }
        if (!res2) {
            if (!BaseSystem.fullThrottle) LOG.warn("Right sample dropped: {}", th(right));
            stereoQueueLen.decrementAndGet();
        }
    }

    protected void addMonoSample(int sample) {
        if (!running) {
            return;
        }
        addStereoSample(sample, sample);
    }

    public void start() {
        running = true;
        //LOG.debug("Running: {}", running);
    }

    public void stop() {
        running = false;
        //LOG.debug("Running: {}", running);
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
        stop();
        sampleQueue.clear();
        stereoQueueLen.set(0);
        start();
    }
}
