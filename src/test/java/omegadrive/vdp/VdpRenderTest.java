/*
 * Copyright (c) 2018-2019 Federico Berti
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

import omegadrive.automated.SavestateGameLoader;
import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.input.InputProvider;
import omegadrive.save.SavestateTest;
import omegadrive.system.Genesis;
import omegadrive.system.SystemProvider;
import omegadrive.ui.GenesisWindow;
import omegadrive.util.Util;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.RenderType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Ignore
public class VdpRenderTest {

    private static int[][] screenData;
    private VdpRenderDump renderDump = new VdpRenderDump();
    private JLabel label;
    private JFrame f;
    private static String saveStateFolder = SavestateTest.saveStateFolder.toAbsolutePath().toString();
    static int CYCLES = 1_000;

    @Before
    public void beforeTest(){
        System.setProperty("emu.headless", "true");
    }

    private void testSavestateViewerSingle(Path saveFile, String rom) throws Exception {
        GenesisVdpProvider vdpProvider = prepareVdp(saveFile);
        VdpTestUtil.runToStartFrame(vdpProvider);
//            renderDump.saveRenderToFile(screenData, vdpProvider.getVideoMode(), RenderType.FULL);
        BufferedImage bi = renderDump.getImage(screenData, vdpProvider.getVideoMode(), RenderType.FULL);
        String title = rom + " (" + saveFile.getFileName().toString() + ")";
        showImage(bi, title);
    }


    @Test
    public void testInterlaced() throws Exception {
        String saveName = "s2_int.gs0";
//        saveName = "cc_int.gs0";
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

    @Test
    @Ignore
    public void testVpdPerformanceOne() throws Exception {
        Map.Entry<String, String> entry = (Map.Entry) SavestateGameLoader.saveStates.entrySet().toArray()[0];
        Path saveFile = Paths.get(saveStateFolder, entry.getKey());
        GenesisVdpProvider vdpProvider = prepareVdp(saveFile);
        long start = System.nanoTime();
        long cycle = 0;
        System.out.println(entry.getValue());
        do {
            VdpTestUtil.runToStartFrame(vdpProvider);
            cycle++;
            if (cycle % CYCLES == 0) {
                printPerf(System.nanoTime() - start, CYCLES);
                start = System.nanoTime();
            }
        } while (true);
    }



    private void testVpdPerformanceSingle(Path saveFile, String rom) throws Exception {
        GenesisVdpProvider vdpProvider = prepareVdp(saveFile);
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

    private GenesisVdpProvider prepareVdp(Path saveFile) {
        SystemProvider genesisProvider = createTestProvider();
        GenesisBusProvider busProvider = SavestateTest.loadSaveState(saveFile);
        busProvider.attachDevice(genesisProvider);
        GenesisVdpProvider vdpProvider = busProvider.getVdp();
        return vdpProvider;
    }

    private static SystemProvider createTestProvider() {
        InputProvider.bootstrap();

        Genesis g = new Genesis(GenesisWindow.HEADLESS_INSTANCE) {
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
