package s32x.sh2.cache;

import omegadrive.Device;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.bus.Sh2Bus;
import s32x.util.S32xUtil.CpuDeviceAccess;

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * Translated from yabause:
 * https://github.com/Yabause/yabause/blob/master/yabause/src/sh2cache.h
 */
public interface Sh2Cache extends Device {

    Logger LOG = LogHelper.getLogger(Sh2Cache.class.getSimpleName());

    int CACHE_LINES = 64;
    int CACHE_BYTES_PER_LINE = 16;
    int CACHE_BYTES_PER_LINE_MASK = CACHE_BYTES_PER_LINE - 1;
    int CACHE_WAYS = 4;

    int AREA_MASK = 0xE0000000;
    int TAG_MASK = 0x1FFFFC00;
    int ENTRY_MASK = 0x000003F0;
    int ENTRY_SHIFT = 4;
    int LINE_MASK = CACHE_BYTES_PER_LINE - 1;

    int SH2_ADDRESS_SPACE_PARTITION_BITS = 3;
    int CACHE_ADDRESS_BITS = 32 - SH2_ADDRESS_SPACE_PARTITION_BITS;

    int CACHE_USE_H3 = 0; //topmost 3 bits
    int CACHE_THROUGH_H3 = 1;
    int CACHE_PURGE_H3 = 2;
    int CACHE_ADDRESS_ARRAY_H3 = 3;
    int CACHE_DATA_ARRAY_H3 = 6;
    int CACHE_IO_H3 = 7;

    int CACHE_USE = CACHE_USE_H3 << CACHE_ADDRESS_BITS;
    int CACHE_THROUGH = CACHE_THROUGH_H3 << CACHE_ADDRESS_BITS;
    int CACHE_PURGE = CACHE_PURGE_H3 << CACHE_ADDRESS_BITS;
    int CACHE_ADDRESS_ARRAY = CACHE_ADDRESS_ARRAY_H3 << CACHE_ADDRESS_BITS;
    int CACHE_DATA_ARRAY = CACHE_DATA_ARRAY_H3 << CACHE_ADDRESS_BITS;
    int CACHE_IO = CACHE_IO_H3 << CACHE_ADDRESS_BITS;
    int CACHE_PURGE_MASK = CACHE_PURGE - 1;

    int CACHE_PURGE_DELAY = 2;
    int DATA_ARRAY_SIZE = 0x1000;
    int DATA_ARRAY_MASK = DATA_ARRAY_SIZE - 1;

    class Sh2CacheLine implements Serializable {
        @Serial
        private static final long serialVersionUID = -8588821133794717884L;
        public int tag; //u32
        public int v;
        public byte[] data = new byte[CACHE_BYTES_PER_LINE]; //u8
    }

    class Sh2CacheEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 4340652925400702503L;
        int enable; //u32
        int[] lru = new int[CACHE_LINES]; //u32
        Sh2CacheLine[][] way = new Sh2CacheLine[CACHE_WAYS][CACHE_LINES];
    }

    class CacheInvalidateContext {
        public CpuDeviceAccess cpu;
        public Sh2CacheLine line;
        public int cacheReadAddr, prevCacheAddr;
        public boolean force;
    }

    void cacheClear();

    @Deprecated
    int readDirect(int addr, Size size);

    int cacheMemoryRead(int addr, Size size);

    boolean cacheMemoryWrite(int addr, int val, Size size);

    ByteBuffer getDataArray();

    CacheRegContext updateState(int ccrValue);

    CacheRegContext getCacheContext();

    Sh2CacheContext getSh2CacheContext();

    default int readMemoryUncached(Sh2Bus memory, int address, Size size) {
        return memory.read(address | CACHE_THROUGH, size);
    }

    default void writeMemoryUncached(Sh2Bus memory, int address, int value, Size size) {
        memory.write(address | CACHE_THROUGH, value, size);
    }

    //set to false only for testing
    boolean USE_CACHE = true;

    static Sh2Cache createCacheInstance(CpuDeviceAccess cpu, final Sh2Bus memory) {
        Sh2Cache c;
        if (USE_CACHE) {
            c = new Sh2CacheImpl(cpu, memory);
        } else {
            c = createNoCacheInstance(cpu, memory);
        }
        return c;
    }

    class CacheRegContext implements Serializable {
        @Serial
        private static final long serialVersionUID = -5571586968550411713L;

        public CpuDeviceAccess cpu;
        public int ccr;
        public int way;
        public int cachePurge;
        public int twoWay;
        public int dataReplaceDis;
        public int instReplaceDis;
        public int cacheEn;

        @Override
        public String toString() {
            return "CacheContext{" +
                    "way=" + way +
                    ", cachePurge=" + cachePurge +
                    ", twoWay=" + twoWay +
                    ", dataReplaceDis=" + dataReplaceDis +
                    ", instReplaceDis=" + instReplaceDis +
                    ", cacheEn=" + cacheEn +
                    '}';
        }
    }

    class Sh2CacheContext implements Serializable {
        @Serial
        private static final long serialVersionUID = -7850221349320557387L;
        public Sh2CacheEntry ca;
        public CacheRegContext cacheContext;

        public final byte[] dataArray = new byte[DATA_ARRAY_SIZE];
    }

    //TESTING ONLY
    static Sh2Cache createNoCacheInstance(CpuDeviceAccess cpu, final Sh2Bus memory) {
        return new Sh2CacheImpl(cpu, memory) {
            @Override
            public CacheRegContext updateState(int value) {
                CacheRegContext ctx = super.updateState(value);
                if (ctx.cacheEn > 0) {
                    LogHelper.logWarnOnce(LOG, "Ignoring cache enable, as cache emulation is not active");
                }
                ca.enable = 0; //always disabled
                return ctx;
            }

            @Override
            public int readDirect(int addr, Size size) {
                return memory.readMemoryUncachedNoDelay(addr, size);
            }
        };
    }
}
