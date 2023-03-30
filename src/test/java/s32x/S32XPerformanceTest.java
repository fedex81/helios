package s32x;

import omegadrive.SystemLoader;
import omegadrive.input.InputProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static omegadrive.vdp.MdVdpTestUtil.getVdpProvider;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
@Disabled
public class S32XPerformanceTest {

    static Path testFilePath = Paths.get("./res/misc/roms", "t1.zip");
    static int fps = 60;
    protected int frameCnt = 0;
    int sampleCnt = 0;
    long last = 0;
    int ignoreFramceCounter = 5; //warmup
    long start = System.nanoTime();

    @BeforeAll
    public static void beforeTest() {
        System.setProperty("helios.headless", "false");
        System.setProperty("helios.fullSpeed", "false");
        System.setProperty("helios.enable.sound", "true");
        System.setProperty("68k.debug", "false");
        System.setProperty("z80.debug", "false");
//        System.setProperty("ui.scale.on.thread", "true");
//        System.setProperty("md.show.vdp.debug.viewer", "true");
    }

    protected static SystemProvider createTestProvider() {
        InputProvider.bootstrap();
        Assertions.assertTrue(testFilePath.toFile().exists(), "File not found: " + testFilePath.toAbsolutePath());
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
    public void testVdpPerf() {
        SystemProvider system = createTestProvider();
        createAndAddVdpListener(system);
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
            hitCounter(0, -1);
            long now = System.nanoTime();

            printFramePerf(sampleCnt, now, last, start, frameCnt);
            last = now;
            sampleCnt++;
        }
    }

    private void hitCounter(int startFrame, int endFrame) {
        return;
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
