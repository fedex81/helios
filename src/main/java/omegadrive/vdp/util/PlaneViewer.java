package omegadrive.vdp.util;

import omegadrive.vdp.VdpRenderDump;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.RenderType;
import omegadrive.vdp.model.VdpRenderHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * PlaneViewer
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class PlaneViewer implements UpdatableViewer {

    private static final Logger LOG = LogManager.getLogger(CramViewer.class.getSimpleName());

    private static final int PANEL_TEXT_HEIGHT = 20;
    private static final int PANEL_HEIGHT = 256 + PANEL_TEXT_HEIGHT;
    private static final int PANEL_WIDTH = 320;

    private VdpRenderHandler renderHandler;
    private static final int CRAM_MASK = GenesisVdpProvider.VDP_CRAM_SIZE - 1;
    private JPanel panel;
    private JFrame frame;
    private JPanel[] panelList = new JPanel[RenderType.values().length];
    private BufferedImage[] imageList = new BufferedImage[RenderType.values().length];

    private PlaneViewer(VdpRenderHandler renderHandler) {
        this.renderHandler = renderHandler;
        this.frame = new JFrame();
        this.panel = new JPanel();
        initPanel();
    }

    public static PlaneViewer createInstance(VdpRenderHandler renderHandler) {
        return new PlaneViewer(renderHandler);
    }

    private void initPanel() {
        int numCols = 3;
        int numRows = 2;
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame();
            this.panel = new JPanel(new GridLayout(numRows, numCols));
            int k = 0;
            for (RenderType type : RenderType.values()) {
                JPanel cpanel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (g instanceof Graphics2D) {
                            Graphics2D g2 = (Graphics2D) g;
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);

                            g2.drawString(type.name(), 70, 10);
                            g2.drawImage(imageList[type.ordinal()], 0, PANEL_TEXT_HEIGHT, this);
                        }
                    }
                };
                cpanel.setBackground(Color.BLACK);
                cpanel.setForeground(Color.WHITE);
                cpanel.setName(type.name());
                cpanel.setSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
                panelList[k] = cpanel;
                k++;
            }
            Arrays.stream(panelList).forEach(panel::add);
            panel.setSize(new Dimension(PANEL_WIDTH * numCols, PANEL_HEIGHT * numRows));
        });
    }

    private void initFrame() {
        int numCols = 3;
        int numRows = 2;
        initPanel();
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame();
            frame.add(panel);
            frame.setMinimumSize(new Dimension(PANEL_WIDTH * numCols, PANEL_HEIGHT * numRows));
            frame.setTitle("Plane Viewer");
            frame.pack();
            frame.setVisible(true);
        });
    }

    public JPanel getPanel() {
        return panel;
    }

    Map<RenderType, Object> javaColorRes = new HashMap<>();
    private int[] javaPalette;

    @Override
    public void update() {
        Dimension finalDim = renderHandler.getVideoMode().getDimension();
        Dimension layerDim = new Dimension(320, 256);
        for (RenderType type : RenderType.values()) {
            BufferedImage img = imageList[type.ordinal()];
            Dimension d = type == RenderType.FULL ? finalDim : layerDim;
            Object data = toJavaColor(type, renderHandler.getPlaneData(type));
            imageList[type.ordinal()] = VdpRenderDump.writeDataToImage(img, type, d, data);
        }
        panel.repaint();
    }

    private Object toJavaColor(RenderType type, final Object data) {
        Object out = null;
        if (data instanceof int[][]) {
            int[][] in = (int[][]) data;
            int[][] out1 = getHolder(type, in);
            for (int i = 0; i < in.length; i++) {
                for (int j = 0; j < in[i].length; j++) {
                    out1[i][j] = getJavaColorValue(in[i][j] & CRAM_MASK);
                }
            }
            out = out1;
        } else if (data instanceof int[]) {
            out = data;
        } else {
            LOG.error("Error");
        }
        return out;
    }

    private int[][] getHolder(RenderType type, final int[][] data) {
        int[][] res = (int[][]) javaColorRes.get(type);
        if (res == null || data.length != res.length) {
            res = data.clone();
            javaColorRes.put(type, res);
        }
        return res;
    }

    public int getJavaColorValue(int cramIndex) {
        return javaPalette[cramIndex >> 1];
    }
}
