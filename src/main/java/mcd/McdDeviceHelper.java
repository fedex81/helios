package mcd;

import mcd.asic.Asic;
import mcd.asic.AsicModel;
import mcd.bus.*;
import mcd.cdc.Cdc;
import mcd.cdd.Cdd;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.bus.md.MdBus;
import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.sound.SoundProvider;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class McdDeviceHelper {

    private static final Logger LOG = LogHelper.getLogger(McdDeviceHelper.class.getSimpleName());

    private McdDeviceHelper() {
        assert false;
    }

    public static McdLaunchContext setupDevices() {
        McdLaunchContext ctx = new McdLaunchContext();
        ctx.initContext(false);
        return ctx;
    }

    /**
     * NO_SOUND
     */
    public static McdLaunchContext setupDevicesTest() {
        McdLaunchContext ctx = new McdLaunchContext();
        ctx.initContext(true);
        return ctx;
    }

    public static class McdLaunchContext {
        public MC68000Wrapper subCpu;
        public MdMainBusProvider mdBus;
        public MegaCdSubCpuBusIntf subBus;
        public MegaCdMainCpuBusIntf mainBus;
        public MegaCdMemoryContext memoryContext;
        public McdSubInterruptHandler interruptHandler;

        public McdPcm pcm;

        public Cdd cdd;
        public Cdc cdc;

        public AsicModel.AsicOp asic;

        public void initContext(boolean testMode) {
            boolean soundEnabled = !testMode && SoundProvider.ENABLE_SOUND;
            LOG.info("Sound enabled: {}, testMode: {}, enableSound flag: {}", soundEnabled, testMode, SoundProvider.ENABLE_SOUND);
            memoryContext = new MegaCdMemoryContext();
            pcm = new McdPcm(soundEnabled);
            mdBus = new MdBus();
            subBus = new MegaCdSubCpuBus(memoryContext);
            mainBus = new MegaCdMainCpuBus(memoryContext, mdBus);

            subCpu = MC68000Wrapper.createInstance(SUB_M68K, subBus);
            interruptHandler = McdSubInterruptHandler.create(memoryContext, subCpu);
            cdc = Cdc.createInstance(memoryContext, interruptHandler);
            cdd = Cdd.createInstance(memoryContext, interruptHandler, cdc, soundEnabled);
            asic = new Asic(memoryContext, interruptHandler);
            subBus.attachDevices(subCpu, pcm, cdd, asic, cdc, interruptHandler);
            mainBus.setSubDevices(subCpu, subBus);
            //TODO check, on boot the sub bus is set to request (ie. main has it) hence sub cpu cannot run
            subCpu.setStop(true);
        }

        public void stepDevices(int cyles) {
            subBus.step(cyles);
        }

        public void reset() {
            //TODO
        }
    }
}
