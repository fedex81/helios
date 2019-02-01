package omegadrive.save;

import m68k.cpu.MC68000;
import omegadrive.bus.BusProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.GenesisMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.GenesisStateHandler;
import omegadrive.savestate.GstStateHandler;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.fm.YM2612;
import omegadrive.util.FileLoader;
import omegadrive.vdp.GenesisVdp;
import omegadrive.vdp.VdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.z80.IMemory;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.junit.Assert;
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

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class SavestateTest {

    public static Path saveStateFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "savestate");

    @Test
    public void testLoadAndSave() throws IOException {
        System.out.println(new File(".").getAbsolutePath());
        Set<Path> files = Files.list(saveStateFolder).
                filter(p -> p.getFileName().toString().contains(".gs")).collect(Collectors.toSet());
        Assert.assertFalse(files.isEmpty());
        for (Path saveFile : files) {
            System.out.println("Testing: " + saveFile.getFileName());
            testLoadSaveInternal(saveFile);
        }
    }

    private GenesisStateHandler testLoadSaveInternal(Path saveFile) {
        BusProvider busProvider = BusProvider.createBus();

        int[] data = FileLoader.readFileSafe(saveFile);
        GenesisStateHandler loadHandler = GstStateHandler.createLoadInstance(saveFile.getFileName().toString(), data);
        VdpProvider vdpProvider1 = GenesisVdp.createInstance(busProvider);
        MC68000Wrapper cpu1 = new MC68000Wrapper(busProvider);
        MemoryProvider cpuMem1 = new GenesisMemoryProvider();
        Z80Provider z80p1 = Z80CoreWrapper.createInstance(busProvider);
        FmProvider fm1 = new YM2612();
        loadHandler.loadVdpState(vdpProvider1);
        loadHandler.load68k(cpu1, cpuMem1);
        loadHandler.loadZ80(z80p1);
        loadHandler.loadFmState(fm1);

        String name = loadHandler.getFileName() + "_TEST_" + System.currentTimeMillis() + ".gs0";
        GenesisStateHandler saveHandler = GstStateHandler.createSaveInstance(name);
        saveHandler.saveVdp(vdpProvider1);
        saveHandler.save68k(cpu1, cpuMem1);
        saveHandler.saveZ80(z80p1);
        saveHandler.saveFm(fm1);

        int[] savedData = saveHandler.getData();
        GenesisStateHandler loadHandler1 = GstStateHandler.createLoadInstance(saveFile.getFileName().toString(), savedData);
        VdpProvider vdpProvider2 = GenesisVdp.createInstance(busProvider);
        MC68000Wrapper cpu2 = new MC68000Wrapper(busProvider);
        MemoryProvider cpuMem2 = new GenesisMemoryProvider();
        Z80Provider z80p2 = Z80CoreWrapper.createInstance(busProvider);
        FmProvider fm2 = new YM2612();
        loadHandler1.loadVdpState(vdpProvider2);
        loadHandler1.load68k(cpu2, cpuMem2);
        loadHandler.loadZ80(z80p2);
        loadHandler.loadFmState(fm2);

        compareVdp(vdpProvider1, vdpProvider2);
        compare68k(cpu1, cpu2, cpuMem1, cpuMem2);
        compareZ80(z80p1, z80p2);
        compareFm(fm1, fm2);

//        Assert.assertArrayEquals("Data mismatch", data, savedData);
        return saveHandler;
    }

    private void compareFm(FmProvider fm1, FmProvider fm2) {
        int limit = GstStateHandler.FM_DATA_SIZE / 2;
        for (int i = 0; i < limit; i++) {
            Assert.assertEquals("reg0:" + i, fm1.readRegister(0, i), fm2.readRegister(0, i));
            Assert.assertEquals("reg1:" + i, fm1.readRegister(1, i), fm2.readRegister(1, i));
        }
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

        IntStream.range(0, IMemory.MEMORY_SIZE).forEach(
                i -> Assert.assertEquals("Z80Ram:" + i, z80p1.readMemory(i), z80p2.readMemory(i))
        );
        Assert.assertEquals("z80Reset", z80p1.isReset(), z80p2.isReset());
        Assert.assertEquals("z80BusReq", z80p1.isBusRequested(), z80p2.isBusRequested());
        Assert.assertEquals("z80Banking", z80p1.getZ80BusProvider().getRomBank68kSerial(), z80p2.getZ80BusProvider().getRomBank68kSerial());
    }

    private void compare68k(MC68000Wrapper cpu1w, MC68000Wrapper cpu2w, MemoryProvider mem1, MemoryProvider mem2) {
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

    private void compareVdp(VdpProvider vdp1, VdpProvider vdp2) {
        IntStream.range(0, 24).forEach(i ->
                Assert.assertEquals("VdpReg" + i, vdp1.getRegisterData(i), vdp2.getRegisterData(i)));

        VdpMemoryInterface vm1 = vdp1.getVdpMemory();
        VdpMemoryInterface vm2 = vdp2.getVdpMemory();
        IntStream.range(0, VdpProvider.VDP_VRAM_SIZE).forEach(i ->
                Assert.assertEquals("Vram" + i, vm1.readVramByte(i), vm2.readVramByte(i)));
        IntStream.range(0, VdpProvider.VDP_VSRAM_SIZE).forEach(i ->
                Assert.assertEquals("Vsram" + i, vm1.readVsramByte(i), vm2.readVsramByte(i)));
        IntStream.range(0, VdpProvider.VDP_VRAM_SIZE).forEach(i ->
                Assert.assertEquals("Cram" + i, vm1.readCramByte(i), vm2.readCramByte(i)));
    }
}
