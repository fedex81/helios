package s32x;

import mcd.McdDeviceHelper;
import mcd.MegaCd;
import mcd.bus.Mcd32xMainBus;
import mcd.bus.McdSubInterruptHandler;
import omegadrive.SystemLoader.SystemType;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
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
 * This supports CD only MCD_32X, cart slot is empty.
 * - Night Trap (USA) (Disc 1) (Sega CD 32X) (RE-1), works with choppy audio (SegaCD emulation issue)
 * TODO support CD + cart
 */
public class MegaCd32x extends Md32x {

    private static final Logger LOG = LogHelper.getLogger(MegaCd32x.class.getSimpleName());

    protected double nextSub68kCycle = M68K_DIVIDER;
    protected M68kProvider subCpu;

    protected McdSubInterruptHandler interruptHandler;
    protected McdDeviceHelper.McdLaunchContext mcdLaunchContext;
    private double mcd68kRatio;
    protected S32xBusIntf s32xBus;

    //TODO debug
    private final static SystemType forceSystem
            = null;
//                = SystemType.MEGACD;
    // = SystemType.S32X;

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
        Mcd32xMainBus b = new Mcd32xMainBus(mcdLaunchContext, forceSystem);
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
        if (forceSystem == SystemType.MEGACD) {
            displayContext.videoMode = vdp.getVideoMode();
        }
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
        super.handleSoftReset();
        if (softReset) {
            subCpu.softReset();
        }
    }

    @Override
    protected void resetAfterRomLoad() {
        super.resetAfterRomLoad();
        subCpu.reset();
    }

    @Override
    protected void initAfterRomLoad() {
        super.initAfterRomLoad();
        MegaCd.megaCdDiscInsert(mcdLaunchContext, mediaSpec);
    }
}
