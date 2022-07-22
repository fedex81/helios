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
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

import static omegadrive.system.perf.GenesisPerf.DevicePerf.*;

public class GenesisPerf extends Genesis {

    enum DevicePerf {M68K, VDP, Z80, FM, SVP}

    final static DevicePerf[] dpVals = DevicePerf.values();

    static class PerfHolder {
        public double[] mCycleFrame = new double[RegionDetector.Region.USA.getFps()];
        public int[] ipsFrame = new int[RegionDetector.Region.USA.getFps()];
        public double mCycleCnt;
        public int ipsCount;

        public void updateFrameCount(int frameCnt) {
            mCycleFrame[frameCnt] = mCycleCnt;
            ipsFrame[frameCnt] = ipsCount;
        }

        public double getMCyclesFrame(int frameCnt) {
            return IntStream.range(0, frameCnt).mapToDouble(i -> mCycleFrame[i]).sum();
        }

        public int getInstPerFrame(int frameCnt) {
            return IntStream.range(0, frameCnt).map(i -> ipsFrame[i]).sum();
        }

        public void resetCounters() {
            mCycleCnt = ipsCount = 0;
        }
    }

    private static final Logger LOG = LogHelper.getLogger(GenesisPerf.class.getSimpleName());

    static final String statsFormat = "%s mCycles: %f, ref: %d, ipf: %d, mCycles(errorPerc): %f%n";

    private PerfHolder[] pfh = new PerfHolder[dpVals.length];
    private int mclkHz, mClockHzRef;
    long frameWaitNs, lastSecTimeNs, frameProcessingNs;
    int totalCycles, frameCnt;

    double[] mCycleAvg = new double[pfh.length];
    int[] ipFrame = new int[pfh.length];

    public GenesisPerf(DisplayWindow emuFrame) {
        super(emuFrame);
        for (int i = 0; i < pfh.length; i++) {
            pfh[i] = new PerfHolder();
        }
    }

    private void stats() {
        Arrays.stream(pfh).forEach(p -> p.updateFrameCount(frameCnt));

        frameWaitNs += elapsedWaitNs;
        frameProcessingNs += frameProcessingDelayNs;
        frameCnt++;
        totalCycles += counter;

        if (frameCnt == videoMode.getRegion().getFps()) {
            long nowNs = System.nanoTime();
            long lastSecLenMs = Duration.ofNanos(nowNs - lastSecTimeNs).toMillis();
            IntStream.range(0, pfh.length).forEach(i -> mCycleAvg[i] = pfh[i].getMCyclesFrame(frameCnt));
            IntStream.range(0, pfh.length).forEach(i -> ipFrame[i] = pfh[i].getInstPerFrame(frameCnt));
            long waitMs = Duration.ofNanos(frameWaitNs).toMillis();
            long frameProcMs = Duration.ofNanos(frameProcessingNs).toMillis();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Last 1s duration in ms %d, errorPerc %f%n", lastSecLenMs, 100 - (100 * lastSecLenMs / 1000.0)));
            sb.append(String.format("helios cycles: %d, frameProcMs: %d, sleepMs %d%n", totalCycles, frameProcMs, waitMs));
            for (int i = 0; i < pfh.length; i++) {
                if (!hasSvp && i == SVP.ordinal()) {
                    continue;
                }
                double errorPerc = 100 - (100 * mClockHzRef / mCycleAvg[i]);
                sb.append(String.format(statsFormat, dpVals[i].name(), mCycleAvg[i], mClockHzRef, ipFrame[i], errorPerc));
            }
            LOG.info(sb.toString());
            frameCnt = 0;
            frameWaitNs = 0;
            totalCycles = 0;
            frameProcessingNs = 0;
            lastSecTimeNs = nowNs;
        }
    }

    private void addMCycles(DevicePerf d, double num) {
        pfh[d.ordinal()].mCycleCnt += num;
    }

    private void addIps(DevicePerf d, int num) {
        pfh[d.ordinal()].ipsCount += num;
    }

    @Override
    protected void loop() {
        updateVideoMode(true);
        int cnt;
        do {
            cnt = counter;
            cnt = runZ80(cnt);
            cnt = run68k(cnt);
            cnt = runFM(cnt);
            if (hasSvp) {
                runSvp(cnt);
            }
            //this should be last as it could change the counter
            cnt = runVdp(cnt);
            counter = cnt;
        } while (!runningRomFuture.isDone());
    }

    private final int runSvp(int untilClock) {
        while (nextSvpCycle <= untilClock) {
            ssp16.ssp1601_run(SVP_RUN_CYCLES);
            nextSvpCycle += SVP_CYCLES;
            addIps(SVP, SVP_RUN_CYCLES);
            addMCycles(SVP, SVP_CYCLES);
        }
        return Math.max(untilClock, nextSvpCycle);
    }

    private final int runVdp(int untilClock) {
        while (nextVdpCycle <= untilClock) {
            int vdpMclk = vdp.runSlot();
            nextVdpCycle += vdpVals[vdpMclk - 4];
            addMCycles(VDP, vdpVals[vdpMclk - 4]);
            addIps(VDP, 1);
            if (counter == 0) { //counter could be reset to 0 when calling vdp::runSlot
                untilClock = counter;
            }
        }
        return (int) Math.max(untilClock, nextVdpCycle);
    }

    private final int run68k(int untilClock) {
        while (next68kCycle <= untilClock) {
            boolean isRunning = bus.is68kRunning();
            boolean canRun = !cpu.isStopped() && isRunning;
            int cycleDelay = 1;
            if (canRun) {
                cycleDelay = cpu.runInstruction();
            }
            //interrupts are processed after the current instruction
            if (isRunning) {
                bus.handleVdpInterrupts68k();
            }
            cycleDelay = Math.max(1, cycleDelay);
            next68kCycle += M68K_DIVIDER * cycleDelay;
            addMCycles(M68K, cycleDelay);
            addIps(M68K, 1);
        }
        return Math.max(untilClock, next68kCycle);
    }

    private final int runZ80(int untilClock) {
        while (nextZ80Cycle <= untilClock) {
            int cycleDelay = 0;
            boolean running = bus.isZ80Running();
            if (running) {
                cycleDelay = z80.executeInstruction();
                bus.handleVdpInterruptsZ80();
            }
            cycleDelay = Math.max(1, cycleDelay);
            nextZ80Cycle += Z80_DIVIDER * cycleDelay;
            addMCycles(Z80, Z80_DIVIDER * cycleDelay);
            addIps(Z80, 1);
        }
        return Math.max(untilClock, nextZ80Cycle);
    }

    private final int runFM(int untilClock) {
        while (nextFMCycle <= untilClock) {
            bus.getFm().tick();
            nextFMCycle += FM_DIVIDER;
            addMCycles(FM, FM_DIVIDER);
            addIps(FM, 1);
        }
        return Math.max(untilClock, nextFMCycle);
    }

    @Override
    protected void updateVideoMode(boolean force) {
        VideoMode prev = videoMode;
        super.updateVideoMode(force);
        if (videoMode != prev || force) {
            mclkHz = videoMode.isPal() ? Util.GEN_PAL_MCLOCK_MHZ : Util.GEN_NTSC_MCLOCK_MHZ;
            mClockHzRef = mclkHz / (M68K_DIVIDER * MCLK_DIVIDER);
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
        Arrays.stream(pfh).forEach(PerfHolder::resetCounters);
    }
}
