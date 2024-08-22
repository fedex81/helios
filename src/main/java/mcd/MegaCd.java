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

package mcd;

import mcd.bus.McdSubInterruptHandler;
import mcd.cart.MegaCdCartInfoProvider;
import mcd.cdd.ExtendedCueSheet;
import mcd.util.McdMemView;
import omegadrive.SystemLoader;
import omegadrive.bus.md.SvpMapper;
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
import omegadrive.system.BaseSystem;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.util.Util;
import omegadrive.util.ZipUtil;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.util.UpdatableViewer;
import org.slf4j.Logger;
import s32x.util.Md32xRuntimeData;

import java.nio.file.Path;
import java.util.Optional;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static omegadrive.util.Util.GEN_NTSC_MCLOCK_MHZ;
import static omegadrive.util.Util.GEN_PAL_MCLOCK_MHZ;

/**
 * Megadrive main class
 *
 * @author Federico Berti
 */
public class MegaCd extends BaseSystem<GenesisBusProvider> {

    private final static Logger LOG = LogHelper.getLogger(MegaCd.class.getSimpleName());

    public final static boolean verbose = false;
    //the emulation runs at MCLOCK_MHZ/MCLK_DIVIDER
    public final static int MCLK_DIVIDER = 7;
    protected final static double VDP_RATIO = 4.0 / MCLK_DIVIDER;  //16 -> MCLK/4, 20 -> MCLK/5
    protected final static int M68K_DIVIDER = 7 / MCLK_DIVIDER;
    public final static double[] vdpVals = {VDP_RATIO * BaseVdpProvider.MCLK_DIVIDER_FAST_VDP, VDP_RATIO * BaseVdpProvider.MCLK_DIVIDER_SLOW_VDP};
    protected final static int Z80_DIVIDER = 14 / MCLK_DIVIDER;
    protected final static int FM_DIVIDER = 42 / MCLK_DIVIDER;

    public final static double MCD_SUB_68K_CLOCK_MHZ = 12_500_000;

    //mcd-verificator(NTSC) is very sensitive
    public final static double MCD_68K_RATIO_NTSC = 1.0 / (MCD_SUB_68K_CLOCK_MHZ / (GEN_NTSC_MCLOCK_MHZ / 7.0));
    public final static double MCD_68K_RATIO_PAL = 1.0 / (MCD_SUB_68K_CLOCK_MHZ / (GEN_PAL_MCLOCK_MHZ / 7.0));

    private double mcd68kRatio;


    static {
        System.setProperty("68k.debug", "true");
        System.setProperty("helios.68k.debug.mode", "0");
        System.setProperty("z80.debug", "false");
    }

    protected Z80Provider z80;
    protected M68kProvider cpu;
    protected M68kProvider subCpu;

    protected McdSubInterruptHandler interruptHandler;
    protected Md32xRuntimeData rt;
    protected McdDeviceHelper.McdLaunchContext mcdLaunchContext;
    protected UpdatableViewer memView;
    protected double nextVdpCycle = vdpVals[0];
    protected int next68kCycle = M68K_DIVIDER;
    protected int nextZ80Cycle = Z80_DIVIDER;
    protected int nextFMCycle = FM_DIVIDER;

    protected double nextSub68kCycle = M68K_DIVIDER;

    protected MegaCd(DisplayWindow emuFrame) {
        super(emuFrame);
        systemType = SystemLoader.SystemType.MEGACD;
    }

    public static SystemProvider createNewInstance(DisplayWindow emuFrame) {
        return new MegaCd(emuFrame);
    }

    @Override
    public void init() {
        super.init();
        stateHandler = BaseStateHandler.EMPTY_STATE;
        joypad = GenesisJoypad.create(this);
        inputProvider = InputProvider.createInstance(joypad);

        memory = MemoryProvider.createGenesisInstance();
        mcdLaunchContext = McdDeviceHelper.setupDevices();
        bus = mcdLaunchContext.mainBus;
        vdp = GenesisVdpProvider.createVdp(bus);
        cpu = MC68000Wrapper.createInstance(M68K, bus);
        z80 = Z80CoreWrapper.createInstance(getSystemType(), bus);
        vdp.addVdpEventListener(mcdLaunchContext.subBus);
        bus.attachDevices(this, memory, joypad, vdp, cpu, z80, sound);
        mcdLaunchContext.subBus.attachDevice(this);
        subCpu = mcdLaunchContext.subCpu;
        interruptHandler = mcdLaunchContext.interruptHandler;
        reloadWindowState();
        createAndAddVdpEventListener();
    }

    protected void loop() {
        updateVideoMode(true);
        do {
            runMain68k();
            runSub68k();
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

    double subCnt = 0;

    protected void runSub68k() {
        while (nextSub68kCycle <= cycleCounter) {
            boolean canRun = !subCpu.isStopped();// && !MC68000Wrapper.subCpuBusHalt;
            int cycleDelayCpu = 1;
            if (canRun) {
                Md32xRuntimeData.setAccessTypeExt(SUB_M68K);
                cycleDelayCpu = subCpu.runInstruction() + Md32xRuntimeData.resetCpuDelayExt();
            }
            //interrupts are processed after the current instruction
            interruptHandler.handleInterrupts();
            cycleDelayCpu = Math.max(1, cycleDelayCpu);
            subCnt += cycleDelayCpu; //cycles @ 12.5 Mhz
            mcdLaunchContext.stepDevices(cycleDelayCpu);
            //convert cycles @ 12.5 Mhz to cycles @ 7.67 Mhz
            nextSub68kCycle += M68K_DIVIDER * mcd68kRatio * cycleDelayCpu;
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
    @Override
    protected void updateVideoMode(boolean force) {
        if (force || displayContext.videoMode != vdp.getVideoMode()) {
            displayContext.videoMode = vdp.getVideoMode();
            double microsPerTick = getMicrosPerTick();
            sound.getFm().setMicrosPerTick(microsPerTick);
            targetNs = (long) (getRegion().getFrameIntervalMs() * Util.MILLI_IN_NS);
            mcd68kRatio = displayContext.videoMode.isPal() ? MCD_68K_RATIO_PAL : MCD_68K_RATIO_NTSC;
            mcdLaunchContext.pcm.updateVideoMode(displayContext.videoMode);
            mcdLaunchContext.cdd.updateVideoMode(displayContext.videoMode);
            LOG.info("Video mode changed: {}, mcd68kRatio: {}, microsPerTick: {}", displayContext.videoMode, mcd68kRatio, microsPerTick);
        }
    }

    private double getMicrosPerTick() {
        double mclkhz = displayContext.videoMode.isPal() ? GEN_PAL_MCLOCK_MHZ : GEN_NTSC_MCLOCK_MHZ;
        return 1_000_000.0 / (mclkhz / (FM_DIVIDER * MCLK_DIVIDER));
    }

    @Override
    protected RomContext createRomContext(SysUtil.RomSpec rom) {
        RomContext rc = new RomContext(rom);
        assert rc.romFileType.isDiscImage() ? !ZipUtil.isCompressedByteStream(rom.file) : true;
        MdCartInfoProvider mcip = MegaCdCartInfoProvider.createMcdInstance(memory, rc);
        rc.cartridgeInfoProvider = mcip;
        rc.region = RegionDetector.selectRegion(display, mcip);
        return rc;
    }

    @Override
    public void newFrame() {
        memView.update();
        mcdLaunchContext.pcm.newFrame();
        mcdLaunchContext.cdd.newFrame();
        displayContext.megaCdLedState = Optional.of(mcdLaunchContext.subBus.getLedState());
        super.newFrame();
    }

    protected UpdatableViewer createMemView() {
        return McdMemView.createInstance(bus, mcdLaunchContext, vdp.getVdpMemory());
    }

    /**
     * Counters can go negative when the video mode changes
     */
    @Override
    protected void resetCycleCounters(int counter) {
        assert nextZ80Cycle >= counter && next68kCycle >= counter &&
                nextSub68kCycle >= counter &&
                nextVdpCycle + 1 >= counter;
        logSlowFrames();
        nextZ80Cycle = Math.max(1, nextZ80Cycle - counter);
        next68kCycle = Math.max(1, next68kCycle - counter);
        nextSub68kCycle = Math.max(1, nextSub68kCycle - counter);
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
        megaCdDiscInsert();
        memView = createMemView();
    }

    //fudge it
    private void megaCdDiscInsert() {
        boolean segaMode1 = mcdLaunchContext.mainBus.isEnableMode1();
        boolean bios = mcdLaunchContext.mainBus.isBios();
        boolean tryInsertAsDisc = !segaMode1 && !bios && romContext.romFileType.isDiscImage();
        boolean biosNoDisc = false;
        boolean biosCdAudio = true;
        if (bios) {
            LOG.info("Bios mode, noDisc: {}, cdAudio: {}", biosNoDisc, biosCdAudio);
        }
        if (tryInsertAsDisc) {
            mcdLaunchContext.cdd.tryInsert(romContext.sheet);
        } else if (segaMode1 || (!bios || biosCdAudio)) {
            //insert an audio CD, for testing mode1 CD Player
            Path p = Path.of("./test_roms/SonicCD", "SonicCD_AudioOnly.cue");
            //test mcd-ver
//            Path p = Path.of("./test_roms/SonicCD", "SonicCD.cue");
            if (p.toFile().exists()) {
                ExtendedCueSheet cueSheet = new ExtendedCueSheet(p, SysUtil.RomFileType.BIN_CUE);
                mcdLaunchContext.cdd.tryInsert(cueSheet);
            }
        }
    }

    @Override
    protected void resetAfterRomLoad() {
        super.resetAfterRomLoad();
        Md32xRuntimeData.releaseInstance();
        rt = Md32xRuntimeData.newInstance(systemType);
        cpu.reset();
        subCpu.reset();
        z80.reset(); //TODO confirm this is needed
    }

    @Override
    protected void handleCloseRom() {
        super.handleCloseRom();
        mcdLaunchContext.cdd.close();
        mcdLaunchContext.pcm.close();
    }

    @Override
    protected void handleSoftReset() {
        if (softReset) {
            cpu.softReset();
        }
        super.handleSoftReset();
    }

    private void logSlowFrames() {
        long fc = getFrameCounter();
        if ((fc + 1) % romContext.region.getFps() == 0) {
            boolean rangeOk = Math.abs(MCD_SUB_68K_CLOCK_MHZ - subCnt) < 100_000; //100Khz slack
            if (fc > 100 && !rangeOk) {
                LOG.warn("Frame#{} SubCpu timing off!!!, 68K clock: {}, mode: {}", fc, subCnt, displayContext.videoMode);
            }
            subCnt = 0;
        }
    }
}
