/*
 * Genesis
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/05/19 16:46
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

import omegadrive.SystemLoader;
import omegadrive.bus.gen.GenesisBus;
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
import omegadrive.ui.GenesisWindow;
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

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Genesis emulator main class
 *
 * MEMORY MAP:	https://en.wikibooks.org/wiki/Genesis_Programming
 */
public class Genesis extends BaseSystem<GenesisBusProvider> {

    private static Logger LOG = LogManager.getLogger(Genesis.class.getSimpleName());

    protected Z80Provider z80;
    protected M68kProvider cpu;

    public static boolean verbose = false;

    private volatile boolean saveStateFlag = false;
    private volatile GenesisStateHandler stateHandler = GenesisStateHandler.EMPTY_STATE;

    public static SystemProvider createNewInstance(GenesisWindow emuFrame) {
        return new Genesis(emuFrame);
    }

    protected Genesis(GenesisWindow emuFrame){
        super(emuFrame);
    }

    @Override
    public void init() {
        joypad = new GenesisJoypad();
        inputProvider = InputProvider.createInstance(joypad);

        memory = MemoryProvider.createGenesisInstance();
        bus = new GenesisBus();
        vdp = GenesisVdpProvider.createVdp(bus);
        cpu = new MC68000Wrapper(bus);
        z80 = Z80CoreWrapper.createGenesisInstance(bus);
        //sound attached later
        sound = SoundProvider.NO_SOUND;

        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(cpu).attachDevice(z80);
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
                stateHandler.loadZ80(z80, bus);
                stateHandler.load68k((MC68000Wrapper) cpu, memory);
                LOG.info("Savestate loaded from: " + stateHandler.getFileName());
            } else {
                stateHandler.saveFm(sound.getFm());
                stateHandler.saveZ80(z80, bus);
                stateHandler.save68k((MC68000Wrapper) cpu, memory);
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
            targetNs = (long) (region.getFrameIntervalMs() * Util.MILLI_IN_NS);
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
            int cycleDelay = 0;
            boolean running = bus.isZ80Running();
            if (running) {
                cycleDelay = z80.executeInstruction();
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
        sound = JavaSoundManager.createSoundProvider(getSystemType(), region);
        bus.attachDevice(sound);
        resetAfterRomLoad();
    }

    protected void resetAfterRomLoad() {
        //detect ROM first
        joypad.init();
        vdp.init();
        bus.init();
        cpu.reset();
        z80.reset(); //TODO confirm this is needed
    }

    @Override
    public SystemLoader.SystemType getSystemType() {
        return SystemLoader.SystemType.GENESIS;
    }
}
