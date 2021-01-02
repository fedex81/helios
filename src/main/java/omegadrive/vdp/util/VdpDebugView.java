package omegadrive.vdp.util;

import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.model.VdpRenderHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;

/**
 * VdpDebugView
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class VdpDebugView implements UpdatableViewer {

    private static final Logger LOG = LogManager.getLogger(VdpDebugView.class.getSimpleName());

    private static boolean DEBUG_VIEWER_ENABLED;

    static {
        DEBUG_VIEWER_ENABLED =
                Boolean.parseBoolean(System.getProperty("md.show.vdp.debug.viewer", "false"));
        if (DEBUG_VIEWER_ENABLED) {
            LOG.info("Debug viewer enabled");
        }
    }

    private VdpRenderHandler renderHandler;
    private VdpMemoryInterface memoryInterface;
    private CramViewer cramViewer;
    private PlaneViewer planeViewer;
    private TileViewer tileViewer;
    private JFrame frame;
    private JPanel panel;

    private VdpDebugView(GenesisVdpProvider vdp, VdpMemoryInterface memoryInterface, VdpRenderHandler renderHandler) {
        this.memoryInterface = memoryInterface;
        this.renderHandler = renderHandler;
        this.cramViewer = CramViewer.createInstance(memoryInterface);
        this.planeViewer = PlaneViewer.createInstance(vdp, memoryInterface, renderHandler);
        this.tileViewer = TileViewer.createInstance(vdp, memoryInterface, renderHandler);
        init();
    }

    public static UpdatableViewer createInstance(GenesisVdpProvider vdp, VdpMemoryInterface memoryInterface, VdpRenderHandler renderHandler) {
        return DEBUG_VIEWER_ENABLED ? new VdpDebugView(vdp, memoryInterface, renderHandler) : UpdatableViewer.NO_OP_VIEWER;
    }

    private void init() {
        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame();
            this.panel = new JPanel();
            this.panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(Color.GRAY);
            JPanel planePanel = planeViewer.getPanel();
            JPanel cramPanel = cramViewer.getPanel();
            JPanel tilePanel = tileViewer.getPanel();
            this.panel.add(planePanel);
            this.panel.add(tilePanel);
            this.panel.add(cramPanel);
            int w = planePanel.getWidth();
            int h = planePanel.getHeight() + cramPanel.getHeight();
            cramPanel.setMaximumSize(cramPanel.getSize());
            this.panel.setSize(new Dimension(w, h));
            frame.add(panel);
            frame.setMinimumSize(panel.getSize());
            frame.setTitle("Vdp Debug Viewer");
            frame.pack();
            frame.setVisible(true);
        });
    }

    @Override
    public void update() {
        cramViewer.update();
        planeViewer.update();
        tileViewer.update();
    }

    @Override
    public void updateLine(int line) {
        planeViewer.updateLine(line);
    }

    @Override
    public void reset() {
        frame.setVisible(false);
        frame.dispose();
    }
}

