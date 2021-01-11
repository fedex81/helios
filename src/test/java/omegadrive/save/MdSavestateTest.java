/*
 * SavestateTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 18:48
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

import m68k.cpu.MC68000;
import omegadrive.Device;
import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.bus.gen.GenesisZ80BusProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.savestate.BaseStateHandler.Type;
import omegadrive.savestate.GstStateHandler;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.ym2612.nukeykt.Ym2612Nuke;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.vdp.gen.GenesisVdp;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemory;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static omegadrive.SystemLoader.SystemType.GENESIS;

public class MdSavestateTest extends BaseSavestateTest {

    public static Path saveStateFolder = Paths.get(baseSaveStateFolder.toAbsolutePath().toString(), "md");

    public static Set<Path> getSavestateList(Path saveStateFolder, String fileExt) throws IOException {
        System.out.println(new File(".").getAbsolutePath());
        Set<Path> files = Files.list(saveStateFolder).
                filter(p -> p.getFileName().toString().contains(fileExt)).collect(Collectors.toSet());
        return files;
    }

    public static GenesisBusProvider loadSaveState(Path saveFile) {
        GenesisBusProvider busProvider = setupNewSystem();
        BaseStateHandler loadHandler = BaseStateHandler.createInstance(
                GENESIS, saveFile.toAbsolutePath().toString(), Type.LOAD, busProvider.getAllDevices(Device.class));
        loadHandler.processState();
        return busProvider;
    }

    public static GenesisBusProvider setupNewSystem() {
        GenesisBusProvider busProvider = GenesisBusProvider.createBus();

        GenesisVdpProvider vdpProvider1 = GenesisVdp.createInstance(busProvider);
        MC68000Wrapper cpu1 = new MC68000Wrapper(busProvider);
        IMemoryProvider cpuMem1 = MemoryProvider.createGenesisInstance();
        Z80Provider z80p1 = Z80CoreWrapper.createGenesisInstance(busProvider);
        FmProvider fm1 = new Ym2612Nuke(AbstractSoundManager.audioFormat, 0);
        SoundProvider sp1 = getSoundProvider(fm1);
        busProvider.attachDevice(vdpProvider1).attachDevice(cpu1).attachDevice(cpuMem1).attachDevice(z80p1).attachDevice(sp1);
        return busProvider;
    }

    @Test
    public void testLoadAndSave() throws IOException {
        testLoadAndSave(saveStateFolder, ".gs");
    }

    private static SoundProvider getSoundProvider(FmProvider fm) {
        return new SoundProviderAdapter() {
            @Override
            public FmProvider getFm() {
                return fm;
            }
        };
    }

    private void compareFm(FmProvider fm1, FmProvider fm2) {
        int limit = GstStateHandler.FM_DATA_SIZE / 2;
        for (int i = 0; i < limit; i++) {
            Assert.assertEquals("reg0:" + i, fm1.readRegister(0, i), fm2.readRegister(0, i));
            Assert.assertEquals("reg1:" + i, fm1.readRegister(1, i), fm2.readRegister(1, i));
        }
    }

    private void compareZ80(Z80Provider z80p1, Z80Provider z80p2, GenesisBusProvider bus1, GenesisBusProvider bus2) {
        compareZ80(z80p1, z80p2);

        IntStream.range(0, GenesisZ80BusProvider.Z80_RAM_MEMORY_SIZE).forEach(
                i -> Assert.assertEquals("Z80Ram:" + i, z80p1.readMemory(i), z80p2.readMemory(i))
        );
        Assert.assertEquals("z80Reset", bus1.isZ80ResetState(), bus2.isZ80ResetState());
        Assert.assertEquals("z80BusReq", bus1.isZ80BusRequested(), bus2.isZ80BusRequested());
        Assert.assertEquals("z80Banking", GenesisZ80BusProvider.getRomBank68kSerial(z80p1),
                GenesisZ80BusProvider.getRomBank68kSerial(z80p2));
    }

    private void compare68k(MC68000Wrapper cpu1w, MC68000Wrapper cpu2w, IMemoryProvider mem1, IMemoryProvider mem2) {
        MC68000 cpu1 = cpu1w.getM68k();
        MC68000 cpu2 = cpu2w.getM68k();

        Assert.assertEquals("SR", cpu1.getSR(), cpu2.getSR());
        Assert.assertEquals("PC", cpu1.getPC(), cpu2.getPC());
//        Assert.assertEquals("SSP", cpu1.getSSP(), cpu2.getSSP()); TODO
        Assert.assertEquals("USP", cpu1.getUSP(), cpu2.getUSP());
        IntStream.range(0, 8).forEach(i -> Assert.assertEquals("D" + i, cpu1.getDataRegisterLong(i),
                cpu2.getDataRegisterLong(i)));
        IntStream.range(0, 8).forEach(i -> Assert.assertEquals("A" + i, cpu1.getAddrRegisterLong(i),
                cpu2.getAddrRegisterLong(i)));

        IntStream.range(0, MemoryProvider.M68K_RAM_SIZE).forEach(i ->
                Assert.assertEquals("8k Ram" + i, mem1.readRamByte(i), mem2.readRamByte(i)));

    }

    private void compareVdp(GenesisVdpProvider vdp1, GenesisVdpProvider vdp2) {
        IntStream.range(0, 24).forEach(i ->
                Assert.assertEquals("VdpReg" + i, vdp1.getRegisterData(i), vdp2.getRegisterData(i)));

        VdpMemory vm1 = vdp1.getVdpMemory();
        VdpMemory vm2 = vdp2.getVdpMemory();
        IntStream.range(0, GenesisVdpProvider.VDP_VRAM_SIZE).forEach(i ->
                Assert.assertEquals("Vram" + i, vm1.getVram()[i], vm2.getVram()[i]));
        IntStream.range(0, GenesisVdpProvider.VDP_VSRAM_SIZE).forEach(i ->
                Assert.assertEquals("Vsram" + i, vm1.getVsram()[i], vm2.getVsram()[i]));
        IntStream.range(0, GenesisVdpProvider.VDP_CRAM_SIZE).forEach(i ->
                Assert.assertEquals("Cram" + i, vm1.getCram()[i], vm2.getCram()[i]));
    }

    @Override
    protected void testLoadSaveInternal(Path saveFile) {
        String filePath = saveFile.toAbsolutePath().toString();
        GenesisBusProvider busProvider1 = setupNewSystem();

        BaseStateHandler loadHandler = BaseStateHandler.createInstance(
                GENESIS, filePath, Type.LOAD, busProvider1.getAllDevices(Device.class));
        byte[] data = loadHandler.getData();

        loadHandler.processState();

        String name = loadHandler.getFileName() + "_TEST_" + System.currentTimeMillis() + ".gs0";

        BaseStateHandler saveHandler = BaseStateHandler.createInstance(
                GENESIS, name, Type.SAVE, busProvider1.getAllDevices(Device.class));

        GenesisBusProvider busProvider2 = setupNewSystem();

        BaseStateHandler loadHandler1 = BaseStateHandler.createInstance(
                GENESIS, filePath, Type.LOAD, busProvider2.getAllDevices(Device.class));
        byte[] savedData = loadHandler1.getData();
        loadHandler1.processState();

        compareDevices(busProvider1, busProvider2);

        Assert.assertArrayEquals("Data mismatch", data, savedData);
    }

    private void compareDevices(GenesisBusProvider b1, GenesisBusProvider b2) {
        compareVdp(getDevice(b1, GenesisVdpProvider.class), getDevice(b2, GenesisVdpProvider.class));
        compare68k(getDevice(b1, MC68000Wrapper.class), getDevice(b2, MC68000Wrapper.class),
                getDevice(b1, IMemoryProvider.class), getDevice(b2, IMemoryProvider.class));
        compareZ80(getDevice(b1, Z80Provider.class), getDevice(b2, Z80Provider.class), b1, b2);
        compareFm(getDevice(b1, SoundProvider.class).getFm(), getDevice(b1, SoundProvider.class).getFm());
    }

    static class SoundProviderAdapter implements SoundProvider {

        @Override
        public PsgProvider getPsg() {
            return null;
        }

        @Override
        public FmProvider getFm() {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public boolean isMute() {
            return false;
        }

        @Override
        public void setEnabled(boolean mute) {

        }

        @Override
        public void setEnabled(Device device, boolean enabled) {

        }
    }
}
