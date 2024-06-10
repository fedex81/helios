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
import omegadrive.bus.model.Z80BusProvider;
import omegadrive.bus.z80.ColecoBus;
import omegadrive.bus.z80.MsxBus;
import omegadrive.bus.z80.Sg1000Bus;
import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.ColecoPad;
import omegadrive.joypad.MsxPad;
import omegadrive.joypad.TwoButtonsJoypad;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.Tms9918aVdp;
import org.slf4j.Logger;

public class Z80BaseSystem extends BaseSystem<Z80BusProvider> {

    private static final Logger LOG = LogHelper.getLogger(Z80BaseSystem.class.getSimpleName());

    protected Z80Provider z80;
    private final Z80Provider.Interrupt vdpInterruptType;

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
        super.init();
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

    private static final int VDP_DIVIDER = 1;  //10.738635 Mhz
    private static final int Z80_DIVIDER = 3; //3.579545 Mhz

    private void initCommon() {
        stateHandler = BaseStateHandler.EMPTY_STATE;
        inputProvider = InputProvider.createInstance(joypad);
        vdp = new Tms9918aVdp();
        z80 = Z80CoreWrapper.createInstance(systemType, bus);
        bus.attachDevices(this, memory, joypad, vdp, z80, sound);
        reloadWindowState();
        createAndAddVdpEventListener();
    }

    private int nextZ80Cycle = Z80_DIVIDER;

    @Override
    protected void loop() {
        targetNs = (long) (getRegion().getFrameIntervalMs() * Util.MILLI_IN_NS);
        updateVideoMode(true);
        do {
            runZ80(cycleCounter);
            runVdp(cycleCounter);
            cycleCounter++;
        } while (!runningRomFuture.isDone());
    }

    @Override
    protected void initAfterRomLoad() {
        super.initAfterRomLoad();
        resetAfterRomLoad();
    }

    protected void resetAfterRomLoad() {
        super.resetAfterRomLoad();
        z80.reset();
    }

    @Override
    protected void resetCycleCounters(int counter) {
        nextZ80Cycle -= counter;
    }

    @Override
    protected void updateVideoMode(boolean force) {
        VideoMode vm = vdp.getVideoMode();
        if (force || videoMode != vm) {
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
        if ((counter & 1) == 1) {
            vdp.runSlot();
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
}