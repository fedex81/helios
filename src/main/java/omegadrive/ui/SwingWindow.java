/*
 * SwingWindow
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:55
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

package omegadrive.ui;

import com.google.common.base.Strings;
import omegadrive.SystemLoader;
import omegadrive.SystemLoader.SystemType;
import omegadrive.input.InputProvider;
import omegadrive.input.InputProvider.PlayerNumber;
import omegadrive.joypad.JoypadProvider.JoypadType;
import omegadrive.system.SysUtil.RomSpec;
import omegadrive.system.SystemProvider;
import omegadrive.ui.flatlaf.FlatLafHelper;
import omegadrive.ui.util.UiFileFilters;
import omegadrive.ui.util.UiFileFilters.FileResourceType;
import omegadrive.util.*;
import org.slf4j.Logger;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static omegadrive.system.SystemProvider.SystemEvent.*;
import static omegadrive.ui.PrefStore.getRomSpecFromRecentItem;
import static omegadrive.ui.util.UiFileFilters.FileResourceType.SAVE_STATE_RES;
import static omegadrive.util.FileUtil.QUICK_SAVE_PATH;
import static omegadrive.util.ScreenSizeHelper.*;

public class SwingWindow implements DisplayWindow {

    private static final Logger LOG = LogHelper.getLogger(SwingWindow.class.getSimpleName());

    private static final boolean UI_SCALE_ON_THREAD
            = Boolean.parseBoolean(System.getProperty("ui.scale.on.thread", "true"));
    private static final Path WINDOW_ICONS_PATH = Paths.get("./res", "icon");
    private static final Predicate<Path> ICONS_FILE_FILTER = (p) -> p.getFileName().toString().startsWith("helios") &&
            p.getFileName().toString().endsWith(".jpg");

    private Dimension fullScreenSize;
    private Dimension outputNonScaledScreenSize = DEFAULT_SCALED_SCREEN_SIZE;
    private Dimension outputScreenSize = DEFAULT_SCALED_SCREEN_SIZE;

    private BufferedImage dest;
    private int[] pixelsSrc;
    private int[] pixelsDest;
    private double scale = DEFAULT_SCALE_FACTOR;

    private final JLabel screenLabel = new JLabel();
    private final JLabel perfLabel = new JLabel("");

    private JFrame jFrame;
    private SystemProvider mainEmu;

    private List<AbstractButton> regionItems;
    private JCheckBoxMenuItem fullScreenItem, debugInfoItem, soundEnItem;
    private JMenu recentFilesMenu, joypadTypeMenu;
    private JMenuItem[] recentFilesItems;
    private final Map<PlayerNumber, JMenu> inputMenusMap;
    private final static int screenChangedCheckFrequency = 60;
    private List<AbstractButton> screenItems;
    private int screenChangedCheckCounter = screenChangedCheckFrequency;
    private Dimension nativeScreenSize = DEFAULT_BASE_SCREEN_SIZE;
    private final Map<SystemProvider.SystemEvent, AbstractAction> actionMap = new HashMap<>();

    private int showInfoCount = SHOW_INFO_FRAMES_DELAY;
    private Optional<String> actionInfo = Optional.empty();
    private MouseCursorHandler cursorHandler;
    private AWTEventListener awtEventListener;
    private final ExecutorService executorService;

    public SwingWindow(SystemProvider mainEmu) {
        this.mainEmu = mainEmu;
        this.inputMenusMap = new LinkedHashMap<>();
        Arrays.stream(PlayerNumber.values()).
                forEach(pn -> inputMenusMap.put(pn, new JMenu(pn.name())));
        executorService = UI_SCALE_ON_THREAD ?
                Executors.newSingleThreadExecutor(new PriorityThreadFactory("frameSubmitter")) :
                null;
    }

    public void setTitle(String title) {
        jFrame.setTitle(APP_NAME + mainEmu.getSystemType().getShortName() + " " + VERSION + " - " + title);
        reloadRecentFiles();
    }

    private void addKeyAction(AbstractButton component, SystemProvider.SystemEvent event, ActionListener l) {
        AbstractAction action = toAbstractAction(component.getText(), l);
        if (event != NONE) {
            action.putValue(Action.ACCELERATOR_KEY, KeyBindingsHandler.getInstance().getKeyStrokeForEvent(event));
            actionMap.put(event, action);
        }
        component.setAction(action);
    }

    private void addAction(AbstractButton component, ActionListener act) {
        addKeyAction(component, NONE, act);
    }

    private AbstractAction toAbstractAction(String name, ActionListener listener) {
        return new MyAbstractAction(name, listener);
    }

    private void showHelpMessage(String title, String msg) {
        JTextArea area = new JTextArea(msg);
        area.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setPreferredSize(jFrame.getPreferredSize());
        JOptionPane.showMessageDialog(this.jFrame,
                scrollPane, "Help: " + title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void fullScreenAction(ActionEvent doToggle) {
        if (doToggle == null) {
            setFullScreen(!fullScreenItem.getState());
        }
    }

    private GraphicsDevice getGraphicsDevice() {
        return jFrame.getGraphicsConfiguration().getDevice();
    }

    private BufferedImage createImage(GraphicsDevice gd, Dimension d) {
        BufferedImage bi = gd.getDefaultConfiguration().createCompatibleImage(d.width, d.height);
        if (bi.getType() != BufferedImage.TYPE_INT_RGB) {
            //mmh we need INT_RGB here
            bi = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
        }
        pixelsDest = ImageUtil.getPixels(bi);
        return bi;
    }

    private void showDebugInfo(ActionEvent event) {
        //key_accel has event == null -> toggle
        showDebugInfo((event == null) != debugInfoItem.getState());
    }

    private void showDebugInfo(boolean state) {
        SwingUtilities.invokeLater(() -> {
            debugInfoItem.setState(state);
            jFrame.getJMenuBar().setVisible(state);
            //always show the menu when windowed
            if (!fullScreenItem.getState()) {
                jFrame.getJMenuBar().setVisible(true);
            }
            perfLabel.setVisible(state);
            jFrame.repaint();
        });
    }

    public void resetScreen() {
        Util.sleep(250);
        SwingUtilities.invokeLater(() -> {
            Arrays.fill(pixelsDest, 0);
            screenLabel.invalidate();
            screenLabel.repaint();
            perfLabel.setText("");
            jFrame.setTitle(FRAME_TITLE_HEAD);
            cursorHandler.reset();
            LOG.info("Blanking screen");
        });
    }

    @Override
    public void setFullScreen(boolean value) {
        SwingUtilities.invokeLater(() -> {
            fullScreenItem.setState(value);
            LOG.info("Full screen: {}", fullScreenItem.isSelected());
        });
    }

    @Override
    public String getRegionOverride() {
        return regionItems.stream().filter(AbstractButton::isSelected).
                map(AbstractButton::getText).findFirst().orElse(null);
    }

    private Future<?> previousFrame = CompletableFuture.completedFuture(null);

    //NOTE: this will copy the input array
    @Override
    public void renderScreenLinear(int[] data, Optional<String> label, VideoMode videoMode) {
        if (data.length != pixelsSrc.length) {
            pixelsSrc = data.clone();
        }
        System.arraycopy(data, 0, pixelsSrc, 0, data.length);
        if (UI_SCALE_ON_THREAD) {
            assert checkSlowDown();
            previousFrame = executorService.submit(() -> renderScreenLinearInternal(pixelsSrc, label, videoMode));
        } else {
            renderScreenLinearInternal(pixelsSrc, label, videoMode);
        }
    }

    private boolean checkSlowDown() {
        if (!previousFrame.isDone()) {
            LOG.error("Slow frame!!");
        }
        return true;
    }

    public void init() {
        Util.registerJmx(this);
        GraphicsDevice gd = SwingScreenSupport.setupScreens();
        fullScreenSize = gd.getDefaultConfiguration().getBounds().getSize();
        LOG.info("Full screen size: {}", fullScreenSize);
        LOG.info("Emulation viewport size: {}", ScreenSizeHelper.DEFAULT_SCALED_SCREEN_SIZE);
        LOG.info("Application size: {}", DEFAULT_FRAME_SIZE);

        pixelsSrc = new int[0];
        dest = createImage(gd, outputNonScaledScreenSize);
        screenLabel.setIcon(new ImageIcon(dest));

        addDndListener(screenLabel);

        jFrame = new JFrame(FRAME_TITLE_HEAD, gd.getDefaultConfiguration());

        jFrame.getContentPane().setBackground(Color.BLACK);
        jFrame.getContentPane().setForeground(Color.BLACK);

        JMenuBar bar = new JMenuBar();

        JMenu menu = new JMenu("File");
        bar.add(menu);

        JMenu setting = new JMenu("Setting");
        bar.add(setting);

        JMenuItem pauseItem = new JMenuItem("Pause");
        addKeyAction(pauseItem, TOGGLE_PAUSE, e -> handleSystemEvent(TOGGLE_PAUSE, null, null));
        setting.add(pauseItem);

        JMenuItem resetItem = new JMenuItem("Hard Reset");
        addKeyAction(resetItem, RESET, e -> mainEmu.reset());
        setting.add(resetItem);

        JMenuItem softResetItem = new JMenuItem("Soft Reset");
        addKeyAction(softResetItem, SOFT_RESET, e -> handleSystemEvent(SOFT_RESET, null, null));
        setting.add(softResetItem);

        JMenu regionMenu = new JMenu("Region");
        setting.add(regionMenu);

        JMenu screensMenu = new JMenu("Screens");
        createAddScreenItems(screensMenu);
        setting.add(screensMenu);

        JMenu inputMenu = new JMenu("Input");
        reloadControllers(InputProvider.DEFAULT_CONTROLLERS);
        inputMenusMap.values().forEach(inputMenu::add);
        setting.add(inputMenu);

        joypadTypeMenu = new JMenu("Joypad Type");
        createAndAddJoypadTypes(joypadTypeMenu);
        setting.add(joypadTypeMenu);

        JMenu menuView = new JMenu("View");
        bar.add(menuView);

        regionItems = createRegionItems();
        regionItems.forEach(regionMenu::add);

        fullScreenItem = new JCheckBoxMenuItem("Full Screen", false);
        addKeyAction(fullScreenItem, TOGGLE_FULL_SCREEN, this::fullScreenAction);
        menuView.add(fullScreenItem);

        debugInfoItem = new JCheckBoxMenuItem("Debug Info", false);
        addKeyAction(debugInfoItem, SHOW_FPS, this::showDebugInfo);
        menuView.add(debugInfoItem);

        JMenu themeMenu = new JMenu("Themes");
        List<AbstractButton> themeItems = createThemeItems();
        themeItems.forEach(themeMenu::add);
        if (!themeItems.isEmpty()) {
            menuView.add(themeMenu);
        }

        soundEnItem = new JCheckBoxMenuItem("Enable Sound", true);
        addKeyAction(soundEnItem, SOUND_ENABLED, e -> handleSystemEvent(SOUND_ENABLED, soundEnItem.getState(), null));
        setting.add(soundEnItem);

        JMenu helpMenu = new JMenu("Help");
        bar.add(helpMenu);
        bar.add(Box.createHorizontalGlue());
        bar.add(perfLabel);

        JMenuItem loadRomItem = new JMenuItem("Load ROM");
        addKeyAction(loadRomItem, NEW_ROM, e -> handleNewRom());

        recentFilesMenu = new JMenu("Recent Files");
        recentFilesItems = new JMenuItem[PrefStore.recentFileTotal];
        IntStream.range(0, recentFilesItems.length).forEach(i -> {
            recentFilesItems[i] = new JMenuItem();
            addKeyAction(recentFilesItems[i], NONE, e -> handleNewRomFromRecent(recentFilesItems[i].getToolTipText()));
            recentFilesMenu.add(recentFilesItems[i]);
        });
        reloadRecentFiles();

        JMenuItem closeRomItem = new JMenuItem("Close ROM");
        addKeyAction(closeRomItem, CLOSE_ROM, e -> handleSystemEvent(CLOSE_ROM, null, null));

        JMenuItem loadStateItem = new JMenuItem("Load State");
        addKeyAction(loadStateItem, LOAD_STATE, e -> handleLoadState());

        JMenuItem saveStateItem = new JMenuItem("Save State");
        addKeyAction(saveStateItem, SAVE_STATE, e -> handleSaveState());

        JMenuItem quickSaveStateItem = new JMenuItem("Quick Save State");
        addKeyAction(quickSaveStateItem, QUICK_SAVE, e -> handleQuickSaveState());

        JMenuItem quickLoadStateItem = new JMenuItem("Quick Load State");
        addKeyAction(quickLoadStateItem, QUICK_LOAD, e -> handleQuickLoadState());

        JMenuItem exitItem = new JMenuItem("Exit");
        addKeyAction(exitItem, CLOSE_APP, e -> {
            handleSystemEvent(CLOSE_APP, null, null);
            System.exit(0);
        });

        JMenuItem aboutItem = new JMenuItem("About");
        addAction(aboutItem, e -> showHelpMessage(aboutItem.getText(), getAboutString()));

        JMenuItem creditsItem = new JMenuItem("Credits");
        addAction(creditsItem, e -> showHelpMessage(creditsItem.getText(),
                FileUtil.readFileContentAsString("CREDITS.md")));

        JMenuItem keyBindingsItem = new JMenuItem("Key Bindings");
        addAction(keyBindingsItem, e -> showHelpMessage(keyBindingsItem.getText(),
                KeyBindingsHandler.toConfigString()));

        JMenuItem readmeItem = new JMenuItem("Readme");
        addAction(readmeItem, e -> showHelpMessage(readmeItem.getText(),
                FileUtil.readFileContentAsString("README.md")));

        JMenuItem licenseItem = new JMenuItem("License");
        addAction(licenseItem, e -> showHelpMessage(licenseItem.getText(),
                FileUtil.readFileContentAsString("LICENSE.md")));

        JMenuItem historyItem = new JMenuItem("History");
        addAction(historyItem, e -> showHelpMessage(historyItem.getText(),
                FileUtil.readFileContentAsString("HISTORY.md")));

        menu.add(loadRomItem);
        menu.add(recentFilesMenu);
        menu.add(closeRomItem);
        menu.add(loadStateItem);
        menu.add(saveStateItem);
        menu.add(quickLoadStateItem);
        menu.add(quickSaveStateItem);
        menu.add(exitItem);
        helpMenu.add(aboutItem);
        helpMenu.add(keyBindingsItem);
        helpMenu.add(readmeItem);
        helpMenu.add(creditsItem);
        helpMenu.add(historyItem);
        helpMenu.add(licenseItem);

        screenLabel.setHorizontalAlignment(SwingConstants.CENTER);
        screenLabel.setVerticalAlignment(SwingConstants.CENTER);
        this.cursorHandler = new MouseCursorHandler(jFrame);
        setIcons(jFrame);

        jFrame.setMinimumSize(DEFAULT_FRAME_SIZE);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setResizable(true);
        jFrame.setJMenuBar(bar);
        jFrame.add(screenLabel, -1);

        jFrame.pack();
        SwingScreenSupport.centerWindow(jFrame);

        jFrame.setVisible(true);
        showDebugInfo(SystemLoader.showFps);
    }

    private void renderScreenLinearInternal(int[] data, Optional<String> label, VideoMode videoMode) {
        resizeScreen(videoMode);
        RenderingStrategy.renderNearest(data, pixelsDest, nativeScreenSize, outputScreenSize);
        label.ifPresent(this::showLabel);
        screenLabel.repaint();
        detectUserScreenChange();
        cursorHandler.newFrame();
    }

    private void detectUserScreenChange() {
        if (--screenChangedCheckCounter == 0) {
            screenChangedCheckCounter = screenChangedCheckFrequency;
            int prev = SwingScreenSupport.getCurrentScreen();
            int newScreen = SwingScreenSupport.detectUserScreenChange(jFrame.getGraphicsConfiguration().getDevice());
            if (prev != newScreen) {
                LOG.info("Detected user change, showing on screen: {}", newScreen);
                handleScreenChangeItems(screenItems, newScreen);
            }
        }
    }

    private void showLabel(String label) {
        showInfoCount--;
        if (actionInfo.isPresent()) {
            label += " - " + actionInfo.get();
        }
        if (!label.equalsIgnoreCase(perfLabel.getText())) {
            perfLabel.setText(label);
        }
        if (showInfoCount <= 0) {
            actionInfo = Optional.empty();
        }
    }

    private boolean resizeScreen(VideoMode videoMode) {
        boolean goFullScreen = fullScreenItem.getState();
        Dimension newBaseScreenSize = getScreenSize(videoMode, 1, false);
        if (!newBaseScreenSize.equals(nativeScreenSize)) {
            nativeScreenSize = newBaseScreenSize;
        }
        double scale = DEFAULT_SCALE_FACTOR;
        if (goFullScreen) {
            scale = ScreenSizeHelper.getFullScreenScaleFactor(fullScreenSize, nativeScreenSize);
        }
        return resizeScreenInternal(newBaseScreenSize, scale, goFullScreen);
    }

    private boolean resizeScreenInternal(Dimension newScreenSize, double scale, boolean isFullScreen) {
        boolean scaleChanged = this.scale != scale;
        boolean baseResize = !newScreenSize.equals(outputNonScaledScreenSize);
        if (baseResize || scaleChanged) {

            outputNonScaledScreenSize = newScreenSize;
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
            outputScreenSize = ScreenSizeHelper.getScreenSize(nativeScreenSize, scale, FIX_ASPECT_RATIO);
            dest = createImage(getGraphicsDevice(), outputScreenSize);
            screenLabel.setIcon(new ImageIcon(dest));
            jFrame.setPreferredSize(isFullScreen ? fullScreenSize : nativeScreenSize);
            jFrame.getJMenuBar().setVisible(!isFullScreen);
            jFrame.pack();
            SwingScreenSupport.centerWindow(jFrame);
            LOG.info("Emulation Viewport size: {}", outputScreenSize);
            LOG.info("Application size: {}", jFrame.getSize());
        };
    }

    private Optional<RomSpec> loadFileDialog(Component parent, FileResourceType type) {
        return fileDialog(parent, type, true);
    }

    private Optional<RomSpec> fileDialog(Component parent, FileResourceType type, boolean load) {
        int dialogType = load ? JFileChooser.OPEN_DIALOG : JFileChooser.SAVE_DIALOG;
        boolean isSaveState = type == SAVE_STATE_RES;
        String lastFileStr = isSaveState ? PrefStore.lastSaveFile : PrefStore.lastRomFile;
        File lastFile = new File(lastFileStr);
        JFileChooser fileChooser = new JFileChooser(lastFile);
        fileChooser.setDialogType(dialogType);
        FileFilter[] filters = UiFileFilters.getFilterSet(type).toArray(FileFilter[]::new);
        Arrays.stream(filters).forEach(fileChooser::addChoosableFileFilter);
        fileChooser.setFileFilter(filters[0]);
        if (lastFile.isFile()) {
            fileChooser.setSelectedFile(lastFile);
        }
        int result = fileChooser.showDialog(parent, null);

        SystemType systemType = SystemType.NONE;
        Optional<File> res = Optional.empty();
        if (result == JFileChooser.APPROVE_OPTION) {
            res = Optional.ofNullable(fileChooser.getSelectedFile());
            systemType = UiFileFilters.getSystemTypeFromFilterDesc(type,
                    fileChooser.getFileFilter().getDescription(), mainEmu.getSystemType());
        }
        final SystemType st = systemType;
        return res.map(f -> RomSpec.of(f, st));
    }

    @Override
    public void showInfo(String info) {
        actionInfo = Optional.of(info);
        showInfoCount = SHOW_INFO_FRAMES_DELAY;
    }

    private Optional<RomSpec> loadRomDialog(Component parent) {
        return loadFileDialog(parent, FileResourceType.ROM);
    }

    private Optional<File> loadStateFileDialog(Component parent) {
        return loadFileDialog(parent, SAVE_STATE_RES).map(r -> r.file.toFile());
    }

    private void handleLoadState() {
        Optional<File> optFile = loadStateFileDialog(jFrame);
        if (optFile.isPresent()) {
            Path file = optFile.get().toPath();
            handleSystemEvent(LOAD_STATE, file, file.getFileName().toString());
            PrefStore.lastSaveFile = file.toAbsolutePath().toString();
        }
    }

    private void handleQuickLoadState() {
        Path file = Paths.get(QUICK_SAVE_PATH, FileUtil.QUICK_SAVE_FILENAME);
        handleSystemEvent(QUICK_LOAD, file, file.getFileName().toString());
    }

    private void handleQuickSaveState() {
        Path p = Paths.get(QUICK_SAVE_PATH, FileUtil.QUICK_SAVE_FILENAME);
        handleSystemEvent(QUICK_SAVE, p, p.getFileName().toString());
    }

    private void handleSystemEvent(SystemProvider.SystemEvent event, Object par, String msg) {
        mainEmu.handleSystemEvent(event, par);
        showInfo(event + (Strings.isNullOrEmpty(msg) ? "" : ": " + msg));
    }

    private void handleSaveState() {
        Optional<File> optFile = fileDialog(jFrame, SAVE_STATE_RES, false).map(r -> r.file.toFile());
        optFile.ifPresent(file -> handleSystemEvent(SAVE_STATE, file.toPath(), file.getName()));
    }

    private void handleNewRom() {
        handleSystemEvent(CLOSE_ROM, null, null);
        Optional<RomSpec> optFile = loadRomDialog(jFrame);
        if (optFile.isPresent()) {
            RomSpec romSpec = optFile.get();
            SystemLoader.getInstance().handleNewRomFile(romSpec);
            reloadRecentFiles();
            showInfo(NEW_ROM + ": " + romSpec);
            PrefStore.lastRomFile = romSpec.toString();
        }
    }

    private void handleNewRomFromRecent(String path) {
        RomSpec romSpec = getRomSpecFromRecentItem(path);
        showInfo(NEW_ROM + ": " + romSpec);
        SystemLoader.getInstance().handleNewRomFile(romSpec);
        PrefStore.lastRomFile = romSpec.toString();
    }

    private void addDndListener(Component component) {
        component.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(data instanceof List<?> droppedFiles)) {
                        return;
                    }
                    if (droppedFiles.isEmpty()) {
                        return;
                    }
                    Object firstElement = droppedFiles.get(0);
                    if (!(firstElement instanceof File file)) {
                        return;
                    }
                    Path path = file.toPath();
                    if (UiFileFilters.ROM_FILTER.accept(file)) {
                        SystemLoader.getInstance().handleNewRomFile(RomSpec.of(path));
                        reloadRecentFiles();
                        showInfo(NEW_ROM + ": " + path.getFileName());
                    } else if (UiFileFilters.SAVE_STATE_FILTER.accept(file)) {
                        handleSystemEvent(LOAD_STATE, path, path.getFileName().toString());
                    }
                } catch (Exception ex) {
                    LOG.warn("Unable to process drag and drop event: {}", e);
                }
            }
        });
    }

    @Override
    public void reloadSystem(SystemProvider systemProvider) {
        Optional.ofNullable(mainEmu).ifPresent(sys -> sys.handleSystemEvent(CLOSE_ROM, null));
        this.mainEmu = systemProvider;

        Arrays.stream(jFrame.getKeyListeners()).forEach(jFrame::removeKeyListener);
        Optional.ofNullable(mainEmu).ifPresent(sp -> {
            setTitle("");
            boolean en = mainEmu.getSystemType() == SystemType.GENESIS ||
                    mainEmu.getSystemType() == SystemType.S32X;
            joypadTypeMenu.setEnabled(en);
            mainEmu.handleSystemEvent(SOUND_ENABLED, soundEnItem.getState());
        });
    }

    @Override
    public void addKeyListener(KeyListener keyAdapter) {
        if (awtEventListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(awtEventListener);
        }
        awtEventListener = e -> {
            if (e instanceof KeyEvent ke) {
                handleSystemInputEvent(keyAdapter, ke);
                handleUiEvent(ke);
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(awtEventListener, AWTEvent.KEY_EVENT_MASK);
    }

    private void handleSystemInputEvent(KeyListener keyAdapter, KeyEvent ke) {
        switch (ke.getID()) {
            case KeyEvent.KEY_PRESSED:
                keyAdapter.keyPressed(ke);
                break;
            case KeyEvent.KEY_RELEASED:
                keyAdapter.keyReleased(ke);
                break;
            case KeyEvent.KEY_TYPED:
                keyAdapter.keyTyped(ke);
                break;
            default:
                LOG.warn("Unknown key event: {}", ke);
                break;
        }
    }

    private void handleUiEvent(KeyEvent ke) {
        KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(ke);
        SystemProvider.SystemEvent event = KeyBindingsHandler.getInstance().getSystemEventIfAny(keyStroke);
        if (event != null && event != NONE) {
            //if the menuBar is visible it will handle the event, otherwise we need to perform the action here
            boolean menuVisible = jFrame.getJMenuBar().isVisible();
            if (!menuVisible) {
                Optional.ofNullable(actionMap.get(event)).ifPresent(act -> act.actionPerformed(null));
            }
        }
    }

    private List<AbstractButton> createRegionItems() {
        List<AbstractButton> l = new ArrayList<>();
        l.add(new JRadioButtonMenuItem("AutoDetect", true));
        Arrays.stream(RegionDetector.Region.values()).sorted().
                forEach(r -> l.add(new JRadioButtonMenuItem(r.name(), false)));
        ButtonGroup bg = new ButtonGroup();
        l.forEach(bg::add);
        return l;
    }

    private List<AbstractButton> createAddScreenItems(JMenu screensMenu) {
        List<String> l = SwingScreenSupport.detectScreens();
        screenItems = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            String s = l.get(i);
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(s, i == SwingScreenSupport.getCurrentScreen());
            screenItems.add(it);
            screensMenu.add(it);
        }
        for (int i = 0; i < screenItems.size(); i++) {
            final int num = i;
            addKeyAction(screenItems.get(i), NONE, e -> handleScreenChange(screenItems, num));
        }
        return screenItems;
    }

    private List<AbstractButton> createThemeItems() {
        List<AbstractButton> l = new ArrayList<>();
        FlatLafHelper.lafMap.keySet().
                forEach(r -> l.add(new JRadioButtonMenuItem(r, false)));
        ButtonGroup bg = new ButtonGroup();
        l.forEach(bg::add);
        for (int i = 0; i < l.size(); i++) {
            AbstractButton ab = l.get(i);
            final int idx = i;
            addKeyAction(ab, NONE, e -> setLaf(l, idx, false));
        }
        //init look and feel
        SwingUtilities.invokeLater(() -> setLaf(l, PrefStore.getSwingUiThemeIndex(), true));
        return l;
    }

    private void setLaf(List<AbstractButton> l, int idx, boolean initSelection) {
        if (l.isEmpty()) {
            LOG.warn("Empty list of lookAndFeels");
            return;
        }
        if (idx < 0 || idx >= l.size()) {
            LOG.warn("Unable to set index: {}, size: {}", idx, l.size());
            idx = 0;
        }
        final AbstractButton btn = l.get(idx);
        if (initSelection) {
            btn.setSelected(true);
        }
        FlatLafHelper.handleLafChange(btn.getText());
        PrefStore.setSwingUiThemeIndex(idx);
    }

    private void handleScreenChange(List<AbstractButton> items, int newScreen) {
        int cs = SwingScreenSupport.getCurrentScreen();
        if (cs != newScreen) {
            SwingScreenSupport.showOnScreen(newScreen, jFrame);
        }
        handleScreenChangeItems(items, newScreen);
    }

    private void handleScreenChangeItems(List<AbstractButton> items, int newScreen) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setSelected(i == newScreen);
        }
    }

    private void reloadRecentFiles() {
        List<String> l = PrefStore.getRecentFilesList();
        IntStream.range(0, recentFilesItems.length).forEach(i -> {
            String val = i < l.size() ? l.get(i) : "<none>";
            val = Strings.isNullOrEmpty(val) ? "<none>" : val;
            int idx = val.lastIndexOf(File.separatorChar);
            String text = i + ". " + (idx > 0 ? val.substring(idx + 1) : val);
            recentFilesItems[i].setVisible(true);
            recentFilesItems[i].setEnabled(!Strings.isNullOrEmpty(val));
            recentFilesItems[i].setText(text);
            recentFilesItems[i].setToolTipText(val);
        });
    }

    @Override
    public void reloadControllers(Collection<String> list) {
        for (Map.Entry<PlayerNumber, JMenu> entry : inputMenusMap.entrySet()) {
            PlayerNumber pn = entry.getKey();
            JMenu menu = entry.getValue();
            menu.removeAll();
            List<JRadioButtonMenuItem> l = new ArrayList<>();
            list.forEach(c -> {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(c,
                        InputProvider.KEYBOARD_CONTROLLER.equalsIgnoreCase(c));
                addAction(item, e -> handleSystemEvent(CONTROLLER_CHANGE, pn.name() + ":" + c,
                        pn.name() + ":" + c));
                l.add(item);

            });
            ButtonGroup bg = new ButtonGroup();
            l.forEach(bg::add);
            l.forEach(menu::add);
            //fudgePlayer1Using1stController
            if (list.size() > 2 && pn == PlayerNumber.P1) {
                LOG.info("Auto-selecting {} using Controller: {}", pn, l.get(2).getText());
                l.get(2).doClick();
            }
        }
    }

    private void createAndAddJoypadTypes(JMenu joypadTypeMenu) {
        for (PlayerNumber pn : PlayerNumber.values()) {
            JMenu pMenu = new JMenu(pn.name());
            ButtonGroup bg = new ButtonGroup();
            for (JoypadType type : JoypadType.values()) {
                if (type == JoypadType.BUTTON_2) {
                    continue;
                }
                JRadioButtonMenuItem it = new JRadioButtonMenuItem(type.name(), type == JoypadType.BUTTON_6);
                addAction(it, e -> handleSystemEvent(PAD_SETUP_CHANGE, pn.name() + ":" + type.name(),
                        pn.name() + ":" + type.name()));
                bg.add(it);
                pMenu.add(it);
            }
            joypadTypeMenu.add(pMenu);
        }
    }

    private void setIcons(Window w) {
        if (!WINDOW_ICONS_PATH.toFile().exists()) {
            LOG.warn("Unable to find icons at: {}", WINDOW_ICONS_PATH.toAbsolutePath());
            return;
        }
        try {
            List<Path> paths = Files.list(WINDOW_ICONS_PATH).filter(ICONS_FILE_FILTER).toList();
            LOG.info("Window icons found: {}", Arrays.toString(paths.toArray()));
            List<Image> l = paths.stream().map
                    (p -> new ImageIcon(p.toAbsolutePath().toString()).getImage()).collect(Collectors.toList());
            w.setIconImages(l);
        } catch (Exception e) {
            LOG.warn("Unable to load icons from: {}", WINDOW_ICONS_PATH.toAbsolutePath());
        }
    }

    @Override
    public void close() {
        Optional.ofNullable(executorService).ifPresent(ExecutorService::shutdownNow);
    }

    private static class MyAbstractAction extends AbstractAction {
        private final ActionListener listener;

        public MyAbstractAction(String name, ActionListener listener) {
            super(name);
            this.listener = listener;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            listener.actionPerformed(e);
        }

        @Override
        public void setEnabled(boolean newValue) {
            super.setEnabled(true);
        }
    }
}
