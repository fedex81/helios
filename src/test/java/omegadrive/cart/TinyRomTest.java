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

package omegadrive.cart;

import omegadrive.SystemLoader;
import omegadrive.input.InputProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SystemProvider;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import static omegadrive.vdp.MdVdpTestUtil.getVdpProvider;

/**
 * Tiny rom by snkenjoy
 * Verify that it is loaded correctly, and it is running.
 * <p>
 * When run in a headless env requires the sound to be disabled.
 */
public class TinyRomTest {

    static Path testFilePath = Paths.get("src/test/resources", "tiny.bin");

    private AtomicBoolean done = new AtomicBoolean();

    @BeforeClass
    public static void beforeTest() {
        System.setProperty("helios.headless", "true");
        System.setProperty("helios.fullSpeed", "false");
        System.setProperty("helios.enable.sound", "false");
    }

    protected static SystemProvider createTestProvider() {
        InputProvider.bootstrap();
        return SystemLoader.getInstance().handleNewRomFile(MediaSpecHolder.of(testFilePath));
    }

    /**
     * ssp = 0x600620bc
     * pc =  0xc0000000 = 0 (24 bits)
     * 00000000   6006                    bra.s    $00000008  //start here, branch
     * 00000008   317c 0ff0 fffc          move.w   #$0ff0,$fffc(a0) //pass=1: write ff0 to RAM @ 0xffff_fffc,
     * //pass>1: write ff0 to CRAM (ie. set a color)
     * 0000000e   41f9 00c00004           lea      $00c00004,a0 //set a0 to vdp_ctrl_port
     * 00000014   20bc 80148700           move.l   #$80148700,(a0) //set vpd reg0, reg7
     * 0000001a   30bc 8134               move.w   #$8134,(a0) //set vpd reg1
     * 0000001e   60e2                    bra.s    $00000002
     * 00000002   20bc c0000000           move.l   #$c0000000,(a0) //set vdp cramWrite mode
     */
    @Test
    public void testTinyRom() {
        //Assumptions.assumeFalse(SoundProvider.ENABLE_SOUND); //this fails the test, when it shouldn't
        if (SoundProvider.ENABLE_SOUND) {
            System.out.println("Sound should be disabled");
            return;
        }
        SystemProvider system = createTestProvider();
        assert system != null;
        createAndAddVdpListener(system);
        checkOkWithinMs(1000);
        Assertions.assertTrue(done.get());
    }

    private void checkOkWithinMs(int ms) {
        long start = System.currentTimeMillis();
        boolean exit;
        do {
            Util.sleep(10);
            exit = done.get() || (System.currentTimeMillis() - start > ms);
        } while (!exit);
    }

    protected void createAndAddVdpListener(SystemProvider systemProvider) {
        try {
            BaseVdpProvider vdpProvider = getVdpProvider(systemProvider);
            vdpProvider.addVdpEventListener(new BaseVdpProvider.VdpEventListener() {
                private int count = 0;

                @Override
                public void onNewFrame() {
//                    System.out.println("New frame: " + count);
                    count++;
                    done.set(count > 10);
                }
            });
        } catch (Exception e) {
            System.err.println("Unable to collect stats");
            e.printStackTrace();
        }
    }
}