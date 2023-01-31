/*
 * SmsSavestateTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 24/10/19 18:49
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

package omegadrive.save;

import omegadrive.Device;
import omegadrive.bus.z80.SmsBus;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.savestate.MekaStateHandler;
import omegadrive.system.Sms;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.SystemTestUtil;
import omegadrive.vdp.SmsVdp;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.IntStream;

import static omegadrive.SystemLoader.SystemType.SMS;
import static omegadrive.savestate.BaseStateHandler.Type.LOAD;
import static omegadrive.savestate.BaseStateHandler.Type.SAVE;

public class SmsSavestateTest extends BaseSavestateTest {

    public static Path saveStateFolder = Paths.get(baseSaveStateFolder.toAbsolutePath().toString(), "sms");

    SystemProvider sp = Sms.createNewInstance(SMS, DisplayWindow.HEADLESS_INSTANCE);

    @Test
    public void testLoadAndSave() throws IOException {
        testLoadAndSave(saveStateFolder, ".s0");
    }

    @Override
    protected void testLoadSaveInternal(Path saveFile) {
        String filePath = saveFile.toAbsolutePath().toString();

        SmsBus busProvider1 = SystemTestUtil.setupNewSmsSystem(sp);
        BaseStateHandler loadHandler = BaseStateHandler.createInstance(SMS, filePath, LOAD,
                busProvider1.getAllDevices(Device.class));
        loadHandler.processState();

        String name = loadHandler.getFileName() + "_TEST_" + System.currentTimeMillis() + ".gs0";
        BaseStateHandler saveHandler = BaseStateHandler.createInstance(SMS, name, SAVE,
                busProvider1.getAllDevices(Device.class));
        saveHandler.processState();
//        saveHandler.storeData();

        byte[] saveData = saveHandler.getData();

        SmsBus busProvider2 = SystemTestUtil.setupNewSmsSystem(sp);
        BaseStateHandler loadHandler2 = MekaStateHandler.
                createLoadInstance(name, saveData, busProvider2.getAllDevices(Device.class));
        loadHandler2.processState();
        Assert.assertArrayEquals(saveData, loadHandler2.getData());

        compareDevices(busProvider1, busProvider2);

//        Assert.assertArrayEquals("Data mismatch", data, savedData);
    }

    private void compareDevices(SmsBus b1, SmsBus b2) {
        compareVdp(getDevice(b1, SmsVdp.class), getDevice(b2, SmsVdp.class));
        compareZ80(getDevice(b1, Z80Provider.class), getDevice(b2, Z80Provider.class));
        compareMemory(getDevice(b1, IMemoryProvider.class), getDevice(b2, IMemoryProvider.class));
        compareBus(b1, b2);
    }

    private void compareBus(SmsBus bus1, SmsBus bus2) {
        Assert.assertEquals(bus1.getMapperControl(), bus2.getMapperControl());
        Assert.assertArrayEquals(bus1.getFrameReg(), bus2.getFrameReg());
    }

    private void compareVdp(SmsVdp vdp1, SmsVdp vdp2) {
        IntStream.range(0, SmsVdp.VDP_REGISTERS_SIZE).forEach(i ->
                Assert.assertEquals("VdpReg" + i, vdp1.getRegisterData(i), vdp2.getRegisterData(i)));
        IntStream.range(0, SmsVdp.VDP_VRAM_SIZE).forEach(i ->
                Assert.assertEquals("Vram" + i, vdp1.getVRAM()[i],
                        vdp2.getVRAM()[i]));
        IntStream.range(0, SmsVdp.VDP_CRAM_SIZE).forEach(i ->
                Assert.assertEquals("Cram" + i, vdp1.getCRAM()[i],
                        vdp2.getCRAM()[i]));
    }
}
