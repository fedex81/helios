/*
 * Genesis
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 26/10/19 15:29
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
import omegadrive.bus.md.SvpMapper;
import omegadrive.bus.megacd.MegaCdMainCpuBus;
import omegadrive.bus.megacd.MegaCdMemoryContext;
import omegadrive.bus.megacd.MegaCdSecCpuBus;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.cpu.ssp16.Ssp16;
import omegadrive.cpu.z80.Z80CoreWrapper;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.input.InputProvider;
import omegadrive.joypad.GenesisJoypad;
import omegadrive.memory.MemoryProvider;
import omegadrive.savestate.BaseStateHandler;
import omegadrive.sound.SoundProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.util.MemView;
import omegadrive.vdp.util.UpdatableViewer;
import org.slf4j.Logger;
import s32x.util.Md32xRuntimeData;

import java.nio.file.Path;
import java.util.Optional;

import static s32x.util.S32xUtil.CpuDeviceAccess.M68K;
import static s32x.util.S32xUtil.CpuDeviceAccess.Z80;

/**
 * Megadrive main class
 *
 * @author Federico Berti
 */
public class MegaCd extends BaseSystem<GenesisBusProvider> {

    public final static boolean verbose = false;
    //the emulation runs at MCLOCK_MHZ/MCLK_DIVIDER
    public final static int MCLK_DIVIDER = 7;
    protected final static double VDP_RATIO = 4.0 / MCLK_DIVIDER;  //16 -> MCLK/4, 20 -> MCLK/5
    protected final static int M68K_DIVIDER = 7 / MCLK_DIVIDER;
    public final static double[] vdpVals = {VDP_RATIO * BaseVdpProvider.MCLK_DIVIDER_FAST_VDP, VDP_RATIO * BaseVdpProvider.MCLK_DIVIDER_SLOW_VDP};
    protected final static int Z80_DIVIDER = 14 / MCLK_DIVIDER;
    protected final static int FM_DIVIDER = 42 / MCLK_DIVIDER;

    private final static Logger LOG = LogHelper.getLogger(Genesis.class.getSimpleName());

    static {
        System.setProperty("68k.debug", "true");
        System.setProperty("helios.68k.debug.mode", "2");
        System.setProperty("z80.debug", "true");
    }

    protected Z80Provider z80;
    protected M68kProvider cpu;
    protected M68kProvider secCpu;
    protected Md32xRuntimeData rt;
    protected MegaCdMemoryContext megaCdMemoryContext;

    protected MegaCdSecCpuBus secCpuBus;
    protected UpdatableViewer memView;
    protected double nextVdpCycle = vdpVals[0];
    protected int next68kCycle = M68K_DIVIDER;
    protected int nextZ80Cycle = Z80_DIVIDER;
    protected int nextFMCycle = FM_DIVIDER;

    protected int nextSec68kCycle = M68K_DIVIDER;

    public static String cpuCode = "M";

    //6*16.6= 100ms
    public static int secCpuResetFrameCounter = 0;

    protected MegaCd(DisplayWindow emuFrame) {
        super(emuFrame);
        systemType = SystemLoader.SystemType.GENESIS;
        megaCdMemoryContext = new MegaCdMemoryContext();
        secCpuBus = new MegaCdSecCpuBus(megaCdMemoryContext);
    }

    public static SystemProvider createNewInstance(DisplayWindow emuFrame) {
        return new MegaCd(emuFrame);
    }

    @Override
    public void init() {
        stateHandler = BaseStateHandler.EMPTY_STATE;
        joypad = GenesisJoypad.create(this);
        inputProvider = InputProvider.createInstance(joypad);

        memory = MemoryProvider.createGenesisInstance();
        bus = createBus();
        vdp = GenesisVdpProvider.createVdp(bus);
        cpuCode = "M";
        cpu = MC68000Wrapper.createInstance(bus);
        cpuCode = "S";
        secCpu = MC68000Wrapper.createInstance(secCpuBus);
        cpuCode = "M";
        ((MC68000Wrapper) secCpu).setStop(true);
        z80 = Z80CoreWrapper.createInstance(getSystemType(), bus);
        //sound attached later
        sound = SoundProvider.NO_SOUND;

        bus.attachDevice(this).attachDevice(memory).attachDevice(joypad).attachDevice(vdp).
                attachDevice(cpu).attachDevice(z80);
        reloadWindowState();
        createAndAddVdpEventListener();
    }

    protected void loop() {
        updateVideoMode(true);
        do {
            runMain68k();
            //TODO screen gets all garbled
            runSec68k();
            runZ80();
            runFM();
            //this should be last as it could change the counter
            runVdp();
            cycleCounter++;
        } while (!futureDoneFlag);
    }

    protected final void runVdp() {
        while (nextVdpCycle <= cycleCounter) {
            //NOTE counter could be reset to 0 when calling vdp::runSlot
            int vdpMclk = vdp.runSlot();
            nextVdpCycle += vdpVals[vdpMclk - 4];
            assert nextVdpCycle > cycleCounter;
        }
    }

    protected final void runMain68k() {
        while (next68kCycle <= cycleCounter) {
            boolean isRunning = bus.is68kRunning();
            boolean canRun = !cpu.isStopped() && isRunning;
            int cycleDelay = 1;
            if (canRun) {
                Md32xRuntimeData.setAccessTypeExt(M68K);
                cycleDelay = cpu.runInstruction() + Md32xRuntimeData.resetCpuDelayExt();
            }
            //interrupts are processed after the current instruction
            if (isRunning) {
                bus.handleVdpInterrupts68k();
            }
            cycleDelay = Math.max(1, cycleDelay);
            next68kCycle += M68K_DIVIDER * cycleDelay;
            assert Md32xRuntimeData.resetCpuDelayExt() == 0;
        }
    }

    protected void runSec68k() {
        while (nextSec68kCycle <= cycleCounter) {
            boolean canRun = !secCpu.isStopped();
            int cycleDelay = 1;
            if (canRun) {
                Md32xRuntimeData.setAccessTypeExt(M68K);
                cycleDelay = secCpu.runInstruction() + Md32xRuntimeData.resetCpuDelayExt();
            }
            //interrupts are processed after the current instruction
            if (bus.is68kRunning()) {
                //TODO
            }
            cycleDelay = Math.max(1, cycleDelay);
            nextSec68kCycle += M68K_DIVIDER * cycleDelay;
            assert Md32xRuntimeData.resetCpuDelayExt() == 0;
        }
    }

    protected final void runZ80() {
        while (nextZ80Cycle <= cycleCounter) {
            int cycleDelay = 0;
            boolean running = bus.isZ80Running();
            if (running) {
                Md32xRuntimeData.setAccessTypeExt(Z80);
                cycleDelay = z80.executeInstruction();
                bus.handleVdpInterruptsZ80();
                cycleDelay += Md32xRuntimeData.resetCpuDelayExt();
            }
            cycleDelay = Math.max(1, cycleDelay);
            nextZ80Cycle += Z80_DIVIDER * cycleDelay;
            assert Md32xRuntimeData.resetCpuDelayExt() == 0;
        }
    }

    protected final void runFM() {
        while (nextFMCycle <= cycleCounter) {
            bus.getFm().tick();
            nextFMCycle += FM_DIVIDER;
        }
    }

    protected GenesisBusProvider createBus() {
        return new MegaCdMainCpuBus(megaCdMemoryContext);
    }

    @Override
    protected void updateVideoMode(boolean force) {
        if (force || videoMode != vdp.getVideoMode()) {
            videoMode = vdp.getVideoMode();
            double microsPerTick = getMicrosPerTick();
            sound.getFm().setMicrosPerTick(microsPerTick);
            targetNs = (long) (getRegion().getFrameIntervalMs() * Util.MILLI_IN_NS);
            LOG.info("Video mode changed: {}, microsPerTick: {}", videoMode, microsPerTick);
        }
    }

    private double getMicrosPerTick() {
        double mclkhz = videoMode.isPal() ? Util.GEN_PAL_MCLOCK_MHZ : Util.GEN_NTSC_MCLOCK_MHZ;
        return 1_000_000.0 / (mclkhz / (FM_DIVIDER * MCLK_DIVIDER));
    }

    @Override
    protected RegionDetector.Region getRomRegion() {
        return RegionDetector.detectRegion((MdCartInfoProvider) romContext.cartridgeInfoProvider);
    }

    @Override
    protected RomContext createRomContext(Path rom) {
        RomContext rc = new RomContext();
        rc.romPath = rom;
        MdCartInfoProvider mcip = MdCartInfoProvider.createInstance(memory, rc.romPath);
        rc.cartridgeInfoProvider = mcip;
        String regionOverride = Optional.ofNullable(mcip.getEntry().forceRegion).
                orElse(emuFrame.getRegionOverride());
        romContext = rc;
        rc.region = getRegionInternal(regionOverride);
        return rc;
    }

    @Override
    public void newFrame() {
        memView.update();
        super.newFrame();
        if (secCpuResetFrameCounter == 1) {
            ((MC68000Wrapper) secCpu).setStop(false);
            secCpu.reset();
            secCpuBus.resetDone();
            LOG.info("SecCpu reset completed: " + telemetry.getFrameCounter());
        }
        secCpuResetFrameCounter = Math.max(0, secCpuResetFrameCounter - 1);
//        LOG.info("Frame: " + telemetry.getFrameCounter());
    }

    protected UpdatableViewer createMemView() {
        return MemView.createInstance(bus, vdp.getVdpMemory());
    }

    /**
     * Counters can go negative when the video mode changes
     */
    @Override
    protected void resetCycleCounters(int counter) {
        assert nextZ80Cycle >= counter && next68kCycle >= counter &&
                nextSec68kCycle >= counter &&
                nextVdpCycle + 1 >= counter;
        nextZ80Cycle = Math.max(1, nextZ80Cycle - counter);
        next68kCycle = Math.max(1, next68kCycle - counter);
        nextSec68kCycle = Math.max(1, nextSec68kCycle - counter);
        nextVdpCycle = Math.max(1, nextVdpCycle - counter);
        nextFMCycle = Math.max(1, nextFMCycle - counter);
    }

    @Override
    protected void initAfterRomLoad() {
        super.initAfterRomLoad();
        bus.attachDevice(sound);
        vdp.addVdpEventListener(sound);
        SvpMapper.ssp16 = Ssp16.NO_SVP;
        resetAfterRomLoad();
        memView = createMemView();
    }

    @Override
    protected void resetAfterRomLoad() {
        super.resetAfterRomLoad();
        Md32xRuntimeData.releaseInstance();
        rt = Md32xRuntimeData.newInstance();
        cpu.reset();
        secCpu.reset();
        z80.reset(); //TODO confirm this is needed
    }

    @Override
    protected void handleSoftReset() {
        if (softReset) {
            cpu.softReset();
        }
        super.handleSoftReset();
    }
}