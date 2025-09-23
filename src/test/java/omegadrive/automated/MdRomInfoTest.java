package omegadrive.automated;

import omegadrive.system.MediaSpecHolder;
import omegadrive.util.FileUtil;
import omegadrive.util.Util;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static omegadrive.SystemLoader.SystemType.MD;
import static omegadrive.util.Util.isSubSequence;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class MdRomInfoTest {

    private static String romFolder = "/home/fede/roms/md";

    public static void main(String[] args) throws Exception {
        System.out.println("Current folder: " + new File(".").getAbsolutePath());
//        new MdRomHeaderTest().testCartridgeInfo();
        new MdRomInfoTest().testCodeBlock();
        System.exit(0);
    }

    private void testCartridgeInfo() throws Exception {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.walk(folder, FileVisitOption.FOLLOW_LINKS).
                filter(p -> AutomatedGameTester.testSystemRomsPredicate.test(MD, p)).collect(Collectors.toList());
        System.out.println("Loaded files: " + testRoms.size());
        System.out.close();
        for (Path rom : testRoms) {
            System.out.println(rom.toAbsolutePath());
            try {
                MediaSpecHolder msh = MediaSpecHolder.of(rom);
                System.out.println(msh);
            } catch (Exception e) {
                System.err.println("Exception: " + rom.getFileName());
                e.printStackTrace();
            }
        }
    }

    private void testValidRom() throws Exception {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.walk(folder, FileVisitOption.FOLLOW_LINKS).
                filter(p -> AutomatedGameTester.systemFilterMap.get(MD).test(p)).collect(Collectors.toList());
        System.out.println("Loaded files: " + testRoms.size());
        System.out.close();
        int cnt = 0;
        for (Path rom : testRoms) {
            System.out.println(rom.toAbsolutePath());
            if (++cnt % 100 == 0) {
                System.err.println(cnt + "," + rom.toAbsolutePath());
            }
            try {
                MediaSpecHolder msh = MediaSpecHolder.of(rom);
                byte[] b = FileUtil.readBinaryFile(msh.cartFile.romFile);
                Assertions.assertEquals(0x100, isSubSequence("SEGA".getBytes(), b), msh.toString());
            } catch (Exception | Error e) {
                System.err.println("Exception: " + rom.getFileName());
                e.printStackTrace();
            }
        }
    }


    /**
     * 68M 0000210a   33fc 0100 00a11100      move.w   #$0100,$00a11100
     * 68M 00002112   3239 00a11100           move.w   $00a11100,d1
     */
    private byte[] sequence = {
            0x33, (byte) 0xfc, 0x01, 0x00, 0x00, (byte) 0xa1, 0x11, 0x00,
            0x32, 0x39, 0x00, (byte) 0xa1, 0x11, 0x00
    };

    private byte[] sequence2 = "nintendo".getBytes();

    private void testCodeBlock() throws Exception {
        Path folder = Paths.get(romFolder);
        List<Path> testRoms = Files.walk(folder, FileVisitOption.FOLLOW_LINKS).
                filter(p -> AutomatedGameTester.systemFilterMap.get(MD).test(p)).collect(Collectors.toList());
        System.out.println("Loaded files: " + testRoms.size());
        System.out.close();
        int cnt = 0;
        byte[] toMatch = sequence;
        for (Path rom : testRoms) {
            System.out.println(rom.toAbsolutePath());
            if (++cnt % 100 == 0) {
                System.err.println(cnt + "," + rom.toAbsolutePath());
            }
            try {
                MediaSpecHolder msh = MediaSpecHolder.of(rom);
                byte[] b = FileUtil.readBinaryFile(msh.cartFile.romFile);
                int start = Util.indexOfSubSequence(toMatch, b);
                if (start >= 0) {
                    System.err.println("***MATCH***, " + msh);
                    checkMatch(start, b, toMatch);
                }
            } catch (Exception | Error e) {
                System.err.println("Exception: " + rom.getFileName());
                e.printStackTrace();
            }
//            break;
        }
    }

    private void checkMatch(int start, byte[] b, byte[] subseq) {
        byte[] seq2 = subseq.clone();
        Arrays.fill(seq2, (byte) 0);
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.position(start);
        bb.get(seq2);
        Assertions.assertArrayEquals(subseq, seq2);
        System.out.println(Arrays.toString(seq2));
    }
}
