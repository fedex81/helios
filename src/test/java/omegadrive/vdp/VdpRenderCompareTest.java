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

import omegadrive.automated.SavestateGameLoader;
import omegadrive.util.ImageUtil;
import omegadrive.util.Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VdpRenderCompareTest extends VdpRenderTest {

    private static final boolean SHOW_IMAGES_ON_FAILURE = true;
    public static String EXT = "bmp";
    public static String DOT_EXT = "." + EXT;
    private static Path compareFolderPath = Paths.get(saveStateFolder, "compare");
    protected static String compareFolder = compareFolderPath.toAbsolutePath().toString();
    private BufferedImage diffImage;

    public static BufferedImage convertToBufferedImage(Image image) {
        BufferedImage newImage = new BufferedImage(
                image.getWidth(null), image.getHeight(null),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImage;
    }

    @Before
    public void beforeTest() {
        System.setProperty("emu.headless", "true");
    }

    @Test
    public void testCompareAll() {
        File[] files = Paths.get(saveStateFolder).toFile().listFiles();
//        for (Map.Entry<String, String> e : SavestateGameLoader.saveStates.entrySet()) {
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            //TODO investigate
            if (file.getName().endsWith("mickeym.gs0")) {
                continue;
            }
//            testOverwriteBaselineImage(file.getName());
            System.out.println("Testing: " + file);
            testCompareOne(file.getName());
        }
    }

    @Test
    public void testCompare() {
        testCompareOne("s2_int.gs0");
    }

    private void testCompareOne(String saveName) {
        Path saveFile = Paths.get(saveStateFolder, saveName);
        Path baselineImageFile = Paths.get(compareFolder, saveName + DOT_EXT);
        Image i = testSavestateViewerSingle(saveFile, SavestateGameLoader.saveStates.get(saveName));
        BufferedImage actual = convertToBufferedImage(i);
        BufferedImage base = ImageUtil.loadImageFromFile(baselineImageFile.toFile());
        BufferedImage baseLine = convertToBufferedImage(base);
        boolean match = compareImage(baseLine, actual);
        if (!match) {
            if (SHOW_IMAGES_ON_FAILURE) {
                JFrame f1 = showImageFrame(scaleImage(baseLine, 4), "BASELINE_" + saveName + DOT_EXT);
                JFrame f2 = showImageFrame(scaleImage(actual, 4), saveName);
                JFrame f3 = showImageFrame(scaleImage(diffImage, 4), "DIFF_" + saveName + " (Diffs are non white pixels)");
                Util.waitForever();
            }
        }
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

    private void testOverwriteBaselineImage(String saveName) {
        Path saveFile = Paths.get(saveStateFolder, saveName);
        Image i = testSavestateViewerSingle(saveFile, SavestateGameLoader.saveStates.get(saveName));
        saveToFile(saveName, i);
    }

    private void saveToFile(String saveName, Image i) {
        if (!(i instanceof RenderedImage)) {
            i = convertToBufferedImage(i);
        }
        RenderedImage ri = (RenderedImage) i;
        Path file = Paths.get(compareFolder, saveName + DOT_EXT);
        ImageUtil.saveImageToFile(ri, file.toFile(), EXT);
        System.out.println("Image saved: " + file.toAbsolutePath().toString());
    }
}
