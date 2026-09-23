package omegadrive.sound;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */

import omegadrive.util.*;
import org.slf4j.Logger;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AdaptiveAudioBuffer {

    private static final Logger LOG = LogHelper.getLogger(AdaptiveAudioBuffer.class.getSimpleName());

    enum BufferState {
        EMPTY, FAST_1, FAST_2, NICE, GOOD, STABLE,
        SLOW_5, SLOW_6, SLOW_7, SLOW_8, SLOW_9, SLOW_10, SLOW_11, SLOW_12, SLOW_13, SLOW_14, SLOW_15;

        public static BufferState[] vals = BufferState.values();
    }

    private final static double MAX_CHANGE_FACTOR = 0.01; // 1%
    private final CircularQueue ring;
    private final int chunkSize;
    private final int stereoChunkSize;

    // --- Array Pool Configuration ---
    private final int[][] arrayPool;
    private int poolTop = 0;

    // Accumulation Buffers for addSample
    private final int[] inputBuffer;
    private int sampleIndex = 0;

    private final int fastTargetSize, slowTargetSize;

    private final int[] byteReadScratchBuffer;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public AdaptiveAudioBuffer(int chunkSize, CircularQueue ring) {
        this.chunkSize = chunkSize;
        this.stereoChunkSize = chunkSize * 2;
        fastTargetSize = ((int) (stereoChunkSize * (1 + MAX_CHANGE_FACTOR))) & ~1;
        slowTargetSize = ((int) (stereoChunkSize * (1 - MAX_CHANGE_FACTOR))) & ~1;
        this.ring = ring;

        // Initialize the array pool.
        // It needs enough capacity to handle the ring capacity plus temporary in-flight splits.
        int maxPooledArrays = ring.getCapacity() + 4;
        this.arrayPool = new int[maxPooledArrays][stereoChunkSize];
        this.poolTop = maxPooledArrays; // All arrays are initially available

        this.inputBuffer = new int[this.stereoChunkSize];
        this.byteReadScratchBuffer = new int[ring.getCapacity() * stereoChunkSize * 2];
    }

    /**
     * Rents a stereo-sized array chunk from the pool.
     */
    private int[] rentArray() {
        if (poolTop > 0) {
            return arrayPool[--poolTop];
        }
        // Fallback allocation if pool exhausts (should not happen if dimensioned correctly)
        return new int[stereoChunkSize];
    }

    /**
     * Returns a used stereo array chunk back to the pool.
     */
    private void returnArray(int[] array) {
        if (array == null || array.length != stereoChunkSize) return;
        if (poolTop < arrayPool.length) {
            arrayPool[poolTop++] = array;
        }
    }

    /**
     * PRODUCER SIDE (Bulk): Processes an array of interleaved stereo samples.
     * Splices bulk input across multiple chunks seamlessly using the internal accumulator.
     */
    public void addSamples(int[] buf, int count) {
        if (buf == null || buf.length == 0 || count == 0) {
            return;
        }
        assert (count & 1) == 0;
        lock.writeLock().lock();
        try {
            int len = count;
            for (int i = 0; i < len; i += 2) {
                addSampleInternal(buf[i], buf[i + 1]);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    int errCnt = 0;

    /**
     * PRODUCER SIDE (Raw Bytes): Processes an array of interleaved stereo bytes.
     * Parses 16-bit signed Little-Endian sample pairs [LSB, MSB, LSB, MSB...]
     * directly into the internal int accumulator framework copy-lessly.
     *
     * @param data  The source byte array.
     * @param count The number of BYTES to read from the array (must be a multiple of 4 for stereo).
     */
    public void addSamples(byte[] data, int count) {
        if (data == null || data.length == 0 || count == 0) {
            return;
        }
        // A stereo 16-bit sample frame consists of 4 bytes: 2 for Left, 2 for Right
        assert (count & 3) == 0;

        //TODO when this happens we're gonna have audio issues
        if (count != stereoChunkSize << 1) {
            if ((++errCnt & 0xFFF) == 0) {
                LOG.warn("Samples added vs expected: {} vs {}, times: {}", count, stereoChunkSize << 1, errCnt);
            }
        }
        lock.writeLock().lock();
        try {
            int len = count;
            for (int i = 0; i < len; i += 4) {
                // Parse Left Channel (Little Endian: data[i] is LSB, data[i+1] is MSB)
                int left = (data[i] & 0xFF) | (data[i + 1] << 8);

                // Parse Right Channel (Little Endian: data[i+2] is LSB, data[i+3] is MSB)
                int right = (data[i + 2] & 0xFF) | (data[i + 3] << 8);

                // Route directly into our zero-allocation internal staging block
                addSampleInternal(left, right);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }


    /**
     * PRODUCER SIDE: Called by the emulator core for every generated audio frame.
     */
    public void addSample(int left, int right) {
        lock.writeLock().lock();
        try {
            addSampleInternal(left, right);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void addSampleInternal(int left, int right) {
        inputBuffer[sampleIndex++] = SoundUtil.clampToShort(left);
        inputBuffer[sampleIndex++] = SoundUtil.clampToShort(right);

        if (sampleIndex >= stereoChunkSize) {
            // Rent an array from the pool instead of using 'new'
            int[] chunkToPush = rentArray();
            System.arraycopy(inputBuffer, 0, chunkToPush, 0, stereoChunkSize);
//            LOG.info("Pushed to ring buffer");
            boolean pushed = ring.push(chunkToPush);
            if (!pushed) {
                // If the queue is full, return the array immediately to prevent memory leaks
                returnArray(chunkToPush);
            }
            sampleIndex = 0;
        }
    }

    /**
     * CONSUMER SIDE (Raw Bytes): Called when a byte-based audio layer demands data.
     * Pulls the resampled int data from the state machine and flattens it directly
     * into 16-bit signed Little-Endian format inside the provided byte buffer.
     *
     * @param buf The target byte array to fill.
     * @return The total number of BYTES written into buf.
     */
    public int read(byte[] buf, int num) {
        if (buf == null || buf.length == 0 || num == 0) {
            return 0;
        }

        // Calculate how many int samples we can safely fit into the byte array
        // (2 bytes per 16-bit sample)
        int maxSamplesToFill = num / 2;

        assert maxSamplesToFill < byteReadScratchBuffer.length;

        lock.writeLock().lock();
        try {
            // 1. Evaluate the ring queue status and extract resampled data into our int scratchpad
            int samplesWritten = readInternal(byteReadScratchBuffer, maxSamplesToFill);

            // 2. Flatten the integers back down into Little-Endian bytes [LSB, MSB, LSB, MSB...]
            for (int i = 0; i < samplesWritten; i++) {
                Util.setShortLE(buf, i * 2, (short) byteReadScratchBuffer[i]);
            }

            // Return the total number of bytes written
            return samplesWritten * 2;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * CONSUMER SIDE (Int primitives): Called when an integer-based audio layer demands data.
     * Evaluates the ring queue state and copies resampled data directly into the provided buffer.
     *
     * @param buf The target int array to fill.
     * @return The total number of samples written into buf.
     */
    public int read(int[] buf) {
        if (buf == null || buf.length == 0) {
            return 0;
        }

        lock.writeLock().lock();
        try {
            // Direct pass-through to the core state machine switcher
            return readInternal(buf, buf.length);
        } finally {
            lock.writeLock().unlock();
        }
    }


    BufferState lastState = null;

    /**
     * Refactored internal method to isolate the core switch state logic
     * from the primitive type target endpoints.
     */
    private int readInternal(int[] targetBuf, int maxSamples) {
        int st = ring.counter() & 0xF;
        assert st < BufferState.vals.length;
        BufferState state = BufferState.vals[st];
        if (state != lastState) {
            LogHelper.logWarnOnce(LOG, "Audio buffer state {} -> {}", lastState, state);
//            LOG.debug("Buffer state {} -> {}", lastState, state);
            lastState = state;
        }

        // We wrap targetBuf using a length constraint wrapper if your state methods
        // rely on targetBuf.length, or pass it directly if they respect sizes.
        return switch (state) {
            case EMPTY -> handleEmpty(targetBuf, maxSamples);
            case FAST_1, FAST_2 -> handleTooFast(targetBuf, maxSamples);
            case NICE, GOOD, STABLE -> handleGood(targetBuf, maxSamples);
            default -> handleTooSlow(targetBuf, maxSamples);
        };
    }


    private int handleEmpty(int[] buf, int num) {
        java.util.Arrays.fill(buf, 0, num, (short) 0);
        return num;
    }

    private int handleGood(int[] buf, int num) {
        int[] chunkToPlay = ring.pop();
        if (chunkToPlay == null) {
            return handleEmpty(buf, num);
        }
        int len = Math.min(num, chunkToPlay.length);
        System.arraycopy(chunkToPlay, 0, buf, 0, len);

        // Consumer has safely read the buffer; return it to the pool
        returnArray(chunkToPlay);
        return len;
    }

    private int handleTooFast(int[] buf, int num) {
        int[] chunkToStretch = ring.pop();
        if (chunkToStretch == null) {
            return handleEmpty(buf, num);
        }
        SoundFilterUtil.interpolateInterleavedStereo(chunkToStretch, buf, fastTargetSize);

        // Return the pulled chunk back to the pool
        returnArray(chunkToStretch);

        return fastTargetSize;
    }

    private int handleTooSlow(int[] buf, int num) {
        int[] chunkToStretch = ring.pop();
        if (chunkToStretch == null) {
            return handleEmpty(buf, num);
        }
        SoundFilterUtil.interpolateInterleavedStereo(chunkToStretch, buf, slowTargetSize);

        // Return the pulled chunk back to the pool
        returnArray(chunkToStretch);

        return slowTargetSize;
    }

}
