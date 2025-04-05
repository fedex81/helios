package s32x;

import mcd.McdDeviceHelper;
import mcd.MegaCd;
import mcd.bus.Mcd32xMainBus;
import mcd.bus.McdSubInterruptHandler;
import omegadrive.SystemLoader.SystemType;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SysUtil;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Util;
import omegadrive.util.VideoMode;
import omegadrive.vdp.util.UpdatableViewer;
import org.slf4j.Logger;
import s32x.bus.S32xBusIntf;

import java.util.Optional;

import static mcd.MegaCd.MCD_68K_RATIO_NTSC;
import static mcd.MegaCd.MCD_68K_RATIO_PAL;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.vdp.util.MemView.NO_MEMVIEW;

/**
 * MegaCd + 32x
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2025
 *
 * MCD_32X software, cart slot is empty.
 * - Night Trap (USA) (Disc 1) (Sega CD 32X) (RE-1), works with choppy audio (SegaCD emulation issue)
 */
public class MegaCd32x extends Md32x {

    private static final Logger LOG = LogHelper.getLogger(MegaCd32x.class.getSimpleName());

    protected double nextSub68kCycle = M68K_DIVIDER;
    protected M68kProvider subCpu;

    protected McdSubInterruptHandler interruptHandler;
    protected McdDeviceHelper.McdLaunchContext mcdLaunchContext;
    private double mcd68kRatio;
    protected S32xBusIntf s32xBus;
    double subCnt = 0;

    static {
        //TODO homebrew bios does not support SCD-32X mode (ie. no 32x cart, waitForCD and then boot)
        System.setProperty("32x.use.homebrew.bios", "false");
//        System.setProperty("68k.debug", "true");
//        System.setProperty("helios.68k.debug.mode", "2");
//        System.setProperty("sh2.master.debug", "true");
//        System.setProperty("sh2.slave.debug", "true");
    }

    public MegaCd32x(DisplayWindow emuFrame) {
        super(emuFrame);
        systemType = SystemType.MEGACD_S32X;
        LogHelper.logWarnOnceForce(LOG, "TowerOfPower");
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
        MegaCd.megaCdDiscInsert(mcdLaunchContext, mediaSpec);
        checkDoomFusion();
        subCpu.reset();
    }

    @Override
    protected S32xBusIntf getS32xBus() {
        assert s32xBus != null;
        return s32xBus;
    }

    public static SystemProvider createNewInstance(DisplayWindow emuFrame) {
        return new MegaCd32x(emuFrame);
    }

    @Override
    protected void loop() {
        updateVideoMode(true);
        assert cycleCounter == 1;
        do {
            run68k();
            runSub68k();
            runZ80();
            runFM();
            runSh2();
            runDevices();
            //this should be last as it could change the counter
            runVdp();
            cycleCounter++;
        } while (!futureDoneFlag);
    }


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
        //Mcd stuff
        if (force || prev != vdp.getVideoMode()) {
            assert displayContext.videoMode == vdp.getVideoMode();
            mcd68kRatio = displayContext.videoMode.isPal() ? MCD_68K_RATIO_PAL : MCD_68K_RATIO_NTSC;
            mcdLaunchContext.pcm.updateVideoMode(displayContext.videoMode);
            mcdLaunchContext.cdd.updateVideoMode(displayContext.videoMode);
            mcdLaunchContext.interruptHandler.setRegion(displayContext.videoMode.getRegion());
            LOG.info("Video mode changed: {}, mcd68kRatio: {}", displayContext.videoMode, mcd68kRatio);
            //32x hack
//            ((BaseVdpAdapterEventSupport.VdpEventListener)s32xBus).onVdpEvent(BaseVdpAdapterEventSupport.VdpEvent.VIDEO_MODE, displayContext.videoMode);
        }
    }

    @Override
    protected MdMainBusProvider createBus() {
        Mcd32xMainBus b = new Mcd32xMainBus(mcdLaunchContext);
        s32xBus = b.s32xBus;
        return b;
    }

    @Override
    public void newFrame() {
        mcdLaunchContext.pcm.newFrame();
        mcdLaunchContext.cdd.newFrame();
        displayContext.megaCdLedState = Optional.of(mcdLaunchContext.subBus.getLedState());
        super.newFrame();
    }

    @Override
    protected void doRendering(int[] data) {
        super.doRendering(data);
        //TODO use MD video mode, 32x can use a different video mode
        displayContext.videoMode = vdp.getVideoMode();
    }

    @Override
    protected void resetCycleCounters(int counter) {
        super.resetCycleCounters(counter);
        assert nextSub68kCycle >= counter;
        nextSub68kCycle = Math.max(1, nextSub68kCycle - counter);
    }

    protected UpdatableViewer createMemView() {
        return NO_MEMVIEW;
    }

    @Override
    protected void handleCloseRom() {
        super.handleCloseRom();
        mcdLaunchContext.cdd.close();
        mcdLaunchContext.pcm.close();
    }

    @Override
    public void handleNewRom(MediaSpecHolder romSpec) {
        super.handleNewRom(romSpec);
        StaticBootstrapSupport.initStatic(this);
    }

    @Override
    protected void handleSoftReset() {
        if (softResetPending) {
            subCpu.softReset();
        }
        super.handleSoftReset();
    }

    @Override
    protected void init32x() {
        super.init32x();
    }

    private void checkDoomFusion() {
        //TODO HACK for Doom Fusion
        //CD32x official releases boot from CD with no cart inserted
        //DoomFusion boots from cart with CD inserted
        MdCartInfoProvider cartInfo = ((MdCartInfoProvider) mediaSpec.getBootableMedia().mediaInfoProvider);
        String serial = cartInfo.getSerial();
        String name = cartInfo.getRomName();
        if (name.startsWith("DCD32X") || serial.contains("DMF32XCD")) {
            String isoName = Util.getNameWithoutExtension(name) + ".iso";
            MediaSpecHolder.MediaSpec iso = MediaSpecHolder.MediaSpec.of(
                    mediaSpec.getBootableMedia().romFile.resolveSibling(isoName), SysUtil.RomFileType.ISO, mediaSpec.systemType);
            assert iso.romFile.toFile().exists();
            LOG.warn("DoomCD32x Fusion detected attempting to load iso file: {}", iso.romFile.toAbsolutePath());
            mediaSpec.cdFile = iso;
            mediaSpec.systemType = iso.systemType = mediaSpec.cartFile.systemType = SystemType.MEGACD_S32X;
            mediaSpec.reload();
            MegaCd.megaCdDiscInsert(mcdLaunchContext, mediaSpec);
        } else {
            assert mediaSpec.hasDiscImage();
            mediaSpec.systemType = mediaSpec.cdFile.systemType = SystemType.MEGACD_S32X;
        }
    }
}
