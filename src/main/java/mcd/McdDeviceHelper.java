package mcd;

import mcd.asic.Asic;
import mcd.bus.MegaCdMainCpuBus;
import mcd.bus.MegaCdSubCpuBus;
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

        public McdPcm pcm;

        public Cdd cdd;

        public Asic asic;

        public void initContext() {
            memoryContext = new MegaCdMemoryContext();
            pcm = new McdPcm();
            subBus = new MegaCdSubCpuBus(memoryContext);
            mainBus = new MegaCdMainCpuBus(memoryContext);
            subCpu = MC68000Wrapper.createInstance(SUB_M68K, subBus);
            cdd = Cdd.createInstance(memoryContext);
            asic = new Asic(memoryContext);
            subBus.attachDevice(subCpu).attachDevice(pcm).attachDevice(cdd).attachDevice(asic);
            mainBus.subCpu = subCpu;
            mainBus.subCpuBus = subBus;
            //TODO check, on boot the sub bus is set to request (ie. main has it) hence sub cpu cannot run
            subCpu.setStop(true);
        }

        public void stepDevices(int cyles) {
            pcm.step(cyles);
            cdd.step(cyles);
        }

        public void reset() {
            //TODO
        }
    }
}
