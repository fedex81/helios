package s32x.util;

import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.RomHolder;
import org.slf4j.Logger;
import s32x.DmaFifo68k;
import s32x.S32XMMREG;
import s32x.bus.S32xBusIntf;
import s32x.bus.Sh2Bus;
import s32x.bus.Sh2BusImpl;
import s32x.pwm.Pwm;
import s32x.sh2.*;
import s32x.sh2.device.Sh2DeviceHelper;
import s32x.sh2.device.Sh2DeviceHelper.Sh2DeviceContext;
import s32x.vdp.MarsVdp;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static s32x.sh2.prefetch.Sh2Prefetch.Sh2DrcContext;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class MarsLauncherHelper {

    private static final Logger LOG = LogHelper.getLogger(MarsLauncherHelper.class.getSimpleName());

    static final boolean masterDebug = Boolean.parseBoolean(System.getProperty("sh2.master.debug", "false"));
    static final boolean slaveDebug = Boolean.parseBoolean(System.getProperty("sh2.slave.debug", "false"));
    static final boolean homebrewBios = Boolean.parseBoolean(System.getProperty("32x.use.homebrew.bios", "true"));

    static String biosBasePath = "res/bios/";

    static String masterBiosName = "32x_bios_m.bin";
    static String slaveBiosName = "32x_bios_s.bin";
    static String mdBiosName = "32x_bios_g.bin";
    static String hb_masterBiosName = "32x_hbrew_bios_m.bin";
    static String hb_slaveBiosName = "32x_hbrew_bios_s.bin";
    static String hb_mdBiosName = "32x_hbrew_bios_g.bin";


    public static BiosHolder initBios() {
        BiosHolder bh = doInit(homebrewBios);
        if (bh == BiosHolder.NO_BIOS && !homebrewBios) {
            System.out.println("Unable to find official bios files, attempting to use homebrew bios");
            bh = doInit(true);
        }
        if (bh == BiosHolder.NO_BIOS) {
            LOG.error("Unable to find bios files");
            System.err.println("Unable to find bios files");
        }
        return bh;
    }

    private static BiosHolder doInit(boolean isHb) {
        if (isHb) {
            LOG.info("Using homebrew bios: {}, {}, {}", hb_masterBiosName, hb_slaveBiosName, hb_mdBiosName);
            masterBiosName = hb_masterBiosName;
            slaveBiosName = hb_slaveBiosName;
            mdBiosName = hb_mdBiosName;
        }
        BiosHolder biosHolder = BiosHolder.NO_BIOS;
        try {
            Path biosMasterPath = Paths.get(biosBasePath, masterBiosName);
            Path biosSlavePath = Paths.get(biosBasePath, slaveBiosName);
            Path biosM68kPath = Paths.get(biosBasePath, mdBiosName);
            biosHolder = new BiosHolder(biosMasterPath, biosSlavePath, biosM68kPath);
        } catch (Exception | Error e) {
            biosHolder = BiosHolder.NO_BIOS;
            if (isHb) {
                e.printStackTrace();
            }
        }
        return biosHolder;
    }

    public static Sh2LaunchContext setupRom(S32xBusIntf bus, RomHolder romHolder) {
        return setupRom(bus, romHolder, initBios());
    }

    public static Sh2LaunchContext setupRom(S32xBusIntf bus, RomHolder romHolder, BiosHolder biosHolder) {
        Sh2Helper.Sh2Config cfg = Sh2Helper.Sh2Config.get();
        Sh2LaunchContext ctx = new Sh2LaunchContext();
        ctx.masterCtx = new Sh2Context(BufferUtil.CpuDeviceAccess.MASTER, cfg.sh2Cycles, masterDebug);
        ctx.slaveCtx = new Sh2Context(BufferUtil.CpuDeviceAccess.SLAVE, cfg.sh2Cycles, slaveDebug);
        ctx.biosHolder = biosHolder;
        ctx.bus = bus;
        ctx.rom = ByteBuffer.wrap(romHolder.data);
        ctx.s32XMMREG = new S32XMMREG();
        ctx.dmaFifo68k = new DmaFifo68k(ctx.s32XMMREG.regContext);
        Sh2DrcContext mDrcCtx = new Sh2DrcContext();
        Sh2DrcContext sDrcCtx = new Sh2DrcContext();
        mDrcCtx.sh2Ctx = ctx.masterCtx;
        sDrcCtx.sh2Ctx = ctx.slaveCtx;
        mDrcCtx.cpu = ctx.masterCtx.cpuAccess;
        sDrcCtx.cpu = ctx.slaveCtx.cpuAccess;

        Sh2Bus memory = new Sh2BusImpl(ctx.s32XMMREG, ctx.rom, biosHolder, bus, mDrcCtx, sDrcCtx);
        ctx.memory = memory;
        ctx.mDevCtx = Sh2DeviceHelper.createDevices(ctx.masterCtx, ctx);
        ctx.sDevCtx = Sh2DeviceHelper.createDevices(ctx.slaveCtx, ctx);
        ctx.mDevCtx.sci.setOther(ctx.sDevCtx.sci);
        ctx.sh2 = (ctx.masterCtx.debug || ctx.slaveCtx.debug) ?
                new Sh2Debug(ctx.memory) : new Sh2Impl(ctx.memory);
        mDrcCtx.sh2 = sDrcCtx.sh2 = ctx.sh2;
        mDrcCtx.memory = sDrcCtx.memory = ctx.memory;
        ctx.pwm = new Pwm(ctx.s32XMMREG.regContext);
        ctx.masterCtx.devices = ctx.mDevCtx;
        ctx.slaveCtx.devices = ctx.sDevCtx;
        ctx.initContext();
        return ctx;
    }

    public static class Sh2LaunchContext {
        public Sh2Context masterCtx, slaveCtx;
        public Sh2DeviceContext mDevCtx, sDevCtx;
        public S32xBusIntf bus;
        public BiosHolder biosHolder;
        public Sh2Bus memory;
        public Sh2 sh2;
        public DmaFifo68k dmaFifo68k;
        public S32XMMREG s32XMMREG;
        public ByteBuffer rom;
        public MarsVdp marsVdp;
        public Pwm pwm;

        public void initContext() {
            bus.attachDevices(sh2, s32XMMREG);
            memory.getSh2MMREGS(BufferUtil.CpuDeviceAccess.MASTER).init(mDevCtx);
            memory.getSh2MMREGS(BufferUtil.CpuDeviceAccess.SLAVE).init(sDevCtx);
            s32XMMREG.setInterruptControl(mDevCtx.intC, sDevCtx.intC);
            s32XMMREG.setDmaControl(dmaFifo68k);
            s32XMMREG.setPwm(pwm);
            pwm.setIntControls(mDevCtx.intC, sDevCtx.intC);
            pwm.setDmac(mDevCtx.dmaC, sDevCtx.dmaC);
            dmaFifo68k.setDmac(mDevCtx.dmaC, sDevCtx.dmaC);
            mDevCtx.dmaC.setDma68s(dmaFifo68k);
            sDevCtx.dmaC.setDma68s(dmaFifo68k);
            bus.setBios68k(biosHolder.getBiosData(BufferUtil.CpuDeviceAccess.M68K));
            bus.setRom(rom);
            bus.setSh2Context(masterCtx, slaveCtx);
            sh2.reset(masterCtx);
            sh2.reset(slaveCtx);
            marsVdp = bus.getMarsVdp();
        }

        public void reset() {
            mDevCtx.sh2MMREG.reset();
            sDevCtx.sh2MMREG.reset();
            pwm.reset();
            s32XMMREG.reset();
            memory.reset();
            dmaFifo68k.reset();
        }
    }
}
