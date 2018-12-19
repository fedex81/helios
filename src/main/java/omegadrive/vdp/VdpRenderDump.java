package omegadrive.vdp;

import omegadrive.ui.RenderingStrategy;
import omegadrive.util.ImageUtil;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.RenderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class VdpRenderDump {

    private static Logger LOG = LogManager.getLogger(VdpRenderDump.class.getSimpleName());

    static GraphicsDevice gd;
    static boolean isHeadless;

    static {
        isHeadless = GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance();
        if (!isHeadless) {
            gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        }
    }

    private Path folder = Paths.get(".", "screencap");
    private int[] pixels = new int[0];
    private BufferedImage bi;

    public VdpRenderDump() {
        if (!isHeadless) {
            bi = gd.getDefaultConfiguration().createCompatibleImage(1, 1);
        }
    }


    public void saveRenderToFile(int[][] data, VideoMode videoMode, RenderType type) {
        if (isHeadless) {
            LOG.warn("Not supported in headless mode");
            return;
        }
        Dimension d = videoMode.getDimension();
        long now = System.currentTimeMillis();
        String fileName = type.toString() + "_" + now + ".jpg";

        if (bi.getWidth() * bi.getHeight() != d.width * d.height) {
            bi = gd.getDefaultConfiguration().createCompatibleImage(d.width, d.height);
            pixels = getPixels(bi);
        }
        RenderingStrategy.toLinear(pixels, data, d);
        Path file = Paths.get(folder.toAbsolutePath().toString(), fileName);
        LOG.info("Saving render to: " + file.toAbsolutePath().toString());
        ImageUtil.saveImageToFile(bi, file.toFile());
    }

    private int[] getPixels(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }
}
