/*
 * Copyright (c) 2018-2019 Federico Berti
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

package omegadrive.cheat;

import omegadrive.cart.cheat.BasicGenesisRawCode;
import omegadrive.cart.cheat.CheatCodeHelper;
import omegadrive.cart.cheat.GameGenieHelper;
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
