package omegadrive.vdp.util;

import omegadrive.util.PriorityThreadFactory;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.model.VdpRenderHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VdpDebugView
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class VdpDebugView implements UpdatableViewer {

    private static final Logger LOG = LogManager.getLogger(VdpDebugView.class.getSimpleName());
    private ExecutorService service = Executors.newSingleThreadExecutor(
            new PriorityThreadFactory(Thread.MIN_PRIORITY, this));

    private static final boolean DEBUG_VIEWER_ENABLED;

    static {
        DEBUG_VIEWER_ENABLED =
                Boolean.parseBoolean(System.getProperty("md.show.vdp.debug.viewer", "false"));
        if (DEBUG_VIEWER_ENABLED) {
            LOG.info("Debug viewer enabled");
        }
    }

    private final VdpRenderHandler renderHandler;
    private final VdpMemoryInterface memoryInterface;
    private final CramViewer cramViewer;
    private final PlaneViewer planeViewer;
    private final TileViewer tileViewer;
    private JFrame frame;
    private JPanel panel;
    private JPanel additionalPanel;

    private boolean s32xMode = false;

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
            buildPanel();
            frame.setTitle("Vdp Debug Viewer");
            frame.pack();
            frame.setVisible(true);
        });
    }

    private void buildPanel() {
        frame.remove(panel);
        frame.invalidate();
        JPanel planePanel = planeViewer.getPanel();
        JPanel cramPanel = cramViewer.getPanel();
        JPanel tilePanel = tileViewer.getPanel();
        int h = planePanel.getHeight() + cramPanel.getHeight() + tilePanel.getHeight();
        this.panel.add(planePanel);
        if (s32xMode) {
            this.panel.add(additionalPanel);
            h += additionalPanel.getHeight();
        } else {
            this.panel.add(tilePanel);
        }
        this.panel.add(cramPanel);
        int w = Math.max(planePanel.getWidth(), cramPanel.getWidth());
        w = Math.max(w, tilePanel.getWidth()) + 20;

        cramPanel.setMaximumSize(cramPanel.getSize());
        this.panel.setSize(new Dimension(w, h));
        frame.add(panel);
        frame.setMinimumSize(panel.getSize());
        frame.pack();
    }

    @Override
    public void update() {
        //need to do it now, as the render will be changed by the 32x layer
        if (s32xMode) {
            planeViewer.update();
        }
        service.submit(() -> {
            cramViewer.update();
            tileViewer.update();
            if (!s32xMode) {
                planeViewer.update();
            }
        });
    }

    @Override
    public void updateLine(int line) {
        planeViewer.updateLine(line);
    }

    public void setAdditionalPanel(JPanel panel) {
        this.additionalPanel = panel;
        s32xMode = panel != null;
        buildPanel();
    }

    @Override
    public void reset() {
        service.shutdownNow();
        frame.setVisible(false);
        frame.dispose();
    }
}

