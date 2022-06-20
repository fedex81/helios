/*
 * AudioRateControl
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
import omegadrive.util.SoundUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AudioRateControl
 * <p>
 * bufferSize = 50 ms
 * bufferTooSmall < 37 ms (= bufferSize * targetBufferFactor)
 * bufferTooBig > 74 ms (= bufferTooSmall*2)
 * <p>
 * if  bufferTooSmall < currentBufferSize < bufferTooBig then do nothing
 * else currentBufferSize < bufferTooSmall then produce_more_samples
 * else currentBufferSize > bufferTooBig   then produce_less_samples
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
    private static final double targetBufferFactor = 0.75;

    private final StatsHolder statsHolder;
    private final int stereoBufferSize;
    private final int stereoTargetBufferSize;
    private final AudioFormat audioFormat;

    public AudioRateControl(String sourceName, AudioFormat audioFormat) {
        this.audioFormat = audioFormat;
        this.stereoBufferSize = SoundUtil.getStereoSamplesBufferSize(audioFormat);
        this.stereoTargetBufferSize = (int) (stereoBufferSize * targetBufferFactor);
        //176400 bytes = 88200 16 bit samples
        statsHolder = new StatsHolder(sourceName, SoundUtil.getSamplesBufferSize(audioFormat, 1000) >> 1);
        LOG.info("Init bufferLenMs: {}, targetBufferLenMs: {}, stereoTargetBufferSize: {}, " +
                        "stereoBufferSize: {}\naudioFormat: {}",
                SoundProvider.AUDIO_BUFFER_LEN_MS, (int) (SoundProvider.AUDIO_BUFFER_LEN_MS * targetBufferFactor),
                stereoTargetBufferSize, stereoBufferSize, audioFormat);
    }

    public static Optional<String> getLatestStats() {
        if (StatsHolder.statsHolderMap.isEmpty()) {
            return Optional.empty();
        }
        String s = StatsHolder.statsHolderMap.values().stream().
                map(StatsHolder::computeStringStats).collect(Collectors.joining(","));
        return Optional.ofNullable(s);
    }

    public double adaptiveRateControl(long stereoQueueLen, double fmCalcsPerMicros, int sampleRate) {
        double fm = fmCalcsPerMicros;
        boolean tooSmall = stereoQueueLen < stereoTargetBufferSize;
        boolean tooBig = stereoQueueLen > (stereoTargetBufferSize << 1);
        boolean steadyState = !tooBig && !tooSmall;
        if (steadyState) {
            fm += fm > FM_CALCS_PER_MICROS ? -slowPace : slowPace;
        } else {
            fm += tooBig ? slowPace : 0;
            fm += tooSmall ? -fastPace : 0;
        }
        //limit
        fm = fm > UPPER_LIMIT ? UPPER_LIMIT : (fm < LOWER_LIMIT ? LOWER_LIMIT : fm);
        if (stereoQueueLen > statsHolder.maxLen) {
            if (DEBUG) {
                LOG.info("{}hz, q_av {}, b_size {}, steady {}", sampleRate, stereoQueueLen, stereoBufferSize, steadyState);
            }
            statsHolder.maxLen = stereoQueueLen;
        }
        statsHolder.latestLen = stereoQueueLen;
        statsHolder.fmCalcPerMicros = fm;
        statsHolder.computeTelemetryStats();
        return fm;
    }

    private static class StatsHolder {
        public static Map<String, StatsHolder> statsHolderMap = Maps.newHashMap();
        private static final NumberFormat bufferMsFormatter = new DecimalFormat("000");
        public long maxLen = 0;
        public long latestLen = 0;
        public long audioDelayMs = 0;
        public double fmCalcPerMicros = 0;
        public String sourceName;
        public String infoString;
        private final int samplesPerSecond;

        protected StatsHolder(String sourceName, int samplesPerSecond) {
            this.sourceName = sourceName;
            this.samplesPerSecond = samplesPerSecond;
            statsHolderMap.clear();
            statsHolderMap.put(sourceName, this);
        }

        protected void computeTelemetryStats() {
            audioDelayMs = (long) (1000.0 * latestLen / samplesPerSecond);
            Telemetry.getInstance().addSample(sourceName + ".fmCalcPerMicros", fmCalcPerMicros);
            Telemetry.getInstance().addSample(sourceName + ".audioDelayMs", audioDelayMs);
            Telemetry.getInstance().addSample(sourceName + ".audioQueueLen", latestLen);
        }

        protected String computeStringStats() {
            if (latestLen > 0 && maxLen > 0) {
                long maxAudioDelayMs = (long) (1000.0 * maxLen / samplesPerSecond);
                infoString = sourceName + " " +
                        bufferMsFormatter.format(audioDelayMs) + " / " +
                        bufferMsFormatter.format(maxAudioDelayMs) + "ms";
            }
            return infoString;
        }
    }
}
