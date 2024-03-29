package s32x;

import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import s32x.event.PollSysEventManager;
import s32x.sh2.Sh2Helper;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.MASTER;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SLAVE;

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
        //TODO soft reset shouldn't do this
        DmaFifo68k.rv = false;
        Sh2Helper.clear();
        StaticBootstrapSupport.instance = instance;
        LogHelper.clear();
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
