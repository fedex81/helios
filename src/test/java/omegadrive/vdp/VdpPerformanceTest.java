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
import omegadrive.automated.SavestateGameLoader;
import omegadrive.input.InputProvider;
import omegadrive.system.BaseSystem;
import omegadrive.system.SystemProvider;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Ignore
public class VdpPerformanceTest {

    static Path testFilePath = Paths.get(SavestateGameLoader.romFolder, "Sonic The Hedgehog (W) (REV00) [!].bin");
    static int fps = 60;
    int frameCnt = 0;
    int sampleCnt = 0;
    long last = 0;
    int ignoreFramceCounter = 5; //warmup
    long start = System.nanoTime();

    @BeforeClass
    public static void beforeTest() {
        System.setProperty("emu.headless", "true");
    }

    private static SystemProvider createTestProvider() {
        InputProvider.bootstrap();
        return SystemLoader.getInstance().handleNewRomFile(testFilePath);
    }

    private static void printFramePerf(long sampleCnt, long nowNs, long lastNs, long startNs, int frameCount) {
        long lastTickMs = Duration.ofNanos(nowNs - lastNs).toMillis();
        long totalMs = Duration.ofNanos(nowNs - startNs).toMillis();
        String str = String.format("%d, lastSecRealMs: %d, lastFPS: %f, totalSecMs: %d, avgFPS: %f",
                sampleCnt, lastTickMs, fps / (lastTickMs / 1000.0), totalMs, frameCount / (totalMs / 1000.0));
        System.out.println(str);
    }

    @Test
    public void testVdpPerf() throws Exception {
        BaseSystem system = (BaseSystem) createTestProvider();
        fps = system.getRegion().getFps();
        Field f = BaseSystem.class.getDeclaredField("vdp");
        f.setAccessible(true);
        GenesisVdpProvider vdpProvider = (GenesisVdpProvider) f.get(system);
        vdpProvider.addVdpEventListener(new BaseVdpProvider.VdpEventAdapter() {
            @Override
            public void onNewFrame() {
                doStats();
            }
        });
        Util.waitForever();
    }

    private void doStats() {
        if (--ignoreFramceCounter >= 0) {
            start = System.nanoTime();
            last = start - 1;
            return;
        }
        frameCnt++;
        if (frameCnt % fps == 0) {
            long now = System.nanoTime();
            printFramePerf(sampleCnt, now, last, start, frameCnt);
            last = now;
            sampleCnt++;
        }
    }

}
