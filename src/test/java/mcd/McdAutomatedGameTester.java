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

package mcd;

import omegadrive.SystemLoader;
import omegadrive.joypad.MdJoypad;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SystemProvider;
import omegadrive.util.FileUtil;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static omegadrive.input.InputProvider.PlayerNumber.P1;
import static omegadrive.joypad.JoypadProvider.JoypadAction.PRESSED;
import static omegadrive.joypad.JoypadProvider.JoypadAction.RELEASED;
import static omegadrive.joypad.JoypadProvider.JoypadButton.A;
import static omegadrive.joypad.JoypadProvider.JoypadButton.S;
import static omegadrive.system.SystemProvider.SystemEvent.CLOSE_ROM;

public class McdAutomatedGameTester {

    static long MAX_RUNTIME_MS = 60_000;

    public static Path resFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources");

    private static String romFolder = "/data/emu/roms/megacd";

    private static boolean noIntro = true;
    private static String header = "rom;boot;sound";
    private static int BOOT_DELAY_MS = 500;
    private static int LOG_CHECK_DELAY_MS = 30;
    private static int AUDIO_DELAY_MS = 25000;
    private static final int TEN_MEGABYTES = 10 * 1024 * 1024;

    private static String romList = "";
    private static List<String> blackList = FileUtil.readFileContent(Paths.get(resFolder.toAbsolutePath().toString()
            , "blacklist.txt"));

    private static String[] testableTypes = new String[]{".cue", ".iso"};

    private static Predicate<Path> testAllRomsPredicate = p ->
            Arrays.stream(testableTypes).anyMatch(p.toString()::endsWith);

    static {
        System.setProperty("helios.headless", "false");
        System.setProperty("md.sram.folder", "/tmp/helios/md/sram");
        System.setProperty("helios.enable.sound", "false");
        System.setProperty("helios.fps", "true");
        System.setProperty("68k.stop.on.exception", "true");
        System.setProperty("xxmd.show.vdp.debug.viewer", "true");
        System.setProperty("helios.32x.sh2.drc.debug", "false");
        System.setProperty("helios.ui.default.screen", "1");
        new File(System.getProperty("md.sram.folder")).mkdirs();
    }

    private long seed = System.currentTimeMillis();
    private Random random = new Random(seed);


    public static void main(String[] args) throws Exception {
        System.out.println("Current folder: " + new File(".").getAbsolutePath());
        System.out.println("Blacklist entries: " + blackList.size());
        new McdAutomatedGameTester().bootRecursiveRoms(true);
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
//        System.out.println(str);
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
            jbBtnPressDone = false;
            bootTimeMs = System.currentTimeMillis();
            MediaSpecHolder romSpec = MediaSpecHolder.of(rom);
            String name = FileUtil.getFileName(rom);
            System.out.println(count++ + ": " + name);
            system = systemLoader.handleNewRomFile(romSpec);
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
                    //JP bios need START and then BTN_A press
                    jbBiosButtonPresses(system);
                } while (totalDelay < MAX_RUNTIME_MS && !tooManyErrors);
                system.handleSystemEvent(CLOSE_ROM, null);
            }

            logFileLen = logFileLength(logFile);
            Util.sleep(750);
            long lenByte = logFileLength(logFile);
            if (lenByte > TEN_MEGABYTES) {
                fileTooBig(logFile);
                break;
            }
        }
    }

    private boolean jbBtnPressDone = false;
    private long bootTimeMs = 0;

    private void jbBiosButtonPresses(SystemProvider system) {
        if (jbBtnPressDone || system.getRegion() != RegionDetector.Region.JAPAN) {
            jbBtnPressDone = true;
            return;
        }
        if (System.currentTimeMillis() - bootTimeMs < 5000) {
            return;
        }
        Util.sleep(BOOT_DELAY_MS << 1);
        Optional<MdJoypad> optPad = ((MegaCd) system).mcdLaunchContext.mainBus.getBusDeviceIfAny(MdJoypad.class);
        MdJoypad joypad = optPad.get();
        joypad.setButtonAction(P1, S, PRESSED);
        Util.sleep(BOOT_DELAY_MS << 1);
        joypad.setButtonAction(P1, S, RELEASED);
        joypad.setButtonAction(P1, A, PRESSED);
        Util.sleep(BOOT_DELAY_MS);
        joypad.setButtonAction(P1, A, RELEASED);
        jbBtnPressDone = true;
    }

    private void fileTooBig(File logFile) {
        long lenByte = logFileLength(logFile);
        System.out.println("Log file too big: " + lenByte);
        try {
            FileChannel.open(logFile.toPath(), StandardOpenOption.WRITE).truncate(TEN_MEGABYTES).close();
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