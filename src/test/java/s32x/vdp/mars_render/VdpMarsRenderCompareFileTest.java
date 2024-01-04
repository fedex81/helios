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

package s32x.vdp.mars_render;

import com.google.common.io.Files;
import omegadrive.util.FileUtil;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import s32x.vdp.MarsVdp;
import s32x.vdp.MarsVdp.DebugMarsVdpRenderContext;
import s32x.vdp.MarsVdpImpl;
import s32x.vdp.composite_render.VdpRenderCompareTest;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static omegadrive.util.TestRenderUtil.*;

public class VdpMarsRenderCompareFileTest extends VdpRenderCompareTest {

    public static Path baseDataFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "render_mars");
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
        Path saveFile = Paths.get(baseDataFolderName, "spaceh_01.dat.zip");
        super.testCompareFile(saveFile, true);
    }

    protected boolean testCompareOne(Path datFile) {
        DebugMarsVdpRenderContext d = toMarsContext(datFile);
        System.out.println(datFile.getFileName().toString() + ": " + d.renderContext.vdpContext);
        String fileName = Files.getNameWithoutExtension(datFile.getFileName().toString());
        BufferedImage actual = renderToImage(datFile);
        return testCompareOne(fileName + "_" + S32xRenderType.S32X, actual);
    }

    protected BufferedImage renderToImage(Path datFile) {
        DebugMarsVdpRenderContext d = toMarsContext(datFile);
        VideoMode vm = d.renderContext.vdpContext.videoMode;
        MarsVdp.MarsVdpContext vdpContext = d.renderContext.vdpContext;
        MarsVdp vdp = MarsVdpImpl.createInstance(vdpContext, ShortBuffer.wrap(d.frameBuffer0),
                ShortBuffer.wrap(d.frameBuffer1), ShortBuffer.wrap(d.palette));
        vdp.updateVideoMode(vm);
        vdp.draw(vdpContext);
        Image i = saveRenderToImage(vdp.getMarsVdpRenderContext().screen, vm);
        String fileName = Files.getNameWithoutExtension(datFile.getFileName().toString());
        return convertToBufferedImage(i);
    }

    private DebugMarsVdpRenderContext toMarsContext(Path datFile) {
        byte[] data = FileUtil.readBinaryFile(datFile, "dat");
        Object o = Util.deserializeObject(data);
        DebugMarsVdpRenderContext mvrc = (DebugMarsVdpRenderContext) o;
        return mvrc;
    }

    @Override
    protected void testOverwriteBaselineImage(Path datFile) {
        Image i = renderToImage(datFile);
        String fileName = Files.getNameWithoutExtension(datFile.getFileName().toString());
        saveToFile(compareFolder, fileName, S32xRenderType.S32X, IMG_EXT, i);
    }
}