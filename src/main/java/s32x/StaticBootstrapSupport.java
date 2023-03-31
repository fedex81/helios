package s32x;

import s32x.event.PollSysEventManager;
import s32x.sh2.Sh2Helper;
import s32x.util.S32xUtil.CpuDeviceAccess;

import static s32x.util.S32xUtil.CpuDeviceAccess.MASTER;
import static s32x.util.S32xUtil.CpuDeviceAccess.SLAVE;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class StaticBootstrapSupport {

    public interface NextCycleResettable extends PollSysEventManager.SysEventListener {
        void setNextCycle(CpuDeviceAccess cpu, int value);
    }

    public static NextCycleResettable instance;

    /**
     * TODO remove
     * one-stop shop for all the hacky bits...
     */
    public static void initStatic(NextCycleResettable instance) {
        assert instance != null;
        PollSysEventManager.instance.reset();
        PollSysEventManager.instance.addSysEventListener(instance.getClass().getSimpleName(), instance);
        DmaFifo68k.rv = false;
        Sh2Helper.clear();
        StaticBootstrapSupport.instance = instance;
    }

    public static void afterStateLoad() {
        assert instance != null;
        PollSysEventManager.instance.resetPoller(MASTER);
        PollSysEventManager.instance.resetPoller(SLAVE);
        Sh2Helper.clear();
        StaticBootstrapSupport.setNextCycleExt(MASTER, 0);
        StaticBootstrapSupport.setNextCycleExt(SLAVE, 0);
    }

    public static void setNextCycleExt(CpuDeviceAccess cpu, int value) {
        instance.setNextCycle(cpu, value);
    }
}
