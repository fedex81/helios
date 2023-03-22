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
import omegadrive.input.InputProvider;
import omegadrive.save.MdSavestateTest;
import omegadrive.system.Megadrive;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.TestRenderUtil;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import org.junit.Before;
import org.junit.Ignore;

import java.awt.*;
import java.nio.file.Path;

import static omegadrive.util.SystemTestUtil.createTestJoypadProvider;

@Ignore
public class VdpRenderTest implements BaseVdpProvider.VdpEventListener {

    protected static int[] screenData;
    protected static String baseDataFolder = MdSavestateTest.saveStateFolder.toAbsolutePath().toString();
    int count = 0;

    @Before
    public void beforeTest(){
        System.setProperty("helios.headless", "true");
    }

    protected GenesisVdpProvider vdpProvider;

    private static SystemProvider createTestProvider() {
        InputProvider.bootstrap();
        return Megadrive.createNewInstance(DisplayWindow.HEADLESS_INSTANCE);
    }

    protected Image testSavestateViewerSingle(Path saveFile) {
        GenesisVdpProvider vdpProvider = prepareVdp(saveFile);
        MdVdpTestUtil.runToStartFrame(vdpProvider);
        return TestRenderUtil.saveRenderToImage(screenData, vdpProvider.getVideoMode());
    }

    protected GenesisVdpProvider prepareVdp(Path saveFile) {
        SystemProvider genesisProvider = createTestProvider();
        GenesisBusProvider busProvider = MdSavestateTest.loadSaveState(saveFile);
        busProvider.attachDevice(genesisProvider).attachDevice(createTestJoypadProvider());
        busProvider.init();
        vdpProvider = busProvider.getVdp();
        vdpProvider.addVdpEventListener(this);
        return vdpProvider;
    }

    @Override
    public void onNewFrame() {
        int[] sd = vdpProvider.getScreenDataLinear();
        boolean isValid = TestRenderUtil.isValidImage(sd);
        if (!isValid) {
            System.out.println("Empty render #" + count);
        }
        VdpRenderTest.screenData = sd;
        count++;
    }
}
