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

import omegadrive.util.TestFileUtil;
import omegadrive.util.TestRenderUtil;
import omegadrive.util.Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static omegadrive.util.TestRenderUtil.*;

@Ignore
@Disabled
public class VdpRenderCompareTest extends VdpRenderTest {

    protected static boolean SHOW_IMAGES_ON_FAILURE = true;
    public static String IMG_EXT = "bmp";
    public static String DOT_EXT = "." + IMG_EXT + ".zip";
    private static Path compareFolderPath = Paths.get(baseDataFolder, "compare");
    protected static String compareFolder = compareFolderPath.toAbsolutePath().toString();
    private BufferedImage diffImage;

    @Before
    public void beforeTest() {
        System.setProperty("helios.headless", "true");
        System.setProperty("md.show.vdp.debug.viewer", "false");
    }

    @Test
    @Ignore
    public void testCompareAll() {
//        SHOW_IMAGES_ON_FAILURE = true;
        File[] files = Paths.get(baseDataFolder).toFile().listFiles();
        StringBuilder sb = new StringBuilder();
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            System.out.println("Testing: " + file);
            boolean res = testCompareOne(file.toPath());
            if (res) {
                sb.append("Mismatch: " + file + "\n");
                System.out.println("Error: " + file);
                break;
//                FileUtil.compressAndSaveToZipFile(file.toPath());
            }
        }
        System.out.println("Done");
        if (SHOW_IMAGES_ON_FAILURE) {
            Util.waitForever();
        } else {
            Assert.assertTrue(sb.toString(), sb.length() == 0);
        }
    }

    protected void testCompareFile(Path file, boolean overwrite) {
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

            Assert.assertEquals("Image size doesn't match", d1, d2);

            diffImage = convertToBufferedImage(baseline);
            for (int i = 0; i < d1.width; i++) {
                for (int j = 0; j < d1.height; j++) {
                    //NOTE: ignore lsb (bit) as it encodes the blanking information for that pixel
                    int r1 = baseline.getRGB(i, j) & ~1;
                    int r2 = actual.getRGB(i, j) & ~1;
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

    private void testOverwriteBaselineImage(Path saveFile) {
        Image i = testSavestateViewerSingle(saveFile);
        TestRenderUtil.saveToFile(compareFolder, saveFile.getFileName().toString(), IMG_EXT, i);
    }

    protected boolean testCompareOne(Path saveFile) {
        Image i = testSavestateViewerSingle(saveFile);
        BufferedImage actual = convertToBufferedImage(i);
        return testCompareOne(saveFile.getFileName().toString(), actual);
    }

    protected boolean testCompareOne(String saveName, BufferedImage actual) {
        Path baselineZipImageFile = Paths.get(compareFolder, saveName + DOT_EXT);
        Image base = TestFileUtil.decompressAndLoadFromZipFile(baselineZipImageFile, saveName + "." + IMG_EXT, IMG_EXT);
        Assert.assertNotNull("File missing: " + baselineZipImageFile.toAbsolutePath(), base);
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
