package omegadrive.util;

import org.slf4j.Logger;

import java.util.concurrent.locks.LockSupport;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class Sleeper {

    public static final boolean BUSY_WAIT;

    //Linux has a timer slack of 50micros, windows is hopeless
    //https://lwn.net/Articles/369549/
    public static final long SLEEP_RESOLUTION_NS = 50_000;
    private final static Logger LOG = LogHelper.getLogger(Util.class.getSimpleName());

    static {
        startSleeperThread();
        BUSY_WAIT = Boolean.parseBoolean(System.getProperty("helios.busy.wait", "false"));
        LOG.info("Busy waiting instead of sleeping: {}", BUSY_WAIT);
    }

    public static boolean isWindows() {
        return Util.OS_NAME.contains("win");
    }

    //futile attempt at getting high resolution sleeps on windows
    private static void startSleeperThread() {
        if (isWindows()) {
            Runnable r = () -> Util.sleep(Long.MAX_VALUE);
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("sleeperForWindows");
            t.start();
        }
    }

    private static void handleSleepDelay(long prev, long now, long expectedIntervalNs) {
        if (now - prev > expectedIntervalNs + Util.MILLI_IN_NS) {
            LOG.info("JVM over-sleeping ({} ms): {}",
                    expectedIntervalNs / (double) Util.MILLI_IN_NS, (now - prev) / (double) Util.MILLI_IN_NS);
        }
    }

    private static void handleSlowdown(String when, long now, long deadlineNs) {
        if (now > deadlineNs + Util.MILLI_IN_NS) {
//            LOG.info("Slowdown detected {} sleeping, delay_ms: {}", when, (now - deadlineNs) / (double) Util.MILLI_IN_NS);
        }
    }

    //sleeps for the given interval, doesn't mind returning a bit early
    public static void parkFuzzy(final long intervalNs) {
        if (intervalNs < SLEEP_RESOLUTION_NS) {
            return;
        }
        parkExactly(intervalNs - SLEEP_RESOLUTION_NS);
    }

    public static void parkExactly(long intervalNs) {
        assert intervalNs > 0;
        if (intervalNs < SLEEP_RESOLUTION_NS) {
            return;
        }
        if (BUSY_WAIT) {
            long deadlineNs = System.nanoTime() + intervalNs;
            while (System.nanoTime() < deadlineNs) {
                Thread.yield();
            }
            return;
        }
        boolean done;
        long start = System.nanoTime();
        final long deadlineNs = start + intervalNs;
        if (deadlineNs < start) {
            handleSlowdown("Before", start, deadlineNs);
            return;
        }
        do {
            LockSupport.parkNanos(intervalNs);
            long nowNs = System.nanoTime();
            intervalNs = Math.max(deadlineNs - intervalNs, SLEEP_RESOLUTION_NS);
            done = nowNs > deadlineNs;
        } while (!done);
        handleSlowdown("After", System.nanoTime(), deadlineNs);
    }

    public static void parkExactlyHybrid(final long intervalNs) {
        if (BUSY_WAIT) {
            long deadlineNs = System.nanoTime() + intervalNs;
            while (System.nanoTime() < deadlineNs) {
                Thread.yield();
            }
            return;
        }
        long start = System.nanoTime();
        final long deadlineNs = start + intervalNs;
        if (deadlineNs < start) {
            handleSlowdown("Before", start, deadlineNs);
            return;
        }
        long now = System.nanoTime();
        if (intervalNs > Util.MILLI_IN_NS) {
            long remainingNs = intervalNs;
            long spinIntervalNs = Util.MILLI_IN_NS;
            do {
                long prevNow = System.nanoTime();
                LockSupport.parkNanos(remainingNs - spinIntervalNs);
                now = System.nanoTime();
                handleSleepDelay(prevNow, now, remainingNs - spinIntervalNs);
                remainingNs = deadlineNs - now;
            } while (remainingNs > spinIntervalNs);
            handleSlowdown("After-park", now, deadlineNs);
        }
        while (now < deadlineNs) {
            Thread.yield();
            now = System.nanoTime();
        }
        handleSlowdown("After-spin", now, deadlineNs);
    }
}
