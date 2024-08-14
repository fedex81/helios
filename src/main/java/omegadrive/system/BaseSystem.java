/*
 * BaseSystem
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 15:21
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

package omegadrive.system;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.SystemLoader.SystemType;
import omegadrive.UserConfigHolder;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.cart.CartridgeInfoProvider;
import omegadrive.input.InputProvider;
import omegadrive.input.KeyboardInput;
import omegadrive.joypad.JoypadProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.system.SysUtil.RomSpec;
import omegadrive.system.perf.Telemetry;
import omegadrive.ui.DisplayWindow;
import omegadrive.ui.PrefStore;
import omegadrive.util.*;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport.VdpEventListener;
import omegadrive.vdp.model.BaseVdpProvider;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static omegadrive.system.SystemProvider.RomContext.NO_ROM;

public abstract class BaseSystem<BUS extends BaseBusProvider> implements
        SystemProvider, SystemProvider.NewFrameListener, SystemProvider.SystemClock {

    private final static Logger LOG = LogHelper.getLogger(BaseSystem.class.getSimpleName());

    static final long MAX_DRIFT_NS = Duration.ofMillis(10).toNanos();
    private static final long DRIFT_THRESHOLD_NS = Util.MILLI_IN_NS / 10;

    protected IMemoryProvider memory;
    protected BaseVdpProvider vdp;
    protected JoypadProvider joypad;
    protected SoundProvider sound;
    protected InputProvider inputProvider;
    protected BUS bus;
    protected SystemType systemType;
    protected RomContext romContext = NO_ROM;
    protected Future<Void> runningRomFuture;
    protected DisplayWindow display;

    protected DisplayWindow.DisplayContext displayContext;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    protected volatile boolean saveStateFlag = false;
    protected volatile BaseStateHandler stateHandler;

    private boolean vdpDumpScreenData = false;
    private volatile boolean pauseFlag = false;
    protected volatile boolean futureDoneFlag = false;
    protected volatile boolean softReset = false;
    private boolean soundEnFlag = true;

    protected int cycleCounter = 1;

    //frame pacing stuff
    protected final Telemetry telemetry = Telemetry.getInstance();
    public static final boolean fullThrottle;
    protected long elapsedWaitNs, frameProcessingDelayNs;
    protected long targetNs, startNs = 0;
    private long driftNs = 0;

    private final CyclicBarrier pauseBarrier = new CyclicBarrier(2);

    static {
        fullThrottle = Boolean.parseBoolean(java.lang.System.getProperty("helios.fullSpeed", "false"));
    }

    protected abstract void loop();

    @Override
    public void init() {
        sound = AbstractSoundManager.createSoundProvider(systemType);
        displayContext = new DisplayWindow.DisplayContext();
        displayContext.megaCdLedState = Optional.empty();
        displayContext.videoMode = VideoMode.PAL_H40_V30;
    }

    protected void initAfterRomLoad() {
        sound.init(romContext.region);
    }

    protected abstract void resetCycleCounters(int counter);

    protected abstract void updateVideoMode(boolean force);

    protected BaseSystem(DisplayWindow emuFrame) {
        this.display = emuFrame;
    }

    @Override
    public void handleSystemEvent(SystemEvent event, Object parameter) {
        LOG.info("Event: {}, with parameter: {}", event, parameter);
        switch (event) {
            case NEW_ROM:
                handleNewRom((RomSpec) parameter);
                break;
            case CLOSE_ROM:
                handleCloseRom();
                break;
            case LOAD_STATE:
            case QUICK_LOAD:
                handleLoadState((Path) parameter);
                break;
            case SAVE_STATE:
            case QUICK_SAVE:
                handleSaveState((Path) parameter);
                break;
            case TOGGLE_FULL_SCREEN:
                display.setFullScreen((Boolean) parameter);
                break;
            case TOGGLE_PAUSE:
                handlePause();
                break;
            case SOUND_ENABLED:
                soundEnFlag = (boolean) parameter;
                Optional.ofNullable(sound).ifPresent(s -> s.setEnabled(soundEnFlag));
                break;
            case TOGGLE_SOUND_RECORD:
                sound.setRecording(!sound.isRecording());
                break;
            case CLOSE_APP:
                handleCloseApp();
                break;
            case CONTROLLER_CHANGE:
                String[] s = parameter.toString().split(":");
                inputProvider.setPlayerController(InputProvider.PlayerNumber.valueOf(s[0]), s[1]);
                break;
            case SOFT_RESET:
                softReset = true;
                break;
            case PAD_SETUP_CHANGE:
            case FORCE_PAD_TYPE:
                UserConfigHolder.addUserConfig(event, parameter);
                break;
            default:
                LOG.warn("Unable to handle event: {}, with parameter: {}", event, parameter);
                break;
        }
    }

    protected void handleSoftReset() {
        if (softReset) {
            LOG.info("Soft Reset");
        }
        softReset = false;
    }

    protected void reloadWindowState() {
        display.addKeyListener(KeyboardInput.createKeyAdapter(systemType, joypad));
        display.reloadControllers(inputProvider.getAvailableControllers());
    }

    public void handleNewRom(RomSpec romSpec) {
        init();
        Runnable runnable = new RomRunnable(romSpec);
        PrefStore.addRecentFile(romSpec.toString());
        runningRomFuture = executorService.submit(runnable, null);
    }

    private void handleCloseApp() {
        try {
            handleCloseRom();
            display.close();
            sound.close();
            Util.executorService.shutdown();
            Util.executorService.awaitTermination(1, TimeUnit.SECONDS);
            PrefStore.close();
        } catch (Exception e) {
            LOG.error("Error while closing app", e);
        }
    }

    protected BaseStateHandler createStateHandler(Path file, BaseStateHandler.Type type) {
        return BaseStateHandler.createInstance(systemType, file, type, bus.getAllDevices(Device.class));
    }

    private void handleLoadState(Path file) {
        stateHandler = createStateHandler(file, BaseStateHandler.Type.LOAD);
        LOG.info("Savestate action detected: {} , using file: {}",
                stateHandler.getType(), stateHandler.getFileName());
        this.saveStateFlag = true;
    }

    private void handleSaveState(Path file) {
        stateHandler = createStateHandler(file, BaseStateHandler.Type.SAVE);
        LOG.info("Savestate action detected: {} , using file: {}",
                stateHandler.getType(), stateHandler.getFileName());
        this.saveStateFlag = true;
    }

    protected void processSaveState() {
        if (saveStateFlag) {
            stateHandler.processState();
            if (stateHandler.getType() == BaseStateHandler.Type.SAVE) {
                stateHandler.storeData();
            } else {
                sound.getPsg().reset();
            }
            stateHandler = BaseStateHandler.EMPTY_STATE;
            saveStateFlag = false;
        }
    }

    protected void handleCloseRom() {
        handleRomInternal();
    }

    @Override
    public boolean isRomRunning() {
        return runningRomFuture != null && !runningRomFuture.isDone();
    }

    protected void pauseAndWait() {
        if (!pauseFlag) {
            return;
        }
        LOG.info("Pause start: {}", pauseFlag);
        try {
            Util.waitOnBarrier(pauseBarrier);
            LOG.info("Pause end: {}", pauseFlag);
        } finally {
            pauseBarrier.reset();
        }
    }

    protected void getStats(long nowNs, long prevStartNs) {
        if (!SystemLoader.showFps) {
            return;
        }
        telemetry.newFrame(nowNs - prevStartNs, driftNs).ifPresent(statsConsumer);
    }

    protected long syncCycle(long startCycle) {
        long now = System.nanoTime();
        if (fullThrottle) {
            return now;
        }
        long driftDeltaNs = 0;
        if (Math.abs(driftNs) > DRIFT_THRESHOLD_NS) {
            driftDeltaNs = driftNs > 0 ? DRIFT_THRESHOLD_NS : -DRIFT_THRESHOLD_NS;
            driftNs -= driftDeltaNs;
        }
        long baseRemainingNs = startCycle + targetNs + driftDeltaNs;
        long remainingNs = baseRemainingNs - now;
        if (remainingNs > 0) { //too fast
            Sleeper.parkFuzzy(remainingNs);
            remainingNs = baseRemainingNs - System.nanoTime();
        }
        driftNs += remainingNs;
        driftNs = Math.min(MAX_DRIFT_NS, driftNs);
        driftNs = Math.max(-MAX_DRIFT_NS, driftNs);
        return System.nanoTime();
    }

    private void handleRomInternal() {
        if (pauseFlag) {
            handlePause();
        }
        if (isRomRunning()) {
            futureDoneFlag = true;
            runningRomFuture.cancel(true);
            while (isRomRunning()) {
                Util.sleep(100);
            }
            LOG.info("Rom thread cancel: {}", romContext.romSpec);
            display.resetScreen();
            sound.reset();
            bus.closeRom();
            telemetry.reset();
            Optional.ofNullable(vdp).ifPresent(Device::reset);
            cycleCounter = 1;
        }
    }

    protected void createAndAddVdpEventListener() {
        vdp.addVdpEventListener(new VdpEventListener() {
            @Override
            public void onNewFrame() {
                newFrame();
            }

            @Override
            public int order() {
                return 100;
            }
        });
    }

    @Override
    public void newFrame() {
        long startWaitNs = System.nanoTime();
        long prevStartNs = startNs;
        elapsedWaitNs = syncCycle(startNs) - startWaitNs;
        startNs = System.nanoTime();
        updateVideoMode(false);
        getStats(startNs, prevStartNs);
        doRendering(vdp.getScreenDataLinear());
        frameProcessingDelayNs = startNs - startWaitNs - elapsedWaitNs;
        handleVdpDumpScreenData();
        processSaveState();
        pauseAndWait();
        resetCycleCounters(cycleCounter);
        cycleCounter = 0;
        futureDoneFlag = runningRomFuture.isDone();
        handleSoftReset();
        inputProvider.handleEvents();
//        LOG.info("Frame: {}", getFrameCounter());
//        LOG.info("{}, {}", elapsedWaitNs, frameProcessingDelayNs);
    }

    final Consumer<String> statsConsumer = st -> displayContext.label = Optional.of(st);

    class RomRunnable implements Runnable {
        private final RomSpec romSpec;
        private static final String threadNamePrefix = "cycle-";

        public RomRunnable(RomSpec romSpec) {
            assert romSpec != RomSpec.NO_ROM;
            this.romSpec = romSpec;
        }

        @Override
        public void run() {
            try {
                assert romSpec.systemType != SystemType.NONE ? romSpec.systemType == getSystemType() : true;
                byte[] data = FileUtil.readBinaryFile(romSpec.file, getSystemType());
                if (data.length == 0) {
                    LOG.error("Unable to open/access file: {}", romSpec);
                    return;
                }
                memory.setRomData(data);
                romContext = createRomContext(romSpec);
                String romName = FileUtil.getFileName(romSpec.file);
                display.setRomData(romContext);
                Thread.currentThread().setName(threadNamePrefix + romName);
                Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 1);
                LOG.info("Running rom: {},\n{}", romName, romContext);
                initAfterRomLoad();
                sound.setEnabled(soundEnFlag);
                LOG.info("Starting game loop");
                loop();
                LOG.info("Exiting rom thread loop");
            } catch (Exception | Error e) {
                e.printStackTrace();
                LOG.error("Error", e);
            }
            handleCloseRom();
        }
    }

    protected RomContext createRomContext(RomSpec rom) {
        RomContext rc = new RomContext(rom);
        rc.cartridgeInfoProvider = CartridgeInfoProvider.createInstance(memory, rom.file);
        rc.region = RegionDetector.selectRegion(display, rc.cartridgeInfoProvider);
        return rc;
    }

    protected void handleVdpDumpScreenData() {
        if (vdpDumpScreenData) {
            vdp.dumpScreenData();
            vdpDumpScreenData = false;
        }
    }

    protected void doRendering(int[] data) {
        displayContext.data = data;
        display.renderScreenLinear(displayContext);
    }

    private void handlePause() {
        boolean wasPausing = pauseFlag;
        pauseFlag = !wasPausing;
        sound.setEnabled(wasPausing);
        LOG.info("Pausing: {}, soundEn: {}", pauseFlag, wasPausing);
        if (wasPausing) {
            Util.waitOnBarrier(pauseBarrier);
        }
    }

    @Override
    public void reset() {
        handleCloseRom();
        handleNewRom(romContext.romSpec);
    }

    protected void resetAfterRomLoad() {
        vdp.setRegion(romContext.region);
        //detect ROM first
        joypad.init();
        vdp.init();
        bus.init();
        futureDoneFlag = false;
    }

    @Override
    public final SystemType getSystemType() {
        return systemType;
    }

    @Override
    public RomContext getRomContext() {
        assert romContext != null;
        return romContext;
    }

    @Override
    public int getCycleCounter() {
        return cycleCounter;
    }

    @Override
    public long getFrameCounter() {
        return telemetry.getFrameCounter();
    }
}
