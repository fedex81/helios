package omegadrive;

import omegadrive.bus.BusProvider;
import omegadrive.bus.GenesisBus;
import omegadrive.input.InputProvider;
import omegadrive.joypad.GenesisJoypad;
import omegadrive.joypad.JoypadProvider;
import omegadrive.m68k.M68kProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.GenesisMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.GenesisStateHandler;
import omegadrive.savestate.GstStateHandler;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.javasound.JavaSoundManager;
import omegadrive.ui.EmuFrame;
import omegadrive.ui.GenesisWindow;
import omegadrive.util.FileLoader;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.vdp.GenesisVdp;
import omegadrive.vdp.GenesisVdpMemoryInterface;
import omegadrive.vdp.VdpProvider;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public class Genesis implements GenesisProvider {

    private static Logger LOG = LogManager.getLogger(Genesis.class.getSimpleName());

    private static final String PROPERTIES_FILENAME = "./emu.properties";

    protected MemoryProvider memory;
    protected VdpProvider vdp;
    protected BusProvider bus;
    protected Z80Provider z80;
    protected M68kProvider cpu;
    protected JoypadProvider joypad;
    protected SoundProvider sound;
    protected InputProvider inputProvider;

    private RegionDetector.Region region = null;
    private String romName;

    private Future<Void> runningRomFuture;
    private Path romFile;
    private GenesisWindow emuFrame;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static boolean verbose = false;
    public static boolean showFps = false;

    private boolean vdpDumpScreenData = false;
    private volatile boolean pauseFlag = false;
    private volatile boolean saveStateFlag = false;
    private volatile GenesisStateHandler stateHandler = GenesisStateHandler.EMPTY_STATE;
    private CyclicBarrier pauseBarrier = new CyclicBarrier(2);

    private static NumberFormat df = DecimalFormat.getInstance();

    static {
        df.setMinimumFractionDigits(3);
        df.setMinimumFractionDigits(3);
    }


    public static void main(String[] args) throws Exception {
        loadProperties();
        InputProvider.bootstrap();
        boolean isHeadless = isHeadless();
        LOG.info("Headless mode: " + isHeadless);
        Genesis genesis = new Genesis(isHeadless);
        if (args.length > 0) {
            //linux pulseaudio can crash if we start too quickly
            Util.sleep(250);
            String filePath = args[0];
            LOG.info("Launching file at: " + filePath);
            genesis.handleNewRom(Paths.get(filePath));
        }
        if (isHeadless) {
            Util.waitForever();
        }
    }

    private static void loadProperties() throws Exception {
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
    }

    public static GenesisProvider createInstance() {
        InputProvider.bootstrap();
        Genesis genesis = null;
        try {
            genesis = new Genesis(false);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return genesis;
    }

    public Genesis(boolean isHeadless) throws InvocationTargetException, InterruptedException {
        Util.registerJmx(this);
        SwingUtilities.invokeAndWait(() -> createFrame(isHeadless));
        sound = SoundProvider.NO_SOUND;
    }

    @Override
    public void init() {
        joypad = new GenesisJoypad();
        inputProvider = InputProvider.createInstance(joypad);

        memory = new GenesisMemoryProvider();
        bus = BusProvider.createBus();
        vdp = VdpProvider.createVdp(bus);
        cpu = new MC68000Wrapper(bus);
        z80 = Z80CoreWrapper.createInstance(bus);
        //sound attached later
        sound = SoundProvider.NO_SOUND;
        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(cpu).attachDevice(z80).attachDevice(sound);
    }

    private static boolean isHeadless() {
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        return ge.isHeadlessInstance();
    }


    @Override
    public void setPlayers(int i) {
        inputProvider.setPlayers(i);
    }

    @Override
    public void setDebug(boolean value) {
        Genesis.verbose = value;
        GenesisVdp.verbose = value;
        GenesisVdpMemoryInterface.verbose = value;
        GenesisBus.verbose = value;
        MC68000Wrapper.verbose = value;
        Z80CoreWrapper.verbose = value;
    }

    // Create the frame on the event dispatching thread
    private void createFrame(boolean isHeadless) {
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

    private void saveStateAction(String fileName, boolean load, int[] data) {
        stateHandler = load ? GstStateHandler.createLoadInstance(fileName, data) : GstStateHandler.createSaveInstance(fileName);
        if (stateHandler == GenesisStateHandler.EMPTY_STATE) {
            return;
        }
        LOG.info("Save state action detected: {}, using file: ", stateHandler.getType(), fileName);
        this.saveStateFlag = true;
    }

    private void processSaveState() {
        if (saveStateFlag) {
            if (stateHandler.getType() == GenesisStateHandler.Type.LOAD) {
                stateHandler.loadFmState(sound.getFm());
                stateHandler.loadVdpState(vdp);
                stateHandler.loadZ80(z80);
                stateHandler.load68k((MC68000Wrapper) cpu, bus.getMemory());
                bus.reset();
                LOG.info("Savestate loaded from: " + stateHandler.getFileName());
            } else {
                stateHandler.saveFm(sound.getFm());
                stateHandler.saveZ80(z80);
                stateHandler.save68k((MC68000Wrapper) cpu, bus.getMemory());
                stateHandler.saveVdp(vdp);
                int[] data = stateHandler.getData();
                try {
                    FileLoader.writeFile(Paths.get(stateHandler.getFileName()), data);
                    LOG.info("Savestate persisted to: " + stateHandler.getFileName());
                } catch (IOException e) {
                    LOG.error("Unable to write to file: " + stateHandler.getFileName(), e);
                }
            }
            stateHandler = GenesisStateHandler.EMPTY_STATE;
            saveStateFlag = false;
        }
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
            bus.closeRom();
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
                int[] data = FileLoader.loadBinaryFile(file);
                if (data.length == 0) {
                    return;
                }
                memory.setCartridge(data);
                romName = file.getFileName().toString();
                Thread.currentThread().setName(threadNamePrefix + romName);
                emuFrame.setTitle(romName);
                region = getRegionInternal(memory);
                LOG.info("Running rom: " + romName + ", region: " + region);
                sound = JavaSoundManager.createSoundProvider(region);
                bus.attachDevice(sound);

                resetAfterRomLoad();

                loop();
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error(e);
            }
            handleCloseRom();
        }
    }

    protected void resetAfterRomLoad() {
        //detect ROM first
        bus.reset();
        cpu.reset();
        cpu.initialize();
        joypad.initialize();
        vdp.init();
        z80.reset();
        z80.initialize();
    }


    private RegionDetector.Region getRegionInternal(MemoryProvider memory) {
        RegionDetector.Region romRegion = RegionDetector.detectRegion(memory);
        String regionOvr = emuFrame.getRegionOverride();
        RegionDetector.Region ovrRegion = RegionDetector.getRegion(regionOvr);
        if (ovrRegion != null && ovrRegion != romRegion) {
            LOG.info("Setting region override from: " + romRegion + " to " + ovrRegion);
            romRegion = ovrRegion;
        }
        return romRegion;
    }

    private static int VDP_CYCLE_SCALE = 4; //slot resolution = 13.30/4 = 3.325 Mhz PAL
    private static int VDP_DIVIDER = 2;  //3.325 Mhz PAL
    private static int M68K_DIVIDER = 1; //7.6 Mhz PAL  0.5714
    private static int Z80_DIVIDER = 2; //3.546 Mhz PAL 0.2666

    private static long nsToMillis = 1_000_000;
    private long oneScanlineCounter = VDP_DIVIDER * VdpProvider.H40_SLOTS;
    private long targetNs;

    void loop() {
        LOG.info("Starting game loop");

        int counter = 1;
        long start = System.currentTimeMillis() - 1;
        long startCycle = System.nanoTime();
        long lastRender = start;
        targetNs = (long) (region.getFrameIntervalMs() * nsToMillis);

        do {
            try {
                run68k(counter);
                runZ80(counter);
                runVdp(counter);
                if (canRenderScreen) {
                    long now = System.currentTimeMillis();
                    renderScreenInternal(getStats(now, lastRender, counter));
                    handleVdpDumpScreenData();
                    updateScanlineCounter();
                    canRenderScreen = false;
                    syncCycle(startCycle);
                    if (Thread.currentThread().isInterrupted()) {
                        LOG.info("Game thread stopped");
                        break;
                    }
                    sound.output(0);

                    processSaveState();
                    pauseAndWait();
                    lastRender = now;

                    startCycle = System.nanoTime();
                    nextZ80Cycle -= counter;
                    next68kCycle -= counter;
                    nextVdpCycle -= counter;
                    counter = 0;

                }
                counter++;
            } catch (Exception e) {
                LOG.error("Error main cycle", e);
                break;
            }
        } while (!runningRomFuture.isDone());
        LOG.info("Exiting rom thread loop");
    }

    private void updateScanlineCounter() {
        int newVal = VDP_DIVIDER * (vdp.getVideoMode().isH32() ? VdpProvider.H32_SLOTS : VdpProvider.H40_SLOTS);
        if (newVal != oneScanlineCounter) {
            LOG.debug("Scanline counter has changed from: {} to: {}", oneScanlineCounter, newVal);
            oneScanlineCounter = newVal;
        }
    }

    private void pauseAndWait() {
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

    private void syncSound(long counter) {
        if (counter % oneScanlineCounter == 0) {
            sound.updateElapsedMicros(64);
//            LOG.info("Update sound {}", vdp.getVdpStateString());
        }
    }

    private void syncCycle(long startCycle) {
        long elapsedNs = System.nanoTime() - startCycle;
        if (targetNs - elapsedNs > nsToMillis) {
            Util.sleep(((targetNs - elapsedNs) / nsToMillis));
        }
    }

    int next68kCycle = M68K_DIVIDER;
    int nextZ80Cycle = Z80_DIVIDER;
    int nextVdpCycle = VDP_DIVIDER;

    private void runVdp(long counter) {
        if (counter == nextVdpCycle) {
            vdp.run(1);
            syncSound(counter);
            nextVdpCycle += VDP_DIVIDER;
        }
    }

    //TODO fifo full shoud not stop 68k
    //TODO fifo full and 68k uses vdp -> stop 68k
    //TODO check: interrupt shouldnt be processed when 68k is frozen but are
    //TODO prcessed when 68k is stopped
    private void run68k(long counter) {
        if (counter == next68kCycle) {
            boolean isFrozen = bus.shouldStop68k();
            boolean canRun = !cpu.isStopped() && !isFrozen;
            int cycleDelay = 1;
            if (canRun) {
                cycleDelay = cpu.runInstruction();
            }
            //interrupts are processed after the current instruction
            if (!isFrozen) {
                bus.handleVdpInterrupts68k();
            }
            cycleDelay = Math.max(1, cycleDelay);
            next68kCycle += M68K_DIVIDER * cycleDelay;
        }
    }

    private void runZ80(long counter) {
        if (counter == nextZ80Cycle) {
            int cycleDelay = z80.executeInstruction();
            //when halted it can still process interrupts
            if (cycleDelay >= 0 || z80.isHalted()) {
                bus.handleVdpInterruptsZ80();
            }
            cycleDelay = Math.max(1, cycleDelay);
            nextZ80Cycle += Z80_DIVIDER * cycleDelay;
        }
    }

    private String getStats(long now, long lastRender, long counter) {
        if (!showFps) {
            return "";
        }
        long fps = 1000 / (now - lastRender + 1);
        double intvSec = (now - lastRender) / 1000d;
//        String cps = df.format((counter / intvSec) / 1000000d);
//        String s = cps + "Mcps, " + fps + "fps";
        return fps + "fps";
    }

    private boolean canRenderScreen = false;
    int[][] vdpScreen = new int[0][];

    public void renderScreen(int[][] screenData) {
        if (screenData.length != vdpScreen.length) {
            vdpScreen = screenData.clone();
        }
        Util.arrayDataCopy(screenData, vdpScreen);
        canRenderScreen = true;
    }

    private void handleVdpDumpScreenData() {
        if (vdpDumpScreenData) {
            vdp.dumpScreenData();
            vdpDumpScreenData = false;
        }
    }

    private void renderScreenInternal(String label) {
        emuFrame.renderScreen(vdpScreen, label, vdp.getVideoMode());
    }

    private void keyPressedHandler(KeyEvent e) {
        keyHandler(e, true);
    }

    private void keyHandler(KeyEvent e, boolean pressed) {
        int val = pressed ? 0 : 1;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                bus.getJoypad().setU(val);
                break;
            case KeyEvent.VK_LEFT:
                bus.getJoypad().setL(val);
                break;
            case KeyEvent.VK_RIGHT:
                bus.getJoypad().setR(val);
                break;
            case KeyEvent.VK_DOWN:
                bus.getJoypad().setD(val);
                break;
            case KeyEvent.VK_ENTER:
                bus.getJoypad().setS(val);
                break;
            case KeyEvent.VK_A:
                bus.getJoypad().setA(val);
                break;
            case KeyEvent.VK_S:
                bus.getJoypad().setB(val);
                break;
            case KeyEvent.VK_D:
                bus.getJoypad().setC(val);
                break;
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
