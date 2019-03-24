package omegadrive;

import omegadrive.bus.BaseBusProvider;
import omegadrive.bus.gen.GenesisBus;
import omegadrive.input.InputProvider;
import omegadrive.input.KeyboardInput;
import omegadrive.joypad.JoypadProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.ui.EmuFrame;
import omegadrive.ui.GenesisWindow;
import omegadrive.util.FileLoader;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.gen.GenesisVdp;
import omegadrive.vdp.gen.GenesisVdpMemoryInterface;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80CoreWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Genesis emulator main class
 *
 * @author Federico Berti
 * <p>
 * MEMORY MAP:	https://en.wikibooks.org/wiki/Genesis_Programming
 */
public abstract class BaseSystem implements SystemProvider {

    private static Logger LOG = LogManager.getLogger(BaseSystem.class.getSimpleName());

    private static final String PROPERTIES_FILENAME = "./emu.properties";

    protected IMemoryProvider memory;
    protected BaseVdpProvider vdp;
    protected JoypadProvider joypad;
    protected SoundProvider sound;
    protected InputProvider inputProvider;

    protected RegionDetector.Region region = null;
    private String romName;

    protected Future<Void> runningRomFuture;
    private Path romFile;
    private GenesisWindow emuFrame;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static boolean verbose = false;
    public static boolean showFps = false;
    public static boolean headless = false;

    private boolean vdpDumpScreenData = false;
    private volatile boolean pauseFlag = false;

    private CyclicBarrier pauseBarrier = new CyclicBarrier(2);

    private static NumberFormat df = DecimalFormat.getInstance();

    static {
        df.setMinimumFractionDigits(3);
        df.setMinimumFractionDigits(3);
    }


    protected abstract void loop();

    protected abstract void initAfterRomLoad();

    protected abstract void saveStateAction(String fileName, boolean load, int[] data);

    protected abstract void processSaveState();

    protected abstract BaseBusProvider getBusProvider();

    protected abstract RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOverride);

    protected abstract FileFilter getRomFileFilter();

    protected static void loadProperties() throws Exception {
        try (
                FileReader reader = new FileReader(PROPERTIES_FILENAME)
        ) {
            System.getProperties().load(reader);
        } catch (Exception e) {
            LOG.error("Unable to load properties file: " + PROPERTIES_FILENAME);
        }
        System.getProperties().store(System.out, null);
        verbose = Boolean.valueOf(System.getProperty("emu.debug", "false"));
        showFps = Boolean.valueOf(System.getProperty("emu.fps", "false"));
        headless = Boolean.valueOf(System.getProperty("emu.headless", "false"));
    }

    protected BaseSystem(boolean isHeadless) throws InvocationTargetException, InterruptedException {
        Util.registerJmx(this);
        SwingUtilities.invokeAndWait(() -> createFrame(isHeadless));
        sound = SoundProvider.NO_SOUND;
    }

    protected static boolean isHeadless() {
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        return ge.isHeadlessInstance() || headless;
    }


    @Override
    public void setPlayers(int i) {
        inputProvider.setPlayers(i);
    }

    @Override
    public void setDebug(boolean value) {
        BaseSystem.verbose = value;
        GenesisVdp.verbose = value;
        GenesisVdpMemoryInterface.verbose = value;
        GenesisBus.verbose = value;
        MC68000Wrapper.verbose = value;
        Z80CoreWrapper.verbose = value;
    }

    // Create the frame on the event dispatching thread
    protected void createFrame(boolean isHeadless) {
        emuFrame = isHeadless ? GenesisWindow.HEADLESS_INSTANCE : new EmuFrame(this);
        emuFrame.init();

        emuFrame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                keyPressedHandler(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                keyReleasedHandler(e);
            }
        });
    }

    protected void reloadKeyListeners() {
        emuFrame.addKeyListener(KeyboardInput.createKeyAdapter(joypad));
    }

    public void handleNewRom(Path file) {
        init();
        this.romFile = file;
        Runnable runnable = new RomRunnable(file);
        runningRomFuture = executorService.submit(runnable, null);
    }

    public void handleCloseRom() {
        handleRomInternal();
    }

    @Override
    public void handleCloseApp() {
        handleCloseRom();
        sound.close();
    }

    @Override
    public void handleLoadState(Path file) {
        int[] data = FileLoader.readFileSafe(file);
        saveStateAction(file.getFileName().toString(), true, data);
    }

    @Override
    public void handleSaveState(Path file) {
        String fileName = file.toAbsolutePath().toString();
        saveStateAction(fileName, false, null);
    }

    private void handleRomInternal() {
        if (pauseFlag) {
            handlePause();
        }
        if (isRomRunning()) {
            runningRomFuture.cancel(true);
            while (isRomRunning()) {
                Util.sleep(100);
            }
            LOG.info("Rom thread cancel");
            emuFrame.resetScreen();
            sound.reset();
            getBusProvider().closeRom();
        }
    }

    @Override
    public boolean isRomRunning() {
        return runningRomFuture != null && !runningRomFuture.isDone();
    }

    @Override
    public boolean isSoundWorking() {
        return sound.isSoundWorking();
    }

    @Override
    public void toggleMute() {
        sound.setMute(!sound.isMute());
    }

    @Override
    public void toggleSoundRecord() {
        sound.setRecording(!sound.isRecording());
    }

    @Override
    public void setFullScreen(boolean value) {
        emuFrame.setFullScreen(value);
    }

    @Override
    public RegionDetector.Region getRegion() {
        return region;
    }

    @Override
    public String getRomName() {
        return romName;
    }

    class RomRunnable implements Runnable {
        private Path file;
        private static final String threadNamePrefix = "cycle-";

        public RomRunnable(Path file) {
            this.file = file;
        }

        @Override
        public void run() {
            try {
                int[] data = FileLoader.loadBinaryFile(file, getRomFileFilter());
                if (data.length == 0) {
                    return;
                }
                memory.setRomData(data);
                romName = file.getFileName().toString();
                Thread.currentThread().setName(threadNamePrefix + romName);
                emuFrame.setTitle(romName);
                region = getRegionInternal(memory, emuFrame.getRegionOverride());
                LOG.info("Running rom: " + romName + ", region: " + region);
                initAfterRomLoad();
                loop();
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error(e);
            }
            handleCloseRom();
        }
    }


    protected VideoMode videoMode = VideoMode.PAL_H40_V30;
    protected long targetNs;


    protected void pauseAndWait() {
        if (!pauseFlag) {
            return;
        }
        LOG.info("Pause: " + pauseFlag);
        try {
            Util.waitOnBarrier(pauseBarrier);
            LOG.info("Pause: " + pauseFlag);
        } finally {
            pauseBarrier.reset();
        }
    }

    protected long syncCycle(long startCycle) {
        return Util.parkUntil(startCycle + targetNs);
    }

    int points = 0;
    long startNs = 0;
    long lastFps = 0;

    protected String getStats(long nowNs) {
        if (!showFps) {
            return "";
        }
        points++;
        if (points % 25 == 0) {
            lastFps = Util.SECOND_IN_NS / ((nowNs - startNs) / points);
            points = 0;
            startNs = nowNs;
        }
        return lastFps + "fps";
    }

    protected boolean canRenderScreen = false;
    int[][] vdpScreen = new int[0][];

    public void renderScreen(int[][] screenData) {
        if (screenData.length != vdpScreen.length) {
            vdpScreen = screenData.clone();
        }
        Util.arrayDataCopy(screenData, vdpScreen);
        canRenderScreen = true;
    }

    protected void handleVdpDumpScreenData() {
        if (vdpDumpScreenData) {
            vdp.dumpScreenData();
            vdpDumpScreenData = false;
        }
    }

    protected void renderScreenInternal(String label) {
        emuFrame.renderScreen(vdpScreen, label, videoMode);
    }

    private void keyPressedHandler(KeyEvent e) {
        keyHandler(e, true);
    }

    private void keyHandler(KeyEvent e, boolean pressed) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_B:
                if (!pressed) {
                    vdpDumpScreenData = !vdpDumpScreenData;
                }
                break;
            case KeyEvent.VK_P:
                if (!pressed) {
                    handlePause();
                }
                break;
            case KeyEvent.VK_ESCAPE:
                if (!pressed) {
                    handleCloseRom();
                }
                break;
        }
    }

    @Override
    public void handlePause() {
        boolean isPausing = pauseFlag;
        pauseFlag = !pauseFlag;
        if (isPausing) {
            Util.waitOnBarrier(pauseBarrier);
        }
    }

    @Override
    public void reset() {
        handleCloseRom();
        handleNewRom(romFile);
    }

    private void keyReleasedHandler(KeyEvent e) {
        keyHandler(e, false);
    }
}
