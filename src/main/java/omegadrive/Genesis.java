package omegadrive;

import omegadrive.bus.BusProvider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.GenesisJoypad;
import omegadrive.joypad.JoypadProvider;
import omegadrive.m68k.M68kProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.GenesisMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.javasound.JavaSoundManager;
import omegadrive.ui.EmuFrame;
import omegadrive.ui.GenesisWindow;
import omegadrive.util.FileLoader;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.vdp.VdpProvider;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//	MEMORY MAP:	https://en.wikibooks.org/wiki/Genesis_Programming
public class Genesis implements GenesisProvider {

    private static Logger LOG = LogManager.getLogger(Genesis.class.getSimpleName());

    private static final String PROPERTIES_FILENAME = "./emu.properties";

    private MemoryProvider memory;
    private VdpProvider vdp;
    private BusProvider bus;
    private Z80Provider z80;
    private M68kProvider cpu;
    private JoypadProvider joypad;
    private SoundProvider sound;
    private InputProvider inputProvider;

    private RegionDetector.Region region = null;

    private Future<Void> runningGameFuture;
    private GenesisWindow emuFrame;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static boolean verbose = false;
    private static NumberFormat df = DecimalFormat.getInstance();

    static {
        df.setMinimumFractionDigits(3);
        df.setMinimumFractionDigits(3);
    }


    public static void main(String[] args) throws Exception {
        loadProperties();
        InputProvider.bootstrap();
        Genesis genesis = new Genesis();
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
    }

    public static GenesisProvider createInstance() {
        InputProvider.bootstrap();
        Genesis genesis = null;
        try {
            genesis = new Genesis();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return genesis;
    }

    private Genesis() throws InvocationTargetException, InterruptedException {
        Util.registerJmx(this);
        init();
        SwingUtilities.invokeAndWait(() -> createFrame());
    }

    @Override
    public void init() {
        bus = BusProvider.createBus();
        memory = new GenesisMemoryProvider();
        joypad = new GenesisJoypad();
        inputProvider = InputProvider.createInstance(joypad);

        vdp = VdpProvider.createVdp(bus);
        cpu = new MC68000Wrapper(bus);
        z80 = new Z80CoreWrapper(bus);
        //sound attached later
        sound = SoundProvider.NO_SOUND;
        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(cpu).attachDevice(z80).attachDevice(sound);
    }


    @Override
    public void setPlayers(int i) {
        inputProvider.setPlayers(i);
    }

    // Create the frame on the event dispatching thread
    private void createFrame() {
        emuFrame = new EmuFrame(this);
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

    public void handleNewGame(Path file) {
        GameRunnable runnable = new GameRunnable(file);
        runningGameFuture = executorService.submit(runnable, null);
    }

    public void handleNewGame() {
        handleCloseGame();
        Optional<File> optFile = FileLoader.openRomDialog();
        if (optFile.isPresent()) {
            Path file = optFile.get().toPath();
            handleNewGame(file);
        }
    }

    public void handleCloseGame() {
        handleCloseGameInternal();
        sound.reset();
    }

    private void handleCloseGameInternal() {
        if (isGameRunning()) {
            runningGameFuture.cancel(true);
            while (isGameRunning()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            LOG.info("Game stopped");
            emuFrame.resetScreen();
        }
    }

    @Override
    public boolean isGameRunning() {
        return runningGameFuture != null && !runningGameFuture.isDone();
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

    class GameRunnable implements Runnable {
        private Path file;
        private static final String threadNamePrefix = "cycle-";

        public GameRunnable(Path file) {
            this.file = file;
        }

        @Override
        public void run() {

            String fileName = file.toAbsolutePath().toString();
            try {
                if (fileName.toLowerCase().endsWith(".md")
                        || fileName.toLowerCase().endsWith(".bin")) {
                    int[] data = FileLoader.readFile(file);
                    if (data == null || data.length == 0) {
                        throw new RuntimeException("Empty file!");
                    }
                    memory.setCartridge(data);
                } else {
                    throw new RuntimeException("Unexpected file: " + fileName);
                }
            } catch (Exception e) {
                LOG.error("Unable to load: " + file.toAbsolutePath().toString(), e);
                return;
            }

            String rom = file.getFileName().toString();
            Thread.currentThread().setName(threadNamePrefix + rom);
            emuFrame.setTitle(rom);
            region = RegionDetector.detectRegion(memory);
            LOG.info("Running game: " + rom + ", region: " + region);
            sound = JavaSoundManager.createSoundProvider(region);
            bus.attachDevice(sound);

            //detect ROM first
            bus.reset();
            bus.setSsf2Mapper(GameQuirks.isSsf2Mapper(memory));
            cpu.reset();
            cpu.initialize();
            joypad.initialize();
            vdp.init();
            z80.reset();
            z80.initialize();

            loop();
            handleCloseGame();
        }
    }

    private static int CYCLES = 2;
    private static long nsToMillis = 1000000;

    void loop() {
        LOG.info("Starting game loop");

        long counter = 1;
        long start = System.currentTimeMillis() - 1;
        long startCycle = System.nanoTime();
        int targetFps = region.getFps();
        long lastRender = start;
        int fps = targetFps;
        for (; ; ) {
            try {
                run68k(counter);
                runZ80(counter);
                vdp.run(CYCLES);
                vdp.dmaFill();
                syncSound(counter);
                //            	vdp.dmaFill();
                if (canRenderScreen) {
                    long now = System.currentTimeMillis();
                    fps = Math.max((int) (1000d / (now - lastRender)), 1);
                    double intvSec = (lastRender - start) / 1000d;

                    renderScreenInternal(getStats(fps, intvSec, counter));
                    canRenderScreen = false;
                    syncCycle(startCycle, targetFps);
                    if (Thread.currentThread().isInterrupted()) {
                        LOG.info("Game thread stopped");
                        break;
                    }
                    sound.output(0);
                    lastRender = now;
                    startCycle = System.nanoTime();
                }
                counter++;
            } catch (Exception e) {
                LOG.error("Error main cycle", e);
                break;
            }
        }
    }

    private long latestNs = System.nanoTime();

    private void syncSound(long counter) {
        if (counter % 100 == 0) {
            long now = System.nanoTime();
            int elapsedMicros = (int) ((now - latestNs) / 1000);
            if (elapsedMicros > 0) {
                sound.updateElapsedMicros(elapsedMicros);
                latestNs = now;
            }
        }
    }

    private void syncCycle(long startCycle, int targetFps) {
        long targetNs = (long) ((1000d / targetFps) * nsToMillis);
        long elapsedNs = System.nanoTime() - startCycle;
        if (targetNs - elapsedNs > nsToMillis) {
            Util.sleep(((targetNs - elapsedNs) / nsToMillis));
        }
    }

    private void run68k(long counter) {
        //run half speed compared to VDP
        if (counter % 2 == 0) {
            if (!cpu.isStopped()) {
                if (!bus.checkInterrupts()) {
                    cpu.runInstruction();
                }
            }
        }
    }

    private void runZ80(long counter) {
        //run half speed compared to 68k
        if ((counter + 1) % 4 == 0) {
            z80.executeInstruction();
        }
    }


    private String getStats(int fps, double intvSec, long counter) {
        String cps = df.format((counter / intvSec) / 1000000d);
        String s = cps + "Mcps, " + fps + "fps";
        return s;
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
            case KeyEvent.VK_ESCAPE:
                if (pressed) {
                    handleCloseGame();
                }
                break;
        }
    }

    private void keyReleasedHandler(KeyEvent e) {
        keyHandler(e, false);
    }
}
