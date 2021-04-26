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

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.input.GamepadTest;
import omegadrive.input.InputProvider;
import omegadrive.save.MdSavestateTest;
import omegadrive.system.Genesis;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import org.junit.Before;
import org.junit.Ignore;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;

@Ignore
public class VdpRenderTest implements BaseVdpProvider.VdpEventListener {

    protected static int[] screenData;
    protected VdpRenderDump renderDump = new VdpRenderDump();
    protected static String saveStateFolder = MdSavestateTest.saveStateFolder.toAbsolutePath().toString();
    int count = 0;

    @Before
    public void beforeTest(){
        System.setProperty("helios.headless", "true");
    }

    protected GenesisVdpProvider vdpProvider;

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

    protected Image testSavestateViewerSingle(Path saveFile) {
        GenesisVdpProvider vdpProvider = prepareVdp(saveFile);
        MdVdpTestUtil.runToStartFrame(vdpProvider);
        return saveRenderToImage(screenData, vdpProvider.getVideoMode());
    }

    private static boolean isValidImage(int[] screenData) {
        for (int i = 0; i < screenData.length; i++) {
            if (screenData[i] > 0) {
                return true;
            }
        }
        return false;
    }

    protected BufferedImage saveRenderToImage(int[] data, VideoMode videoMode) {
        BufferedImage bi = renderDump.getImage(videoMode);
        int[] linear = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
        System.arraycopy(data, 0, linear, 0, linear.length);
        return bi;
    }

    protected GenesisVdpProvider prepareVdp(Path saveFile) {
        SystemProvider genesisProvider = createTestProvider();
        GenesisBusProvider busProvider = MdSavestateTest.loadSaveState(saveFile);
        busProvider.attachDevice(genesisProvider).attachDevice(GamepadTest.createTestJoypadProvider());
        busProvider.init();
        vdpProvider = busProvider.getVdp();
        vdpProvider.addVdpEventListener(this);
        return vdpProvider;
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
