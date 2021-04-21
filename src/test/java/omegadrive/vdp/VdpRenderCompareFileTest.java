/*
 * VdpRenderTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 14:30
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

package omegadrive.vdp;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class VdpRenderCompareFileTest extends VdpRenderCompareTest {

    static List<String> ignoredTests = ImmutableList.<String>of(
            "vr_01.gsh" //needs to init SSP16
    );

    static Stream<String> fileProvider() {
        File[] files = Paths.get(saveStateFolder).toFile().listFiles();
        Predicate<File> validFile = f -> !f.isDirectory() && !ignoredTests.contains(f.getName());
        return Arrays.stream(files).filter(validFile).map(f -> f.getName()).sorted();
    }

    @BeforeEach
    public void beforeTest() {
        System.setProperty("helios.headless", "true");
        System.setProperty("md.show.vdp.debug.viewer", "false");
    }

    @ParameterizedTest
    @MethodSource("fileProvider")
    public void testCompareFile(String fileName) {
        SHOW_IMAGES_ON_FAILURE = false;
        Path saveFile = Paths.get(saveStateFolder, fileName);
        boolean error = testCompareOne(saveFile);
        Assert.assertFalse("Error: " + fileName, error);
    }

    @Ignore
    @Disabled
    @Override
    public void testCompareAll() {
        super.testCompareAll();
    }

    @Ignore
    @Disabled
    @Test
    public void testCompare() {
        Path saveFile = Paths.get(saveStateFolder, "vr_01.gsh");
        super.testCompareFile(saveFile, true);
    }
}