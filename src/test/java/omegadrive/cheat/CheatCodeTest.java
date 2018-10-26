package omegadrive.cheat;

import omegadrive.bus.cheat.BasicGenesisRawCode;
import omegadrive.bus.cheat.CheatCodeHelper;
import omegadrive.bus.cheat.GameGenieHelper;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class CheatCodeTest {

    private static String[] codes = {"AWHA-CA92", "SCGT-DJYL", "KRGT-CAE0", "GLGT-CAE0", "D4GT-CAE0", "CWGT-CAE0"};

    private static String PAT_FILES = ".pat";
    private static Path cheatFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "cheat");
    private static Path cheatFolder2 = Paths.get(new File(".").getAbsolutePath(),
            "docs", "Kega Fusion Cheat Code Pack", "Kega Fusion Cheat Code Pack");


    @Test
    public void ggTest() {
        Arrays.stream(codes).forEach(c ->
                Assert.assertTrue(c.equalsIgnoreCase(GameGenieHelper.encode(GameGenieHelper.decode(c))))
        );
//        System.out.println(GameGenieHelper.decode("AABT-AA5J"));
    }

    @Test
    public void loadFileTest() throws IOException {
        System.out.println(new File(".").getAbsolutePath());
        Set<Path> patFiles = Files.list(cheatFolder).
                filter(p -> p.getFileName().toString().endsWith(PAT_FILES)).collect(Collectors.toSet());
        Assert.assertFalse(patFiles.isEmpty());
        patFiles = new TreeSet<>(patFiles);
        for (Path file : patFiles) {
            System.out.println(file.toString());
            try {
                List<String> lines = Files.readAllLines(file);
                Set<BasicGenesisRawCode> set =
                        lines.stream().map(CheatCodeHelper::parseCheatCode).collect(Collectors.toSet());
            } catch (Exception e) {
                System.err.println("Unable to load: " + file.toString());
                e.printStackTrace();
            }
//            set.stream().forEach(c -> System.out.println(c + ", ROM patch: " + CheatCodeHelper.isRomPatch(c)));
        }
    }
}
