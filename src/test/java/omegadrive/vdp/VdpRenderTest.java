package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.GenesisProvider;
import omegadrive.automated.SavestateGameLoader;
import omegadrive.bus.BusProvider;
import omegadrive.input.InputProvider;
import omegadrive.save.SavestateTest;
import omegadrive.util.Util;
import omegadrive.vdp.model.RenderType;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
@Ignore
public class VdpRenderTest {

    private static int[][] screenData;
    private VdpRenderDump renderDump = new VdpRenderDump();
    private JLabel label;
    private JFrame f;
    private static String saveStateFolder = SavestateTest.saveStateFolder.toAbsolutePath().toString();
    static int CYCLES = 1_000;

    private void testSavestateViewerSingle(Path saveFile, String rom) throws Exception {
        VdpProvider vdpProvider = prepareVdp(saveFile);
        VdpTestUtil.runToStartFrame(vdpProvider);
//            renderDump.saveRenderToFile(screenData, vdpProvider.getVideoMode(), RenderType.FULL);
        BufferedImage bi = renderDump.getImage(screenData, vdpProvider.getVideoMode(), RenderType.FULL);
        String title = rom + " (" + saveFile.getFileName().toString() + ")";
        showImage(bi, title);
    }


    @Test
    public void testInterlaced() throws Exception {
        String saveName = "s2_int.gs0";
        saveName = "cc_int.gs0";
        Path saveFile = Paths.get(saveStateFolder, saveName);
        testSavestateViewerSingle(saveFile, SavestateGameLoader.saveStates.get(saveName));
        Util.waitForever();
    }

    @Test
    public void testSavestateViewerAll() throws Exception {
        for (Map.Entry<String, String> e : SavestateGameLoader.saveStates.entrySet()) {
            String saveStateFile = e.getKey();
            Path saveFile = Paths.get(saveStateFolder, saveStateFile);
            testSavestateViewerSingle(saveFile, e.getValue());
            Util.sleep(2000);
        }
    }

    @Test
    public void testVpdPerformance() throws Exception {
        for (Map.Entry<String, String> e : SavestateGameLoader.saveStates.entrySet()) {
            String saveStateFile = e.getKey();
            Path saveFile = Paths.get(saveStateFolder, saveStateFile);
            testVpdPerformanceSingle(saveFile, e.getValue());
            Util.sleep(2000);
        }
    }

    private void testVpdPerformanceSingle(Path saveFile, String rom) throws Exception {
        VdpProvider vdpProvider = prepareVdp(saveFile);
        int cycle = CYCLES;
        long start = System.nanoTime();
        do {
            VdpTestUtil.runToStartFrame(vdpProvider);
        } while (--cycle > 0);
        System.out.println(rom);
        printPerf(System.nanoTime() - start, CYCLES);

    }

    private static void printPerf(long intervalNs, int cycles) {
        double timeMs = intervalNs / 1_000_000d;
        double fps = cycles / (timeMs / 1000);
        System.out.println("Time ms: " + timeMs + ", FPS: " + fps);
    }

    private VdpProvider prepareVdp(Path saveFile) throws Exception {
        GenesisProvider genesisProvider = createTestProvider();
        BusProvider busProvider = SavestateTest.loadSaveState(saveFile);
        busProvider.attachDevice(genesisProvider);
        VdpProvider vdpProvider = busProvider.getVdp();
        return vdpProvider;
    }

    private static GenesisProvider createTestProvider() throws Exception {
        InputProvider.bootstrap();

        Genesis g = new Genesis(true) {
            int count = 0;

            @Override
            public void renderScreen(int[][] sd) {
                boolean isValid = isValidImage(sd);
                if (isValid) {
                    VdpRenderTest.screenData = sd;
                } else {
                    System.out.println("Skipping frame#" + count);
                }
                count++;
            }
        };

        return g;
    }

    private static boolean isValidImage(int[][] screenData) {
        for (int i = 0; i < screenData.length; i++) {
            for (int j = 0; j < screenData[i].length; j++) {
                if (screenData[i][j] > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showImage(BufferedImage bi, String title) {
        if (label == null) {
            label = new JLabel();
            JPanel panel = new JPanel();
            panel.add(label);
            f = new JFrame();
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.add(panel);
        }
        label.setIcon(new ImageIcon(bi));
        f.setTitle(title);
        f.pack();
        f.setVisible(true);
    }
}
