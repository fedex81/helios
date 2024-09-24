package mcd.util;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.dict.S32xDict;
import s32x.dict.S32xDict.RegSpecS32x;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class Md32xCommHelper {

    private static final Logger LOG = LogHelper.getLogger(Md32xCommHelper.class.getSimpleName());

    public static final int CYCLE_LOOKBACK = 50;

    private static Md32xCommHelper INSTANCE = new Md32xCommHelper();

    public static Md32xCommHelper getInstance() {
        return INSTANCE;
    }

    static class CommWriteInfo {
        public CpuDeviceAccess cpu;
        public RegSpecS32x regSpec;
        public int value, prevValue;
        public Size size;

        public int cycle;
        public long frame;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("cpu", cpu)
                    .add("regSpec", regSpec)
                    .add("value", th(value))
                    .add("prevValue", th(prevValue))
                    .add("size", size)
                    .add("cycle", cycle)
                    .add("frame", frame)
                    .toString();
        }
    }

    long currentFrame = 0;

    private Table<Integer, RegSpecS32x, CommWriteInfo> t = TreeBasedTable.create();

    public void write(CpuDeviceAccess cpu, RegSpecS32x regSpec, int prevValue, int nextValue, Size size) {
        switch (size) {
            //TODO byte is wrong??
            case BYTE, WORD -> writeWord(cpu, regSpec, prevValue, nextValue);
            case LONG -> {
                RegSpecS32x regSpec2 = S32xDict.getRegSpec(cpu, regSpec.addr + 2);
                writeWord(cpu, regSpec, prevValue >> 16, nextValue >> 16);
                writeWord(cpu, regSpec2, prevValue & 0xFFFF, nextValue & 0xFFFF);
            }
        }
    }

    private void writeWord(CpuDeviceAccess cpu, RegSpecS32x regSpec, int prevValue, int nextValue) {
        assert MdRuntimeData.getAccessTypeExt() == cpu;
        SystemProvider.SystemClock clock = MdRuntimeData.getSystemClockExt();
        CommWriteInfo cwi = new CommWriteInfo();
        cwi.frame = clock.getFrameCounter();
        cwi.cycle = clock.getCycleCounter() + MdRuntimeData.getCpuDelayExt();
        cwi.cpu = cpu;
        cwi.regSpec = regSpec;
        cwi.size = Size.WORD;
        cwi.prevValue = prevValue & cwi.size.getMask();
        cwi.value = nextValue & cwi.size.getMask();
        if (currentFrame != cwi.frame) {
            t.clear();
            currentFrame = cwi.frame;
        }
        dropOldWrites(cwi.cycle);
        t.put(cwi.cycle, regSpec, cwi);
        if (cpu == CpuDeviceAccess.SLAVE && regSpec == RegSpecS32x.COMM1 && cwi.value == 0xe1) {
            System.out.println("here");
        }
    }

    private void dropOldWrites(int cycle) {
        Iterator<Table.Cell<Integer, RegSpecS32x, CommWriteInfo>> it = t.cellSet().iterator();
        while (it.hasNext()) {
            var cell = it.next();
            if (cycle - cell.getRowKey() > CYCLE_LOOKBACK) {
                it.remove();
            }
        }
    }

    AtomicInteger currentCyclePrev = new AtomicInteger(-1);

    public int read(CpuDeviceAccess cpu, RegSpecS32x regSpec, int res, Size size) {
        int w1;
        if (size == Size.LONG) {
            w1 = readWord(cpu, regSpec, res >> 16);
            RegSpecS32x regSpec2 = S32xDict.getRegSpec(cpu, regSpec.addr + 2);
            int w2 = readWord(cpu, regSpec2, res & 0xFFFF);
            w1 = ((w1 << 16) & 0xFFFF_0000) | (w2 & 0xFFFF);
        } else {
            w1 = readWord(cpu, regSpec, res);
        }
        return w1;
    }

    private int readWord(CpuDeviceAccess cpu, RegSpecS32x regSpec, int res) {
        SystemProvider.SystemClock clock = MdRuntimeData.getSystemClockExt();
        long frame = clock.getFrameCounter();
        if (frame > currentFrame) {
            t.clear();
            return res;
        }
        int cycle = clock.getCycleCounter() + MdRuntimeData.getCpuDelayExt();
        currentCyclePrev.set(-1);
        AtomicReference<Table.Cell<Integer, RegSpecS32x, CommWriteInfo>> refCell = new AtomicReference<>();
        t.cellSet().forEach(cell -> {
            final int cellCycle = cell.getRowKey();
            if (regSpec == cell.getColumnKey() && ((cycle - cellCycle) < CYCLE_LOOKBACK)) {
                if (cycle < cellCycle && cellCycle > currentCyclePrev.get()) {
                    refCell.set(cell);
                    currentCyclePrev.set(cellCycle);
                }
            }
        });
        int val = res;
        if (refCell.get() != null) {
            val = refCell.get().getValue().prevValue;
            LOG.info("{} {} {}, Should be using value: {}\n{}", cpu, cycle, regSpec, th(val), refCell);
        }
        return val;
    }
}
