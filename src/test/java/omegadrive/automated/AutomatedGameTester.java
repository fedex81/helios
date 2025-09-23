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
import omegadrive.SystemLoader.SystemType;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SystemProvider;
import omegadrive.util.FileUtil;
import omegadrive.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static omegadrive.SystemLoader.SystemType.MD;
import static omegadrive.system.SysUtil.compressedBinaryTypes;
import static omegadrive.system.SysUtil.sysFileExtensionsMap;
import static omegadrive.system.SystemProvider.SystemEvent.CLOSE_ROM;

public class AutomatedGameTester {

    static long RUN_DELAY_MS = 30_000;

    public static Path resFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources");

    private static String romFolder = ".";

    private static boolean noIntro = true;
    private static String header = "rom;boot;sound";
    private static int BOOT_DELAY_MS = 500;
    private static int LOG_CHECK_DELAY_MS = 30;
    private static int AUDIO_DELAY_MS = 25000;

    private static String romList = "";
    private static List<String> blackList = FileUtil.readFileContent(Paths.get(resFolder.toAbsolutePath().toString()
            , "blacklist.txt"));

    public static final EnumMap<SystemType, Predicate<Path>> systemFilterMap;

    public static BiPredicate<SystemType, Path> testSystemRomsPredicate = (st, p) ->
            Arrays.stream(sysFileExtensionsMap.get(st)).anyMatch(p.toString()::endsWith);

    static {
        systemFilterMap = new EnumMap<>(SystemType.class);
        Predicate<Path> mdFilter = p -> testSystemRomsPredicate.test(MD, p) ||
                Arrays.stream(compressedBinaryTypes).anyMatch(p.toString()::endsWith);
        Predicate<Path> mdExtraFilter = p -> !p.toString().toLowerCase().contains("32x");
        systemFilterMap.put(MD, mdExtraFilter.and(mdFilter));
        for (SystemType st : SystemType.values()) {
            if (st != MD) {
                systemFilterMap.put(st, p -> testSystemRomsPredicate.test(st, p));
            }
        }
    }

    private static Predicate<Path> testVerifiedRomsPredicate = p ->
            systemFilterMap.get(MD).test(p) &&
                    (noIntro || p.getFileName().toString().contains("[!]"));

    static {
        System.setProperty("helios.headless", "true");
        System.setProperty("helios.enable.sound", "false");
        System.setProperty("helios.test.mode", "true");
        System.setProperty("md.sram.folder", "/tmp/helios/md/sram");
        new File(System.getProperty("md.sram.folder")).mkdirs();
    }

    private long seed = System.currentTimeMillis();
    private Random random = new Random(seed);


    public static void main(String[] args) throws Exception {
        System.out.println("Current folder: " + new File(".").getAbsolutePath());
        System.out.println("Blacklist entries: " + blackList.size());
//        new AutomatedGameTester().testAll(false);
//        new AutomatedGameTester().testList();
        new AutomatedGameTester().bootRecursiveRoms(MD, true);
        System.exit(0);
    }

    private void bootRecursiveRoms(SystemType st, boolean shuffle) throws IOException {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.walk(folder, FileVisitOption.FOLLOW_LINKS).
                filter(p -> systemFilterMap.get(st).test(p)).collect(Collectors.toList());
        System.out.println("Folder: " + folder.toAbsolutePath());
        System.out.println("Loaded files: " + testRoms.size());
        System.out.println("Randomizer seed: " + seed);
        if (shuffle) {
            Collections.shuffle(testRoms, random);
        }
        try {
            bootRoms(testRoms);
        } catch (Exception | Error e) {
            e.printStackTrace();
        }
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
        SystemProvider system = null;
        for (Path rom : testRoms) {
            String name = rom.getFileName().toString();
            System.out.println(count++ + ": " + name);
            try {
                MediaSpecHolder romSpec = MediaSpecHolder.of(rom);
                system = systemLoader.handleNewRomFile(romSpec);
            } catch (Exception | Error e) {
                e.printStackTrace();
            }
            if (system == null) {
                System.out.print(" - SKIP");
                continue;
            }
            Util.sleep(BOOT_DELAY_MS);
            boolean tooManyErrors = false;
            int totalDelay = BOOT_DELAY_MS;

            if (system.isRomRunning()) {
                do {
                    tooManyErrors = checkLogFileSize(logFile, name, logFileLen);
                    logFileLen = logFileLength(logFile);
                    Util.sleep(LOG_CHECK_DELAY_MS);
                    totalDelay += LOG_CHECK_DELAY_MS;
                } while (totalDelay < RUN_DELAY_MS && !tooManyErrors);
                system.handleSystemEvent(CLOSE_ROM, null);
            }
            Util.sleep(750);
            long lenByte = logFileLength(logFile);
            if (lenByte > 10 * 1024 * 1024) { //10Mbytes
                fileTooBig(logFile);
                break;
            }
        }
    }

    private void fileTooBig(File logFile) {
        long lenByte = logFileLength(logFile);
        System.out.println("Log file too big: " + lenByte);
        try {
            FileChannel.open(logFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
            lenByte = logFileLength(logFile);
            System.out.println("Truncating log file, size: " + lenByte);
        } catch (IOException e) {
            e.printStackTrace();
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