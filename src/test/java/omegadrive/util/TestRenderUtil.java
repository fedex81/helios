package omegadrive.util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class TestRenderUtil {

    public static GraphicsDevice gd;
    static boolean isHeadless;

    static {
        isHeadless = GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance();
        if (!isHeadless) {
            gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        }
    }

    public enum S32xRenderType {FULL, MD, S32X}

    public static void saveToFile(String folderName, String saveName, S32xRenderType type, String imgExt, Image i) {
        Path folder = Paths.get(folderName);
        Path res = TestFileUtil.compressAndSaveToZipFile(saveName + "_" + type.name() + "." + imgExt, folder, i, imgExt);
        System.out.println("Image saved: " + res.toAbsolutePath());
    }

    public static void saveToFile(String folderName, String saveName, String imgExt, Image i) {
        Path folder = Paths.get(folderName);
        Path res = TestFileUtil.compressAndSaveToZipFile(saveName + "." + imgExt, folder, i, imgExt);
        System.out.println("Image saved: " + res.toAbsolutePath().toString());
    }

    public static BufferedImage convertToBufferedImage(Image image) {
        BufferedImage newImage = new BufferedImage(
                image.getWidth(null), image.getHeight(null),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = newImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImage;
    }

    public static Image scaleImage(Image i, int factor) {
        return i.getScaledInstance(i.getWidth(null) * factor, i.getHeight(null) * factor, 0);
    }

    public static boolean isValidImage(int[] screenData) {
        for (int i = 0; i < screenData.length; i++) {
            if (screenData[i] > 0) {
                return true;
            }
        }
        return false;
    }

    public static Image cloneImage(Image img) {
        return img.getScaledInstance(-1, -1, Image.SCALE_DEFAULT);
    }

    public static JFrame showImageFrame(Image bi, String title) {
        JLabel label = new JLabel();
        JPanel panel = new JPanel();
        panel.add(label);
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        f.add(panel);
        label.setIcon(new ImageIcon(bi));
        f.setTitle(title);
        f.pack();
        f.setVisible(true);
        return f;
    }

    public static BufferedImage getImage(VideoMode videoMode) {
        Dimension d = videoMode.getDimension();
        BufferedImage bi = gd.getDefaultConfiguration().createCompatibleImage(d.width, d.height);
        return bi;
    }

    public static BufferedImage saveRenderToImage(int[] data, VideoMode videoMode) {
        BufferedImage bi = getImage(videoMode);
        int[] linear = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
        System.arraycopy(data, 0, linear, 0, linear.length);
        return bi;
    }
}
