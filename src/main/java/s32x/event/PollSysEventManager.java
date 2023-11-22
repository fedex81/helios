package s32x.event;

import omegadrive.Device;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import s32x.bus.Sh2Bus;
import s32x.sh2.drc.Sh2DrcBlockOptimizer.PollerCtx;
import s32x.util.Md32xRuntimeData;
import s32x.util.S32xUtil.CpuDeviceAccess;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static omegadrive.util.Util.th;
import static s32x.sh2.drc.Sh2DrcBlockOptimizer.NO_POLLER;
import static s32x.util.S32xUtil.CpuDeviceAccess.MASTER;
import static s32x.util.S32xUtil.CpuDeviceAccess.SLAVE;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public interface PollSysEventManager extends Device {

    Logger LOG = LogHelper.getLogger(PollSysEventManager.class.getSimpleName());

    PollSysEventManager instance = new SysEventManagerImpl();

    PollerCtx[] currentPollers = {NO_POLLER, NO_POLLER};
    AtomicInteger pollerActiveMask = new AtomicInteger();

    enum SysEvent {
        NONE,
        INT,
        SYS,
        SDRAM,
        FRAMEBUFFER,
        COMM,
        DMA,
        PWM,
        VDP,
        START_POLLING,
        SH2_RESET_ON,
        SH2_RESET_OFF;
    }

    void fireSysEvent(CpuDeviceAccess cpu, SysEvent event);

    boolean addSysEventListener(CpuDeviceAccess cpu, String name, SysEventListener l);

    boolean removeSysEventListener(CpuDeviceAccess cpu, String name, SysEventListener l);

    default boolean addSysEventListener(String name, SysEventListener l) {
        addSysEventListener(MASTER, name, l);
        addSysEventListener(SLAVE, name, l);
        return true;
    }

    default int removeSysEventListener(String name, SysEventListener l) {
        int num = 0;
        boolean s1 = removeSysEventListener(MASTER, name, l);
        boolean s2 = removeSysEventListener(SLAVE, name, l);
        num += (s1 ? 1 : 0) + (s2 ? 1 : 0);
        return num;
    }

    default void resetPoller(CpuDeviceAccess cpu) {
        PollerCtx pctx = PollSysEventManager.currentPollers[cpu.ordinal()];
        if (pctx != NO_POLLER) {
            pctx.stopPolling();
            PollSysEventManager.currentPollers[cpu.ordinal()] = NO_POLLER;
            pollerActiveMask.set(pollerActiveMask.get() & ~(cpu.ordinal() + 1));
        }
    }

    default void setPoller(CpuDeviceAccess cpu, PollerCtx ctx) {
        assert PollSysEventManager.currentPollers[cpu.ordinal()] == NO_POLLER;
        PollSysEventManager.currentPollers[cpu.ordinal()] = ctx;
        pollerActiveMask.set(pollerActiveMask.get() | (cpu.ordinal() + 1));
    }

    default PollerCtx getPoller(CpuDeviceAccess cpu) {
        return PollSysEventManager.currentPollers[cpu.ordinal()];
    }

    default int anyPollerActive() {
        return pollerActiveMask.get();
    }

    static int readPollValue(PollerCtx blockPoller) {
        if (blockPoller.isPollingBusyLoop()) {
            return 0;
        }
        //VF (Japan, USA) (Beta) (1995-06-15)
        //A block gets invalidated while it is polling, when polling ends we access the invalid_block
        if (!blockPoller.piw.block.isValid()) {
            LOG.warn("Unexpected state, block is invalid?\n{}", blockPoller);
            return 0;
        }
        Sh2Bus memory = blockPoller.piw.block.drcContext.memory;
        //NOTE always checks the memory value, never checks the cached value (if any);
        //could there be instances where only the cached value changes? No
        //could there be instances where only the memory value changes? Yes, but we already detect them (?)
        return memory.readMemoryUncachedNoDelay(blockPoller.blockPollData.memLoadTarget,
                blockPoller.blockPollData.memLoadTargetSize);
    }

    static boolean pollValueCheck(CpuDeviceAccess cpu, SysEvent event, PollerCtx pctx) {
        if (event == SysEvent.INT) {
            return true;
        }
        assert Md32xRuntimeData.getCpuDelayExt(cpu) == 0;
        int value = readPollValue(pctx);
        if (value == pctx.pollValue) {
            System.out.println("?? Poll stop but value unchanged: " + th(pctx.pollValue) + "," + th(value));
        }
        //TODO spot proto
        assert value != pctx.pollValue;
        return true;
    }

    interface SysEventListener {
        void onSysEvent(CpuDeviceAccess cpu, SysEvent event);
    }

    class SysEventManagerImpl implements PollSysEventManager {

        private final Map<String, SysEventListener> listenerMapMaster = new HashMap<>();
        private final Map<String, SysEventListener> listenerMapSlave = new HashMap<>();
        //reduce object creation
        private final SysEventListener[][] listenerArr = new SysEventListener[2][0];

        @Override
        public boolean addSysEventListener(CpuDeviceAccess cpu, String name, SysEventListener l) {
            var map = cpu == MASTER ? listenerMapMaster : listenerMapSlave;
            SysEventListener s = map.put(name, l);
            assert s == null;
            listenerArr[cpu.ordinal()] = map.values().toArray(new SysEventListener[0]);
            assert listenerArr[cpu.ordinal()].length == map.size();
            return true;
        }

        @Override
        public boolean removeSysEventListener(CpuDeviceAccess cpu, String name, SysEventListener l) {
            var map = cpu == MASTER ? listenerMapMaster : listenerMapSlave;
            SysEventListener s = map.remove(name);
            listenerArr[cpu.ordinal()] = map.values().toArray(new SysEventListener[0]);
            assert listenerArr[cpu.ordinal()].length == map.size();
            return s != null;
        }

        @Override
        public void fireSysEvent(CpuDeviceAccess cpu, SysEvent event) {
            assert cpu.ordinal() < 2;
            final SysEventListener[] a = listenerArr[cpu.ordinal()];
            for (int i = 0; i < a.length; i++) {
                a[i].onSysEvent(cpu, event);
            }
        }

        @Override
        public void reset() {
            listenerMapMaster.clear();
            listenerMapSlave.clear();
            listenerArr[0] = new SysEventListener[0];
            listenerArr[1] = new SysEventListener[0];
            currentPollers[0] = currentPollers[1] = NO_POLLER;
            pollerActiveMask.set(0);
        }
    }
}
