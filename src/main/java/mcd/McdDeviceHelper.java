package mcd;

import mcd.asic.Asic;
import mcd.bus.McdSubInterruptHandler;
import mcd.bus.MegaCdMainCpuBus;
import mcd.bus.MegaCdSubCpuBus;
import mcd.cdc.Cdc;
import mcd.cdd.Cdd;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.cpu.m68k.MC68000Wrapper;
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
        ctx.initContext();
        return ctx;
    }

    public static class McdLaunchContext {
        public MC68000Wrapper subCpu;
        public MegaCdSubCpuBus subBus;
        public MegaCdMainCpuBus mainBus;
        public MegaCdMemoryContext memoryContext;
        public McdSubInterruptHandler interruptHandler;

        public McdPcm pcm;

        public Cdd cdd;
        public Cdc cdc;

        public Asic asic;

        public void initContext() {
            memoryContext = new MegaCdMemoryContext();
            pcm = new McdPcm();
            subBus = new MegaCdSubCpuBus(memoryContext);
            mainBus = new MegaCdMainCpuBus(memoryContext);
            subCpu = MC68000Wrapper.createInstance(SUB_M68K, subBus);
            interruptHandler = McdSubInterruptHandler.create(memoryContext, subCpu);
            cdd = Cdd.createInstance(memoryContext, interruptHandler);
            cdc = Cdc.createInstance(memoryContext, interruptHandler);
            asic = new Asic(memoryContext, interruptHandler);
            subBus.attachDevices(subCpu, pcm, cdd, asic, cdc, interruptHandler);
            mainBus.subCpu = subCpu;
            mainBus.subCpuBus = subBus;
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
