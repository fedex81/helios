package omegadrive.system.perf;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import omegadrive.sound.fm.AudioRateControl;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private final static Logger LOG = LogManager.getLogger(Telemetry.class.getSimpleName());
    public static final boolean enable = false;
    private static final Function<Map<?, Double>, String> toStringFn = map -> {
        String res = Arrays.toString(map.values().stream().toArray());
        return res.substring(1, res.length() - 2);
    };

    private static final BiFunction<Timing, Timing, String> toTimestampFn =
            (now, prev) -> (now.nanoTime - prev.nanoTime) / (double) Util.MILLI_IN_NS + "," +
                    Instant.ofEpochMilli(now.instantNow);

    private static Telemetry telemetry = new Telemetry();
    private static NumberFormat fpsFormatter = new DecimalFormat("#0.00");
    private Table<String, Long, Double> data = TreeBasedTable.create();
    private static final Timing NO_TIMING = new Timing();
    private Path telemetryFile;
    private long frameCounter = 0;
    private static int STATS_EVERY_FRAMES = 50;
    private double fpsAccum = 0;
    private Map<Long, Timing> frameTimeStamp = new HashMap<>();

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

    public void addFpsSample(double value) {
        fpsAccum += value;
        addSample("fps", value);
    }

    public void addSample(String type, double value) {
        if (!enable) {
            return;
        }
        if (frameCounter > 0) {
            data.put(type, frameCounter, value);
        }
    }

    private String getAvgFpsRounded() {
        double r = fpsAccum / STATS_EVERY_FRAMES;
        r = ((int) (r * 100)) / 100d;
        fpsAccum = 0;
        return fpsFormatter.format(r);
    }

    public boolean hasNewStats() {
        return frameCounter % STATS_EVERY_FRAMES == 0; //update fps label every N frames
    }

    public Optional<String> getNewStats() {
        Optional<String> o = Optional.empty();
        if (hasNewStats()) {
            Optional<String> arc = AudioRateControl.getLatestStats();
            o = Optional.of(getAvgFpsRounded() + "fps" + (arc.isPresent() ? ", " + arc.get() : ""));
        }
        return o;
    }

    public void reset() {
        frameCounter = 0;
        data.clear();
        frameTimeStamp.clear();
        telemetryFile = null;
    }

    public Optional<String> newFrame(double lastFps, double driftNs) {
        addFrameTimestamp();
        addFpsSample(lastFps);
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
                    data.rowKeySet().stream().collect(Collectors.joining(",")));
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
