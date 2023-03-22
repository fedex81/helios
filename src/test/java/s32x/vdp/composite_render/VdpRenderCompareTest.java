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

import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.slf4j.Logger;
import s32x.util.TestFileUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

import static s32x.util.TestRenderUtil.*;

@Disabled
public abstract class VdpRenderCompareTest {

    protected static boolean SHOW_IMAGES_ON_FAILURE = true;
    public static String IMG_EXT = "bmp";
    public static String DOT_EXT = "." + IMG_EXT + ".zip";

    private Path compareFolderPath = Paths.get(getBaseDataFolderName(), "compare");
    protected String compareFolder = compareFolderPath.toAbsolutePath().toString();
    private BufferedImage diffImage;

    protected abstract Path getBaseDataFolder();

    public static Stream<String> getFileProvider(Path baseDataFolder) {
        System.out.println(baseDataFolder.toAbsolutePath());
        File[] files = baseDataFolder.toFile().listFiles();
        return Arrays.stream(files).filter(File::isFile).map(f -> f.getName()).sorted();
    }

    protected String getBaseDataFolderName() {
        return getBaseDataFolder().toAbsolutePath().toString();
    }

    protected void testCompareFile(Path file, boolean overwrite) {
        Assertions.assertTrue(file.toFile().exists(), "Missing " + file.toAbsolutePath());
        if (overwrite) {
            testOverwriteBaselineImage(file);
        }
        boolean showingFailures = testCompareOne(file);
        if (showingFailures) {
            Util.waitForever();
        }
//        Util.waitForever();
    }

    private boolean compareImage(BufferedImage baseline, BufferedImage actual) {
        boolean ok = true;
        try {
            Dimension d1 = baseline.getData().getBounds().getSize();
            Dimension d2 = actual.getData().getBounds().getSize();

            Assertions.assertEquals(d1, d2, "Image size doesn't match");

            diffImage = convertToBufferedImage(baseline);
            for (int i = 0; i < d1.width; i++) {
                for (int j = 0; j < d1.height; j++) {
                    int r1 = baseline.getRGB(i, j);
                    int r2 = actual.getRGB(i, j);
                    double ratio = 1.0 * r1 / r2;
                    if (ratio - 1.0 != 0.0) {
//                        System.out.println(i + "," + j + ": " + r1 + "," + r2 + "," + ratio);
                    }
                    diffImage.setRGB(i, j, 0xFF_FF_FF - Math.abs(r1 - r2));
                    ok &= Math.abs(1.0 - ratio) < 0.01;
                }
            }
        } catch (AssertionError ae) {
            ae.printStackTrace();
            ok = false;
        }
        return ok;
    }

    protected abstract void testOverwriteBaselineImage(Path datFile);

    protected abstract boolean testCompareOne(Path datFile);

    private final static Logger LOG = LogHelper.getLogger(Util.class.getSimpleName());

    public static Serializable deserializeObject(byte[] data, int offset, int len) {
        if (data == null || data.length == 0 || offset < 0 || len > data.length) {
            LOG.error("Unable to deserialize object of len: {}", data != null ? data.length : "null");
            return null;
        }
        Serializable res = null;
        try (
                ByteArrayInputStream bis = new ByteArrayInputStream(data, offset, len);
                ObjectInput in = new ObjectInputStream(bis)
        ) {
            res = (Serializable) in.readObject();
        } catch (Exception e) {
            LOG.error("Unable to deserialize object of len: {}, {}", data.length, e.getMessage());
        }
        return res;
    }

    protected boolean testCompareOne(String saveName, BufferedImage actual) {
        Path baselineZipImageFile = Paths.get(compareFolder, saveName + DOT_EXT);
        Image base = TestFileUtil.decompressAndLoadFromZipFile(baselineZipImageFile, saveName + "." + IMG_EXT, IMG_EXT);
        Assertions.assertNotNull(base, "File missing: " + baselineZipImageFile.toAbsolutePath());
        BufferedImage baseLine = convertToBufferedImage(base);
        boolean match = compareImage(baseLine, actual);
        if (!match) {
            if (SHOW_IMAGES_ON_FAILURE) {
                JFrame f1 = showImageFrame(scaleImage(baseLine, 4), "BASELINE_" + saveName + DOT_EXT);
                JFrame f2 = showImageFrame(scaleImage(actual, 4), saveName);
                JFrame f3 = showImageFrame(scaleImage(diffImage, 4), "DIFF_" + saveName + " (Diffs are non white pixels)");
            }
            return true;
        }
        return false;
    }
}
