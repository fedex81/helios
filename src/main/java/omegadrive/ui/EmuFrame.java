package omegadrive.ui;

import com.google.common.base.Strings;
import omegadrive.Genesis;
import omegadrive.GenesisProvider;
import omegadrive.util.FileLoader;
import omegadrive.util.ScreenSizeHelper;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.InvocationTargetException;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * TODO use Canvas instead of JLabel
 */
public class EmuFrame implements GenesisWindow {

    private static Logger LOG = LogManager.getLogger(EmuFrame.class.getSimpleName());

    private static final int DEFAULT_SCREEN = 0;

    private Dimension fullScreenSize;
    private GraphicsDevice[] graphicsDevices;
    private Dimension screenSize = new Dimension(ScreenSizeHelper.DEFAULT_X,
            ScreenSizeHelper.DEFAULT_Y);
    private Dimension baseScreenSize = new Dimension(ScreenSizeHelper.DEFAULT_X,
            ScreenSizeHelper.DEFAULT_Y);

    private BufferedImage src;
    private BufferedImage dest;
    private int[] pixelsSrc;
    private int[] pixelsDest;
    private double scale = 1;


    private final JLabel gameLabel = new JLabel();
    private final JLabel fpsLabel = new JLabel("");
    private final static String FRAME_TITLE_HEAD = "Omega Drive " + FileLoader.loadVersionFromManifest();
    public static String basePath = "./roms/";

    private JFrame jFrame;
    private GenesisProvider mainEmu;
    private JCheckBoxMenuItem usaBios;
    private JCheckBoxMenuItem eurBios;
    private JCheckBoxMenuItem japBios;
    private JCheckBoxMenuItem fullScreenItem;
    private boolean showDebug = false;
    private EventQueue eventQueue;

    public void setTitle(String title) {
        jFrame.setTitle(FRAME_TITLE_HEAD + " - " + title);
    }

    public void repaint() {
        jFrame.repaint();
    }

    public EmuFrame(Genesis mainEmu) {
        this.mainEmu = mainEmu;
    }

    public static void main(String[] args) {
        EmuFrame frame = new EmuFrame(null);
        frame.init();
    }

    public void init() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        Util.registerJmx(this);
        eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        graphicsDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        LOG.info("Screen detected: " + graphicsDevices.length);
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (graphicsDevices.length > 1) {
            gd = graphicsDevices[DEFAULT_SCREEN];
            LOG.info("Using screen: " + DEFAULT_SCREEN);
        }
        fullScreenSize = gd.getDefaultConfiguration().getBounds().getSize();
        LOG.info("Full screen size: " + fullScreenSize);
        LOG.info("Screen size: " + screenSize);

        src = createImage(gd, screenSize, true);
        dest = createImage(gd, screenSize, false);
        gameLabel.setIcon(new ImageIcon(dest));

        jFrame = new JFrame(FRAME_TITLE_HEAD, gd.getDefaultConfiguration());

        jFrame.getContentPane().setBackground(Color.BLACK);
        jFrame.getContentPane().setForeground(Color.BLACK);

        JMenuBar bar = new JMenuBar();

        JMenu menu = new JMenu("File");
        bar.add(menu);

        JMenu menuBios = new JMenu("Region");
        bar.add(menuBios);

        JMenu menuView = new JMenu("View");
        bar.add(menuView);

        usaBios = new JCheckBoxMenuItem("USA", false);
        menuBios.add(usaBios);

        eurBios = new JCheckBoxMenuItem("Europe", false);
        menuBios.add(eurBios);

        japBios = new JCheckBoxMenuItem("Japan", false);
        menuBios.add(japBios);

        fullScreenItem = new JCheckBoxMenuItem("Full Screen", false);
        menuView.add(fullScreenItem);

        JMenu helpMenu = new JMenu("Help");
        bar.add(helpMenu);
        bar.add(fpsLabel);

        JMenuItem loadRomItem = new JMenuItem("Load ROM");
        loadRomItem.addActionListener(e -> mainEmu.handleNewGame());
        JMenuItem closeRomItem = new JMenuItem("Close ROM");
        closeRomItem.addActionListener(e -> mainEmu.handleCloseGame());
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            mainEmu.handleCloseGame();
            System.exit(0);
        });

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(null, "TODO"));

        jFrame.addKeyListener(new KeyAdapter() {

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_F:
                        toggleFullScreen();
                        break;
                    case KeyEvent.VK_M:
                        mainEmu.toggleMute();
                        break;
                    case KeyEvent.VK_0:
                        showDebug = !showDebug;
                        showDebugInfo(showDebug);
                        break;
                    case KeyEvent.VK_R:
                        mainEmu.toggleSoundRecord();
                        break;
                    case KeyEvent.VK_1:
                        mainEmu.setPlayers(1);
                        break;
                    case KeyEvent.VK_2:
                        mainEmu.setPlayers(2);
                        break;
                    case KeyEvent.VK_L:
                        FileLoader.openRomDialog();
                        break;
                }
            }
        });

        menu.add(loadRomItem);
        menu.add(closeRomItem);
        menu.add(exitItem);
        helpMenu.add(aboutItem);

        gameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gameLabel.setVerticalAlignment(SwingConstants.CENTER);

        jFrame.setMinimumSize(new Dimension(640, 480));
        jFrame.setDefaultCloseOperation(jFrame.EXIT_ON_CLOSE);
        jFrame.setResizable(true);
        jFrame.setJMenuBar(bar);
        jFrame.add(gameLabel, -1);

        jFrame.pack();
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
    }

    private void toggleFullScreen() {
        fullScreenItem.setState(!fullScreenItem.getState());
    }

    private GraphicsDevice getGraphicsDevice() {
        return graphicsDevices[DEFAULT_SCREEN];
    }

    private BufferedImage createImage(GraphicsDevice gd, Dimension d, boolean src) {

        BufferedImage bi = gd.getDefaultConfiguration().createCompatibleImage(d.width, d.height);
        if (bi.getType() != BufferedImage.TYPE_INT_RGB) {
            //mmh we need INT_RGB here
            bi = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
        }
        pixelsSrc = src ? getPixels(bi) : pixelsSrc;
        pixelsDest = src ? pixelsDest : getPixels(bi);
        return bi;
    }

    private void showDebugInfo(boolean showDebug) {
        if (fullScreenItem.getState()) {
            jFrame.getJMenuBar().setVisible(showDebug);
        }
        fpsLabel.setVisible(showDebug);
    }

    public void resetScreen() {
        //switch off fullscreen
//        SwingUtilities.invokeLater(() -> {
//            fullScreenItem.setState(false);
//            resizeScreen(RegionDetector.Region.EUROPE, true);
//        });
    }

    @Override
    public void setFullScreen(boolean value) {
        fullScreenItem.setState(value);
    }

    @Override
    public String getRegionOverride() {
        String regionOverride = usaBios.getState() ? "USA" : null;
        regionOverride = eurBios.getState() ? "EUROPE" : regionOverride;
        regionOverride = japBios.getState() ? "JAPAN" : regionOverride;
        return regionOverride;
    }

    public void renderScreen(int[][] data, String label, VideoMode videoMode) {
        SwingUtilities.invokeLater(() -> {
            boolean changed = resizeScreen(videoMode);
            if (scale > 1) {
                Dimension ouput = new Dimension((int) (baseScreenSize.width * scale), (int) (baseScreenSize.height * scale));
                RenderingStrategy.toLinear(pixelsSrc, data, baseScreenSize);
                RenderingStrategy.renderNearest(pixelsSrc, pixelsDest, baseScreenSize, ouput);
            } else {
                RenderingStrategy.toLinear(pixelsDest, data, baseScreenSize);
            }
            if (!Strings.isNullOrEmpty(label)) {
                getFpsLabel().setText(label);
            }
            gameLabel.repaint();
        });
    }

    private boolean resizeScreen(VideoMode videoMode) {
        boolean goFullScreen = fullScreenItem.getState();
        Dimension newBaseScreenSize = ScreenSizeHelper.getScreenSize(videoMode, 1);
        if (!newBaseScreenSize.equals(baseScreenSize)) {
            baseScreenSize = newBaseScreenSize;
        }
        double scale = 1;
        if (goFullScreen) {
            double scaleW = fullScreenSize.getWidth() / baseScreenSize.getWidth();
            double scaleH = fullScreenSize.getHeight() / baseScreenSize.getHeight();
            scale = Math.min(scaleW, scaleH);
        }
        return resizeScreenInternal(newBaseScreenSize, scale, goFullScreen);
    }

    private boolean resizeScreenInternal(Dimension newScreenSize, double scale, boolean isFullScreen) {
        boolean scaleChanged = this.scale != scale;
        boolean baseResize = !newScreenSize.equals(screenSize);
        if (baseResize || scaleChanged) {

            screenSize = newScreenSize;
            this.scale = scale;
            try {
                Runnable resizeRunnable = getResizeRunnable(isFullScreen);
                if (SwingUtilities.isEventDispatchThread()) {
                    resizeRunnable.run();
                } else {
                    SwingUtilities.invokeAndWait(resizeRunnable);
                }
            } catch (InterruptedException | InvocationTargetException e) {
                LOG.error(e.getMessage());
            }
            return true;
        }
        return false;
    }

    private Runnable getResizeRunnable(boolean isFullScreen) {
        return () -> {
            src = createImage(getGraphicsDevice(), baseScreenSize, true);
            Dimension d = new Dimension((int) (src.getWidth() * scale), (int) (src.getHeight() * scale));
            dest = createImage(getGraphicsDevice(), d, false);
            LOG.info("Screen size: " + d);
            gameLabel.setIcon(new ImageIcon(dest));
            jFrame.setPreferredSize(isFullScreen ? fullScreenSize : baseScreenSize);
            jFrame.getJMenuBar().setVisible(!isFullScreen);
            jFrame.setLocationRelativeTo(null); //center
            jFrame.pack();
        };
    }

    @Override
    public void addKeyListener(KeyAdapter keyAdapter) {
        jFrame.addKeyListener(keyAdapter);
    }

    private JLabel getFpsLabel() {
        return fpsLabel;
    }

    private int[] getPixels(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }
}
