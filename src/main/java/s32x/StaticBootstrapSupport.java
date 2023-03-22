package s32x;

import s32x.event.PollSysEventManager;
import s32x.sh2.Sh2Helper;
import s32x.util.S32xUtil;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class StaticBootstrapSupport {

    public static Md32x instance;

    /**
     * TODO remove
     * one-stop shop for all the hacky bits...
     */
    public static void initStatic(Md32x instance) {
        PollSysEventManager.instance.reset();
        PollSysEventManager.instance.addSysEventListener(instance.getClass().getSimpleName(), instance);
        DmaFifo68k.rv = false;
        Sh2Helper.clear();
        StaticBootstrapSupport.instance = instance;
    }

    public static void setNextCycleExt(S32xUtil.CpuDeviceAccess cpu, int value) {
        instance.setNextCycle(cpu, value);
    }
}
