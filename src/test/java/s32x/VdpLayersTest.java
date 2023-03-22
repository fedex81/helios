package s32x;

import omegadrive.util.ImageUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import omegadrive.vdp.VdpRenderDump;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import s32x.vdp.MarsVdp.VdpPriority;
import s32x.vdp.debug.DebugVideoRenderContext;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
@Disabled
public class VdpLayersTest {

    private final static Logger LOG = LogHelper.getLogger(VdpLayersTest.class.getSimpleName());

    private int scale = 3;
    int w = 256;
    int h = 224;
    private Dimension d = new Dimension(w, h);

    @Test
    public void test01() throws IOException {
        Path p = Paths.get(".", "vrc_383683981322708445.dat");
        System.out.println(p.toAbsolutePath().toString());
        byte[] data = Files.readAllBytes(p);
        Object o = deserializeObject(data, 0, data.length);
        DebugVideoRenderContext ctx = (DebugVideoRenderContext) o;
        int[] fg = ctx.marsVdpContext.priority == VdpPriority.S32X ? ctx.s32xData : ctx.mdData;
        int[] bg = ctx.marsVdpContext.priority == VdpPriority.S32X ? ctx.mdData : ctx.s32xData;
        int[] merge = fg.clone();
        for (int i = 0; i < ctx.mdData.length; i++) {
            boolean throughBit = (ctx.s32xData[i] & 1) > 0;
            merge[i] = fg[i] == 0 || (throughBit && bg[i] > 0) ? bg[i] : fg[i];
        }
        showImageScaled("MD", ctx.mdData, d, scale);
        showImageScaled("32X", ctx.s32xData, d, scale);
        showImageScaled("MERGE", merge, d, scale);
        Util.waitForever();
    }

    private static void showImageScaled(String name, int[] data, Dimension d, int scale) {
        BufferedImage mdImg = createImage(data, d.width, d.height);
        showImageFrame(scaleImage(mdImg, scale), name);
    }

    public static BufferedImage createImage(int[] pixels, int w, int h) {
        BufferedImage image = ImageUtil.createImage(VdpRenderDump.gd, new Dimension(w, h));
        int[] px = ImageUtil.getPixels(image);
        System.arraycopy(pixels, 0, px, 0, px.length);
        return image;
    }

    protected static Image scaleImage(Image i, int factor) {
        return i.getScaledInstance(i.getWidth(null) * factor, i.getHeight(null) * factor, 0);
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
}
