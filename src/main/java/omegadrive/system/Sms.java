/*
 * Sms
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 27/10/19 13:09
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
import omegadrive.bus.z80.SmsBus;
import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.TwoButtonsJoypad;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.vdp.SmsVdp;
import org.slf4j.Logger;

import static omegadrive.util.RegionDetector.Region.EUROPE;
import static omegadrive.util.RegionDetector.Region.USA;

public class Sms extends BaseSystem<Z80BusProvider> {

    public static final boolean ENABLE_FM = Boolean.parseBoolean(System.getProperty("sms.enable.fm", "false"));
    private static final Logger LOG = LogHelper.getLogger(Sms.class.getSimpleName());

    public static final int MCLK_PAL = 53203424;
    public static final int MCLK_NTSC = 53693175;
    public static final int VDP_CLK_NTSC = MCLK_NTSC / 5;
    public static final int VDP_CLK_PAL = MCLK_PAL / 5;

    /**
     * VDP clock for PAL is 53203424 / 5.
     * Z80 and PSG clock is 53203424 / 15 or VDP clock / 3.
     * NTSC Master clock is 53693175 Hz.
     */
    protected static final int VDP_DIVIDER = 2;  //10.738635 Mhz
    protected static final int Z80_DIVIDER = 3; //3.579545 Mhz
    protected static final int FM_DIVIDER = (int) (Z80_DIVIDER * 72.0); //49716 hz
    protected static final int PAL_PSG_SAMPLES_PER_FRAME = 49550; //991*50
    protected static final int NTSC_PSG_SAMPLES_PER_FRAME = 49780; //829.6*60

    protected Z80Provider z80;
    int nextZ80Cycle = Z80_DIVIDER;
    int nextVdpCycle = VDP_DIVIDER;

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
        z80 = Z80CoreWrapper.createInstance(systemType, bus);
        vdp.addVdpEventListener(sound);
        bus.attachDevices(this, memory, joypad, vdp, sound, z80);
        reloadWindowState();
        createAndAddVdpEventListener();
    }

    @Override
    public void init() {
        super.init();
        stateHandler = BaseStateHandler.EMPTY_STATE;
        joypad = new TwoButtonsJoypad();
        memory = MemoryProvider.createSmsInstance();
        bus = new SmsBus();
        SmsBus.HW_ENABLE_FM = ENABLE_FM;
        initCommon();
    }

    @Override
    protected void loop() {
        //gameGear always 60fps
        double frameMs = systemType == SystemLoader.SystemType.GG ?
                USA.getFrameIntervalMs() : romContext.region.getFrameIntervalMs();
        targetNs = (long) (frameMs * Util.MILLI_IN_NS);
        updatePsgRate(romContext.region);
        do {
            runZ80(cycleCounter);
            runVdp(cycleCounter);
            runSound(cycleCounter);
            cycleCounter++;
        } while (!runningRomFuture.isDone());
    }

    private void updatePsgRate(RegionDetector.Region region) {
        sound.getPsg().updateRate(region, region == EUROPE ? PAL_PSG_SAMPLES_PER_FRAME : NTSC_PSG_SAMPLES_PER_FRAME);
    }


    @Override
    protected void updateVideoMode(boolean force) {
        displayContext.videoMode = vdp.getVideoMode();
        updatePsgRate(romContext.region);
    }

    @Override
    protected void initAfterRomLoad() {
        super.initAfterRomLoad();
        resetAfterRomLoad();
    }

    @Override
    protected void resetAfterRomLoad() {
        super.resetAfterRomLoad();
        z80.reset();
        vdp.reset();
    }

    @Override
    protected void resetCycleCounters(int counter) {
        nextZ80Cycle -= counter;
        nextVdpCycle -= counter;
    }

    protected void runVdp(long counter) {
        if (counter == nextVdpCycle) {
            vdp.runSlot();
            nextVdpCycle += VDP_DIVIDER;
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

    protected final void runSound(int counter) {
        if ((counter + 1) % FM_DIVIDER == 0) {
            sound.getPsg().tick();
            if (ENABLE_FM) {
                sound.getFm().tick();
            }
        }
    }

    private void handleMaskableInterrupts(){
        bus.handleInterrupts(Z80Provider.Interrupt.IM1);
    }

    private void handleNmi() {
        bus.handleInterrupts(Z80Provider.Interrupt.NMI);
    }
}