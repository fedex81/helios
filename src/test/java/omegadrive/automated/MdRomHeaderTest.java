package omegadrive.automated;

import omegadrive.cart.MdCartInfoProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.SysUtil;
import omegadrive.util.FileUtil;

import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static omegadrive.automated.AutomatedGameTester.testGenRomsPredicate;
import static omegadrive.system.SystemProvider.RomContext;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MdRomHeaderTest {

    private static String romFolder = "/home/fede/roms/md";

    public static void main(String[] args) throws Exception {
        System.out.println("Current folder: " + new File(".").getAbsolutePath());
        new MdRomHeaderTest().testCartridgeInfo();
        System.exit(0);
    }

    private void testCartridgeInfo() throws Exception {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.walk(folder, FileVisitOption.FOLLOW_LINKS).
                filter(p -> testGenRomsPredicate.test(p)).collect(Collectors.toList());
        System.out.println("Loaded files: " + testRoms.size());
        System.out.close();
        for (Path rom : testRoms) {
            System.out.println(rom.toAbsolutePath());
            try {
                RomContext romContext = new RomContext(SysUtil.RomSpec.of(rom));
                byte[] data = FileUtil.readBinaryFile(rom);
                IMemoryProvider memoryProvider = MemoryProvider.createInstance(data, 0);
                MdCartInfoProvider cartridgeInfoProvider = MdCartInfoProvider.createMdInstance(memoryProvider, romContext);
                System.out.println(cartridgeInfoProvider);
            } catch (Exception e) {
                System.err.println("Exception: " + rom.getFileName());
                e.printStackTrace();
            }
        }
    }
}
