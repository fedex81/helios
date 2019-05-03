/*
 * Sms
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/04/19 10:59
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
import omegadrive.bus.sg1k.*;
import omegadrive.input.InputProvider;
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
import omegadrive.vdp.Engine;
import omegadrive.vdp.Sg1000Vdp;
import omegadrive.vdp.SmsVdp;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.filechooser.FileFilter;
import java.lang.reflect.InvocationTargetException;

public class Sms extends BaseSystem {

    private static Logger LOG = LogManager.getLogger(Sms.class.getSimpleName());

    protected Z80Provider z80;
    private SystemLoader.SystemType systemType;

    public static boolean verbose = false;

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, GenesisWindow emuFrame) {
        return new Sms(systemType, emuFrame);
    }

    protected Sms(boolean isHeadless) throws InvocationTargetException, InterruptedException {
        super(isHeadless);
    }


    protected Sms(SystemLoader.SystemType systemType, GenesisWindow emuFrame){
        super(emuFrame);
        this.systemType = systemType;
    }

    @Override
    public void init() {
        joypad = new TwoButtonsJoypad();
        memory = MemoryProvider.createSmsInstance();
        bus = new SmsBus();
        initCommon();
    }

    /** Emulated screen pixels */
    public final static int display[] = new int[SmsVdp.SMS_WIDTH * SmsVdp.SMS_HEIGHT];

    private void initCommon() {
        inputProvider = InputProvider.createInstance(joypad);
        vdp = new SmsVdp(this, display);
        //z80, sound attached later
        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(vdp);
        reloadKeyListeners();
    }

    @Override
    protected RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOvr) {
        RegionDetector.Region romRegion = RegionDetector.Region.JAPAN;
        RegionDetector.Region ovrRegion = RegionDetector.getRegion(regionOvr);
        if (ovrRegion != null && ovrRegion != romRegion) {
            LOG.info("Setting region override from: " + romRegion + " to " + ovrRegion);
            romRegion = ovrRegion;
        }
        return romRegion;
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
//                    renderScreenInternal(getStats(System.nanoTime()));
                    emuFrame.renderScreenLinear(display, getStats(System.nanoTime()), videoMode);
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

        //TODO
        Engine.setSMS();
//        Engine.setGG();

        resetAfterRomLoad();
    }

    protected void resetAfterRomLoad() {
        super.resetAfterRomLoad();
        z80.reset();
        vdp.reset();
        joypad.init();
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


    private void runVdp(long counter) {
        if (counter % 2 == 1) {
            if (vdp.run(1)) {
                canRenderScreen = true;
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