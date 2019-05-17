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
import omegadrive.bus.z80.SmsBus;
import omegadrive.bus.z80.Z80BusProvider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.TwoButtonsJoypad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.javasound.JavaSoundManager;
import omegadrive.ui.GenesisWindow;
import omegadrive.ui.RenderingStrategy;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.SmsVdp;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Sms extends BaseSystem {

    private static Logger LOG = LogManager.getLogger(Sms.class.getSimpleName());

    protected Z80Provider z80;
    private SystemLoader.SystemType systemType;
    private boolean isGG;

    public static boolean verbose = false;

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, GenesisWindow emuFrame) {
        return new Sms(systemType, emuFrame);
    }

    protected Sms(SystemLoader.SystemType systemType, GenesisWindow emuFrame){
        super(emuFrame);
        this.systemType = systemType;
        this.isGG = systemType == SystemLoader.SystemType.GG;
    }

    @Override
    public void init() {
        joypad = new TwoButtonsJoypad();
        memory = MemoryProvider.createSmsInstance();
        bus = new SmsBus();
        initCommon();
    }

    /** Emulated screen pixels */
    private int[] display;
    private int[] ggDisplay = new int[0];
    protected VideoMode ggVideoMode = VideoMode.NTSCU_H20_V18;

    private void initCommon() {
        int numPixels = VideoMode.NTSCJ_H32_V24.getDimension().width * VideoMode.NTSCJ_H32_V24.getDimension().height;
        display = new int[numPixels];
        if(isGG){
            int nump = ggVideoMode.getDimension().width * ggVideoMode.getDimension().height;
            ggDisplay = new int[nump];
        }
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
        int[] viewport = isGG ? ggDisplay : display;
        VideoMode outputVideoMode = isGG ? ggVideoMode : videoMode;

        do {
            try {
                runZ80(counter);
                runVdp(counter);
                if (canRenderScreen) {
                    if(isGG){
                        RenderingStrategy.subImageWithOffset(display, ggDisplay, videoMode.getDimension(),
                                outputVideoMode.getDimension(), SmsVdp.GG_X_OFFSET,
                                SmsVdp.GG_Y_OFFSET);
                    }
                    emuFrame.renderScreenLinear(viewport, getStats(System.nanoTime()), outputVideoMode);
                    handleVdpDumpScreenData();
                    updateVideoMode();
                    handleNmi();
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
            handleMaskableInterrupts();
            cycleDelay = Math.max(1, cycleDelay);
            nextZ80Cycle += Z80_DIVIDER * cycleDelay;
        }
    }



    private void handleMaskableInterrupts(){
        //TODO
        Z80BusProvider sgBus = (Z80BusProvider) bus;
        sgBus.handleInterrupts(Z80Provider.Interrupt.IM1);
    }

    private void handleNmi() {
        //TODO
        Z80BusProvider sgBus = (Z80BusProvider) bus;
        sgBus.handleInterrupts(Z80Provider.Interrupt.NMI);
    }

    @Override
    public SystemLoader.SystemType getSystemType() {
        return systemType;
    }
}