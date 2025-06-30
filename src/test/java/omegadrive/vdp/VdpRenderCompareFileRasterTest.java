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

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import omegadrive.util.FileUtil;
import omegadrive.util.TestRenderUtil;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.MdVdpProvider;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static omegadrive.vdp.util.VdpPortAccessLogger.VdpWriteContext;

/**
 * Interlace mode only, store and compares only the even field (#0)
 */
@Ignore
@Disabled
//TODO when running all tests within the project, it is comparing the wrong field ??
public class VdpRenderCompareFileRasterTest extends VdpRenderCompareTest {

    private static Path saveStateFolderPath;
    static Set<String> excluded = ImmutableSet.of();
    private Image[] fields = new Image[2];

    static {
        saveStateFolderPath = Paths.get(baseDataFolder, "raster");
        baseDataFolder = saveStateFolderPath.toAbsolutePath().toString();
        Path compareFolderPath = Paths.get(baseDataFolder, "compare");
        compareFolder = compareFolderPath.toAbsolutePath().toString();
        System.setProperty("helios.interlace.one.field", "false");
    }

    private int fieldCompleted = -1;
    private AtomicBoolean ready = new AtomicBoolean();

    @BeforeEach
    public void beforeTest() {
        System.setProperty("helios.headless", "true");
        System.setProperty("md.show.vdp.debug.viewer", "false");
    }

    static Stream<String> fileProvider() {
        File[] files = FileUtil.listFilesSafe(saveStateFolderPath.toFile());
        Predicate<File> validFile = f -> !f.isDirectory() && !f.getName().endsWith(".dat") &&
                !excluded.contains(f.getName());
        return Arrays.stream(files).filter(validFile).map(f -> f.getName()).sorted();
    }

    @ParameterizedTest
    @MethodSource("fileProvider")
    public void testCompareFile(String fileName) {
        SHOW_IMAGES_ON_FAILURE = true;
        BufferedImage actual = runAndGetImage(fileName);
        boolean error = testCompareOne(fileName, actual);
        if (error && fileName.contains("s2_")) {
            Util.waitForever();
        }
        Assert.assertFalse("Error: " + fileName, error);
//        Util.waitForever();
    }

    private BufferedImage runAndGetImage(String fileName) {
        Path saveFile = Paths.get(baseDataFolder, fileName);
        Path datFile = getDatFile(saveFile);
        vdpProvider = prepareVdp(saveFile);
        runToEvenField();
        ready.set(true);
        System.out.println("Ready");
        writeVdpData(vdpProvider, datFile);
        while (fields[0] == null) {
            MdVdpTestUtil.runToStartFrame(vdpProvider);
        }
        BufferedImage actual = TestRenderUtil.convertToBufferedImage(fields[0]);
        return actual;
    }

    private void runToEvenField() {
        do {
            MdVdpTestUtil.runToStartFrame(vdpProvider);
        } while (fieldCompleted != 1);
    }

    private void writeVdpData(MdVdpProvider vdp, Path datFile) {
        List<VdpWriteContext> list = getDatFileContents(datFile);
        System.out.println("Replaying vdpPort writes: " + list.size());
        for (VdpWriteContext v : list) {
            if (vdp.getHCounter() != v.hcExternal || vdp.getVCounter() != v.vcExternal) {
                MdVdpTestUtil.runToCounter(vdp, v.hcExternal, v.vcExternal);
            }
            vdp.writeVdpPortWord(v.portType, v.value);
//            System.out.println(v.line);
        }
    }

    private Path getDatFile(Path saveFile) {
        String ext = Files.getFileExtension(saveFile.getFileName().toString());
        String datFile = saveFile.getFileName().toString().replace("." + ext, ".dat");
        Path p = Paths.get(saveFile.getParent().toAbsolutePath().toString(), datFile);
        return p;
    }

    private List<VdpWriteContext> getDatFileContents(Path datFile) {
        List<String> lines = FileUtil.readFileContent(datFile);
        List<VdpWriteContext> vdpWrites = lines.stream().map(VdpWriteContext::parseShortString).
                collect(Collectors.toList());
        if (vdpWrites.isEmpty()) {
            System.out.println("Dat file missing or empty: " + datFile.toAbsolutePath().toString());
        }
        return vdpWrites;
    }

    @Override
    public void onVdpEvent(BaseVdpProvider.VdpEvent event, Object value) {
        switch (event) {
            case INTERLACE_FIELD_CHANGE:
                fieldCompleted = (Integer.parseInt(value.toString()) + 1) & 1;
                System.out.println(fieldCompleted + "->" + value);
                if (ready.get()) {
                    Image f = TestRenderUtil.cloneImage(
                            TestRenderUtil.saveRenderToImage(screenData, vdpProvider.getVideoMode()));
                    if (fields[fieldCompleted] != null) {
                        System.out.println("Attempting to overwrite field# " + fieldCompleted);
                        return;
                    }
                    fields[fieldCompleted] = f;
                    System.out.println("Storing image for field#" + fieldCompleted);
                }
                break;
        }
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
        String fileName = "ccars_int_01.gsh";
        boolean save = false;
        BufferedImage i = runAndGetImage(fileName);
        if (save) {
            TestRenderUtil.saveToFile(compareFolder, fileName, IMG_EXT, i);
        } else {
            boolean error = testCompareOne(fileName, i);
            System.out.println("Done");
            if (error) {
                Util.waitForever();
            }
        }
    }
}