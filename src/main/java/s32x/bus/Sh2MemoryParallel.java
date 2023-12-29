package s32x.bus;

import omegadrive.util.BufferUtil;
import omegadrive.util.Size;
import s32x.Sh2MMREG;
import s32x.dict.S32xDict;
import s32x.sh2.Sh2Helper;
import s32x.sh2.cache.Sh2Cache;
import s32x.util.Md32xRuntimeData;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import static omegadrive.util.Util.th;

/**
 * Sh2MemoryParallel
 * <p>
 * TODO ignores cache
 */
public final class Sh2MemoryParallel implements Sh2Bus {
    private boolean replayMode = false, active, dmaRunning;
    private final Sh2Bus memory;

    public void setActive(boolean active) {
        this.active = active;
    }

    public void dmaRunning(boolean running) {
        this.dmaRunning = running;
    }

    static class RwCtx {
        public int address, value, position, cpuDelay;
        public Size size;
        public boolean read;

        @Override
        public String toString() {
            return new StringJoiner(", ", RwCtx.class.getSimpleName() + "[", "]")
                    .add("address=" + th(address))
                    .add("value=" + th(value))
                    .add("position=" + position)
                    .add("size=" + size)
                    .add("read=" + read)
                    .toString();
        }
    }

    private final Map<Integer, RwCtx> map = new HashMap<>();
    private int position = 0;

    public Sh2MemoryParallel(Sh2Bus memory) {
        this.memory = memory;
    }

    @Override
    public int read(int address, Size size) {
        if (!active || dmaRunning) {
            return memory.read(address, size);
        }
        if (address >>> S32xDict.SH2_PC_AREA_SHIFT < 0x10) {
            address |= S32xDict.SH2_CACHE_THROUGH_OFFSET;
        }
        if (!replayMode) {
            RwCtx entry = addEntry(address, 0, size, true);
            int delay = Md32xRuntimeData.getCpuDelayExt();
            int res = memory.read(address, size);
            entry.value = res;
            entry.cpuDelay = Md32xRuntimeData.getCpuDelayExt() - delay;
            return res;
        }
        return checkEntry(address, 0, size, true).value;
    }

    @Override
    public void resetSh2() {
        memory.resetSh2();
    }

    @Override
    public Sh2MMREG getSh2MMREGS(BufferUtil.CpuDeviceAccess master) {
        return memory.getSh2MMREGS(master);
    }

    @Override
    public void fetch(Sh2Helper.FetchResult ft, BufferUtil.CpuDeviceAccess cpu) {
        memory.fetch(ft, cpu);
    }

    @Override
    public int fetchDelaySlot(int pc, Sh2Helper.FetchResult ft, BufferUtil.CpuDeviceAccess cpu) {
        return memory.fetchDelaySlot(pc, ft, cpu);
    }

    @Override
    public void dataWrite(BufferUtil.CpuDeviceAccess cpu, int addr, int val, Size size) {
        memory.dataWrite(cpu, addr, val, size);
    }

    @Override
    public void invalidateCachePrefetch(Sh2Cache.CacheInvalidateContext ctx) {
        memory.invalidateCachePrefetch(ctx);
    }

    @Override
    public void newFrame() {
        memory.newFrame();
    }

    @Override
    public void write(int address, int val, Size size) {
        if (!active || dmaRunning) {
            memory.write(address, val, size);
            return;
        }
        if (address >>> S32xDict.SH2_PC_AREA_SHIFT < 0x10) {
            address |= S32xDict.SH2_CACHE_THROUGH_OFFSET;
        }
        if (!replayMode) {
            RwCtx entry = addEntry(address, val, size, false);
            int delay = Md32xRuntimeData.getCpuDelayExt();
            memory.write(address, val, size);
            entry.cpuDelay = Md32xRuntimeData.getCpuDelayExt() - delay;
            return;
        }
        RwCtx entry = checkEntry(address, val, size, false);
        Md32xRuntimeData.addCpuDelayExt(entry.cpuDelay);
    }

    private RwCtx checkEntry(int address, int val, Size size, boolean read) {
        RwCtx ctx = map.get(position);
        assert ctx != null;
        assert ctx.address == address && ctx.size == size && ctx.read == read :
                th(address) + "," + th(val) + " " + size + " vs  " + ctx;
        if (!read) {
            assert (ctx.value & size.getMask()) == (val & size.getMask()) : th(address) + "," + th(val) + " " + size + " vs  " + ctx;
        }
        position++;
        return ctx;
    }

    private RwCtx addEntry(int address, int val, Size size, boolean read) {
        RwCtx rwCtx = new RwCtx();
        rwCtx.address = address;
        rwCtx.size = size;
        rwCtx.value = val;
        rwCtx.position = position;
        rwCtx.read = read;
        map.put(position, rwCtx);
        position++;
        assert position < 100 : position;
        return rwCtx;
    }

    public void setReplayMode(boolean replayMode) {
        assert this.replayMode != replayMode;
        this.replayMode = replayMode;
        position = 0;
    }

    public void clear() {
        map.clear();
        position = 0;
    }
}