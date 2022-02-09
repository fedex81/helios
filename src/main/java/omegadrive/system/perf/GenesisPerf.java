/*
 * GenesisPerf
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 12:15
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

package omegadrive.system.perf;

import omegadrive.system.Genesis;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Genesis emulator main class
 * <p>
 * MEMORY MAP:	https://en.wikibooks.org/wiki/Genesis_Programming
 */
public class GenesisPerf extends Genesis {

    static int mclkHz, m68kRef, vdpRef, z80Ref, fmRef;
    private static final Logger LOG = LogManager.getLogger(GenesisPerf.class.getSimpleName());
    int[] cycleVdpFrame = new int[RegionDetector.Region.USA.getFps()];
    int[] cycle68kFrame = new int[RegionDetector.Region.USA.getFps()];
    int[] cycleZ80Frame = new int[RegionDetector.Region.USA.getFps()];
    int[] cycleFmFrame = new int[RegionDetector.Region.USA.getFps()];
    int cycleVdpCnt, cycle68kCnt, cycleZ80cnt, cycleFmCnt;
    long frameWaitNs, lastSecTimeNs, frameProcessingNs;
    int totalCycles, frameCnt;
    long samplesAudioProd, samplesAudioCons;

    public GenesisPerf(DisplayWindow emuFrame) {
        super(emuFrame);
    }

    private void stats() {
        cycle68kFrame[frameCnt] = cycle68kCnt;
        cycleVdpFrame[frameCnt] = cycleVdpCnt;
        cycleZ80Frame[frameCnt] = cycleZ80cnt;
        cycleFmFrame[frameCnt] = cycleFmCnt;

        frameWaitNs += elapsedWaitNs;
        frameProcessingNs += frameProcessingDelayNs;
        frameCnt++;
        totalCycles += counter;

        if (frameCnt == videoMode.getRegion().getFps()) {
            long nowNs = System.nanoTime();
            long lastSecLenMs = Duration.ofNanos(nowNs - lastSecTimeNs).toMillis();
            double vdpAvg = IntStream.range(0, frameCnt).mapToDouble(i -> cycleVdpFrame[i]).sum();
            double m68kAvg = IntStream.range(0, frameCnt).mapToDouble(i -> cycle68kFrame[i]).sum();
            double z80Avg = IntStream.range(0, frameCnt).mapToDouble(i -> cycleZ80Frame[i]).sum();
            double fmAvg = IntStream.range(0, frameCnt).mapToDouble(i -> cycleFmFrame[i]).sum();
            long waitMs = Duration.ofNanos(frameWaitNs).toMillis();
            long frameProcMs = Duration.ofNanos(frameProcessingNs).toMillis();
            long prevP = samplesAudioProd;
            long prevC = samplesAudioCons;
            samplesAudioProd = 0;//JavaSoundManager.samplesProducedCount;
            samplesAudioCons = 0; //JavaSoundManager.samplesConsumedCount;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Last 1s duration in ms %d, errorPerc %f%n", lastSecLenMs, 100 - (100 * lastSecLenMs / 1000.0)));
            sb.append(String.format("helios cycles: %d, frameProcMs: %d, sleepMs %d%n", totalCycles, frameProcMs, waitMs));
            sb.append(String.format("68k cycles: %f, ref %d, errorPerc %f%n", m68kAvg, m68kRef, 100 - (100 * m68kRef / m68kAvg)));
            sb.append(String.format("Z80 cycles: %f, ref: %d, errorPerc: %f%n", z80Avg, z80Ref, 100 - (100 * z80Ref / z80Avg)));
            sb.append(String.format("FM cycles: %f, ref: %d, errorPerc: %f%n", fmAvg, fmRef, 100 - (100 * fmRef / fmAvg)));
            sb.append(String.format("VDP cycles: %f%n", vdpAvg));
            sb.append(String.format("Sound samples, produced: %d, consumed %d%n",
                    (samplesAudioProd - prevP) >> 1, (samplesAudioCons - prevC) >> 1));
//            sb.append(String.format("VDP cycles: %f, ref: %d, errorPerc: %f%n", vdpAvg,vdpRef, 100 - (100*vdpRef/vdpAvg)));

            LOG.info(sb.toString());
            frameCnt = 0;
            frameWaitNs = 0;
            totalCycles = 0;
            frameProcessingNs = 0;
            lastSecTimeNs = nowNs;
        }
    }

    @Override
    protected void loop() {
        LOG.info("Starting game loop");
        updateVideoMode(true);
        double prevVdpCycle = 0;
        do {
            try {
                prevVdpCycle = nextVdpCycle;
                run68k();
                runZ80();
                runFM();
                runVdp();
                doCounting(prevVdpCycle);
                counter++;
            } catch (Exception e) {
                LOG.error("Error main cycle", e);
                break;
            }
        } while (!runningRomFuture.isDone());
        LOG.info("Exiting rom thread loop");
    }

    private void doCounting(double prevVdpCycle) {
        cycleVdpCnt += nextVdpCycle > prevVdpCycle ? 1 : 0;
        if (counter % M68K_DIVIDER == 0) {
            cycle68kCnt++;
        }
        if (counter % Z80_DIVIDER == 0) {
            cycleZ80cnt++;
        }
        if (counter % FM_DIVIDER == 0) {
            cycleFmCnt++;
        }
    }

    @Override
    protected void updateVideoMode(boolean force) {
        VideoMode prev = videoMode;
        super.updateVideoMode(force);
        if (videoMode != prev || force) {
            mclkHz = videoMode.isPal() ? Util.GEN_PAL_MCLOCK_MHZ : Util.GEN_NTSC_MCLOCK_MHZ;
            m68kRef = mclkHz / (M68K_DIVIDER * MCLK_DIVIDER);
            z80Ref = mclkHz / (Z80_DIVIDER * MCLK_DIVIDER);
            fmRef = mclkHz / (FM_DIVIDER * MCLK_DIVIDER);
        }
    }

    @Override
    protected Optional<String> getStats(long nowNs, long prevStartNs) {
        stats();
        return super.getStats(nowNs, prevStartNs);
    }

    @Override
    protected void resetCycleCounters(int counter) {
        super.resetCycleCounters(counter);
        cycleVdpCnt = cycleFmCnt = cycle68kCnt = cycleZ80cnt = 0;
    }
}
