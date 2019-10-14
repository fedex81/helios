/*
 * Sms
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 14/10/19 14:57
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
import omegadrive.bus.DeviceAwareBus;
import omegadrive.bus.z80.SmsBus;
import omegadrive.bus.z80.Z80BusProvider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.TwoButtonsJoypad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.savestate.MekaStateHandler;
import omegadrive.savestate.SmsStateHandler;
import omegadrive.sound.javasound.JavaSoundManager;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.vdp.SmsVdp;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Sms extends BaseSystem<Z80BusProvider, SmsStateHandler> {

    private static Logger LOG = LogManager.getLogger(Sms.class.getSimpleName());

    protected Z80Provider z80;
    private SystemLoader.SystemType systemType;

    public static boolean verbose = false;
    public static final boolean ENABLE_FM = Boolean.valueOf(System.getProperty("sms.enable.fm", "false"));

    public static int MCLK_PAL = 53203424;
    public static int MCLK_NTSC = 53693175;

    protected Sms(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        super(emuFrame);
        this.systemType = systemType;
    }

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        return new Sms(systemType, emuFrame);
    }

    private void initCommon() {
        inputProvider = InputProvider.createInstance(joypad);
        vdp = new SmsVdp(this);
        //z80, sound attached later
        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(vdp);
        reloadWindowState();
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

    /**
     * VDP clock for PAL is 53203424 / 5.
     * Z80 and PSG clock is 53203424 / 15 or VDP clock / 3.
     * NTSC Master clock is 53693175 Hz.
     */
    protected static int VDP_DIVIDER = 2;  //10.738635 Mhz
    protected static int Z80_DIVIDER = 3; //3.579545 Mhz
    protected static int FM_DIVIDER = Z80_DIVIDER * 72; //49716 hz

    int nextZ80Cycle = Z80_DIVIDER;
    int nextVdpCycle = VDP_DIVIDER;

    @Override
    public void init() {
        stateHandler = SmsStateHandler.EMPTY_STATE;
        joypad = new TwoButtonsJoypad();
        memory = MemoryProvider.createSmsInstance();
        bus = new SmsBus();
        SmsBus.HW_ENABLE_FM = ENABLE_FM;
        initCommon();
    }


    protected int counter = 1;
    protected long startCycle = System.nanoTime();
    protected int elapsedNs;

    @Override
    protected void loop() {
        LOG.info("Starting game loop");
        targetNs = (long) (region.getFrameIntervalMs() * Util.MILLI_IN_NS);

        do {
            try {
                runZ80(counter);
                runVdp(counter);
                runFM(counter);
                counter++;
            } catch (Exception e) {
                LOG.error("Error main cycle", e);
                break;
            }
        } while (!runningRomFuture.isDone());
        LOG.info("Exiting rom thread loop");
    }

    @Override
    protected void newFrame() {
        videoMode = vdp.getVideoMode();
        renderScreenLinearInternal(vdp.getScreenData()[0], getStats(System.nanoTime()));
        handleVdpDumpScreenData();
        handleNmi();
        elapsedNs = (int) (syncCycle(startCycle) - startCycle);
        if (Thread.currentThread().isInterrupted()) {
            LOG.info("Game thread stopped");
            runningRomFuture.cancel(true);
        }
        processSaveState();
        pauseAndWait();
        resetCycleCounters(counter);
        counter = 0;

        startCycle = System.nanoTime();
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
        vdp.setRegion(region);
        z80.reset();
        vdp.reset();
        joypad.init();
    }

    @Override
    protected SmsStateHandler createStateHandler(Path file, BaseStateHandler.Type type) {
        String fileName = file.toAbsolutePath().toString();
        return type == BaseStateHandler.Type.LOAD ? MekaStateHandler.createLoadInstance(fileName) :
                MekaStateHandler.createSaveInstance(fileName, systemType);
    }


    @Override
    protected void processSaveState() {
        if (saveStateFlag) {
            stateHandler.processState((SmsVdp) vdp, z80, (SmsBus) bus, memory);
            if (stateHandler.getType() == BaseStateHandler.Type.SAVE) {
                stateHandler.storeData();
            } else {
                sound.getPsg().reset();
            }
            stateHandler = SmsStateHandler.EMPTY_STATE;
            saveStateFlag = false;
        }
    }

    protected void resetCycleCounters(int counter) {
        nextZ80Cycle -= counter;
        nextVdpCycle -= counter;
    }

    protected void runVdp(long counter) {
        if (counter % VDP_DIVIDER == 0) {
            if (vdp.run(1) > 0) {
                newFrame();
                ((DeviceAwareBus) bus).onNewFrame(); //TODO
            }
        }
    }

    protected void runZ80(long counter) {
        if (counter == nextZ80Cycle) {
            int cycleDelay = z80.executeInstruction();
            handleMaskableInterrupts();
            cycleDelay = Math.max(1, cycleDelay);
            nextZ80Cycle += Z80_DIVIDER * cycleDelay;
        }
    }

    protected void runFM(int counter) {
        if (counter % FM_DIVIDER == 0) {
            sound.getFm().tick(0);
        }
    }

    private void handleMaskableInterrupts(){
        bus.handleInterrupts(Z80Provider.Interrupt.IM1);
    }

    private void handleNmi() {
        bus.handleInterrupts(Z80Provider.Interrupt.NMI);
    }

    @Override
    public SystemLoader.SystemType getSystemType() {
        return systemType;
    }
}