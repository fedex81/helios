package omegadrive.system.perf;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import omegadrive.sound.fm.ym2612.nukeykt.AudioRateControl;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
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
    private static final boolean enable = false;
    private static final Function<Map<?, Double>, String> toStringFn = map -> {
        String res = Arrays.toString(map.values().toArray());
        return res.substring(1, res.length() - 2);
    };

    private static Telemetry telemetry = new Telemetry();
    private Table<String, Long, Double> data = TreeBasedTable.create();
    private Path telemetryFile;
    private long frameCounter = 0;
    private static int STATS_EVERY_FRAMES = 50;
    private double fpsAccum = 0;


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

    private double getAvgFpsRounded() {
        double r = fpsAccum / STATS_EVERY_FRAMES;
        r = ((int) (r * 100)) / 100d;
        fpsAccum = 0;
        return r;
    }

    public boolean hasNewStats() {
        return frameCounter % STATS_EVERY_FRAMES == 0; //update fps label every N frames
    }

    public Optional<String> getNewStats() {
        Optional<String> o = Optional.empty();
        if (hasNewStats()) {
            Optional<String> arc = Optional.ofNullable(AudioRateControl.getLatestStats());
            o = Optional.of(getAvgFpsRounded() + "fps" + (arc.isPresent() ? ", " + arc.get() : ""));
        }
        return o;
    }

    public void reset() {
        frameCounter = 0;
        data.clear();
        telemetryFile = null;
    }

    public void newFrame() {
        frameCounter++;
        if (!enable) {
            return;
        }
        if (frameCounter == 2) {
            telemetryFile = Paths.get(".", "tel_" + System.currentTimeMillis() + ".log");
            String header = "frame," + data.rowKeySet().stream().collect(Collectors.joining(","));
            writeToFile(telemetryFile, header);
            LOG.info("Logging telemetry file to: {}", telemetryFile.toAbsolutePath());
        }
        if (frameCounter % 600 == 0) {
            String res = "\n" + data.columnKeySet().stream().map(num -> num + "," + toStringFn.apply(data.column(num))).
                    collect(Collectors.joining("\n"));
            data.clear();
            Util.executorService.submit(() -> writeToFile(telemetryFile, res));
        }
    }
}
