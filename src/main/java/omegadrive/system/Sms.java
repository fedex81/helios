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
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.sound.javasound.AbstractSoundManager;
import omegadrive.system.perf.SmsPerf;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.vdp.SmsVdp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Sms extends BaseSystem<Z80BusProvider> {

    public static final boolean ENABLE_FM = Boolean.parseBoolean(System.getProperty("sms.enable.fm", "false"));
    private static final Logger LOG = LogManager.getLogger(Sms.class.getSimpleName());

    public static final int MCLK_PAL = 53203424;
    public static final int MCLK_NTSC = 53693175;
    public static final int VDP_CLK_NTSC = MCLK_NTSC / 5;
    public static final int VDP_CLK_PAL = MCLK_PAL / 5;

    /**
     * VDP clock for PAL is 53203424 / 5.
     * Z80 and PSG clock is 53203424 / 15 or VDP clock / 3.
     * NTSC Master clock is 53693175 Hz.
     */
    protected static int VDP_DIVIDER = 2;  //10.738635 Mhz
    protected static int Z80_DIVIDER = 3; //3.579545 Mhz
    protected static int FM_DIVIDER = (int) (Z80_DIVIDER * 72.0); //49716 hz

    protected Z80Provider z80;
    int nextZ80Cycle = Z80_DIVIDER;
    int nextVdpCycle = VDP_DIVIDER;
    private SystemLoader.SystemType systemType;

    protected Sms(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        super(emuFrame);
        this.systemType = systemType;
    }

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, DisplayWindow emuFrame, boolean debugPerf) {
        return debugPerf ? new SmsPerf(systemType, emuFrame) : new Sms(systemType, emuFrame);
    }

    public static SystemProvider createNewInstance(SystemLoader.SystemType systemType, DisplayWindow emuFrame) {
        return createNewInstance(systemType, emuFrame, false);
    }

    private void initCommon() {
        inputProvider = InputProvider.createInstance(joypad);
        vdp = new SmsVdp(this);
        //z80, sound attached later
        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp);
        reloadWindowState();
        createAndAddVdpEventListener();
    }

    @Override
    protected RegionDetector.Region getRegionInternal(IMemoryProvider memory, String regionOvr) {
        RegionDetector.Region romRegion = RegionDetector.Region.JAPAN;
        RegionDetector.Region ovrRegion = RegionDetector.getRegion(regionOvr);
        if (ovrRegion != null && ovrRegion != romRegion) {
            LOG.info("Setting region override from: {} to {}", romRegion, ovrRegion);
            romRegion = ovrRegion;
        }
        return romRegion;
    }

    @Override
    public void init() {
        stateHandler = BaseStateHandler.EMPTY_STATE;
        joypad = new TwoButtonsJoypad();
        memory = MemoryProvider.createSmsInstance();
        bus = new SmsBus();
        SmsBus.HW_ENABLE_FM = ENABLE_FM;
        initCommon();
    }

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
    protected void updateVideoMode(boolean force) {
        videoMode = vdp.getVideoMode();
    }

    @Override
    protected void initAfterRomLoad() {
        sound = AbstractSoundManager.createSoundProvider(systemType, region);
        z80 = Z80CoreWrapper.createInstance(bus);
        bus.attachDevice(sound).attachDevice(z80);
        vdp.addVdpEventListener(sound);
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

    protected void runFM(int counter) {
        if ((counter + 1) % FM_DIVIDER == 0) {
            sound.getFm().tick();
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