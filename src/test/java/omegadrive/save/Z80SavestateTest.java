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
import omegadrive.SystemLoader;
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.bus.z80.MsxBus;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.savestate.StateUtil;
import omegadrive.savestate.Z80StateBaseHandler;
import omegadrive.util.SystemTestUtil;
import omegadrive.vdp.Tms9918aVdp;
import omegadrive.vdp.model.Tms9918a;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.IntStream;

import static omegadrive.SystemLoader.SystemType.*;
import static omegadrive.savestate.BaseStateHandler.Type.LOAD;
import static omegadrive.savestate.BaseStateHandler.Type.SAVE;

public class Z80SavestateTest extends BaseSavestateTest {

    public static Path saveStateFolder = Paths.get(baseSaveStateFolder.toAbsolutePath().toString(), "z80");
    private SystemLoader.SystemType systemType;

    @Test
    public void testLoadAndSaveSg1k() throws IOException {
        systemType = SG_1000;
        testLoadAndSave(saveStateFolder, StateUtil.fileExtensionMap.get(systemType));
    }

    @Test
    public void testLoadAndSaveMsx() throws IOException {
        systemType = MSX;
        testLoadAndSave(saveStateFolder, StateUtil.fileExtensionMap.get(systemType));
    }

    @Test
    public void testLoadAndSaveColeco() throws IOException {
        systemType = COLECO;
        testLoadAndSave(saveStateFolder, StateUtil.fileExtensionMap.get(systemType));
    }

    @Override
    protected void testLoadSaveInternal(Path saveFile) {
        String filePath = saveFile.toAbsolutePath().toString();

        Z80BusProvider busProvider1 = SystemTestUtil.setupNewZ80System(systemType);
        BaseStateHandler loadHandler = BaseStateHandler.createInstance(systemType, filePath, LOAD,
                busProvider1.getAllDevices(Device.class));
        loadHandler.processState();

        String name = loadHandler.getFileName() + "_TEST_" + System.currentTimeMillis() + ".gs0";
        BaseStateHandler saveHandler = BaseStateHandler.createInstance(systemType, name, SAVE,
                busProvider1.getAllDevices(Device.class));
        saveHandler.processState();
//        saveHandler.storeData();

        byte[] saveData = saveHandler.getData();

        Z80BusProvider busProvider2 = SystemTestUtil.setupNewZ80System(systemType);
        BaseStateHandler loadHandler2 = Z80StateBaseHandler.createLoadInstance(name, systemType, saveData,
                busProvider2.getAllDevices(Device.class));
        loadHandler2.processState();
        Assert.assertArrayEquals(saveData, loadHandler2.getData());

        compareDevices(busProvider1, busProvider2);

//        Assert.assertArrayEquals("Data mismatch", data, savedData);
    }

    private void compareDevices(Z80BusProvider b1, Z80BusProvider b2) {
        compareVdp(getDevice(b1, Tms9918aVdp.class), getDevice(b2, Tms9918aVdp.class));
        compareZ80(getDevice(b1, Z80Provider.class), getDevice(b2, Z80Provider.class));
        compareMemory(getDevice(b1, IMemoryProvider.class), getDevice(b2, IMemoryProvider.class));
        if (b1 instanceof MsxBus) {
            compareBus((MsxBus) b1, (MsxBus) b2);
        }
    }

    private void compareBus(MsxBus b1, MsxBus b2) {
        MsxBus.MsxBusContext c1 = b1.getCtx();
        MsxBus.MsxBusContext c2 = b2.getCtx();
        Assert.assertTrue(c1.psgAddressLatch == c2.psgAddressLatch &&
                c1.slotSelect == c2.slotSelect &&
                c1.ppiC_Keyboard == c2.ppiC_Keyboard &&
                Arrays.equals(c1.pageStartAddress, c2.pageStartAddress) &&
                Arrays.equals(c1.pageSlotMapper, c2.pageSlotMapper)
        );
    }

    private void compareVdp(Tms9918aVdp vdp1, Tms9918aVdp vdp2) {
        IntStream.range(0, Tms9918a.REGISTERS).forEach(i ->
                Assert.assertEquals("VdpReg" + i, vdp1.getRegisterData(i), vdp2.getRegisterData(i)));
        IntStream.range(0, Tms9918a.RAM_SIZE).forEach(i ->
                Assert.assertEquals("Vram" + i, vdp1.getVram()[i], vdp2.getVram()[i]));
    }
}
