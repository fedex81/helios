package omegadrive;

import omegadrive.bus.BaseBusProvider;
import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.GenesisJoypad;
import omegadrive.m68k.M68kProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.GenesisStateHandler;
import omegadrive.savestate.GstStateHandler;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.javasound.JavaSoundManager;
import omegadrive.util.FileLoader;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpCounterMode;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.filechooser.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;

/**
 * Genesis emulator main class
 *
 * @author Federico Berti
 * <p>
 * MEMORY MAP:	https://en.wikibooks.org/wiki/Genesis_Programming
 */
public class Genesis extends BaseSystem {

    private static Logger LOG = LogManager.getLogger(Genesis.class.getSimpleName());

    protected GenesisBusProvider bus;
    protected Z80Provider z80;
    protected M68kProvider cpu;

    public static boolean verbose = false;

    private volatile boolean saveStateFlag = false;
    private volatile GenesisStateHandler stateHandler = GenesisStateHandler.EMPTY_STATE;


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

    public static SystemProvider createInstance() {
        return createInstance(false);
    }

    public static SystemProvider createInstance(boolean headless) {
        InputProvider.bootstrap();
        Genesis genesis = null;
        try {
            genesis = new Genesis(headless);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return genesis;
    }

    protected Genesis(boolean isHeadless) throws InvocationTargetException, InterruptedException {
        super(isHeadless);
    }

    @Override
    protected BaseBusProvider getBusProvider() {
        return bus;
    }

    @Override
    public FileFilter getRomFileFilter() {
        return FileLoader.ROM_FILTER;
    }

    @Override
    public void init() {
        joypad = new GenesisJoypad();
        inputProvider = InputProvider.createInstance(joypad);

        memory = MemoryProvider.createGenesisInstance();
        bus = GenesisBusProvider.createBus();
        vdp = GenesisVdpProvider.createVdp(bus);
        cpu = new MC68000Wrapper(bus);
        z80 = Z80CoreWrapper.createGenesisInstance(bus);
        //sound attached later
        sound = SoundProvider.NO_SOUND;

        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(cpu).attachDevice(z80).attachDevice(sound);
        reloadKeyListeners();
    }

    @Override
    protected void saveStateAction(String fileName, boolean load, int[] data) {
        stateHandler = load ? GstStateHandler.createLoadInstance(fileName, data) : GstStateHandler.createSaveInstance(fileName);
        if (stateHandler == GenesisStateHandler.EMPTY_STATE) {
            return;
        }
        LOG.info("Save state action detected: {}, using file: {}", stateHandler.getType(), fileName);
        this.saveStateFlag = true;
    }

    @Override
    protected void processSaveState() {
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

    @Override
    protected RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOvr) {
        RegionDetector.Region romRegion = RegionDetector.detectRegion(memory);
        RegionDetector.Region ovrRegion = RegionDetector.getRegion(regionOvr);
        if (ovrRegion != null && ovrRegion != romRegion) {
            LOG.info("Setting region override from: " + romRegion + " to " + ovrRegion);
            romRegion = ovrRegion;
        }
        return romRegion;
    }

    //TODO
//    //NTSC_MCLOCK_MHZ = 53693175;
//    //PAL_MCLOCK_MHZ = 53203424;
//    //DIVIDER = 1 = MCLOCK_MHZ
//    private static int VDP_DIVIDER = 16;  //3.325 Mhz PAL
//    private static int M68K_DIVIDER = 7; //7.6 Mhz PAL  0.5714
//    private static int Z80_DIVIDER = 14; //3.546 Mhz PAL 0.2666
//    private static int FM_DIVIDER = 32; //1.67 Mhz PAL 0.2666

    private static int VDP_DIVIDER = 2;  //3.325 Mhz PAL
    private static int M68K_DIVIDER = 1; //7.6 Mhz PAL  0.5714
    private static int Z80_DIVIDER = 2; //3.546 Mhz PAL 0.2666
    private static int FM_DIVIDER = 4; //1.67 Mhz PAL 0.2666

    int next68kCycle = M68K_DIVIDER;
    int nextZ80Cycle = Z80_DIVIDER;
    int nextVdpCycle = VDP_DIVIDER;

    private double microsPerTick = 1;

    protected void loop() {
        LOG.info("Starting game loop");

        int counter = 1;
        long startCycle = System.nanoTime();
        targetNs = (long) (region.getFrameIntervalMs() * Util.MILLI_IN_NS);
        updateVideoMode();

        do {
            try {
                run68k(counter);
                runZ80(counter);
                runFM(counter);
                runVdp(counter);
                if (canRenderScreen) {
                    renderScreenInternal(getStats(startCycle));
                    handleVdpDumpScreenData();
                    updateVideoMode();
                    canRenderScreen = false;
                    int elapsedNs = (int) (syncCycle(startCycle) - startCycle);
                    sound.output(elapsedNs);
                    if (Thread.currentThread().isInterrupted()) {
                        LOG.info("Game thread stopped");
                        break;
                    }
                    processSaveState();
                    pauseAndWait();
                    resetCycleCounters(counter);
                    counter = 0;
                    startCycle = System.nanoTime();
                    bus.newFrame();
                }
                counter++;
            } catch (Exception e) {
                LOG.error("Error main cycle", e);
                break;
            }
        } while (!runningRomFuture.isDone());
        LOG.info("Exiting rom thread loop");
    }

    private void resetCycleCounters(int counter) {
        nextZ80Cycle -= counter;
        next68kCycle -= counter;
        nextVdpCycle -= counter;
    }


    private void updateVideoMode() {
        if (videoMode != vdp.getVideoMode()) {
            VideoMode vm = vdp.getVideoMode();
            double frameTimeMicros = 1_000_000d / vm.getRegion().getFps();
            VdpCounterMode vcm = VdpCounterMode.getCounterMode(vm);
            microsPerTick = (FM_DIVIDER / VDP_DIVIDER) * frameTimeMicros / (vcm.slotsPerLine * vcm.vTotalCount);
            LOG.debug("Video mode changed: {}, microsPerTick: {}", vm, microsPerTick);
            videoMode = vm;
        }
    }

    private void runVdp(long counter) {
        if (counter == nextVdpCycle) {
            vdp.run(1);
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

    private void runFM(int counter) {
        if (counter % FM_DIVIDER == 0) {
            bus.getFm().tick(microsPerTick);
        }
    }

    protected void initAfterRomLoad() {
        sound = JavaSoundManager.createSoundProvider(region);
        bus.attachDevice(sound);
        resetAfterRomLoad();
    }

    private void resetAfterRomLoad() {
        //detect ROM first
        bus.reset();
        cpu.reset();
        cpu.initialize();
        joypad.initialize();
        vdp.init();
        z80.reset();
        z80.initialize();
    }
}
