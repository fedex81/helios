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

package omegadrive.sound.fm.ym2612.nukeykt;

import omegadrive.sound.SoundProvider;
import omegadrive.system.perf.Telemetry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * AudioRateControl
 */
public class AudioRateControl {
    public static final double FM_CALCS_PER_MICROS = (1_000_000.0 / SoundProvider.SAMPLE_RATE_HZ);
    private static final Logger LOG = LogManager.getLogger(AudioRateControl.class.getSimpleName());
    private static final boolean DEBUG = false;
    private static final double HALF_LIMIT = 0.025;
    private static final double LOWER_LIMIT = FM_CALCS_PER_MICROS * (1 - HALF_LIMIT);
    private static final double UPPER_LIMIT = FM_CALCS_PER_MICROS * (1 + HALF_LIMIT);
    static double fastPace = 0.005; //max distortion ~60hz/frame
    static double slowPace = fastPace / 2;
    private static NumberFormat bufferMsFormatter = new DecimalFormat("000");

    private static String latestStats = null;
    private int bufferSize;
    private int targetBufferSize;
    private long maxLen = 0;
    private long latestLen = 0;

    public AudioRateControl(int bufferSize) {
        this.bufferSize = bufferSize;
        this.targetBufferSize = bufferSize >> 1;
        LOG.info("Init with targetBufferSize: {}, bufferSize: {}", targetBufferSize, bufferSize);
    }

    public static String getLatestStats() {
        return latestStats;
    }

    public static void main(String[] args) {
        int i = 0;
        for (double fmCalcs = LOWER_LIMIT; fmCalcs < UPPER_LIMIT; i++) {
            double hz = 1_000_000.0 / fmCalcs;
            System.out.println(i + " , " + fmCalcs + " -> " + hz);
            fmCalcs += slowPace;
        }
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
        if (queueLen > maxLen) {
            if (DEBUG) {
                LOG.info("{}hz, q_av {}, b_size {}, steady {}", sampleRate, queueLen, bufferSize, steadyState);
            }
            maxLen = queueLen;
        }
        latestLen = queueLen;
        computeStats();
        return fm;
    }

    public void computeStats() {
        long audioDelayMs = 0;
        if (latestLen > 0 && maxLen > 0) {
            audioDelayMs = (long) (1000.0 * latestLen / SoundProvider.SAMPLE_RATE_HZ);
            long maxAudioDelayMs = (long) (1000.0 * maxLen / SoundProvider.SAMPLE_RATE_HZ);
            latestStats = bufferMsFormatter.format(audioDelayMs) + " / " +
                    bufferMsFormatter.format(maxAudioDelayMs) + "ms";
        }
        Telemetry.getInstance().addSample("audioDelayMs", audioDelayMs);
        Telemetry.getInstance().addSample("audioQueueLen", latestLen);
    }
}
