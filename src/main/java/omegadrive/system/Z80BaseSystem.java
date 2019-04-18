/*
 * Z80BaseSystem
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:18
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
import omegadrive.bus.BaseBusProvider;
import omegadrive.bus.sg1k.ColecoBus;
import omegadrive.bus.sg1k.MsxBus;
import omegadrive.bus.sg1k.Sg1000Bus;
import omegadrive.bus.sg1k.Sg1000BusProvider;
import omegadrive.input.InputProvider;
import omegadrive.input.KeyboardInput;
import omegadrive.joypad.ColecoPad;
import omegadrive.joypad.MsxPad;
import omegadrive.joypad.TwoButtonsJoypad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.javasound.JavaSoundManager;
import omegadrive.ui.GenesisWindow;
import omegadrive.util.FileLoader;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.Sg1000Vdp;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.filechooser.FileFilter;
import java.lang.reflect.InvocationTargetException;

public class Z80BaseSystem extends BaseSystem {

    private static Logger LOG = LogManager.getLogger(Z80BaseSystem.class.getSimpleName());

    protected Z80Provider z80;
    private SystemLoader.SystemType systemType;

    public static boolean verbose = false;

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, GenesisWindow emuFrame) {
        return new Z80BaseSystem(systemType, emuFrame);
    }

    protected Z80BaseSystem(boolean isHeadless) throws InvocationTargetException, InterruptedException {
        super(isHeadless);
    }


    protected Z80BaseSystem(SystemLoader.SystemType systemType, GenesisWindow emuFrame){
        super(emuFrame);
        this.systemType = systemType;
    }

    @Override
    public void init() {
        switch (systemType){
            case SG_1000:
                joypad = new TwoButtonsJoypad();
                memory = MemoryProvider.createSg1000Instance();
                bus = new Sg1000Bus();
                break;
            case MSX:
                joypad = new MsxPad();
                memory = MemoryProvider.createMsxInstance();
                bus = new MsxBus();
                break;
            case COLECO:
                joypad = new ColecoPad();
                memory = MemoryProvider.createSg1000Instance();
                bus = new ColecoBus();
                break;
        }
        initCommon();
    }

    private void initCommon() {
        inputProvider = InputProvider.createInstance(joypad);
        vdp = new Sg1000Vdp();
        //z80, sound attached later
        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(vdp);
        reloadKeyListeners();
    }

    @Override
    protected RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOvr) {
        return RegionDetector.Region.JAPAN;
    }

    @Override
    protected FileFilter getRomFileFilter() {
        return FileLoader.ROM_FILTER;
    }

    private static int VDP_DIVIDER = 1;  //10.738635 Mhz
    private static int Z80_DIVIDER = 3; //3.579545 Mhz

    int nextZ80Cycle = Z80_DIVIDER;
    int nextVdpCycle = VDP_DIVIDER;

    @Override
    protected void loop() {
        LOG.info("Starting game loop");

        int counter = 1;
        long startCycle = System.nanoTime();
        targetNs = (long) (region.getFrameIntervalMs() * Util.MILLI_IN_NS);
        updateVideoMode();

        do {
            try {
                runZ80(counter);
                runVdp(counter);
                if (canRenderScreen) {
                    renderScreenInternal(getStats(System.nanoTime()));
                    handleVdpDumpScreenData();
                    updateVideoMode();
                    canRenderScreen = false;
                    int elapsedNs = (int) (syncCycle(startCycle) - startCycle);
                    if (Thread.currentThread().isInterrupted()) {
                        LOG.info("Game thread stopped");
                        break;
                    }
//                    processSaveState();
                    pauseAndWait();
                    resetCycleCounters(counter);
                    counter = 0;
                    bus.newFrame();
                    startCycle = System.nanoTime();
                }
                counter++;
            } catch (Exception e) {
                LOG.error("Error main cycle", e);
                break;
            }
        } while (!runningRomFuture.isDone());
        LOG.info("Exiting rom thread loop");
    }

    @Override
    protected void initAfterRomLoad() {
        sound = JavaSoundManager.createSoundProvider(systemType, region);
        z80 = Z80CoreWrapper.createSg1000Instance(bus);
        bus.attachDevice(sound).attachDevice(z80);

        resetAfterRomLoad();
    }

    protected void resetAfterRomLoad() {
        super.resetAfterRomLoad();
        z80.reset();
    }

    @Override
    protected void saveStateAction(String fileName, boolean load, int[] data) {
        LOG.error("Not implemented!");
    }

    @Override
    protected void processSaveState() {
        LOG.error("Not implemented!");
    }

    @Override
    protected BaseBusProvider getBusProvider() {
        return bus;
    }

    private void resetCycleCounters(int counter) {
        nextZ80Cycle -= counter;
        nextVdpCycle -= counter;
    }


    private void updateVideoMode() {
        VideoMode vm = vdp.getVideoMode();
        if (videoMode != vm) {
            LOG.info("Video mode changed: {}", vm);
            videoMode = vm;
        }
    }

    /**
     * NTSC, 256x192
     * -------------
     * <p>
     * Lines  Description
     * <p>
     * 192    Active display
     * 24     Bottom border
     * 3      Bottom blanking
     * 3      Vertical blanking
     * 13     Top blanking
     * 27     Top border
     * <p>
     * V counter values
     * 00-DA, D5-FF
     * <p>
     * vdpTicksPerFrame = (NTSC_SCANLINES = ) 262v * (H32_PIXELS =) 342 = 89604
     * vdpTicksPerSec = 5376240
     */


    private void runVdp(long counter) {
        if (counter % 2 == 1) {
            if (vdp.run(1)) {
                canRenderScreen = true;
                vdpScreen = vdp.getScreenData();
            }
        }
    }


    private void runZ80(long counter) {
        if (counter == nextZ80Cycle) {
            int cycleDelay = z80.executeInstruction();
            handleInterrupt();
            cycleDelay = Math.max(1, cycleDelay);
            nextZ80Cycle += Z80_DIVIDER * cycleDelay;
        }
    }

    private void handleInterrupt(){
        //TODO
        Sg1000BusProvider sgBus = (Sg1000BusProvider) bus;
        sgBus.handleVdpInterruptsZ80();
    }

    @Override
    public SystemLoader.SystemType getSystemType() {
        return systemType;
    }
}