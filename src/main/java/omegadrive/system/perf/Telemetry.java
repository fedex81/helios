package omegadrive.system.perf;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import omegadrive.sound.fm.AudioRateControl;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Telemetry
 * <p>
 * gnuplot> load 'tel.p'
 *
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class Telemetry {
    private final static Logger LOG = LogHelper.getLogger(Telemetry.class.getSimpleName());
    public static final boolean enable = false;

    private static final Function<Map<?, Double>, String> toStringFn = map -> {
        String res = Arrays.toString(map.values().toArray());
        return res.substring(1, res.length() - 2);
    };

    private static final BiFunction<Timing, Timing, String> toTimestampFn =
            (now, prev) -> (now.nanoTime - prev.nanoTime) / (double) Util.MILLI_IN_NS + "," +
                    Instant.ofEpochMilli(now.instantNow);

    private static final Telemetry telemetry = new Telemetry();
    private static final NumberFormat fpsFormatter = new DecimalFormat("#0.00");
    private static final int STATS_EVERY_FRAMES = 50;
    private static final Timing NO_TIMING = new Timing();
    private Path telemetryFile;
    private long frameCounter = 0;
    private final Table<String, Long, Double> data = TreeBasedTable.create();
    private final Map<Long, Timing> frameTimeStamp = new HashMap<>();

    private void addFrameTimestamp() {
        Timing t = new Timing();
        t.instantNow = Instant.now().toEpochMilli();
        t.nanoTime = System.nanoTime();
        frameTimeStamp.put(frameCounter, t);
    }

    public static Telemetry getInstance() {
        return telemetry;
    }

    private static void writeToFile(Path file, String res) {
        try {
            Files.write(file, res.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.error("Unable to write to: {}", file.toAbsolutePath());
        }
    }

    public void addSample(String type, double value) {
        if (!enable) {
            return;
        }
        if (frameCounter > 0) {
            data.put(type, frameCounter, value);
        }
    }

    private String getAvgFpsRounded(double avgFrameTimeMs) {
        return fpsFormatter.format(1000.0 / avgFrameTimeMs);
    }

    private double getAvgFrameTimeMs() {
        Timing current = frameTimeStamp.get(frameCounter);
        Timing prev = frameTimeStamp.getOrDefault(frameCounter - STATS_EVERY_FRAMES, NO_TIMING);
        if (prev == NO_TIMING) { //first frame gets a huge frame delay
            return 16.6;
        }
        return 1.0 * (current.instantNow - prev.instantNow) / STATS_EVERY_FRAMES;
    }

    public boolean hasNewStats() {
        return frameCounter % STATS_EVERY_FRAMES == 0; //update fps label every N frames
    }

    public Optional<String> getNewStats() {
        Optional<String> o = Optional.empty();
        if (hasNewStats()) {
            Optional<String> arc = AudioRateControl.getLatestStats();
            double ft = getAvgFrameTimeMs();
            o = Optional.of(fpsFormatter.format(ft) + "ms (" + getAvgFpsRounded(ft) + "fps)"
                    + (arc.map(s -> ", " + s).orElse("")));
        }
        return o;
    }

    public void reset() {
        frameCounter = 0;
        data.clear();
        frameTimeStamp.clear();
        telemetryFile = null;
    }

    public Optional<String> newFrame(double frameTimeNs, double driftNs) {
        addFrameTimestamp();
        addSample("fps", (1.0 * Util.SECOND_IN_NS) / frameTimeNs);
        addSample("driftNs", driftNs);
        Optional<String> os = getNewStats();
        newFrame();
        return os;
    }

    public void newFrame() {
        frameCounter++;
        if (!enable) {
            return;
        }
        if (frameCounter == 2) {
            telemetryFile = Paths.get(".", "tel_" + System.currentTimeMillis() + ".log");
            String header = String.format("frame,%s,frameTimeMs,frameEndTime",
                    String.join(",", data.rowKeySet()));
            LOG.info("Logging telemetry file to: {}", telemetryFile.toAbsolutePath());
            Util.executorService.submit(() -> writeToFile(telemetryFile, header));
        }
        if (frameCounter % 600 == 0) {
            String res = "\n" + data.columnKeySet().stream().map(this::toLogString).
                    collect(Collectors.joining("\n"));
            data.clear();
            Util.executorService.submit(() -> writeToFile(telemetryFile, res));
        }
    }

    private String toLogString(Long num) {
        return num + "," + toStringFn.apply(data.column(num)) + "," +
                toTimestampFn.apply(
                        frameTimeStamp.getOrDefault(num, NO_TIMING),
                        frameTimeStamp.getOrDefault(num - 1, NO_TIMING));
    }

    static class Timing {
        long instantNow;
        long nanoTime;
    }

    public long getFrameCounter() {
        return frameCounter;
    }
}
