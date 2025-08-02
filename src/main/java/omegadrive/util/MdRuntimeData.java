package omegadrive.util;

import omegadrive.SystemLoader.SystemType;
import omegadrive.system.SystemProvider.SystemClock;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import org.slf4j.Logger;
import s32x.Md32x;
import s32x.sh2.Sh2Helper.Sh2Config;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.MASTER;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.cdaValues;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class MdRuntimeData {

    private static final Logger LOG = LogHelper.getLogger(Md32x.class.getSimpleName());

    private CpuDeviceAccess accessType = MASTER;
    private int accType = accessType.ordinal();
    private final int[] cpuDelay = new int[cdaValues.length];
    private final boolean ignoreDelays;

    private final SystemType type;

    private final SystemClock clock;
    private static MdRuntimeData rt;

    private MdRuntimeData(SystemType type, SystemClock clock) {
        this.type = type;
        this.clock = clock;
        boolean id = false;
        if (type == SystemType.S32X) {
            id = Sh2Config.get().ignoreDelays;
        }
        ignoreDelays = id;
    }

    public static MdRuntimeData newInstance(SystemType type, SystemClock clock) {
        if (rt != null) {
            LOG.error("Previous instance has not been released! {}", rt.type);
        }
        MdRuntimeData mrt = new MdRuntimeData(type, clock);
        rt = mrt;
        return mrt;
    }

    public static MdRuntimeData releaseInstance() {
        MdRuntimeData m = rt;
        rt = null;
        return m;
    }

    public final void addCpuDelay(int delay) {
        //NOTE in general this doesnt work as various subsystems (ie Dmac) can run while polling
//        assert accessType.regSide == S32xUtil.S32xRegSide.SH2 ?
//                !SysEventManager.instance.getPoller(accessType).isPollingActive() : true : accessType;
        cpuDelay[accType] += delay;
    }

    public final int resetCpuDelay() {
        int res = cpuDelay[accType];
        cpuDelay[accType] = 0;
        return res;
    }

    public void setAccessType(CpuDeviceAccess accessType) {
        this.accessType = accessType;
        accType = accessType.ordinal();
    }

    protected CpuDeviceAccess getAccessType() {
        return accessType;
    }

    public static void addCpuDelayExt(int delay) {
        rt.addCpuDelay(delay);
    }

    public static void addCpuDelayExt(int[][] delays, int deviceType) {
        rt.addCpuDelay(delays[rt.accType][deviceType]);
    }

    public static CpuDeviceAccess setAccessTypeExt(CpuDeviceAccess accessType) {
        CpuDeviceAccess prev = rt.accessType;
        rt.accessType = accessType;
        rt.accType = accessType.ordinal();
        return prev;
    }

    public static int resetCpuDelayExt(int value) {
        int res = rt.cpuDelay[rt.accType];
        rt.cpuDelay[rt.accType] = value;
        return rt.ignoreDelays ? 0 : res;
    }

    public static void resetCpuDelayExt(CpuDeviceAccess cpu, int value) {
        rt.cpuDelay[cpu.ordinal()] = value;
    }

    public static void resetAllCpuDelayExt() {
        for (CpuDeviceAccess v : cdaValues) {
            rt.cpuDelay[v.ordinal()] = 0;
        }
    }

    public static int resetCpuDelayExt() {
        return resetCpuDelayExt(0);
    }

    public static int getCpuDelayExt() {
        return rt.cpuDelay[rt.accType];
    }

    public static int getCpuDelayExt(CpuDeviceAccess cpu) {
        return rt.cpuDelay[cpu.ordinal()];
    }


    public static CpuDeviceAccess getAccessTypeExt() {
        return rt.accessType;
    }

    public static SystemClock getSystemClockExt() {
        return rt.clock;
    }

    public static void assertInstanceSet() {
        assert rt != null;
        if (rt == null) {
            LOG.error("Instance not set!!");
        }
    }
}
