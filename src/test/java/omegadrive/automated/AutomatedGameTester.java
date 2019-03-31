package omegadrive.automated;

import omegadrive.system.SystemProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.CartridgeInfoProvider;
import omegadrive.util.FileLoader;
import omegadrive.SystemLoader;
import omegadrive.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class AutomatedGameTester {

    private static String romFolder =
            "/home/fede/roms/sg1000";
//            "/data/emu/roms";
    //            "/data/emu/roms/genesis/nointro";
    //            "/data/emu/roms/genesis/goodgen/unverified";
//            "/home/fede/roms/issues";
//            "/home/fede/roms/tricky";
    private static String romList = "";
    private static boolean noIntro = true;
    private static String startRom = null;
    private static String header = "rom;boot;sound";
    private static int BOOT_DELAY_MS = 500;
    private static int AUDIO_DELAY_MS = 25000;


    private static Predicate<Path> testRomsPredicate = p ->
            (p.toString().endsWith("bin") || p.toString().endsWith("md"));

    private static Predicate<Path> testSgRomsPredicate = p ->
            (p.toString().endsWith("sg") || p.toString().endsWith("sc"));

    private static Predicate<Path> testColecoRomsPredicate = p ->
            p.toString().endsWith("col");

    private static Predicate<Path> testAllRomsPredicate = p ->
            Arrays.stream(SystemLoader.binaryTypes).anyMatch(p.toString()::endsWith);

    private static Predicate<Path> testVerifiedRomsPredicate = p ->
            testRomsPredicate.test(p) &&
                    (noIntro || p.getFileName().toString().contains("[!]"));

    public static void main(String[] args) throws Exception {
        System.out.println("Current folder: " + new File(".").getAbsolutePath());
//        new AutomatedGameTester().testAll(false);
//        new AutomatedGameTester().testCartridgeInfo();
//        new AutomatedGameTester().testList();
//        new AutomatedGameTester().bootRomsGenesis(true);
        new AutomatedGameTester().bootRomsSg1000(true);
//        new AutomatedGameTester().bootRomsColeco(true);
//        new AutomatedGameTester().bootRecursiveRoms(true);
        System.exit(0);
    }

    private void bootRecursiveRoms(boolean shuffle) throws IOException {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.walk(folder). //FileVisitOption.FOLLOW_LINKS).
                filter(p -> p.toFile().isFile() && FileLoader.ROM_FILTER.accept(p.toFile())).collect(Collectors.toList());
        System.out.println("Loaded files: " + testRoms.size());
        if (shuffle) {
            Collections.shuffle(testRoms, new Random());
        }
        try {
            SystemLoader.main(new String[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
        bootRoms(testRoms);
    }

    private void bootRomsSg1000(boolean shuffle) throws IOException {
        filterAndBootRoms(testSgRomsPredicate, shuffle);
    }

    private void bootRomsColeco(boolean shuffle) throws IOException {
        filterAndBootRoms(testColecoRomsPredicate, shuffle);
    }

    private void bootRomsGenesis(boolean shuffle) throws IOException {
        filterAndBootRoms(testVerifiedRomsPredicate, shuffle);
    }

    private void filterAndBootRoms(Predicate<Path> p, boolean shuffle) throws IOException {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.list(folder).filter(p).sorted().collect(Collectors.toList());
        if (shuffle) {
            Collections.shuffle(testRoms, new Random());
        }
        bootRoms(testRoms);
    }

    private void bootRoms(List<Path> testRoms) {
        System.out.println("Roms to test: " + testRoms.size());
        System.out.println(header);
        File logFile = new File("./test_output.log");
        long logFileLen = 0;
        boolean skip = true;
        long RUN_DELAY_MS = 30_000;
        SystemProvider system;
        for (Path rom : testRoms) {
            skip &= shouldSkip(rom);
            if (skip) {
                continue;
            }
            System.out.println(rom.getFileName().toString());
            system = SystemLoader.getInstance().getSystemProvider(rom);
            if(system == null){
                System.out.print(" - SKIP");
                continue;
            }
            system.init();
            system.handleNewRom(rom);
//            genesisProvider.setFullScreen(true);
            Util.sleep(BOOT_DELAY_MS);
            boolean tooManyErrors = false;
            int totalDelay = BOOT_DELAY_MS;
            if (system.isRomRunning()) {
                do {
                    tooManyErrors = checkLogFileSize(logFile, rom.getFileName().toString(), logFileLen);
                    Util.sleep(BOOT_DELAY_MS);
                    totalDelay += BOOT_DELAY_MS;
                } while (totalDelay < RUN_DELAY_MS && !tooManyErrors);
                system.handleCloseRom();
            }

            logFileLen = logFileLength(logFile);
            Util.sleep(500);
            if (tooManyErrors) {
                break;
            }
        }
    }

    private void testAll(boolean random) throws Exception {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.list(folder).filter(testVerifiedRomsPredicate).sorted().collect(Collectors.toList());
        if (random) {
            Collections.shuffle(testRoms);
            Collections.shuffle(testRoms);
            Collections.shuffle(testRoms);
        }
        testRoms(testRoms);
    }

    private void testList() throws Exception {
        String[] arr = romList.split(";");
        List<String> list = Arrays.stream(arr).map(String::trim).sorted().collect(Collectors.toList());
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.list(folder).filter(p -> list.contains(p.getFileName().toString())).
                sorted().collect(Collectors.toList());
        testRoms(testRoms);
    }

    private void testRoms(List<Path> testRoms) {
        System.out.println("Roms to test: " + testRoms.size());
        System.out.println(header);
        boolean skip = true;
        File logFile = new File("./test_output.log");
        long logFileLen = 0;
        SystemProvider system;
        for (Path rom : testRoms) {
            skip &= shouldSkip(rom);
            if (skip) {
                continue;
            }
            system = SystemLoader.getInstance().getSystemProvider(rom);
//            System.out.println("Testing: " + rom.getFileName().toString());
            system.init();
            system.handleNewRom(rom);
//            genesisProvider.setFullScreen(true);
            Util.sleep(BOOT_DELAY_MS);
            boolean boots = false;
            boolean soundOk = false;
            boolean tooManyErrors = false;
            int totalDelay = BOOT_DELAY_MS;
            if (system.isRomRunning()) {
                boots = true;
                do {
                    tooManyErrors = checkLogFileSize(logFile, rom.getFileName().toString(), logFileLen);
                    soundOk = system.isSoundWorking();
                    if (!soundOk) { //wait a bit longer
                        Util.sleep(BOOT_DELAY_MS);
                        totalDelay += BOOT_DELAY_MS;
                        soundOk = system.isSoundWorking();
                    }
                } while (!soundOk && totalDelay < AUDIO_DELAY_MS && !tooManyErrors);
                system.handleCloseRom();
            }
            System.out.println(rom.getFileName().toString() + ";" + boots + ";" + soundOk);
            logFileLen = logFileLength(logFile);
            Util.sleep(2000);
            if (tooManyErrors) {
                break;
            }
        }
    }

    private long logFileLength(File file) {
        return file.exists() ? file.length() : 0;
    }

    private boolean checkLogFileSize(File logFile, String rom, long previousLen) {
        int limit = 100 * 1024; //100 Kbytes
        long len = logFileLength(logFile);
        boolean tooManyErrors = len - previousLen > limit;
        if (tooManyErrors) {
            System.out.println(rom + ": stopping, log file too big, bytes: " + len);
        }
        return tooManyErrors;
    }

    private void testCartridgeInfo() throws Exception {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.list(folder).
                filter(testRomsPredicate).
                sorted().collect(Collectors.toList());
        String str = testRoms.stream().map(p -> p.getFileName().toString()).sorted().collect(Collectors.joining("\n"));
//        System.out.println(str);
        System.out.println("Roms to test: " + testRoms.size());
        String header = "roms;sramEnabled;start;end;sizeKb,romChecksum";
        System.out.println(header);
        boolean skip = true;
        for (Path rom : testRoms) {
            skip &= shouldSkip(rom);
            if (skip) {
                continue;
            }
            int[] data = FileLoader.readFile(rom);
            IMemoryProvider memoryProvider = MemoryProvider.createInstance(data, 0);
            try {
                CartridgeInfoProvider cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memoryProvider,
                        rom.getFileName().toString());
                if (!cartridgeInfoProvider.hasCorrectChecksum()) {
                    System.out.println(rom.getFileName().toString() + ";" + cartridgeInfoProvider.toString());
                }
            } catch (Exception e) {
                System.err.println("Exception: " + rom.getFileName());
            }
        }
    }

    private static boolean shouldSkip(Path rom) {
        boolean skip = true;
        if (startRom == null) {
            return false;
        } else if (rom.getFileName().toString().startsWith(startRom)) {
            skip = false;
            System.out.println("Starting from: " + rom.getFileName().toString());
        }
        return skip;
    }
}