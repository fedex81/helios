package omegadrive.sound;

import omegadrive.sound.javasound.JavaSoundManager;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class DACTest {

    private static Path fileFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "dac");

    static SoundProvider sp = JavaSoundManager.createSoundProvider(RegionDetector.Region.EUROPE);

    public static void main(String[] args) throws IOException {
        System.out.println(new File(".").getAbsolutePath());
        List<String> lines = Files.readAllLines(fileFolder.resolve("sonic1_dac_timings.txt"));
        List<Integer> samples = lines.stream().map(l -> Integer.valueOf(l.split(",")[2])).collect(Collectors.toList());
        List<String> lines1 = Files.readAllLines(fileFolder.resolve("sf2_dac_sample01.txt"));
        List<Integer> samples1 = lines1.stream().map(l -> Integer.valueOf(l.split(",")[2])).collect(Collectors.toList());
        sp.getFm().reset();

        playDAC(samples, 1);
        playDAC(samples1, 5);

        sp.reset();
        sp.close();
    }

    private static void playDAC(List<Integer> samples, int multisampling) {
        sp.getFm().write0(0x2B, 0x80);
        for (int i = 0; i < samples.size(); i++) {
            final int sample = samples.get(i);
            IntStream.range(0, multisampling).forEach(k -> sp.getFm().write0(0x2A, sample));
        }
        sp.getFm().write0(0x2B, 0);
        Util.sleep(2_000);
    }
}
