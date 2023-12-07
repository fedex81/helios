package s32x.util;

import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import s32x.Md32x;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.util.S32xUtil.CpuDeviceAccess;

import static s32x.util.S32xUtil.CpuDeviceAccess.MASTER;
import static s32x.util.S32xUtil.CpuDeviceAccess.cdaValues;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Md32xRuntimeData {

    private static final Logger LOG = LogHelper.getLogger(Md32x.class.getSimpleName());

    private CpuDeviceAccess accessType = MASTER;
    private int accType = accessType.ordinal();
    private final int[] cpuDelay = new int[cdaValues.length];
    private final boolean ignoreDelays;

    private static Md32xRuntimeData rt;

    private Md32xRuntimeData() {
        ignoreDelays = Sh2Config.get().ignoreDelays;
    }

    public static Md32xRuntimeData newInstance() {
        if (rt != null) {
            LOG.error("Previous instance has not been released! {}", rt);
        }
        Md32xRuntimeData mrt = new Md32xRuntimeData();
        rt = mrt;
        return mrt;
    }

    public static Md32xRuntimeData releaseInstance() {
        Md32xRuntimeData m = rt;
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

    public static void setAccessTypeExt(CpuDeviceAccess accessType) {
        rt.accessType = accessType;
        rt.accType = accessType.ordinal();
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
}
