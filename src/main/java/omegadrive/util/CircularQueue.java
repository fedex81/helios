package omegadrive.util;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class CircularQueue {

    private static final Logger LOG = LogHelper.getLogger(CircularQueue.class.getSimpleName());
    private final int[][] buffer;
    private final int capacity;
    private int head = 0;
    private int tail = 0;

    // Tracks the total unconsumed items in the queue
    private final AtomicInteger count = new AtomicInteger(0);

    /**
     * Initializes the queue with a maximum capacity of chunks.
     */
    public CircularQueue(int capacity) {
        this.capacity = capacity;
        this.buffer = new int[capacity][];
    }

    /**
     * Corresponds to r.ring.Counter() & 0xf in Go.
     * Returns the current balance state metric of the buffer.
     */
    public int counter() {
        return count.get();
    }

    /**
     * Pushes an audio chunk reference onto the tail of the queue.
     */
    public boolean push(int[] chunk) {
        if (count.get() >= capacity) {
            LOG.error("overflow");
            return false; // Queue overflow
        }

        buffer[tail] = chunk;
        tail = (tail + 1) % capacity;
        count.incrementAndGet();
        return true;
    }

    /**
     * Pops a single audio chunk from the head of the queue.
     */
    public int[] pop() {
        if (count.get() == 0) {
            LOG.error("underflow");
            return null; // Queue underflow
        }

        int[] chunk = buffer[head];
        buffer[head] = null; // Clear reference for GC
        head = (head + 1) % capacity;
        count.decrementAndGet();
        return chunk;
    }

    public int getCapacity() {
        return capacity;
    }
}

