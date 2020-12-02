/*
 * Ym2612Nuke
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 25/10/19 16:39
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.sound.fm;

import com.google.common.collect.Maps;
import omegadrive.sound.SoundProvider;
import omegadrive.system.perf.Telemetry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AudioRateControl
 */
public class AudioRateControl {
    public static final double FM_CALCS_PER_MICROS = (1_000_000.0 / SoundProvider.SAMPLE_RATE_HZ);
    private static final Logger LOG = LogManager.getLogger(AudioRateControl.class.getSimpleName());
    private static final boolean DEBUG = false;
    private static final double HALF_LIMIT = 0.0125;
    private static final double LOWER_LIMIT = FM_CALCS_PER_MICROS * (1 - HALF_LIMIT);
    private static final double UPPER_LIMIT = FM_CALCS_PER_MICROS * (1 + HALF_LIMIT);
    static double fastPace = 0.05; //max distortion ~60hz/frame
    static double slowPace = fastPace / 2;

    private StatsHolder statsHolder;
    private int bufferSize;
    private int targetBufferSize;

    public AudioRateControl(String sourceName, int bufferSize) {
        this.bufferSize = bufferSize;
        this.targetBufferSize = (int) (bufferSize * 0.75d);
        statsHolder = new StatsHolder(sourceName);
        LOG.info("Init with targetBufferSize: {}, bufferSize: {}", targetBufferSize, bufferSize);
    }

    public static Optional<String> getLatestStats() {
        if (StatsHolder.statsHolderMap.isEmpty()) {
            return Optional.empty();
        }
        String s = StatsHolder.statsHolderMap.values().stream().
                map(StatsHolder::computeStringStats).collect(Collectors.joining(","));
        return Optional.ofNullable(s);
    }

    public double adaptiveRateControl(long queueLen, double fmCalcsPerMicros, int sampleRate) {
        double fm = fmCalcsPerMicros;
        boolean tooSmall = queueLen < targetBufferSize;
        boolean tooBig = queueLen > (targetBufferSize << 1);
        boolean steadyState = !tooBig && !tooSmall;
        if (steadyState) {
            fm += fm > FM_CALCS_PER_MICROS ? -slowPace : slowPace;
        } else {
            fm += tooBig ? slowPace : 0;
            fm += tooSmall ? -fastPace : 0;
        }
        //limit
        fm = fm > UPPER_LIMIT ? UPPER_LIMIT : (fm < LOWER_LIMIT ? LOWER_LIMIT : fm);
        if (queueLen > statsHolder.maxLen) {
            if (DEBUG) {
                LOG.info("{}hz, q_av {}, b_size {}, steady {}", sampleRate, queueLen, bufferSize, steadyState);
            }
            statsHolder.maxLen = queueLen;
        }
        statsHolder.latestLen = queueLen;
        statsHolder.fmCalcPerMicros = fm;
        statsHolder.computeTelemetryStats();
        return fm;
    }

    private static class StatsHolder {
        public static Map<String, StatsHolder> statsHolderMap = Maps.newHashMap();
        private static NumberFormat bufferMsFormatter = new DecimalFormat("000");
        public long maxLen = 0;
        public long latestLen = 0;
        public long audioDelayMs = 0;
        public double fmCalcPerMicros = 0;
        public String sourceName;
        public String infoString;

        protected StatsHolder(String sourceName) {
            this.sourceName = sourceName;
            statsHolderMap.clear();
            statsHolderMap.put(sourceName, this);
        }

        protected void computeTelemetryStats() {
            audioDelayMs = (long) (1000.0 * latestLen / SoundProvider.SAMPLE_RATE_HZ);
            Telemetry.getInstance().addSample(sourceName + ".fmCalcPerMicros", fmCalcPerMicros);
            Telemetry.getInstance().addSample(sourceName + ".audioDelayMs", audioDelayMs);
            Telemetry.getInstance().addSample(sourceName + ".audioQueueLen", latestLen);
        }

        protected String computeStringStats() {
            if (latestLen > 0 && maxLen > 0) {
                long maxAudioDelayMs = (long) (1000.0 * maxLen / SoundProvider.SAMPLE_RATE_HZ);
                infoString = sourceName + " " +
                        bufferMsFormatter.format(audioDelayMs) + " / " +
                        bufferMsFormatter.format(maxAudioDelayMs) + "ms";
            }
            return infoString;
        }
    }
}
