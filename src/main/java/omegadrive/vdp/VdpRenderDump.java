/*
 * VdpRenderDump
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/06/19 17:15
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

import omegadrive.util.ImageUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.VdpMisc.RenderType;
import org.slf4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VdpRenderDump {

    private static final Logger LOG = LogHelper.getLogger(VdpRenderDump.class.getSimpleName());

    public static GraphicsDevice gd;
    static final boolean isHeadless;

    static {
        isHeadless = GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance();
        if (!isHeadless) {
            gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        }
    }

    private final Path folder = Paths.get(".", "screencap");
    private int[] pixels = new int[0];
    private BufferedImage bi;

    public VdpRenderDump() {
        if (!isHeadless) {
            bi = gd.getDefaultConfiguration().createCompatibleImage(1, 1);
        }
    }

    public void saveRenderToFile(int[] data, VideoMode videoMode, RenderType type) {
        if (isHeadless) {
            LOG.warn("Not supported in headless mode");
            return;
        }
        long now = System.currentTimeMillis();
        String fileName = type.toString() + "_" + now + ".jpg";
        bi = getImage(videoMode);
        System.arraycopy(data, 0, pixels, 0, data.length);
        saveImageToFile(bi, fileName);
    }

    private void saveImageToFile(BufferedImage bi, String fileName) {
        Path file = Paths.get(folder.toAbsolutePath().toString(), fileName);
        LOG.info("Saving render to: {}", file.toAbsolutePath());
        ImageUtil.saveImageToFile(bi, file.toFile());
    }

    private static int[] getPixels(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    public BufferedImage getImage(VideoMode videoMode) {
        Dimension d = videoMode.getDimension();
        if (bi.getWidth() * bi.getHeight() != d.width * d.height) {
            bi = gd.getDefaultConfiguration().createCompatibleImage(d.width, d.height);
            pixels = getPixels(bi);
        }
        return bi;
    }

}
