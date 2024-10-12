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

import omegadrive.system.SystemProvider;
import omegadrive.util.FileUtil;
import omegadrive.util.Util;
import omegadrive.vdp.md.MdVdp;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.util.VdpPortAccessLogger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static omegadrive.vdp.MdVdpTestUtil.getVdpProvider;


@Ignore
public class VdpRasterDataGeneratorTest extends VdpPerformanceTest {

    public static int NO_SAVE_FRAME = Integer.MIN_VALUE;
    public static int saveStateAtFrame = NO_SAVE_FRAME;
    public static Path statePath = Paths.get(".", "rdg_" + saveStateAtFrame + ".gsh");
    public static Path datPath = Paths.get(".", "rdg_" + saveStateAtFrame + ".dat");

    static {
        testFilePath = Paths.get("./test_roms", "ccars.zip");
    }

    private SystemProvider system;
    private MdVdp vdp;
    private VdpPortAccessLogger logger;

    @BeforeClass
    public static void beforeTest() {
        System.setProperty("helios.headless", "false");
        System.setProperty("helios.fullSpeed", "true");
        System.setProperty("helios.enable.sound", "false");
        System.setProperty("68k.debug", "false");
        System.setProperty("z80.debug", "false");
//        System.setProperty("md.show.vdp.debug.viewer", "true");
    }

    @Test
    public void testVdpRasterDataGenerate() {
        system = createTestProvider();
        createAndAddVdpListener(system);
        Util.waitForever();
    }


    private void newFrame() {
        if (frameCnt == saveStateAtFrame - 1) { //will trigger a save at next vblank
            System.out.println("Trigger savestate for next frame");
            system.handleSystemEvent(SystemProvider.SystemEvent.SAVE_STATE, statePath);
        }
        if (frameCnt == saveStateAtFrame) {
            System.out.println("Save state: " + statePath.toAbsolutePath().toString());
            System.out.println("Trigger vdp port data recording");
            logger = vdp.toggleVdpPortAccessLogger(true);

        }
        //TODO interlaced needs to store 2 frames ???
        if (frameCnt == saveStateAtFrame + 2) {
            String writes = logger.getWritesAsString();
            System.out.println(writes);
            logger.reset();
            FileUtil.writeFileSafe(datPath, writes.getBytes(StandardCharsets.UTF_8));
            System.out.println("Save dat: " + datPath.toAbsolutePath().toString());
            System.exit(0);
        }
    }

    @Override
    protected void createAndAddVdpListener(SystemProvider systemProvider) {
        super.createAndAddVdpListener(systemProvider);
        try {
            vdp = getVdpProvider(systemProvider);
            vdp.addVdpEventListener(new BaseVdpProvider.VdpEventListener() {
                @Override
                public void onNewFrame() {
                    newFrame();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
