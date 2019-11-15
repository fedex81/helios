/*
 * Z80BaseSystem
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 15:49
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
import omegadrive.bus.z80.ColecoBus;
import omegadrive.bus.z80.MsxBus;
import omegadrive.bus.z80.Sg1000Bus;
import omegadrive.bus.z80.Z80BusProvider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.ColecoPad;
import omegadrive.joypad.MsxPad;
import omegadrive.joypad.TwoButtonsJoypad;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.Tms9918aVdp;
import omegadrive.z80.Z80CoreWrapper;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class Z80BaseSystem extends BaseSystem<Z80BusProvider, BaseStateHandler> {

    private static Logger LOG = LogManager.getLogger(Z80BaseSystem.class.getSimpleName());

    protected Z80Provider z80;
    private SystemLoader.SystemType systemType;
    private Z80Provider.Interrupt vdpInterruptType;

    public static boolean verbose = false;

    protected Z80BaseSystem(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        super(emuFrame);
        this.systemType = systemType;
        vdpInterruptType = systemType == SystemLoader.SystemType.COLECO ? Z80Provider.Interrupt.NMI :
                Z80Provider.Interrupt.IM1;
    }

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        return new Z80BaseSystem(systemType, emuFrame);
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
        stateHandler = BaseStateHandler.EMPTY_STATE;
        inputProvider = InputProvider.createInstance(joypad);
        vdp = new Tms9918aVdp();
        //z80, sound attached later
        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(vdp);
        reloadWindowState();
    }

    @Override
    protected RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOvr) {
        return RegionDetector.Region.JAPAN;
    }

    @Override
    protected BaseStateHandler createStateHandler(Path file, BaseStateHandler.Type type) {
        LOG.error("Not implemented!");
        return stateHandler;
    }

    private static int VDP_DIVIDER = 1;  //10.738635 Mhz
    private static int Z80_DIVIDER = 3; //3.579545 Mhz

    int nextZ80Cycle = Z80_DIVIDER;
    int nextVdpCycle = VDP_DIVIDER;

    int counter = 1;
    long startCycle = System.nanoTime();

    @Override
    protected void loop() {
        LOG.info("Starting game loop");
        targetNs = (long) (region.getFrameIntervalMs() * Util.MILLI_IN_NS);
        updateVideoMode();

        do {
            try {
                runZ80(counter);
                runVdp(counter);
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
        renderScreenLinearInternal(vdp.getScreenDataLinear(), getStats(System.nanoTime()));
        handleVdpDumpScreenData();
        updateVideoMode();
        int elapsedNs = (int) (syncCycle(startCycle) - startCycle);
//                    processSaveState();
        pauseAndWait();
        resetCycleCounters(counter);
        counter = 0;

        startCycle = System.nanoTime();
    }

    @Override
    protected void initAfterRomLoad() {
        sound = AbstractSoundManager.createSoundProvider(systemType, region);
        z80 = Z80CoreWrapper.createSg1000Instance(bus);
        bus.attachDevice(sound).attachDevice(z80);

        resetAfterRomLoad();
    }

    protected void resetAfterRomLoad() {
        super.resetAfterRomLoad();
        z80.reset();
    }

    @Override
    protected void processSaveState() {
        LOG.error("Not implemented!");
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
            if (vdp.runSlot() > 0) {
                newFrame();
                ((DeviceAwareBus) bus).onNewFrame(); //TODO fix
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
        bus.handleInterrupts(vdpInterruptType);
    }

    @Override
    public SystemLoader.SystemType getSystemType() {
        return systemType;
    }
}