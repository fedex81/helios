package s32x;

import omegadrive.Device;
import omegadrive.SystemLoader;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.sound.PwmProvider;
import omegadrive.system.MediaSpecHolder;
import omegadrive.system.Megadrive;
import omegadrive.system.SystemProvider;
import omegadrive.ui.DisplayWindow;
import omegadrive.util.*;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.vdp.md.MdVdp;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport;
import omegadrive.vdp.util.UpdatableViewer;
import org.slf4j.Logger;
import s32x.bus.S32xBus;
import s32x.bus.S32xBusIntf;
import s32x.event.PollSysEventManager;
import s32x.pwm.Pwm;
import s32x.sh2.Sh2;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.sh2.drc.Sh2DrcBlockOptimizer;
import s32x.util.MarsLauncherHelper;
import s32x.util.MarsLauncherHelper.Sh2LaunchContext;
import s32x.util.S32xMemView;
import s32x.vdp.MarsVdp;
import s32x.vdp.MarsVdp.MarsVdpRenderContext;
import s32x.vdp.debug.DebugVideoRenderContext;

import java.util.Optional;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.MASTER;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SLAVE;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class Md32x extends Megadrive implements StaticBootstrapSupport.NextCycleResettable {

    private static final Logger LOG = LogHelper.getLogger(Md32x.class.getSimpleName());

    private static final boolean ENABLE_FM, ENABLE_PWM;

    //23.01Mhz NTSC
    //3 cycles @ 23Mhz = 1 cycle @ 7.67, 23.01/7.67 = 3
    protected final static int SH2_CYCLE_RATIO = 3;
    private static final double SH2_CYCLE_DIV = 1 / Double.parseDouble(System.getProperty("helios.32x.sh2.cycle.div", "3.0"));
    private static final int CYCLE_TABLE_LEN_MASK = 0x1FF;
    private final static int[] sh2CycleTable = new int[CYCLE_TABLE_LEN_MASK + 1];
    private final static Sh2Config BASE_SH2_CONFIG;

    public static final int SH2_SLEEP_VALUE = -10000;

    //TODO chaotix,break with poll1, see startPollingMaybe
    //TODO fifa32x, spot proto special stage (press Y during gameplay) needs cycles < 32
    static {
        boolean prefEn = Boolean.parseBoolean(System.getProperty("helios.32x.sh2.prefetch", "true"));
        boolean drcEn = Boolean.parseBoolean(System.getProperty("helios.32x.sh2.drc", "true"));
        boolean pollEn = Boolean.parseBoolean(System.getProperty("helios.32x.sh2.poll.detect", "true"));
        boolean ignoreDelays = Boolean.parseBoolean(System.getProperty("helios.32x.sh2.ignore.delays", "false"));
        BASE_SH2_CONFIG = new Sh2Config(prefEn, drcEn, pollEn, ignoreDelays);

        Pwm.PWM_USE_BLIP = Boolean.parseBoolean(System.getProperty("helios.32x.pwm.use.blip", "false"));
        ENABLE_FM = Boolean.parseBoolean(System.getProperty("helios.32x.fm.enable", "true"));
        ENABLE_PWM = Boolean.parseBoolean(System.getProperty("helios.32x.pwm.enable", "true"));
//        System.setProperty("68k.debug", "true");
//        System.setProperty("helios.68k.debug.mode", "2");
//        System.setProperty("z80.debug", "true");
//        System.setProperty("helios.z80.debug.mode", "2");
//        System.setProperty("sh2.master.debug", "true");
//        System.setProperty("sh2.slave.debug", "true");
        LOG.info("Enable FM: {}, Enable PWM: {}", ENABLE_FM, ENABLE_PWM);
        for (int i = 0; i < sh2CycleTable.length; i++) {
            sh2CycleTable[i] = Math.max(1, (int) Math.round(i * SH2_CYCLE_DIV));
        }
        BufferUtil.assertPowerOf2Minus1("CYCLE_TABLE_LEN_MASK", CYCLE_TABLE_LEN_MASK);
    }

    public int nextMSh2Cycle = 0, nextSSh2Cycle = 0;
    protected Sh2LaunchContext launchCtx;
    private Sh2 sh2;
    private Sh2Context masterCtx, slaveCtx;
    private MarsVdp marsVdp;

    public Md32x(DisplayWindow emuFrame) {
        super(emuFrame);
        systemType = SystemLoader.SystemType.S32X;
    }

    @Override
    public void init() {
        Sh2Config.setConfig(BASE_SH2_CONFIG.withFastMode());
//        Sh2Config.setConfig(BASE_SH2_CONFIG);
        super.init();
        init32x();
        vdp.addVdpEventListener((BaseVdpAdapterEventSupport.VdpEventListener) bus);
        //TODO bit of a hack
        memView = createMemView();
    }


    protected void init32x() {
//        assert mediaSpec.cartFile.bootable;
        if (mediaSpec.hasRomCart()) { //MCD_32X has no cart
            loadRomDataIfEmpty(mediaSpec, memory);
            assert memory.getRomData().length > 0;
        }
        launchCtx = MarsLauncherHelper.setupRom(getS32xBus(), memory.getRomHolder());
        masterCtx = launchCtx.masterCtx;
        slaveCtx = launchCtx.slaveCtx;
        sh2 = launchCtx.sh2;
        marsVdp = launchCtx.marsVdp;
        //aden 0 -> cycle = 0 = not running
        nextSSh2Cycle = nextMSh2Cycle = launchCtx.s32XMMREG.aden & 1;
        marsVdp.updateDebugView(((MdVdp) vdp).getDebugViewer());
        launchCtx.pwm.setPwmProvider(ENABLE_PWM ? sound.getPwm() : PwmProvider.NO_SOUND);
        sound.setEnabled(sound.getFm(), ENABLE_FM);
        sound.setEnabled(sound.getPwm(), !Pwm.PWM_USE_BLIP);
    }

    protected S32xBusIntf getS32xBus() {
        return (S32xBusIntf) bus;
    }

    public static SystemProvider createNewInstance32x(DisplayWindow emuFrame) {
        return new Md32x(emuFrame);
    }

    @Override
    protected void loop() {
        updateVideoMode(true);
        assert cycleCounter == 1;
        do {
            run68k();
            runZ80();
            runSound();
            runSh2();
            runDevices();
            //this should be last as it could change the counter
            runVdp();
            cycleCounter++;
        } while (!futureDoneFlag);
    }

    //PAL: 1/3.0 gives ~ 450k per frame, 22.8Mhz. but the games are too slow!!!
    //53/7*burstCycles = if burstCycles = 3 -> 23.01Mhz
    protected final void runSh2() {
        if (nextMSh2Cycle == cycleCounter) {
            assert !PollSysEventManager.currentPollers[0].isPollingActive() : PollSysEventManager.currentPollers[0];
            rt.setAccessType(MASTER);
            sh2.run(masterCtx);
            assert (masterCtx.cycles_ran & CYCLE_TABLE_LEN_MASK) == masterCtx.cycles_ran : masterCtx.cycles_ran;
            assert MdRuntimeData.resetCpuDelayExt() == 0;
            nextMSh2Cycle += sh2CycleTable[masterCtx.cycles_ran];
        }
        if (nextSSh2Cycle == cycleCounter) {
            assert !PollSysEventManager.currentPollers[1].isPollingActive() : PollSysEventManager.currentPollers[1];
            rt.setAccessType(SLAVE);
            sh2.run(slaveCtx);
            assert (slaveCtx.cycles_ran & CYCLE_TABLE_LEN_MASK) == slaveCtx.cycles_ran : slaveCtx.cycles_ran;
            assert MdRuntimeData.resetCpuDelayExt() == 0;
            nextSSh2Cycle += sh2CycleTable[slaveCtx.cycles_ran];
        }
    }

    protected void runDevices() {
        assert MdRuntimeData.getCpuDelayExt() == 0;
        //NOTE if Pwm triggers dreq, the cpuDelay should be assigned to the DMA engine, not to the CPU itself
        launchCtx.pwm.step(SH2_CYCLE_RATIO);
        launchCtx.mDevCtx.sh2MMREG.deviceStepSh2Rate(SH2_CYCLE_RATIO);
        launchCtx.sDevCtx.sh2MMREG.deviceStepSh2Rate(SH2_CYCLE_RATIO);
        assert MdRuntimeData.getCpuDelayExt() == 0;
    }

    @Override
    protected MdMainBusProvider createBus() {
        return S32xBus.createS32xBus();
    }

    @Override
    protected void doRendering(int[] data) {
        MarsVdpRenderContext ctx = marsVdp.getMarsVdpRenderContext();
        boolean dumpComposite = false, dumpMars = false;
        if (dumpComposite) {
            DebugVideoRenderContext.dumpCompositeData(ctx, data, displayContext.videoMode);
        }
        if (dumpMars) {
            marsVdp.dumpMarsData();
        }
        int[] fg = marsVdp.doCompositeRendering(displayContext.videoMode, data, ctx);
        //Use 32x videoMode, can be different from MD videoMode
        //TODO support two different video modes when rendering
        VideoMode mdVm = displayContext.videoMode;
        displayContext.videoMode = ctx.vdpContext.videoMode;
        super.doRendering(fg);
        displayContext.videoMode = mdVm;
    }

    @Override
    protected void resetCycleCounters(int counter) {
        super.resetCycleCounters(counter);
        assert counter >= 0;
        if (nextMSh2Cycle >= 0) {
            assert (launchCtx.s32XMMREG.aden & 1) > 0 ? nextMSh2Cycle - counter > 0 : true;
            //NOTE Sh2s will only start at the next vblank, not immediately when aden switches
            nextMSh2Cycle = Math.max(launchCtx.s32XMMREG.aden & 1, nextMSh2Cycle - counter);
        }
        if (nextSSh2Cycle >= 0) {
            assert (launchCtx.s32XMMREG.aden & 1) > 0 ? nextSSh2Cycle - counter > 0 : true;
            nextSSh2Cycle = Math.max(launchCtx.s32XMMREG.aden & 1, nextSSh2Cycle - counter);
        }
        launchCtx.pwm.newFrame();
        launchCtx.mDevCtx.sh2MMREG.newFrame();
        launchCtx.sDevCtx.sh2MMREG.newFrame();
        launchCtx.memory.newFrame();
        if (verbose) LOG.info("New frame: {}", telemetry.getFrameCounter());
        if (telemetry.getFrameCounter() % getRegion().getFps() == 0) { //reset every second
            slowFramesAcc = 0;
        }
    }

    private long slowFramesAcc;

    @Override
    protected long syncCycle(long startCycle) {
        long now = System.nanoTime();
        if (fullThrottle) {
            return now;
        }
        long baseRemainingNs = startCycle + targetNs;
        long remainingNs = baseRemainingNs - now;
        slowFramesAcc += remainingNs;
        if (slowFramesAcc > 0) {
            remainingNs = slowFramesAcc;
            slowFramesAcc = 0;
        }
        if (remainingNs > 0) { //too fast
            Sleeper.parkFuzzy(remainingNs);
            remainingNs = baseRemainingNs - System.nanoTime();
        }
        return System.nanoTime();
    }

    protected UpdatableViewer createMemView() {
        if (launchCtx != null) {
            return S32xMemView.createInstance(bus, launchCtx.memory, vdp.getVdpMemory());
        }
        return UpdatableViewer.NO_OP_VIEWER;
    }

    @Override
    protected void handleCloseRom() {
        super.handleCloseRom();
        Optional.ofNullable(marsVdp).ifPresent(Device::reset);
        Optional.ofNullable(launchCtx).ifPresent(lc -> lc.pwm.reset());
    }

    @Override
    public void handleNewRom(MediaSpecHolder romSpec) {
        super.handleNewRom(romSpec);
        StaticBootstrapSupport.initStatic(this);
    }

    @Override
    public void onSysEvent(CpuDeviceAccess cpu, PollSysEventManager.SysEvent event) {
        switch (event) {
            case START_POLLING -> {
                final Sh2DrcBlockOptimizer.PollerCtx pc = PollSysEventManager.instance.getPoller(cpu);
                assert pc.isPollingActive() : event + "," + pc;
                setNextCycle(cpu, SH2_SLEEP_VALUE);
                MdRuntimeData.resetCpuDelayExt(cpu, 0);
                if (verbose) LOG.info("{} {} {}: {}", cpu, event, cycleCounter, pc);
            }
            case SH2_RESET_ON -> {
                setNextCycle(MASTER, SH2_SLEEP_VALUE);
                setNextCycle(SLAVE, SH2_SLEEP_VALUE);
            }
            case SH2_RESET_OFF -> {
                setNextCycle(MASTER, cycleCounter + 1);
                setNextCycle(SLAVE, cycleCounter + 2);
                MdRuntimeData.resetCpuDelayExt(MASTER, 0);
                MdRuntimeData.resetCpuDelayExt(SLAVE, 0);
            }
            //stop polling
            default -> stopPolling(cpu, event);
        }
    }

    private void stopPolling(CpuDeviceAccess cpu, PollSysEventManager.SysEvent event) {
        final Sh2DrcBlockOptimizer.PollerCtx pctx = PollSysEventManager.instance.getPoller(cpu);
//        assert event == SysEventManager.SysEvent.INT ? pc.isPollingBusyLoop() : true;
        boolean stopOk = event == pctx.event || event == PollSysEventManager.SysEvent.INT;
        if (stopOk) {
            if (verbose) LOG.info("{} stop polling {} {}: {}", cpu, event, cycleCounter, pctx);
            setNextCycle(cpu, cycleCounter + 1);
            PollSysEventManager.instance.resetPoller(cpu);
        }
        if (BufferUtil.assertionsEnabled && !stopOk) {
            LOG.warn("{} {} ignore stop polling: {}", cpu, event, pctx);
        }
    }

    public void setNextCycle(CpuDeviceAccess cpu, int value) {
        if (verbose) LOG.info("{} {} sleeping, nextCycle: {}", cpu, value < 0 ? "START" : "STOP", value);
        if (cpu == MASTER) {
            nextMSh2Cycle = value;
        } else {
            assert cpu == SLAVE;
            nextSSh2Cycle = value;
        }
    }

    //TODO buggy
    @Override
    protected void handleSoftReset() {
        if (softResetPending) {
            sh2.reset(masterCtx);
            sh2.reset(slaveCtx);
            launchCtx.reset();
            MdRuntimeData.resetAllCpuDelayExt();
            StaticBootstrapSupport.initStatic(this);
            nextMSh2Cycle = nextSSh2Cycle = 1;
        }
        super.handleSoftReset();
    }
}
