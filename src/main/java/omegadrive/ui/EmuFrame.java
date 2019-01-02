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
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static omegadrive.util.ScreenSizeHelper.*;

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

    private Dimension fullScreenSize;
    private GraphicsDevice[] graphicsDevices;
    private Dimension screenSize = DEFAULT_SCALED_SCREEN_SIZE;
    private Dimension baseScreenSize = DEFAULT_BASE_SCREEN_SIZE;

    private BufferedImage src;
    private BufferedImage dest;
    private int[] pixelsSrc;
    private int[] pixelsDest;
    private double scale = DEFAULT_SCALE_FACTOR;

    private static String QUICK_SAVE_FILENAME = "quick_save.gs0";

    private final JLabel screenLabel = new JLabel();
    private final JLabel fpsLabel = new JLabel("");
    private final static String FRAME_TITLE_HEAD = "Omega Drive " + FileLoader.loadVersionFromManifest();

    private JFrame jFrame;
    private GenesisProvider mainEmu;
    private JCheckBoxMenuItem usaBios;
    private JCheckBoxMenuItem eurBios;
    private JCheckBoxMenuItem japBios;
    private JCheckBoxMenuItem fullScreenItem;
    private boolean showDebug = false;

    private static FileFilter ROM_FILTER = new FileFilter() {
        @Override
        public String getDescription() {
            return "md and bin files";
        }

        @Override
        public boolean accept(File f) {
            String name = f.getName().toLowerCase();
            return f.isDirectory() || name.endsWith(".md") || name.endsWith(".bin");
        }
    };

    private static FileFilter SAVE_STATE_FILTER = new FileFilter() {
        @Override
        public String getDescription() {
            return "state files";
        }

        @Override
        public boolean accept(File f) {
            String name = f.getName().toLowerCase();
            return f.isDirectory() || name.contains(".gs");
        }
    };

    public void setTitle(String title) {
        jFrame.setTitle(FRAME_TITLE_HEAD + " - " + title);
    }

    public EmuFrame(Genesis mainEmu) {
        this.mainEmu = mainEmu;
    }

    public static void main(String[] args) {
        EmuFrame frame = new EmuFrame(null);
        frame.init();
    }

    private String getAboutString() {
        int year = LocalDate.now().getYear();
        String yrString = year == 2018 ? "2018" : "2018-" + year;
        String res = FRAME_TITLE_HEAD + "\nA Sega Megadrive (Genesis) emulator, written in Java";
        res += "\n\nCopyright " + yrString + ", Federico Berti";
        res += "\n\nSee CREDITS.TXT for more information";
        res += "\n\nReleased under GPL v.3.0 license.";
        return res;
    }

    public void init() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        Util.registerJmx(this);
        graphicsDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        LOG.info("Screen detected: " + graphicsDevices.length);
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (graphicsDevices.length > 1) {
            int screenNumber = Math.min(DEFAULT_SCREEN, graphicsDevices.length - 1);
            gd = graphicsDevices[screenNumber];
        }
        LOG.info("Using screen: " + gd.getIDstring());
        fullScreenSize = gd.getDefaultConfiguration().getBounds().getSize();
        LOG.info("Full screen size: " + fullScreenSize);
        LOG.info("Emulation viewport size: " + ScreenSizeHelper.DEFAULT_SCALED_SCREEN_SIZE);
        LOG.info("Application size: " + DEFAULT_FRAME_SIZE);

        src = createImage(gd, screenSize, true);
        dest = createImage(gd, screenSize, false);
        screenLabel.setIcon(new ImageIcon(dest));

        jFrame = new JFrame(FRAME_TITLE_HEAD, gd.getDefaultConfiguration());

        jFrame.getContentPane().setBackground(Color.BLACK);
        jFrame.getContentPane().setForeground(Color.BLACK);

        JMenuBar bar = new JMenuBar();

        JMenu menu = new JMenu("File");
        bar.add(menu);

        JMenu setting = new JMenu("Setting");
        bar.add(setting);

        JMenuItem pauseItem = new JMenuItem("Pause");
        pauseItem.addActionListener(e -> mainEmu.handlePause());
        setting.add(pauseItem);

        JMenuItem resetItem = new JMenuItem("Reset");
        resetItem.addActionListener(e -> mainEmu.reset());
        setting.add(resetItem);

        JMenu menuBios = new JMenu("Region");
        setting.add(menuBios);

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
        loadRomItem.addActionListener(e -> handleNewRom());
        JMenuItem closeRomItem = new JMenuItem("Close ROM");
        closeRomItem.addActionListener(e -> mainEmu.handleCloseRom());

        JMenuItem loadStateItem = new JMenuItem("Load State");
        loadStateItem.addActionListener(e -> handleLoadState());
        JMenuItem saveStateItem = new JMenuItem("Save State");
        saveStateItem.addActionListener(e -> handleSaveState());

        JMenuItem quickSaveStateItem = new JMenuItem("Quick Save State");
        quickSaveStateItem.addActionListener(e -> handleQuickSaveState());
        JMenuItem quickLoadStateItem = new JMenuItem("Quick Load State");
        quickLoadStateItem.addActionListener(e -> handleQuickLoadState());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            mainEmu.handleCloseApp();
            System.exit(0);
        });

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showHelpMessage(aboutItem.getText(), getAboutString()));

        JMenuItem creditsItem = new JMenuItem("Credits");
        creditsItem.addActionListener(e ->
                showHelpMessage(creditsItem.getText(), FileLoader.loadFileContentAsString("CREDITS.md"))
        );
        JMenuItem readmeItem = new JMenuItem("Readme");
        readmeItem.addActionListener(e ->
                showHelpMessage(readmeItem.getText(), FileLoader.loadFileContentAsString("README.md"))
        );
        JMenuItem licenseItem = new JMenuItem("License");
        licenseItem.addActionListener(e ->
                showHelpMessage(licenseItem.getText(), FileLoader.loadFileContentAsString("LICENSE.md"))
        );

        JMenuItem historyItem = new JMenuItem("History");
        historyItem.addActionListener(e ->
                showHelpMessage(historyItem.getText(), FileLoader.loadFileContentAsString("HISTORY.md"))
        );

        menu.add(loadRomItem);
        menu.add(closeRomItem);
        menu.add(loadStateItem);
        menu.add(saveStateItem);
        menu.add(quickLoadStateItem);
        menu.add(quickSaveStateItem);
        menu.add(exitItem);
        helpMenu.add(aboutItem);
        helpMenu.add(readmeItem);
        helpMenu.add(creditsItem);
        helpMenu.add(historyItem);
        helpMenu.add(licenseItem);

        setupFrameKeyListener();

        screenLabel.setHorizontalAlignment(SwingConstants.CENTER);
        screenLabel.setVerticalAlignment(SwingConstants.CENTER);

        jFrame.setMinimumSize(DEFAULT_FRAME_SIZE);
        jFrame.setDefaultCloseOperation(jFrame.EXIT_ON_CLOSE);
        jFrame.setResizable(true);
        jFrame.setJMenuBar(bar);
        jFrame.add(screenLabel, -1);
//        jFrame.setIconImage(new ImageIcon(EmuFrame.class.getResource("/omega.png")).getImage());

        jFrame.pack();

        //get the center location and then reset it
        jFrame.setLocationRelativeTo(null);
        Point centerPoint = jFrame.getLocation();
        jFrame.setLocation(gd.getDefaultConfiguration().getBounds().x + centerPoint.x,
                gd.getDefaultConfiguration().getBounds().y + centerPoint.y);

        jFrame.setVisible(true);
    }

    private void showHelpMessage(String title, String msg) {
        JTextArea area = new JTextArea(msg);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setPreferredSize(jFrame.getPreferredSize());
        JOptionPane.showMessageDialog(this.jFrame,
                scrollPane, "Help: " + title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void toggleFullScreen() {
        fullScreenItem.setState(!fullScreenItem.getState());
    }

    private GraphicsDevice getGraphicsDevice() {
        return jFrame.getGraphicsConfiguration().getDevice();
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
        Util.sleep(250);
        SwingUtilities.invokeLater(() -> {
            Arrays.fill(pixelsDest, 0);
            screenLabel.invalidate();
            screenLabel.repaint();
            fpsLabel.setText("");
            jFrame.setTitle(FRAME_TITLE_HEAD);
            LOG.info("Blanking screen");
        });
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
            screenLabel.repaint();
        });
    }

    private boolean resizeScreen(VideoMode videoMode) {
        boolean goFullScreen = fullScreenItem.getState();
        Dimension newBaseScreenSize = getScreenSize(videoMode, 1);
        if (!newBaseScreenSize.equals(baseScreenSize)) {
            baseScreenSize = newBaseScreenSize;
        }
        double scale = DEFAULT_SCALE_FACTOR;
        if (goFullScreen) {
            double scaleW = fullScreenSize.getWidth() / baseScreenSize.getWidth();
            double scaleH = fullScreenSize.getHeight() * FULL_SCREEN_WITH_TITLE_BAR_FACTOR / baseScreenSize.getHeight();
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

            screenLabel.setIcon(new ImageIcon(dest));
            jFrame.setPreferredSize(isFullScreen ? fullScreenSize : baseScreenSize);
            jFrame.getJMenuBar().setVisible(!isFullScreen);
            if (!isFullScreen) {
                //TODO this breaks multi-monitor
                jFrame.setLocationRelativeTo(null); //center
            }
            jFrame.pack();
            LOG.info("Emulation Viewport size: " + d);
            LOG.info("Application size: " + jFrame.getSize());
        };
    }

    private Optional<File> loadFileDialog(Component parent, FileFilter filter) {
        Optional<File> res = Optional.empty();
        JFileChooser fileChooser = new JFileChooser(FileLoader.basePath);
        fileChooser.setFileFilter(filter);
        int result = fileChooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            res = Optional.ofNullable(fileChooser.getSelectedFile());
        }
        return res;
    }

    private Optional<File> loadRomDialog(Component parent) {
        return loadFileDialog(parent, ROM_FILTER);
    }

    private Optional<File> loadStateFileDialog(Component parent) {
        return loadFileDialog(parent, SAVE_STATE_FILTER);
    }

    private void handleLoadState() {
        Optional<File> optFile = loadStateFileDialog(jFrame);
        if (optFile.isPresent()) {
            Path file = optFile.get().toPath();
            mainEmu.handleLoadState(file);
        }
    }

    private void handleQuickLoadState() {
        Path file = Paths.get(".", QUICK_SAVE_FILENAME);
        mainEmu.handleLoadState(file);
    }

    private void handleQuickSaveState() {
        Path p = Paths.get(".", QUICK_SAVE_FILENAME);
        mainEmu.handleSaveState(p);
    }

    private void handleSaveState() {
        try {
            Path p = Files.createTempFile("save_", ".gs0");
            mainEmu.handleSaveState(p);
        } catch (IOException e) {
            LOG.error("Unable to create save state file", e);
        }
    }

    private void handleNewRom() {
        mainEmu.handleCloseRom();
        Optional<File> optFile = loadRomDialog(jFrame);
        if (optFile.isPresent()) {
            Path file = optFile.get().toPath();
            mainEmu.handleNewRom(file);
        }
    }

    private void setupFrameKeyListener() {
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
                        loadRomDialog(jFrame);
                        break;
                }
            }
        });
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
