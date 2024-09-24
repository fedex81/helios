package s32x.bus;

import omegadrive.Device;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.BufferUtil;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import s32x.Sh2MMREG;
import s32x.sh2.Sh2Helper;
import s32x.sh2.cache.Sh2Cache.CacheInvalidateContext;
import s32x.sh2.prefetch.Sh2Prefetcher;
import s32x.util.BiosHolder;

import java.nio.ByteBuffer;

import static s32x.sh2.cache.Sh2Cache.CACHE_THROUGH;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public interface Sh2Bus extends Sh2Prefetcher, ReadableByteMemory, Device {

    boolean SH2_MEM_ACCESS_STATS = Boolean.parseBoolean(System.getProperty("helios.32x.sh2.memAccess.stats", "false"));

    MemoryDataCtx EMPTY = new MemoryDataCtx();

    void write(int register, int value, Size size);

    int read(int register, Size size);

    void resetSh2();

    default Sh2MMREG getSh2MMREGS(BufferUtil.CpuDeviceAccess master) {
        return null;
    }

    default MemoryDataCtx getMemoryDataCtx() {
        return EMPTY;
    }

    default void fetch(Sh2Helper.FetchResult ft, BufferUtil.CpuDeviceAccess cpu) {
        ft.opcode = read16(ft.pc) & 0xFFFF;
    }

    default int fetchDelaySlot(int pc, Sh2Helper.FetchResult ft, BufferUtil.CpuDeviceAccess cpu) {
        return read16(pc) & 0xFFFF;
    }

    default void write8(int addr, byte val) {
        write(addr, val, Size.BYTE);
    }

    default void write16(int addr, int val) {
        write(addr, val, Size.WORD);
    }

    default void write32(int addr, int val) {
        write(addr, val, Size.LONG);
    }

    default int read8(int addr) {
        return read(addr, Size.BYTE);
    }

    default int read16(int addr) {
        return read(addr, Size.WORD);
    }

    default int read32(int addr) {
        return read(addr, Size.LONG);
    }

    default int readMemoryUncachedNoDelay(int address, Size size) {
        int delay = MdRuntimeData.getCpuDelayExt();
        int res = read(address | CACHE_THROUGH, size);
        MdRuntimeData.resetCpuDelayExt(delay);
        return res;
    }

    default void invalidateCachePrefetch(CacheInvalidateContext ctx) {
        //do nothing
    }

    default void newFrame() {
    }

    interface MdRomAccess {
        int readRom(int address, Size size);
    }

    class MemoryDataCtx {
        public int romSize, romMask;
        public ByteBuffer rom, sdram;
        public BiosHolder.BiosData[] bios;
    }
}
