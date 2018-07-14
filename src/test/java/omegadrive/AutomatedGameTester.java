package omegadrive;

import omegadrive.memory.MemoryProvider;
import omegadrive.util.CartridgeInfoProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.Util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    private static String romFolder = "data/emu/roms/gen_roms";
    private static String startRom = null;
    private static String header = "rom;boot;sound";
    private static int BOOT_DELAY_MS = 5000;
    private static int AUDIO_DELAY_MS = 25000;

    private static Predicate<Path> testRomsPredicate = p ->
            (p.toString().endsWith("bin") || p.toString().endsWith("smd"));

    private static Predicate<Path> testVerifiedRomsPredicate = p ->
            testRomsPredicate.test(p) &&
                    p.getFileName().toString().contains("[!]");

    public static void main(String[] args) throws Exception {
        System.out.println("Current folder: " + new File(".").getAbsolutePath());
        new AutomatedGameTester().testAll(false);
//        new AutomatedGameTester().testCartridgeInfo();
//        new AutomatedGameTester().testList();
        System.exit(0);
    }

    private void testAll(boolean random) throws Exception {
        Path folder = Paths.get("/", romFolder);
        List<Path> testRoms = Files.list(folder).filter(testVerifiedRomsPredicate).sorted().collect(Collectors.toList());
        if (random) {
            Collections.shuffle(testRoms);
            Collections.shuffle(testRoms);
            Collections.shuffle(testRoms);
        }
        testRoms(testRoms);
    }

    private void testList() throws Exception {
        String[] arr = nonBoot201805.split(";");
        List<String> list = Arrays.stream(arr).map(String::trim).sorted().collect(Collectors.toList());
        Path folder = Paths.get("/", romFolder);
        List<Path> testRoms = Files.list(folder).filter(p -> list.contains(p.getFileName().toString())).
                sorted().collect(Collectors.toList());
        testRoms(testRoms);
    }

    private void testRoms(List<Path> testRoms) {
        GenesisProvider genesisProvider = Genesis.createInstance();
        System.out.println("Roms to test: " + testRoms.size());
        System.out.println(header);
        boolean skip = true;

        for (Path rom : testRoms) {
            skip &= shouldSkip(rom);
            if (skip) {
                continue;
            }
//            System.out.println("Testing: " + rom.getFileName().toString());
            genesisProvider.init();
            genesisProvider.handleNewGame(rom);
            genesisProvider.setFullScreen(true);
            Util.sleep(BOOT_DELAY_MS);
            boolean boots = false;
            boolean soundOk = false;
            int totalDelay = BOOT_DELAY_MS;
            if (genesisProvider.isGameRunning()) {
                boots = true;
                do {
                    soundOk = genesisProvider.isSoundWorking();
                    if (!soundOk) { //wait a bit longer
                        Util.sleep(BOOT_DELAY_MS);
                        totalDelay += BOOT_DELAY_MS;
                        soundOk = genesisProvider.isSoundWorking();
                    }
                } while (!soundOk && totalDelay < AUDIO_DELAY_MS);
                genesisProvider.handleCloseGame();
            }
            System.out.println(rom.getFileName().toString() + ";" + boots + ";" + soundOk);
            Util.sleep(2000);
        }
    }

    private void testCartridgeInfo() throws Exception {
        Path folder = Paths.get("/", romFolder);
        List<Path> testRoms = Files.list(folder).
                filter(testRomsPredicate).
                sorted().collect(Collectors.toList());
        String str = testRoms.stream().map(p -> p.getFileName().toString()).sorted().collect(Collectors.joining("\n"));
//        System.out.println(str);
        System.out.println("Roms to test: " + testRoms.size());
        String header = "roms;sramEnabled;start;end;sizeKb";
        System.out.println(header);
        boolean skip = true;
        for (Path rom : testRoms) {
            skip &= shouldSkip(rom);
            if (skip) {
                continue;
            }
            int[] data = FileLoader.readFile(rom);
            MemoryProvider memoryProvider = MemoryProvider.createInstance(data);
            try {
                CartridgeInfoProvider cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memoryProvider,
                        rom.getFileName().toString());
                if (cartridgeInfoProvider.isSramEnabled()) {
                    System.out.println(rom.getFileName().toString() + ";" + cartridgeInfoProvider.toSramCsvString());
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

    static String nonBoot201805 =
            "Battle Squadron (UE) [!].bin;\n" +
                    "Misadventures of Flink, The (E) [!].bin;\n" +
                    "NBA Pro Basketball '94 (J) [!].bin;\n" +
                    "NBA Showdown 94 (UE) [!].bin;\n" +
                    "NCAA College Football (U) [!].bin;\n" +
                    "NFL 98 (U) [!].bin;\n" +
                    "NFL Prime Time (U) [!].bin;\n" +
                    "Pete Sampras Tennis (UE) (REV00) (J-Cart) [c][!].bin;\n" +
                    "Pro Action Replay (Unl) [!].bin;\n" +
                    "Pro Action Replay 2 V2.1 (Unl) (Mar 1st) [!].bin;\n" +
                    "Pro Action Replay 2 V2.1 (Unl) [!].bin;\n" +
                    "Sangokushi II (J) [!].bin;\n" +
                    "Sega Radica! Volume 1 (U) [!].bin;\n";
}
