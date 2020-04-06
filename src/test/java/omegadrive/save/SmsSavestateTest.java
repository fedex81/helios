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

import omegadrive.SystemLoader;
import omegadrive.bus.z80.SmsBus;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.MekaStateHandler2;
import omegadrive.savestate.SmsStateHandler;
import omegadrive.system.Sms;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.RegionDetector;
import omegadrive.vdp.SmsVdp;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import z80core.Z80State;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SmsSavestateTest {

    public static Path saveStateFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "savestate", "sms");

    SystemProvider sp = Sms.createNewInstance(SystemLoader.SystemType.SMS, DisplayWindow.HEADLESS_INSTANCE);

    public static Set<Path> getSavestateList() throws IOException {
        System.out.println(new File(".").getAbsolutePath());
        Set<Path> files = Files.list(saveStateFolder).
                filter(p -> p.getFileName().toString().contains(".s0")).collect(Collectors.toSet());
        return files;
    }

    @Test
    public void testLoadAndSave() throws IOException {
        Set<Path> files = getSavestateList();
        Assert.assertFalse(files.isEmpty());
        for (Path saveFile : files) {
            System.out.println("Testing: " + saveFile.getFileName());
            testLoadSaveInternal(saveFile);
        }
    }

    @Test
    @Ignore
    public void test1() {
        testLoadSaveInternal(Paths.get(".", "quick_save.s00"));
    }

    private SmsStateHandler testLoadSaveInternal(Path saveFile) {
        String filePath = saveFile.toAbsolutePath().toString();

        SmsBus busProvider1 = new SmsBus();
        IMemoryProvider cpuMem1 = MemoryProvider.createSmsInstance();
        cpuMem1.setRomData(new int[0xFFFF]);
        busProvider1.attachDevice(sp).attachDevice(cpuMem1);
        busProvider1.init();
        SmsStateHandler loadHandler = MekaStateHandler2.createLoadInstance(filePath);
        SmsVdp vdp1 = new SmsVdp(SystemLoader.SystemType.SMS, RegionDetector.Region.USA);
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(busProvider1);

        loadHandler.processState(vdp1, z80p1, busProvider1, cpuMem1);

        String name = loadHandler.getFileName() + "_TEST_" + System.currentTimeMillis() + ".gs0";
        SmsStateHandler saveHandler = MekaStateHandler2.createSaveInstance(name, SystemLoader.SystemType.SMS);

        saveHandler.processState(vdp1, z80p1, busProvider1, cpuMem1);
//        saveHandler.storeData();

        byte[] saveData = saveHandler.getData();

        SmsBus busProvider2 = new SmsBus();
        IMemoryProvider cpuMem2 = MemoryProvider.createSmsInstance();
        cpuMem2.setRomData(new int[0xFFFF]);
        busProvider2.attachDevice(sp).attachDevice(cpuMem2);
        busProvider2.init();
        SmsStateHandler loadHandler2 = MekaStateHandler2.createLoadInstance(name, saveData);
        SmsVdp vdp2 = new SmsVdp(SystemLoader.SystemType.SMS, RegionDetector.Region.USA);
        Z80Provider z80p2 = Z80CoreWrapper.createInstance(busProvider2);

        loadHandler2.processState(vdp2, z80p2, busProvider2, cpuMem2);

        compareVdp(vdp1, vdp2);
        compareZ80(z80p1, z80p2);
        compareMemory(cpuMem1, cpuMem2);
        compareBus(busProvider1, busProvider2);

//        Assert.assertArrayEquals("Data mismatch", data, savedData);
        return saveHandler;
    }

    private void compareBus(SmsBus bus1, SmsBus bus2) {
        Assert.assertEquals(bus1.getMapperControl(), bus2.getMapperControl());
        Assert.assertArrayEquals(bus1.getFrameReg(), bus2.getFrameReg());
    }

    private void compareMemory(IMemoryProvider mem1, IMemoryProvider mem2) {
        IntStream.range(0, MemoryProvider.SMS_Z80_RAM_SIZE).forEach(i ->
                Assert.assertEquals("8k Ram" + i, mem1.readRamByte(i), mem2.readRamByte(i)));
    }

    private void compareZ80(Z80Provider z80p1, Z80Provider z80p2) {
        Z80State s1 = z80p1.getZ80State();
        Z80State s2 = z80p2.getZ80State();
        Assert.assertEquals("AF", s1.getRegAF(), s2.getRegAF());
        Assert.assertEquals("BC", s1.getRegBC(), s2.getRegBC());
        Assert.assertEquals("DE", s1.getRegDE(), s2.getRegDE());
        Assert.assertEquals("HL", s1.getRegHL(), s2.getRegHL());
        Assert.assertEquals("IX", s1.getRegIX(), s2.getRegIX());
        Assert.assertEquals("IY", s1.getRegIY(), s2.getRegIY());
        Assert.assertEquals("PC", s1.getRegPC(), s2.getRegPC());
        Assert.assertEquals("SP", s1.getRegSP(), s2.getRegSP());
        Assert.assertEquals("AFx", s1.getRegAFx(), s2.getRegAFx());
        Assert.assertEquals("BCx", s1.getRegBCx(), s2.getRegBCx());
        Assert.assertEquals("DEx", s1.getRegDEx(), s2.getRegDEx());
        Assert.assertEquals("HLx", s1.getRegHLx(), s2.getRegHLx());
        Assert.assertEquals("IFF1", s1.isIFF1(), s2.isIFF1());
        Assert.assertEquals("IFF2", s1.isIFF2(), s2.isIFF2());
        Assert.assertEquals("IM", s1.getIM(), s2.getIM());
    }

    private void compareVdp(SmsVdp vdp1, SmsVdp vdp2) {
        IntStream.range(0, SmsVdp.VDP_REGISTERS_SIZE).forEach(i ->
                Assert.assertEquals("VdpReg" + i, vdp1.getRegisterData(i), vdp2.getRegisterData(i)));
        IntStream.range(0, SmsVdp.VDP_VRAM_SIZE).forEach(i ->
                Assert.assertEquals("Vram" + i, vdp1.getVdpMemory().getVram()[i], vdp2.getVdpMemory().getVram()[i]));
        IntStream.range(0, SmsVdp.VDP_CRAM_SIZE).forEach(i ->
                Assert.assertEquals("Cram" + i, vdp1.getVdpMemory().getCram()[i], vdp2.getVdpMemory().getCram()[i]));
    }
}
