/*
 * MegaCd
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
import mcd.cdd.ExtendedCueSheet;
import mcd.util.McdMemView;
import omegadrive.SystemLoader;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.Megadrive;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.VideoMode;
import omegadrive.vdp.util.UpdatableViewer;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.GEN_NTSC_MCLOCK_MHZ;
import static omegadrive.util.Util.GEN_PAL_MCLOCK_MHZ;

/**
 * Megadrive main class
 *
 * @author Federico Berti
 */
public class MegaCd extends Megadrive {

    private final static Logger LOG = LogHelper.getLogger(MegaCd.class.getSimpleName());

    public final static boolean verbose = false;
    public final static double MCD_SUB_68K_CLOCK_MHZ = 12_500_000;

    //mcd-verificator(NTSC) is very sensitive
    public final static double MCD_68K_RATIO_NTSC = 1.0 / (MCD_SUB_68K_CLOCK_MHZ / (GEN_NTSC_MCLOCK_MHZ / 7.0));
    public final static double MCD_68K_RATIO_PAL = 1.0 / (MCD_SUB_68K_CLOCK_MHZ / (GEN_PAL_MCLOCK_MHZ / 7.0));

    private double mcd68kRatio;

    //OK -> EU-bios 1.00 (f891e0ea651e2232af0c5c4cb46a0cae2ee8f356)
    //OK -> US-bios 1.00 (c5c24e6439a148b7f4c7ea269d09b7a23fe25075)
    //OK -> JP-bios 1.00H (aka 100s) (230ebfc49dc9e15422089474bcc9fa040f2c57eb)
    //for JP press start and then select CD-ROM
    static {
        System.setProperty("68k.debug", "false");
        System.setProperty("helios.68k.debug.mode", "0");
        System.setProperty("z80.debug", "false");
    }

    protected M68kProvider subCpu;

    protected McdSubInterruptHandler interruptHandler;
    protected McdDeviceHelper.McdLaunchContext mcdLaunchContext;
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
        mcdLaunchContext = McdDeviceHelper.setupDevices();
        super.init();
        vdp.addVdpEventListener(mcdLaunchContext.subBus);
        bus.attachDevices(this, memory, joypad, vdp, cpu, z80, sound);
        mcdLaunchContext.subBus.attachDevice(this);
        subCpu = mcdLaunchContext.subCpu;
        interruptHandler = mcdLaunchContext.interruptHandler;
        subCpu.reset();
    }

    @Override
    protected void postInit() {
        super.postInit();
        megaCdDiscInsert(mcdLaunchContext, mediaSpec);
    }

    @Override
    protected MdMainBusProvider createBus() {
        assert mcdLaunchContext.mainBus != null;
        bus = mcdLaunchContext.mainBus;
        return bus;
    }

    protected void loop() {
        updateVideoMode(true);
        do {
            run68k();
            runSub68k();
            runZ80();
            runFM();
            //this should be last as it could change the counter
            runVdp();
            cycleCounter++;
        } while (!futureDoneFlag);
    }

    double subCnt = 0;

    protected void runSub68k() {
        while (nextSub68kCycle <= cycleCounter) {
            boolean canRun = !subCpu.isStopped();// && !MC68000Wrapper.subCpuBusHalt;
            int cycleDelayCpu = 1;
            MdRuntimeData.setAccessTypeExt(SUB_M68K);
            if (canRun) {
                cycleDelayCpu = subCpu.runInstruction() + MdRuntimeData.resetCpuDelayExt();
            }
            //interrupts are processed after the current instruction
            interruptHandler.handleInterrupts();
            cycleDelayCpu = Math.max(1, cycleDelayCpu);
            subCnt += cycleDelayCpu; //cycles @ 12.5 Mhz
            mcdLaunchContext.stepDevices(cycleDelayCpu);
            //convert cycles @ 12.5 Mhz to cycles @ 7.67 Mhz
            nextSub68kCycle += M68K_DIVIDER * mcd68kRatio * cycleDelayCpu;
            assert MdRuntimeData.resetCpuDelayExt() == 0;
        }
    }

    @Override
    protected void updateVideoMode(boolean force) {
        VideoMode prev = displayContext.videoMode;
        super.updateVideoMode(force);
        if (force || prev != vdp.getVideoMode()) {
            mcd68kRatio = displayContext.videoMode.isPal() ? MCD_68K_RATIO_PAL : MCD_68K_RATIO_NTSC;
            mcdLaunchContext.pcm.updateVideoMode(displayContext.videoMode);
            mcdLaunchContext.cdd.updateVideoMode(displayContext.videoMode);
            mcdLaunchContext.interruptHandler.setRegion(displayContext.videoMode.getRegion());
            LOG.info("Video mode changed: {}, mcd68kRatio: {}", displayContext.videoMode, mcd68kRatio);
        }
    }

    @Override
    public void newFrame() {
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
        super.resetCycleCounters(counter);
        assert nextSub68kCycle >= counter;
        logSlowFrames();
        nextSub68kCycle = Math.max(1, nextSub68kCycle - counter);
    }

    //fudge it
    public static void megaCdDiscInsert(McdDeviceHelper.McdLaunchContext mcdLaunchContext, MediaSpecHolder mediaSpec) {
        boolean segaMode1 = mcdLaunchContext.mainBus.isEnableMode1();
        boolean bios = mcdLaunchContext.mainBus.isBios();
        boolean tryInsertAsDisc = !segaMode1 && !bios && mediaSpec.hasDiscImage();
        boolean biosNoDisc = false;
        boolean biosCdAudio = true;
        if (bios) {
            LOG.info("Bios mode, noDisc: {}, cdAudio: {}", biosNoDisc, biosCdAudio);
        }
        if (tryInsertAsDisc) {
            assert mediaSpec.cdFile.sheetOpt.isPresent();
            mcdLaunchContext.cdd.tryInsert(mediaSpec.cdFile.sheetOpt.get());
        } else if (segaMode1 || (!bios || biosCdAudio)) {
            //insert an audio CD, for testing mode1 CD Player
            Path p = Path.of("./test_roms/SonicCD", "SonicCD_AudioOnly.cue");
            //test mcd-ver
//            Path p = Path.of("./test_roms/SonicCD", "SonicCD.cue");
            if (Files.exists(p)) {
                ExtendedCueSheet cueSheet = new ExtendedCueSheet(p, SysUtil.RomFileType.BIN_CUE);
                mcdLaunchContext.cdd.tryInsert(cueSheet);
            }
        }
    }

    @Override
    protected void handleCloseRom() {
        super.handleCloseRom();
        mcdLaunchContext.cdd.close();
        mcdLaunchContext.pcm.close();
    }

    @Override
    protected void handleSoftReset() {
        if (softResetPending) {
            subCpu.softReset();
        }
        super.handleSoftReset();
    }

    private void logSlowFrames() {
        long fc = getFrameCounter();
        if ((fc + 1) % mediaSpec.getRegion().getFps() == 0) {
            boolean rangeOk = Math.abs(MCD_SUB_68K_CLOCK_MHZ - subCnt) < 100_000; //100Khz slack
            if (fc > 100 && !rangeOk) {
                LOG.warn("Frame#{} SubCpu timing off!!!, 68K clock: {}, mode: {}", fc, subCnt, displayContext.videoMode);
            }
            subCnt = 0;
        }
    }
}
