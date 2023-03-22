package s32x.vdp.debug;

import omegadrive.Device;
import omegadrive.util.ImageUtil;
import omegadrive.util.VideoMode;
import s32x.vdp.MarsVdp;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.stream.IntStream;

import static s32x.vdp.debug.MarsVdpDebugView.ImageType.*;

/**
 * VdpDebugView
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public interface MarsVdpDebugView extends Device {

    MarsVdpDebugView NO_OP = new MarsVdpDebugView() {
        @Override
        public void update(MarsVdp.MarsVdpContext context, int[] buffer) {
        }

        @Override
        public void updateFinalImage(int[] fg) {
        }
    };

    enum ImageType {BUFF_0, BUFF_1, FULL, MDp32X}

    void update(MarsVdp.MarsVdpContext context, int[] buffer);

    void updateFinalImage(int[] fg);

    default JPanel getPanel() {
        return null;
    }

    static MarsVdpDebugView createInstance() {
        return MarsVdpDebugViewImpl.DEBUG_VIEWER_ENABLED ? new MarsVdpDebugViewImpl() : NO_OP;
    }

    class MarsVdpDebugViewImpl implements MarsVdpDebugView {

        protected static final boolean DEBUG_VIEWER_ENABLED;

        static {
            DEBUG_VIEWER_ENABLED =
                    Boolean.parseBoolean(System.getProperty("md.show.vdp.debug.viewer", "false"));
            if (DEBUG_VIEWER_ENABLED) {
//                LOG.info("Debug viewer enabled");
            }
        }

        public static GraphicsDevice gd;
        static boolean isHeadless;
        static Point lastLocation;

        static {
            isHeadless = GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance();
            if (!isHeadless) {
                gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            }
        }

        private static final int PANEL_TEXT_HEIGHT = 20;
        private static final int PANEL_HEIGHT = 256 + PANEL_TEXT_HEIGHT;
        private static final int PANEL_WIDTH = 320;
        private static final Dimension layerDim = new Dimension(PANEL_WIDTH, PANEL_HEIGHT);
        private static final int imgTypeLen = ImageType.values().length;

        private JPanel panel;
        private final ImageIcon[] imgIcons = new ImageIcon[ImageType.values().length];
        private final BufferedImage[] imageList = new BufferedImage[ImageType.values().length];
        private VideoMode videoMode = VideoMode.NTSCJ_H20_V18; //force an update later

        @Override
        public void init() {
            imageList[BUFF_0.ordinal()] = ImageUtil.createImage(gd, layerDim);
            imageList[BUFF_1.ordinal()] = ImageUtil.createImage(gd, layerDim);
            imageList[FULL.ordinal()] = ImageUtil.createImage(gd, layerDim);
            imageList[MDp32X.ordinal()] = ImageUtil.createImage(gd, layerDim);
            this.panel = new JPanel();
            JComponent p0 = createComponent(BUFF_0);
            JComponent p1 = createComponent(BUFF_1);
            JComponent p2 = createComponent(FULL);
            JComponent p3 = createComponent(MDp32X);
            panel.add(p0);
            panel.add(p1);
            panel.add(p2);
            panel.add(p3);
            Dimension d = new Dimension((int) (PANEL_WIDTH * 4.1), (int) (PANEL_HEIGHT * 1.01));
            panel.setMaximumSize(d);
            panel.setBackground(Color.BLACK);
        }

        protected MarsVdpDebugViewImpl() {
            init();
        }

        JComponent createComponent(ImageType type) {
            int num = type.ordinal();
            imgIcons[num] = new ImageIcon(imageList[num]);
            JPanel pnl = new JPanel();
            BoxLayout bl = new BoxLayout(pnl, BoxLayout.Y_AXIS);
            pnl.setLayout(bl);
            JLabel title = new JLabel(type != MDp32X ? "32X " + type : "MD+32X Layer");
            title.setForeground(Color.WHITE);
            JLabel lbl = new JLabel(imgIcons[num]);
            pnl.add(title);
            pnl.add(lbl);
            pnl.setBackground(Color.BLACK);
            pnl.setBorder(BorderFactory.createLineBorder(Color.WHITE));
            return pnl;
        }

        private void updateVideoMode(VideoMode videoMode) {
            IntStream.range(0, imgTypeLen).forEach(i -> {
                imageList[i] = ImageUtil.createImage(gd, videoMode.getDimension());
                imgIcons[i].setImage(imageList[i]);
            });
            this.videoMode = videoMode;
        }

        @Override
        public void update(MarsVdp.MarsVdpContext context, int[] rgb888) {
            if (context.videoMode != this.videoMode) {
                updateVideoMode(context.videoMode);
            }
            copyToImages(context.frameBufferDisplay, rgb888);
            panel.repaint();
        }

        @Override
        public void updateFinalImage(int[] fg) {
            int[] imgDataFull = ImageUtil.getPixels(imageList[MDp32X.ordinal()]);
            System.arraycopy(fg, 0, imgDataFull, 0, fg.length);
        }

        //copy the current front buffer to the FULL image
        private void copyToImages(int num, int[] rgb888) {
            int[] imgData = ImageUtil.getPixels(imageList[num]);
            int[] imgDataFull = ImageUtil.getPixels(imageList[FULL.ordinal()]);
            System.arraycopy(rgb888, 0, imgData, 0, rgb888.length);
            System.arraycopy(rgb888, 0, imgDataFull, 0, rgb888.length);
        }

        @Override
        public JPanel getPanel() {
            return panel;
        }
    }
}

