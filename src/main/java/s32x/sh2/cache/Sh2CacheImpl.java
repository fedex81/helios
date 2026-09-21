package s32x.sh2.cache;

/*
Copyright 2005 Guillaume Duhamel
Copyright 2016 Shinya Miyamoto

This file is part of Yabause.

Yabause is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

Yabause is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Yabause; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/

/*! \file sh2cache.c
\brief SH2 internal cache operations FIL0016332.PDF section 8
*/

import omegadrive.util.*;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import org.slf4j.Logger;
import s32x.bus.Sh2Bus;
import s32x.savestate.Gs32xStateHandler;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Optional;

import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * TODO lru after cache purge, not sure zero is the correct value...
 */
public class Sh2CacheImpl implements Sh2Cache {

    private static final Logger LOG = LogHelper.getLogger(Sh2CacheImpl.class.getSimpleName());
    private static final boolean verbose = false;

    //NOTE looks like this is NOT needed, ie. it doesn't improve compat
    public static final boolean PARANOID_ON_CACHE_ENABLED_TOGGLE = false;

    private static final int LRU_SIZE = 64;
    private static final int[][] LRU_TABLE = new int[CACHE_WAYS][LRU_SIZE];

    // Fast 128-byte primitive array covering both twoWay modes (0 and 1)
    private static final byte[] REPLACE_WAY_LRU_LUT = {
            // ---- twoWay = 0 mode (First 64 elements) ----
            3, 2, 3, 2, 3, 3, 1, 1, 3, 2, 3, 2, 3, 3, 1, 1,
            3, 3, 3, 3, 3, 3, 1, 1, 3, 3, 3, 3, 3, 3, 1, 1,
            3, 2, 3, 2, 3, 3, 3, 3, 3, 2, 3, 2, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 0, 0, 0,

            // ---- twoWay = 1 mode (Next 64 elements repeating 3, 2, 3, 2...) ----
            3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2,
            3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2,
            3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2,
            3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2
    };

    static {
        for (int lru = 0; lru < LRU_SIZE; lru++) {
            LRU_TABLE[3][lru] = lru | 0xb;
            LRU_TABLE[2][lru] = (lru & 0x3E) | 0x14;
            LRU_TABLE[1][lru] = (lru | (1 << 5)) & 0x39;
            LRU_TABLE[0][lru] = lru & 0x7;
        }
    }
    protected final ByteBuffer data_array = ByteBuffer.allocate(DATA_ARRAY_SIZE); // cache (can be used as RAM)

    protected Sh2CacheContext ctx;
    private CacheRegContext cacheRegCtx;
    protected Sh2CacheEntry ca;
    private final CpuDeviceAccess cpu;
    private final Sh2Bus memory;
    private final CacheInvalidateContext invalidCtx;

    private final int[] tags = new int[CACHE_LINES * CACHE_WAYS];


    protected Sh2CacheImpl(CpuDeviceAccess cpu, Sh2Bus memory) {
        this.memory = memory;
        this.cpu = cpu;
        this.ctx = new Sh2CacheContext();
        ca = ctx.ca = new Sh2CacheEntry();
        cacheRegCtx = ctx.cacheContext = new CacheRegContext();
        cacheRegCtx.cpu = cpu;
        this.invalidCtx = new CacheInvalidateContext();
        invalidCtx.cpu = cpu;
        for (int i = 0; i < ca.way.length; i++) {
            for (int j = 0; j < ca.way[i].length; j++) {
                ca.way[i][j] = new Sh2CacheLine();
            }
        }
        rebuildTags();
        Gs32xStateHandler.addDevice(this);
    }

    private static int tagIndex(int entry, int way) {
        return entry * CACHE_WAYS + way;
    }

    private void setTag(int entry, int way, int tagValue) {
        ca.way[way][entry].tag = tagValue;
        tags[tagIndex(entry, way)] = tagValue;
    }

    private void rebuildTags() {
        for (int entry = 0; entry < CACHE_LINES; entry++) {
            for (int way = 0; way < CACHE_WAYS; way++) {
                tags[tagIndex(entry, way)] = ca.way[way][entry].tag;
            }
        }
    }

    private int findWay(int entry, int tagAddr) {
        final int base = entry * CACHE_WAYS;
        for (int i = 0; i < CACHE_WAYS; i++) {
            if (tags[base + i] == tagAddr) return i;
        }
        return -1;
    }

    @Override
    public void cacheClear() {
        for (int entry = 0; entry < CACHE_LINES; entry++) {
            ca.lru[entry] = 0;
            for (int way = 0; way < CACHE_WAYS; way++) {
                invalidatePrefetcher(ca.way[way][entry], entry, -1);
                setTag(entry, way, ca.way[way][entry].tag | CACHE_LINE_DISABLED_MASK);
            }
        }
        if (verbose) LOG.info("{} Cache clear", cpu);
    }

    @Override
    public int readDirect(int addr, Size size) {
        switch (addr & AREA_MASK) {
            case CACHE_USE:
                if (ca.enable > 0) {
                    final int entry = (addr & ENTRY_MASK) >> ENTRY_SHIFT;
                    final int way = findWay(entry, addr & TAG_MASK);
                    if (way >= 0) {
                        return getCachedData(ca.way[way][entry].data, addr & LINE_MASK, size) & size.getMask();
                    }
                }
                assert cpu == MdRuntimeData.getAccessTypeExt();
                return memory.readMemoryUncachedNoDelay(addr, size);
            case CACHE_DATA_ARRAY:
                return readDataArray(addr, size);
            default:
                LOG.error("{} Unexpected cache read: {}, {}", cpu, th(addr), size);
                if (true) throw new RuntimeException();
                break;
        }
        return size.getMask();
    }

    @Override
    public int cacheMemoryRead(int addr, Size size) {
        switch (addr & AREA_MASK) {
            case CACHE_USE: {
                if (ca.enable == 0) {
                    assert cpu == MdRuntimeData.getAccessTypeExt();
                    return readMemoryUncached(memory, addr, size);
                }
                return readCache(addr, size);
            }
            case CACHE_DATA_ARRAY:
                return readDataArray(addr, size);
            case CACHE_PURGE: //associative purge
                //fifa32x expects a read to purge the cache
                if (verbose) LOG.warn("{} CACHE_PURGE read: {}, {}", cpu, th(addr), size);
                purgeCache(addr);
                break;
            case CACHE_ADDRESS_ARRAY:
//                assert size == Size.LONG; //TODO pwm sound demo != LONG
                LOG.warn("{} CACHE_ADDRESS_ARRAY read: {}, {}", cpu, th(addr), size);
                return readAddressArray(addr);
            default:
                LOG.error("{} Unexpected cache read: {}, {}", cpu, th(addr), size);
                if (true) throw new RuntimeException();
                break;
        }
        return size.getMask();
    }

    @Override
    public boolean cacheMemoryWrite(int addr, int val, Size size) {
        boolean change = false;
        switch (addr & AREA_MASK) {
            case CACHE_USE -> {
                if (ca.enable == 0) {
                    writeMemoryUncached(memory, addr, val, size);
                    //needs to be true, see testInstructionRewrite
                    return true;
                }
                change = writeCache(addr, val, size);
            }
            case CACHE_DATA_ARRAY -> change = writeDataArray(addr, val, size);
            //associative purge
            case CACHE_PURGE -> purgeCache(addr);
            case CACHE_ADDRESS_ARRAY -> {
                if (verbose) LOG.info("{} CACHE_ADDRESS_ARRAY write: {}, {} {}", cpu, th(addr), th(val), size);
                //doomRes 1.4, vf
                assert size == Size.LONG;
                writeAddressArray(addr, val);
            }
            default -> {
                LOG.error("{} Unexpected cache write: {}, {} {}", cpu, th(addr), th(val), size);
                if (true) throw new RuntimeException();
            }
        }
        return change;
    }

    private int readCache(int addr, Size size) {
        final int tagaddr = (addr & TAG_MASK);
        final int entry = (addr & ENTRY_MASK) >> ENTRY_SHIFT;

        int hitWay = findWay(entry, tagaddr);
        if (hitWay >= 0) {
            Sh2CacheLine line = ca.way[hitWay][entry];
            updateLru(hitWay, ca.lru, entry);
            if (verbose) LOG.info("{} Cache hit, read at {} {}, val: {}", cpu, th(addr), size,
                    th(getCachedData(line.data, addr & LINE_MASK, size)));
            //two way uses ways0,1
            assert cacheRegCtx.twoWay == 0 || (cacheRegCtx.twoWay == 1 && hitWay > 1);
            return getCachedData(line.data, addr & LINE_MASK, size);
        }
        // cache miss
        int lruway = selectWayToReplace(cacheRegCtx.twoWay, ca.lru[entry]);
        assert cacheRegCtx.twoWay == 0 || (cacheRegCtx.twoWay == 1 && lruway > 1);
        final Sh2CacheLine line = ca.way[lruway][entry];
        invalidatePrefetcher(line, entry, addr); //MetalHead needs this
        updateLru(lruway, ca.lru, entry);
        setTag(entry, lruway, tagaddr);
        assert (tagaddr & CACHE_LINE_DISABLED_MASK) == 0;

        refillCache(line.data, addr);
        //becomes valid
        setTag(entry, lruway, line.tag & ~CACHE_LINE_DISABLED_MASK);
        if (verbose) LOG.info("{} Cache miss, read at {} {}, val: {}", cpu, th(addr), size,
                th(getCachedData(line.data, addr & LINE_MASK, size)));
        return getCachedData(line.data, addr & LINE_MASK, size);
    }

    private boolean writeCache(int addr, int val, Size size) {
        final int tagaddr = (addr & TAG_MASK);
        final int entry = (addr & ENTRY_MASK) >> ENTRY_SHIFT;

        boolean change = false;
        int hitWay = findWay(entry, tagaddr);
        if (hitWay >= 0) {
            Sh2CacheLine line = ca.way[hitWay][entry];
            assert cacheRegCtx.twoWay == 0 || (cacheRegCtx.twoWay == 1 && hitWay > 1);
            int prev = getCachedData(line.data, addr & LINE_MASK, size);
            if (prev != val) {
                setCachedData(line.data, addr & LINE_MASK, val, size);
                change = true;
            }
            updateLru(hitWay, ca.lru, entry);
            if (verbose) LOG.info("Cache write at {}, val: {} {}", th(addr), th(val), size);
        }
        // write through
        writeMemoryUncached(memory, addr, val, size);
        return change;
    }

    private boolean writeDataArray(int addr, int val, Size size) {
        assert cacheRegCtx.cacheEn == 0 || cacheRegCtx.twoWay == 1;
        int dataArrayMask = DATA_ARRAY_MASK >> (cacheRegCtx.cacheEn & cacheRegCtx.twoWay);
        int address = addr & dataArrayMask;
        boolean change = false;
        if (verbose)
            LOG.info("{} Cache data array write: {}({}) {}, val: {}", cpu, th(addr),
                    th(address), size, th(val));
        if (address == (addr & DATA_ARRAY_MASK)) {
            change = BufferUtil.writeBufferRaw(data_array, address, val, size);
        } else {
            LOG.error("{} Error Cache data array write: {}({}) {}, val: {}", cpu, th(addr),
                    th(address), size, th(val));
        }
        return change;
    }

    private int readDataArray(int addr, Size size) {
        assert cacheRegCtx.cacheEn == 0 || cacheRegCtx.twoWay == 1;
        int dataArrayMask = DATA_ARRAY_MASK >> (cacheRegCtx.cacheEn & cacheRegCtx.twoWay);
        int address = addr & dataArrayMask;
        if (verbose) LOG.info("{} Cache data array read: {}({}) {}, val: {}", cpu, th(addr),
                th(addr & dataArrayMask), size,
                Util.th(BufferUtil.readBuffer(data_array, address, size)));
        if (address != (addr & DATA_ARRAY_MASK)) {
            LOG.error("{} Error Cache data array read: {}({}) {}, val: {}", cpu, th(addr),
                    th(addr & dataArrayMask), size,
                    Util.th(BufferUtil.readBuffer(data_array, address, size)));
            return size.getMask();
        }
        return BufferUtil.readBuffer(data_array, address, size);
    }

    private void writeAddressArray(int addr, int data) {
        final int tagaddr = (addr & TAG_MASK);
        final int entry = (addr & ENTRY_MASK) >> ENTRY_SHIFT;
        ca.lru[entry] = (data >> 6) & 63;
        Sh2CacheLine line = ca.way[cacheRegCtx.way][entry];
        int en = ((addr >> 2) & 1) == 0 ? CACHE_LINE_DISABLED_MASK : 0;
        assert (tagaddr & CACHE_LINE_DISABLED_MASK) == 0;
        setTag(entry, cacheRegCtx.way, tagaddr | en);
    }

    //NOTE seems unused
    private int readAddressArray(int addr) {
        final int entry = (addr & ENTRY_MASK) >> ENTRY_SHIFT;
        final int tagaddr = ca.way[cacheRegCtx.way][entry].tag;
        return (tagaddr & 0x7ffff << 10) | (ca.lru[entry] << 4) | cacheRegCtx.cacheEn;
    }

    private void purgeCache(int addr) {
        final int tagaddr = (addr & TAG_MASK);
        final int entry = (addr & ENTRY_MASK) >> ENTRY_SHIFT;
        //can purge more than one line
        for (int i = 0; i < CACHE_WAYS; i++) {
            if (ca.way[i][entry].tag == tagaddr) {
                assert cacheRegCtx.twoWay == 0 || (cacheRegCtx.twoWay == 1 && i > 1);
                //only v bit is changed, the rest of the data remains
                setTag(entry, i, ca.way[i][entry].tag | CACHE_LINE_DISABLED_MASK);
                MdRuntimeData.addCpuDelayExt(CACHE_PURGE_DELAY);
                invalidatePrefetcher(ca.way[i][entry], entry, addr & CACHE_PURGE_MASK);
            }
        }
        if (verbose) LOG.info("{} Cache purge: {}", cpu, th(addr));
        assert addr < 0x4800_0000;
    }



    @Override
    public CacheRegContext updateState(int value) {
        CacheRegContext cacheCtx = ctx.cacheContext;
        cacheCtx.way = (value >> 6) & 3;
        cacheCtx.cachePurge = (value >> 4) & 1;
        cacheCtx.twoWay = (value >> 3) & 1;
        cacheCtx.dataReplaceDis = (value >> 2) & 1;
        cacheCtx.instReplaceDis = (value >> 1) & 1;
        cacheCtx.cacheEn = value & 1;
        if (verbose) LOG.info("{} CCR update: {}", cpu, cacheCtx);
        handleCacheEnabled(cacheCtx);
        if (cacheCtx.cachePurge > 0) {
            cacheClear();
            cacheCtx.cachePurge = 0; //always reverts to 0
            value &= 0xEF;
        }
        cacheCtx.ccr = value;
        return cacheCtx;
    }

    private void handleCacheEnabled(CacheRegContext cacheCtx) {
        int prevCaEn = ca.enable;
        ca.enable = cacheCtx.cacheEn;
        //cache enable does not clear the cache
        if (prevCaEn != cacheCtx.cacheEn) {
            if (verbose) LOG.info("{} Cache enable: {}", cpu, cacheCtx.cacheEn);
            if (PARANOID_ON_CACHE_ENABLED_TOGGLE) {
                //only invalidate prefetch stuff
                for (int entry = 0; entry < CACHE_LINES; entry++) {
                    for (int way = 0; way < CACHE_WAYS; way++) {
                        invalidatePrefetcher(ca.way[way][entry], entry, -1);
                    }
                }
            }
        }
    }

    @Override
    public ByteBuffer getDataArray() {
        return data_array;
    }

    //DEBUG and TEST only
    public static Optional<Integer> getCachedValueIfAny(Sh2CacheImpl cache, int addr, Size size) {
        final int tagaddr = (addr & TAG_MASK);
        final int entry = (addr & ENTRY_MASK) >> ENTRY_SHIFT;

        for (int i = 0; i < 4; i++) {
            Sh2CacheLine line = cache.ca.way[i][entry];
            if (line.tag == tagaddr) {
                return Optional.of(getCachedData(line.data, addr & LINE_MASK, size));
            }
        }
        return Optional.empty();
    }

    private static void updateLru(int way, int[] lruArr, int lruPos) {
        assert way >= 0 && way < CACHE_WAYS;
        assert (lruArr[lruPos] & ~(LRU_SIZE - 1)) == 0;
        lruArr[lruPos] = LRU_TABLE[way][lruArr[lruPos]];
    }

    private static int selectWayToReplace(int twoWay, int lru) {
        assert twoWay >= 0 && twoWay < 2;
        return REPLACE_WAY_LRU_LUT[(twoWay << 6) | (lru & 0x3F)];
    }

    private void refillCache(byte[] data, int addr) {
        MdRuntimeData.addCpuDelayExt(4);
        assert cpu == MdRuntimeData.getAccessTypeExt();
        memory.readMemoryUncachedNoDelay(addr & 0xFFFFFFF0, data);
    }

    private void invalidatePrefetcher(Sh2CacheLine line, int entry, int addr) {
        if ((line.tag & CACHE_LINE_DISABLED_MASK) == 0) {
            boolean force = addr < 0;
            invalidCtx.line = line;
            invalidCtx.prevCacheAddr = line.tag | (entry << ENTRY_SHIFT);
            boolean invalidate = true;
            invalidCtx.cacheReadAddr = force ? invalidCtx.prevCacheAddr : addr;
            //TODO test, Metal Head 0x600e3a0, the cached block should be invalidated even if currently matches
            //TODO memory, as memory could then be changed and a following access to the cached block would
            //TODO show wrong data.
//            if (!invalidate) {
//                int nonCached = readMemoryUncachedNoDelay(memory, invalidCtx.prevCacheAddr, Size.WORD);
//                int cached = getCachedData(line.data, invalidCtx.prevCacheAddr & LINE_MASK, Size.WORD);
//                invalidate |= nonCached != cached;
//            }
            if (invalidate) {
                if (verbose)
                    LOG.info("{} {} on addr {}, cache line {}", force ? "Force invalidate" :
                                    "Cache miss, replacing line",
                            cpu, th(addr), th(line.tag));
                memory.invalidateCachePrefetch(invalidCtx);
            }
        }
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        Sh2Cache.super.saveContext(buffer);
        data_array.rewind().get(ctx.dataArray);
        ctx.cacheContext = cacheRegCtx;
        ctx.ca = ca;
        buffer.put(Util.serializeObject(ctx));
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        Sh2Cache.super.loadContext(buffer);
        Serializable s = Util.deserializeObject(buffer);
        assert s instanceof Sh2CacheContext;
        ctx = (Sh2CacheContext) s;
        data_array.rewind().put(ctx.dataArray);
        ca = ctx.ca;
        cacheRegCtx = ctx.cacheContext;
        rebuildTags();
    }

    @Override
    public CacheRegContext getCacheContext() {
        return cacheRegCtx;
    }

    @Override
    public Sh2CacheContext getSh2CacheContext() {
        return ctx;
    }

    private void setCachedData(final byte[] data, int addr, int val, Size size) {
        Util.writeDataMask(data, addr, val, CACHE_BYTES_PER_LINE_MASK, size);
    }

    private static int getCachedData(final byte[] data, int addr, Size size) {
        return Util.readDataMask(data, addr, CACHE_BYTES_PER_LINE_MASK, size);
    }
}