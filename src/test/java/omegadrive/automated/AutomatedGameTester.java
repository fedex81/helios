/*
 * AutomatedGameTester
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 14:14
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

package omegadrive.automated;

import omegadrive.SystemLoader;
import omegadrive.cart.CartridgeInfoProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static omegadrive.system.SystemProvider.SystemEvent.CLOSE_ROM;

public class AutomatedGameTester {

    static long RUN_DELAY_MS = 30_000;

    public static Path resFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources");

    private static String romFolder = ".";

    private static boolean noIntro = true;
    private static String header = "rom;boot;sound";
    private static int BOOT_DELAY_MS = 500;
    private static int AUDIO_DELAY_MS = 25000;

    private static String romList = "";
    private static List<String> blackList = FileLoader.readFileContent(Paths.get(resFolder.toAbsolutePath().toString()
            , "blacklist.txt"));

    private static Predicate<Path> testGenRomsPredicate = p ->
            Arrays.stream(SystemLoader.mdBinaryTypes).anyMatch(p.toString()::endsWith);

    private static Predicate<Path> testSgRomsPredicate = p ->
            Arrays.stream(SystemLoader.sgBinaryTypes).anyMatch(p.toString()::endsWith);

    private static Predicate<Path> testColecoRomsPredicate = p ->
            Arrays.stream(SystemLoader.cvBinaryTypes).anyMatch(p.toString()::endsWith);

    private static Predicate<Path> testMsxRomsPredicate = p ->
            Arrays.stream(SystemLoader.msxBinaryTypes).anyMatch(p.toString()::endsWith);

    private static Predicate<Path> testSmsRomsPredicate = p ->
            Arrays.stream(SystemLoader.smsBinaryTypes).anyMatch(p.toString()::endsWith);

    private static Predicate<Path> testGgRomsPredicate = p ->
            Arrays.stream(SystemLoader.ggBinaryTypes).anyMatch(p.toString()::endsWith);

    private static Predicate<Path> testAllRomsPredicate = p ->
            Arrays.stream(SystemLoader.binaryTypes).anyMatch(p.toString()::endsWith);

    private static Predicate<Path> testVerifiedRomsPredicate = p ->
            testGenRomsPredicate.test(p) &&
                    (noIntro || p.getFileName().toString().contains("[!]"));

    static {
        System.setProperty("helios.headless", "false");
        System.setProperty("md.sram.folder", "/tmp/helios/md/sram");
        new File(System.getProperty("md.sram.folder")).mkdirs();
    }

    private long seed = System.currentTimeMillis();
    private Random random = new Random(seed);


    public static void main(String[] args) throws Exception {
        System.out.println("Current folder: " + new File(".").getAbsolutePath());
        System.out.println("Blacklist entries: " + blackList.size());
//        new AutomatedGameTester().testAll(false);
//        new AutomatedGameTester().testCartridgeInfo();
//        new AutomatedGameTester().testList();
//        new AutomatedGameTester().bootRomsGenesis(true);
//        new AutomatedGameTester().bootRomsSg1000(true);
//        new AutomatedGameTester().bootRomsColeco(true);
//        new AutomatedGameTester().bootRomsMsx(true);
//        new AutomatedGameTester().bootRomsSms(true);
//        new AutomatedGameTester().bootRomsGg(true);
        new AutomatedGameTester().bootRecursiveRoms(true);
        System.exit(0);
    }

    private void bootRecursiveRoms(boolean shuffle) throws IOException {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.walk(folder, FileVisitOption.FOLLOW_LINKS).
                filter(p -> testAllRomsPredicate.test(p)).collect(Collectors.toList());
        System.out.println("Loaded files: " + testRoms.size());
        System.out.println("Randomizer seed: " + seed);
        if (shuffle) {
            Collections.shuffle(testRoms, random);
        }
        try {
            SystemLoader.main(new String[0]);
            bootRoms(testRoms);
        } catch (Exception | Error e) {
            e.printStackTrace();
        }
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

    private void bootRomsMsx(boolean shuffle) throws IOException {
        filterAndBootRoms(testMsxRomsPredicate, shuffle);
    }

    private void bootRomsSms(boolean shuffle) throws IOException {
        filterAndBootRoms(testSmsRomsPredicate, shuffle);
    }

    private void bootRomsGg(boolean shuffle) throws IOException {
        filterAndBootRoms(testGgRomsPredicate, shuffle);
    }

    private void filterAndBootRoms(Predicate<Path> p, boolean shuffle) throws IOException {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.list(folder).filter(p).sorted().collect(Collectors.toList());
        if (shuffle) {
            Collections.shuffle(testRoms, new Random());
        }
        bootRoms(testRoms);
    }

    private static boolean shouldSkip(Path rom) {
        String str = rom.getFileName().toString();
        boolean skip = blackList.stream().anyMatch(l -> str.startsWith(l));
        skip |= str.contains("[b");
        if (skip) {
            System.out.println("Skipping: " + str);
        }
        return skip;
    }

    private long logFileLength(File file) {
        return file.exists() ? file.length() : 0;
    }

    private void bootRoms(List<Path> testRoms) {
        System.out.println("Total roms: " + testRoms.size());
        testRoms = testRoms.stream().filter(r -> !shouldSkip(r)).collect(Collectors.toList());
        System.out.println("Testable Roms: " + testRoms.size());
        System.out.println(header);
        File logFile = new File("./test_output.log");
        long logFileLen = 0;
        int count = 1;
        SystemLoader systemLoader = SystemLoader.getInstance();
        SystemProvider system;
        for (Path rom : testRoms) {
            String name = rom.getFileName().toString();
            System.out.println(count++ + ": " + name);
            system = systemLoader.handleNewRomFile(rom);
            if (system == null) {
                System.out.print(" - SKIP");
                continue;
            }
//            genesisProvider.setFullScreen(true);
            Util.sleep(BOOT_DELAY_MS);
            boolean tooManyErrors = false;
            int totalDelay = BOOT_DELAY_MS;

            if (system.isRomRunning()) {
                do {
                    tooManyErrors = checkLogFileSize(logFile, name, logFileLen);
                    Util.sleep(BOOT_DELAY_MS);
                    totalDelay += BOOT_DELAY_MS;
                } while (totalDelay < RUN_DELAY_MS && !tooManyErrors);
                system.handleSystemEvent(CLOSE_ROM, null);
            }

            logFileLen = logFileLength(logFile);
            Util.sleep(500);
            long lenByte = logFileLength(logFile);
            if (lenByte > 10 * 1024 * 1024) { //10Mbytes
                System.out.println("Log file too big: " + lenByte);
                break;
            }
        }
    }

    private void testCartridgeInfo() throws Exception {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.list(folder).
                filter(testGenRomsPredicate).
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
            int[] data = Util.toUnsignedIntArray(FileLoader.readBinaryFile(rom));
            IMemoryProvider memoryProvider = MemoryProvider.createInstance(data, 0);
            try {
                CartridgeInfoProvider cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memoryProvider,
                        rom);
                if (!cartridgeInfoProvider.hasCorrectChecksum()) {
                    System.out.println(rom.getFileName().toString() + ";" + cartridgeInfoProvider.toString());
                }
            } catch (Exception e) {
                System.err.println("Exception: " + rom.getFileName());
            }
        }
    }

    private boolean checkLogFileSize(File logFile, String rom, long previousLen) {
        int limit = 50 * 1024; //50 Kbytes
        long len = logFileLength(logFile);
        boolean tooManyErrors = len - previousLen > limit;
        if (tooManyErrors) {
            System.out.println(rom + ": stopping, log file too big, bytes: " + len);
        }
        return tooManyErrors;
    }
}