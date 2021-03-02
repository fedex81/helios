/*
 * VdpRenderTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 14:30
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
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.input.GamepadTest;
import omegadrive.input.InputProvider;
import omegadrive.save.MdSavestateTest;
import omegadrive.system.Genesis;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Ignore
public class VdpRenderTest implements BaseVdpProvider.VdpEventListener {

    private static int[] screenData;
    private VdpRenderDump renderDump = new VdpRenderDump();
    protected static String saveStateFolder = MdSavestateTest.saveStateFolder.toAbsolutePath().toString();
    private JLabel label;
    private JFrame f;
    int count = 0;
    static int CYCLES = 1_000;

    @Before
    public void beforeTest(){
        System.setProperty("helios.headless", "true");
    }

    private GenesisVdpProvider vdpProvider;

    private static SystemProvider createTestProvider() {
        InputProvider.bootstrap();
        return Genesis.createNewInstance(DisplayWindow.HEADLESS_INSTANCE);
    }

    protected static Image scaleImage(Image i, int factor) {
        return i.getScaledInstance(i.getWidth(null) * factor, i.getHeight(null) * factor, 0);
    }

    public static JFrame showImageFrame(Image bi, String title) {
        JLabel label = new JLabel();
        JPanel panel = new JPanel();
        panel.add(label);
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        f.add(panel);
        label.setIcon(new ImageIcon(bi));
        f.setTitle(title);
        f.pack();
        f.setVisible(true);
        return f;
    }

    protected Image testSavestateViewerSingle(Path saveFile, String rom) {
        GenesisVdpProvider vdpProvider = prepareVdp(saveFile);
        MdVdpTestUtil.runToStartFrame(vdpProvider);
        return saveRenderToImage(screenData, vdpProvider.getVideoMode());
    }

    @Test
    @Ignore
    public void testVpdPerformance() {
        for (Map.Entry<String, String> e : SavestateGameLoader.saveStates.entrySet()) {
            String saveStateFile = e.getKey();
            Path saveFile = Paths.get(saveStateFolder, saveStateFile);
            testVpdPerformanceSingle(saveFile, e.getValue());
            Util.sleep(2000);
        }
    }

    private static boolean isValidImage(int[] screenData) {
        for (int i = 0; i < screenData.length; i++) {
            if (screenData[i] > 0) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Ignore
    public void testVpdPerformanceOne() {
        Map.Entry<String, String> entry = (Map.Entry) SavestateGameLoader.saveStates.entrySet().toArray()[0];
        Path saveFile = Paths.get(saveStateFolder, entry.getKey());
        GenesisVdpProvider vdpProvider = prepareVdp(saveFile);
        long start = System.nanoTime();
        long cycle = 0;
        System.out.println(entry.getValue());
        do {
            MdVdpTestUtil.runToStartFrame(vdpProvider);
            cycle++;
            if (cycle % CYCLES == 0) {
                printPerf(System.nanoTime() - start, CYCLES);
                start = System.nanoTime();
            }
        } while (true);
    }

    private static void printPerf(long intervalNs, int cycles) {
        double timeMs = intervalNs / 1_000_000d;
        double fps = cycles / (timeMs / 1000);
        System.out.println("Time ms: " + timeMs + ", FPS: " + fps);
    }

    @Test
    @Ignore
    public void testInterlaced() {
        String saveName = "gen_interlace_test.gs0";
//        saveName = "cc_int.gs0";
        Path saveFile = Paths.get(saveStateFolder, saveName);
        Image i = testSavestateViewerSingle(saveFile, SavestateGameLoader.saveStates.get(saveName));
        showImage(scaleImage(i, 4), saveName);
        Util.waitForever();
    }

    private void testVpdPerformanceSingle(Path saveFile, String rom) {
        GenesisVdpProvider vdpProvider = prepareVdp(saveFile);
        int cycle = CYCLES;
        long start = System.nanoTime();
        do {
            MdVdpTestUtil.runToStartFrame(vdpProvider);
        } while (--cycle > 0);
        System.out.println(rom);
        printPerf(System.nanoTime() - start, CYCLES);

    }

    private BufferedImage saveRenderToImage(int[] data, VideoMode videoMode) {
        BufferedImage bi = renderDump.getImage(videoMode);
        int[] linear = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
        System.arraycopy(data, 0, linear, 0, linear.length);
        return bi;
    }

    @Test
    @Ignore
    public void testSavestateViewerAll() {
        for (Map.Entry<String, String> e : SavestateGameLoader.saveStates.entrySet()) {
            String saveStateFile = e.getKey();
            Path saveFile = Paths.get(saveStateFolder, saveStateFile);
            Image i = testSavestateViewerSingle(saveFile, e.getValue());
            showImage(i, saveStateFile);
            Util.sleep(2000);
        }
    }

    private GenesisVdpProvider prepareVdp(Path saveFile) {
        SystemProvider genesisProvider = createTestProvider();
        GenesisBusProvider busProvider = MdSavestateTest.loadSaveState(saveFile);
        busProvider.attachDevice(genesisProvider).attachDevice(GamepadTest.createTestJoypadProvider());
        vdpProvider = busProvider.getVdp();
        vdpProvider.addVdpEventListener(this);
        return vdpProvider;
    }

    protected void showImage(Image bi, String title) {
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

    @Override
    public void onNewFrame() {
        int[] sd = vdpProvider.getScreenDataLinear();
        boolean isValid = isValidImage(sd);
        if (!isValid) {
            System.out.println("Empty render #" + count);
        }
        VdpRenderTest.screenData = sd;
        count++;
    }
}
