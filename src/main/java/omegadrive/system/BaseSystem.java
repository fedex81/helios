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
import omegadrive.bus.BaseBusProvider;
import omegadrive.input.InputProvider;
import omegadrive.input.KeyboardInput;
import omegadrive.joypad.JoypadProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.sound.SoundProvider;
import omegadrive.system.perf.Telemetry;
import omegadrive.ui.DisplayWindow;
import omegadrive.ui.PrefStore;
import omegadrive.util.FileLoader;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.BaseVdpProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class BaseSystem<BUS extends BaseBusProvider, STH extends BaseStateHandler> implements SystemProvider {

    private final static Logger LOG = LogManager.getLogger(BaseSystem.class.getSimpleName());

    static final long MAX_DRIFT_NS = Duration.ofMillis(10).toNanos();
    private static final long DRIFT_THRESHOLD_NS = Util.MILLI_IN_NS / 10;

    protected IMemoryProvider memory;
    protected BaseVdpProvider vdp;
    protected JoypadProvider joypad;
    protected SoundProvider sound;
    protected InputProvider inputProvider;
    protected BUS bus;

    protected RegionDetector.Region region = RegionDetector.Region.USA;
    protected VideoMode videoMode = VideoMode.PAL_H40_V30;
    private String romName;

    protected Future<Void> runningRomFuture;
    protected Path romFile;
    protected DisplayWindow emuFrame;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    protected volatile boolean saveStateFlag = false;
    protected volatile STH stateHandler;

    private boolean vdpDumpScreenData = false;
    private volatile boolean pauseFlag = false;
    protected volatile boolean futureDoneFlag = false;

    //frame pacing stuff
    protected Telemetry telemetry = Telemetry.getInstance();
    private static final boolean fullThrottle;
    protected long elapsedWaitNs, frameProcessingDelayNs, startCycle;
    protected long targetNs, startNs = 0;
    private long driftNs = 0;
    protected int counter = 1;
    private Optional<String> stats = Optional.empty();
    private double lastFps = 0;


    private CyclicBarrier pauseBarrier = new CyclicBarrier(2);

    static {
        fullThrottle = Boolean.valueOf(java.lang.System.getProperty("helios.fullSpeed", "false"));
    }

    protected abstract void loop();

    protected abstract void initAfterRomLoad();

    protected abstract void processSaveState();

    protected abstract void resetCycleCounters(int counter);

    protected abstract void updateVideoMode(boolean force);

    protected abstract RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOverride);

    protected BaseSystem(DisplayWindow emuFrame) {
        this.emuFrame = emuFrame;
    }

    protected abstract STH createStateHandler(Path file, BaseStateHandler.Type type);

    @Override
    public void handleSystemEvent(SystemEvent event, Object parameter) {
        LOG.info("Event: {}, with parameter: {}", event, Objects.toString(parameter));
        switch (event) {
            case NEW_ROM:
                handleNewRom((Path) parameter);
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
            case TOGGLE_DEBUG_LOGGING:
                setDebug((Boolean) parameter);
                break;
            case TOGGLE_FULL_SCREEN:
                emuFrame.setFullScreen((Boolean) parameter);
                break;
            case TOGGLE_PAUSE:
                handlePause();
                break;
            case TOGGLE_MUTE:
                sound.setEnabled(!sound.isMute());
                break;
            case TOGGLE_SOUND_RECORD:
                sound.setRecording(!sound.isRecording());
                break;
            case CLOSE_APP:
                handleCloseApp();
                break;
            case CONTROLLER_CHANGE:
                String str = parameter.toString();
                String[] s = str.split(":");
                inputProvider.setPlayerController(InputProvider.PlayerNumber.valueOf(s[0]), s[1]);
                break;
            default:
                LOG.warn("Unable to handle event: {}, with parameter: {}", event, Objects.toString(parameter));
                break;
        }
    }

    @Deprecated
    private void setDebug(boolean value) {
    }

    protected void reloadWindowState() {
        emuFrame.addKeyListener(KeyboardInput.createKeyAdapter(getSystemType(), joypad));
        emuFrame.reloadControllers(inputProvider.getAvailableControllers());
    }

    public void handleNewRom(Path file) {
        init();
        this.romFile = file;
        Runnable runnable = new RomRunnable(file);
        PrefStore.addRecentFile(file.toAbsolutePath().toString());
        runningRomFuture = executorService.submit(runnable, null);
    }

    private void handleCloseApp() {
        try {
            handleCloseRom();
            sound.close();
            PrefStore.close();
        } catch (Exception e) {
            LOG.error("Error while closing app", e);
        }
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

    protected void handleCloseRom() {
        handleRomInternal();
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
                int[] data = Util.toUnsignedIntArray(FileLoader.readBinaryFile(file, getSystemType()));
                if (data.length == 0) {
                    LOG.error("Unable to open/access file: {}", file.toAbsolutePath().toString());
                    return;
                }
                memory.setRomData(data);
                romName = file.getFileName().toString();
                Thread.currentThread().setName(threadNamePrefix + romName);
                Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 1);
                emuFrame.setTitle(romName);
                region = getRegionInternal(memory, emuFrame.getRegionOverride());
                LOG.info("Running rom: " + romName + ", region: " + region);
                initAfterRomLoad();
                loop();
            } catch (Exception | Error e) {
                e.printStackTrace();
                LOG.error(e);
            }
            handleCloseRom();
        }
    }

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

    protected final long syncCycle(long startCycle) {
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
            Util.park(remainingNs);
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
            runningRomFuture.cancel(true);
            while (isRomRunning()) {
                Util.sleep(100);
            }
            LOG.info("Rom thread cancel");
            emuFrame.resetScreen();
            sound.reset();
            bus.closeRom();
            telemetry.reset();
            Optional.ofNullable(vdp).ifPresent(Device::reset);
        }
    }

    protected void createAndAddVdpEventListener() {
        vdp.addVdpEventListener(new BaseVdpProvider.VdpEventListener() {
            @Override
            public void onNewFrame() {
                newFrame();
            }
        });
    }

    protected void newFrame() {
        long tstamp = System.nanoTime();
        updateVideoMode(false);
        renderScreenLinearInternal(vdp.getScreenDataLinear(), getStats(startCycle));
        handleVdpDumpScreenData();
        long startWaitNs = System.nanoTime();
        elapsedWaitNs = syncCycle(startCycle) - startWaitNs;
        processSaveState();
        pauseAndWait();
        resetCycleCounters(counter);
        counter = 0;
        startCycle = System.nanoTime();
        frameProcessingDelayNs = startCycle - tstamp - elapsedWaitNs;
        futureDoneFlag = runningRomFuture.isDone();
//        LOG.info("{}, {}", elapsedWaitNs, frameProcessingDelayNs);
    }

    protected Optional<String> getStats(long nowNs) {
        if (!SystemLoader.showFps) {
            return Optional.empty();
        }

        lastFps = (1.0 * Util.SECOND_IN_NS) / ((nowNs - startNs));
        telemetry.newFrame(lastFps, driftNs / 1000d).ifPresent(st -> stats = Optional.of(st));
        startNs = nowNs;
        return stats;
    }

    protected void handleVdpDumpScreenData() {
        if (vdpDumpScreenData) {
            vdp.dumpScreenData();
            vdpDumpScreenData = false;
        }
    }

    protected void renderScreenLinearInternal(int[] data, Optional<String> label) {
        emuFrame.renderScreenLinear(data, label, videoMode);
    }

    private void handlePause() {
        boolean isPausing = pauseFlag;
        pauseFlag = !pauseFlag;
        sound.setEnabled(pauseFlag);
        if (isPausing) {
            Util.waitOnBarrier(pauseBarrier);
        }
    }

    @Override
    public void reset() {
        handleCloseRom();
        handleNewRom(romFile);
    }

    protected void resetAfterRomLoad() {
        vdp.setRegion(region);
        //detect ROM first
        joypad.init();
        vdp.init();
        bus.init();
        futureDoneFlag = false;
    }
}
