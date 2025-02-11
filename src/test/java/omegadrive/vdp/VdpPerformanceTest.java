/*
 * VdpPerformanceTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 14:04
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

package omegadrive.vdp;

import omegadrive.SystemLoader;
import omegadrive.cpu.m68k.MC68000Helper;
import omegadrive.input.InputProvider;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SystemProvider;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static omegadrive.vdp.MdVdpTestUtil.getVdpProvider;

@Ignore
public class VdpPerformanceTest {

    static Path testFilePath = Paths.get("./test_roms", "tf4.zip");
    //    static Path testFilePath = Paths.get("./test_roms", "s1.zip");
    //    static Path testFilePath = Paths.get("./test_roms", "zax.col");
    static int fps = 60;
    protected int frameCnt = 0;
    int sampleCnt = 0;
    long last = 0;
    int ignoreFramceCounter = 5; //warmup
    long start = System.nanoTime();

    @BeforeClass
    public static void beforeTest() {
        System.setProperty("helios.headless", "false");
        System.setProperty("helios.fullSpeed", "true");
        System.setProperty("helios.enable.sound", "true");
//        System.setProperty("68k.debug", "true");
//        System.setProperty("z80.debug", "true");
//        System.setProperty("md.show.vdp.debug.viewer", "true");
    }

    protected static SystemProvider createTestProvider() {
        InputProvider.bootstrap();
        return SystemLoader.getInstance().handleNewRomFile(MediaSpecHolder.of(testFilePath));
    }

    private static void printFramePerf(long sampleCnt, long nowNs, long lastNs, long startNs, int frameCount) {
        long lastTickMs = Duration.ofNanos(nowNs - lastNs).toMillis();
        long totalMs = Duration.ofNanos(nowNs - startNs).toMillis();
        String str = String.format("%d, lastSecRealMs: %d, lastFPS: %f, totalSecMs: %d, avgFPS: %f",
                sampleCnt, lastTickMs, fps / (lastTickMs / 1000.0), totalMs, frameCount / (totalMs / 1000.0));
        System.out.println(str);
    }

    @Test
    public void testVdpPerf() {
        SystemProvider system = createTestProvider();
        assert system != null;
        createAndAddVdpListener(system);
        Util.waitForever();
    }

    private long prevFrameNs;
    private double maxFrameNs = 19 * Util.MILLI_IN_NS;
    private int limit = 300;

    private void doStats() {
        if (--ignoreFramceCounter >= 0) {
            start = System.nanoTime();
            last = start - 1;
//            MC68000WrapperDebug.countHits = true;
            prevFrameNs = System.nanoTime();
            return;
        }
        frameCnt++;
        if (frameCnt > limit) {
            long ns = System.nanoTime();
            long diffNs = ns - prevFrameNs;
            double diffMs = (1.0 * diffNs / Util.MILLI_IN_NS);
            if (diffNs > maxFrameNs && diffMs < 100) {
//                maxFrameNs = diffNs;
//                System.out.println("Frame: " + frameCnt + ", timeMs: " + diffMs);
            } else if (diffMs > 100 && frameCnt > limit + 1) {
                System.out.println("!!!! SLOW Frame: " + frameCnt + ", timeMs: " + diffMs);
            }
            prevFrameNs = System.nanoTime();
        }

        if (frameCnt % fps == 0) {
//            hitCounter(0, -1);
            long now = System.nanoTime();
            printFramePerf(sampleCnt, now, last, start, frameCnt);
            last = now;
            sampleCnt++;
        }
    }

    private void hitCounter(int startFrame, int endFrame) {
        if (!MC68000Helper.M68K_DEBUG) {
            return;
        }
    }

    protected void createAndAddVdpListener(SystemProvider systemProvider) {
        try {
            BaseVdpProvider vdpProvider = getVdpProvider(systemProvider);
            vdpProvider.addVdpEventListener(new BaseVdpProvider.VdpEventListener() {
                @Override
                public void onNewFrame() {
                    fps = systemProvider.getRegion().getFps();
                    doStats();
                }
            });
        } catch (Exception e) {
            System.err.println("Unable to collect stats");
            e.printStackTrace();
        }
    }

}
