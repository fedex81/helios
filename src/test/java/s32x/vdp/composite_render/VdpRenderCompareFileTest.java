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

package s32x.vdp.composite_render;

import com.google.common.io.Files;
import omegadrive.util.FileUtil;
import omegadrive.util.TestRenderUtil.*;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import s32x.vdp.MarsVdp;
import s32x.vdp.MarsVdpImpl;
import s32x.vdp.debug.DebugVideoRenderContext;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static omegadrive.util.TestRenderUtil.*;


/**
 * Test the creation of a composite image of the MD + S32X screen outputs.
 */
public class VdpRenderCompareFileTest extends VdpRenderCompareTest {

    public static Path baseDataFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "render");
    static String baseDataFolderName = baseDataFolder.toAbsolutePath().toString();

    @Override
    public Path getBaseDataFolder() {
        return baseDataFolder;
    }

    static Stream<String> fileProvider() {
        return getFileProvider(baseDataFolder);
    }

    @BeforeAll
    public static void beforeTest() {
        System.setProperty("helios.headless", "true");
        System.setProperty("md.show.vdp.debug.viewer", "false");
        System.out.println(baseDataFolderName);
    }

    @ParameterizedTest
    @MethodSource("fileProvider")
    public void testCompareFile(String fileName) {
        SHOW_IMAGES_ON_FAILURE = false;
        boolean stopWhenDone = false;
        Path saveFile = Paths.get(baseDataFolderName, fileName);
        boolean error = testCompareOne(saveFile);
        if (stopWhenDone && fileName.startsWith("vf2")) {
            Util.waitForever();
        }
        Assertions.assertFalse(error, "Error: " + fileName);
    }

    @Disabled
    @Test
    public void testCompare() {
        Path saveFile = Paths.get(baseDataFolderName, "bthorn_01.dat.zip");
        super.testCompareFile(saveFile, true);
    }

    @Override
    protected void testOverwriteBaselineImage(Path datFile) {
        Image[] i = toImages(datFile);
        String fileName = Files.getNameWithoutExtension(datFile.getFileName().toString());
        for (S32xRenderType type : S32xRenderType.values()) {
            saveToFile(compareFolder, fileName, type, IMG_EXT, i[type.ordinal()]);
        }
    }

    @Override
    protected boolean testCompareOne(Path datFile) {
        Image[] i = toImages(datFile);
        boolean ok = true;
        String fileName = Files.getNameWithoutExtension(datFile.getFileName().toString());
        for (S32xRenderType type : S32xRenderType.values()) {
            BufferedImage actual = convertToBufferedImage(i[type.ordinal()]);
            ok &= testCompareOne(fileName + "_" + type.name(), actual);
        }
        return ok;
    }

    protected Image[] toImages(Path datFile) {
        Image[] img = new Image[3];
        byte[] data = FileUtil.readBinaryFile(datFile, "dat");
        Object o = Util.deserializeObject(data);
        DebugVideoRenderContext dvrc = (DebugVideoRenderContext) o;
        MarsVdp.MarsVdpRenderContext vrc = DebugVideoRenderContext.toMarsVdpRenderContext(dvrc);
        VideoMode vm = vrc.vdpContext.videoMode;
        img[S32xRenderType.MD.ordinal()] = saveRenderToImage(dvrc.mdData, dvrc.mdVideoMode);
        img[S32xRenderType.S32X.ordinal()] = saveRenderToImage(dvrc.s32xData, vm);
        int[] screen = MarsVdpImpl.doCompositeRenderingExt(dvrc.mdVideoMode, dvrc.mdData, vrc);
        img[S32xRenderType.FULL.ordinal()] = saveRenderToImage(screen, vm);
        return img;
    }
}