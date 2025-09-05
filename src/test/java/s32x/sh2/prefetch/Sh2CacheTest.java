package s32x.sh2.prefetch;

import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import s32x.dict.S32xDict;
import s32x.sh2.Sh2Helper;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.sh2.Sh2MultiTestBase;
import s32x.sh2.cache.Sh2Cache;
import s32x.sh2.cache.Sh2CacheImpl;
import s32x.sh2.drc.Sh2Block;

import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.MASTER;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SLAVE;
import static omegadrive.util.Util.th;
import static s32x.dict.S32xDict.SH2_START_SDRAM;
import static s32x.dict.S32xDict.SH2_START_SDRAM_CACHE;
import static s32x.sh2.Sh2Disassembler.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 **/
public class Sh2CacheTest extends Sh2MultiTestBase {

    public static final int ILLEGAL = 0;
    public static final int NOP = 9;
    public static final int SETT = 0x18;
    public static final int CLRMAC = 0x28;
    public static final int JMP_0 = 0x402b;

    protected static Stream<Sh2Config> fileProvider() {
        return Arrays.stream(configList);//.filter(c -> c.prefetchEn && c.drcEn).limit(1);
    }

    @ParameterizedTest
    @MethodSource("fileProvider")
    public void testCache(Sh2Config c) {
        System.out.println("Testing: " + c);
        Runnable r = () -> {
            resetCacheConfig(c);
            testCacheOffInternal();
            testCacheOnInternal();
            testCacheReplaceInternal();
            testCacheDataArrayCacheOffInternal();
            testCacheDataArrayTwoWayCacheInternal();
            testCacheWriteNoHitInternal(Size.BYTE);
            testCacheWriteNoHitInternal(Size.WORD);
            testCacheWriteNoHitInternal(Size.LONG);
        };
        r.run();
        r.run();
    }

    @Override
    protected void initRam(int len) {
        super.initRam(len);
        memory.write16(SH2_START_SDRAM | 4, JMP_0); //JMP 0
        memory.write16(SH2_START_SDRAM | 0x18, JMP_0); //JMP 0
    }

    protected void testCacheOffInternal() {
        MdRuntimeData.setAccessTypeExt(MASTER);
        initRam(0x100);
        int noCacheAddr = SH2_START_SDRAM | 0x8;
        int cacheAddr = SH2_START_SDRAM_CACHE | 0x8;
        clearCache(MASTER);
        enableCache(MASTER, false);

        memory.read16(noCacheAddr);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);
        memory.read16(cacheAddr);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);

        memory.write16(noCacheAddr, CLRMAC);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);
        checkVal(MASTER, noCacheAddr, CLRMAC, Size.WORD);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);
        checkVal(MASTER, cacheAddr, CLRMAC, Size.WORD);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);

        memory.write16(cacheAddr, SETT);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);
        checkVal(MASTER, noCacheAddr, SETT, Size.WORD);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);
        checkVal(MASTER, cacheAddr, SETT, Size.WORD);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);
    }

    protected void testCacheOnInternal() {
        MdRuntimeData.setAccessTypeExt(MASTER);
        initRam(0x100);
        int noCacheAddr = SH2_START_SDRAM | 0x8;
        int cacheAddr = SH2_START_SDRAM_CACHE | 0x8;
        int res = 0;
        clearCache(MASTER);
        enableCache(MASTER, true);

        //read noCache, cache not accessed or modified
        memory.read16(noCacheAddr);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);

        //write noCache, cache empty, cache not modified
        memory.write16(noCacheAddr, CLRMAC);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);

        checkVal(MASTER, noCacheAddr, CLRMAC, Size.WORD);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, Size.WORD);


        //read cache, entry added to cache
        res = memory.read16(cacheAddr);
        checkCacheContents(MASTER, Optional.of(CLRMAC), noCacheAddr, Size.WORD);


        //write no cache, cache not modified
        memory.write16(noCacheAddr, SETT);
        checkCacheContents(MASTER, Optional.of(CLRMAC), noCacheAddr, Size.WORD);

        //cache clear, reload from SDRAM
        clearCache(MASTER);
        res = memory.read16(cacheAddr);
        checkCacheContents(MASTER, Optional.of(SETT), noCacheAddr, Size.WORD);

        //cache disable, write to SDRAM
        enableCache(MASTER, false);
        memory.write16(cacheAddr, NOP);
        //enable cache, still holding the previous value
        enableCache(MASTER, true);
        checkCacheContents(MASTER, Optional.of(SETT), noCacheAddr, Size.WORD);

        //cache out of sync
        res = memory.read16(cacheAddr);
        checkCacheContents(MASTER, Optional.of(SETT), noCacheAddr, Size.WORD);
        res = memory.read16(noCacheAddr);
        checkCacheContents(MASTER, Optional.of(SETT), noCacheAddr, Size.WORD);

        //needs a purge or a write
        memory.write16(cacheAddr, NOP);
        checkCacheContents(MASTER, Optional.of(NOP), noCacheAddr, Size.WORD);
    }

    protected int[] cacheReplace_cacheAddr = new int[5];

    protected void testCacheReplaceInternal() {
        int[] cacheAddr = cacheReplace_cacheAddr;
        int[] noCacheAddr = new int[5];

        //i == 0 -> this region should be all NOPs
        for (int i = 0; i < cacheAddr.length; i++) {
            cacheAddr[i] = SH2_START_SDRAM_CACHE | (i << 12) | 0xC0;
            noCacheAddr[i] = SH2_START_SDRAM | cacheAddr[i];
        }

        //NOTE: add a jump instruction to stop the DRC
        memory.write16(cacheAddr[0] + 14, JMP);
        memory.write16(cacheAddr[1], ADD0);
        memory.write16(cacheAddr[1] + 14, JMP);
        memory.write16(cacheAddr[2], CLRMAC);
        memory.write16(cacheAddr[2] + 14, JMP);
        memory.write16(cacheAddr[3], SETT);
        memory.write16(cacheAddr[3] + 14, JMP);
        memory.write16(cacheAddr[4], MACL);
        memory.write16(cacheAddr[4] + 14, JMP);

        enableCache(MASTER, true);
        enableCache(SLAVE, true);
        clearCache(MASTER);
        clearCache(SLAVE);

        checkCacheContents(MASTER, Optional.empty(), noCacheAddr[0], Size.WORD);
        checkCacheContents(SLAVE, Optional.empty(), noCacheAddr[0], Size.WORD);

        MdRuntimeData.setAccessTypeExt(MASTER);

        //fetch triggers a cache refill on cacheAddr
        doCacheFetch(MASTER, cacheAddr[0]);

        checkCacheContents(MASTER, Optional.of(NOP), noCacheAddr[0], Size.WORD);

        //cache miss, fill the ways, then replace the entry
        memory.read(cacheAddr[1], Size.WORD);
        checkCacheContents(MASTER, Optional.of(NOP), noCacheAddr[0], Size.WORD);
        checkCacheContents(MASTER, Optional.of(ADD0), noCacheAddr[1], Size.WORD);

        memory.read(cacheAddr[2], Size.WORD);
        checkCacheContents(MASTER, Optional.of(NOP), noCacheAddr[0], Size.WORD);
        checkCacheContents(MASTER, Optional.of(ADD0), noCacheAddr[1], Size.WORD);
        checkCacheContents(MASTER, Optional.of(CLRMAC), noCacheAddr[2], Size.WORD);

        memory.read(cacheAddr[3], Size.WORD);
        checkCacheContents(MASTER, Optional.of(NOP), noCacheAddr[0], Size.WORD);
        checkCacheContents(MASTER, Optional.of(ADD0), noCacheAddr[1], Size.WORD);
        checkCacheContents(MASTER, Optional.of(CLRMAC), noCacheAddr[2], Size.WORD);
        checkCacheContents(MASTER, Optional.of(SETT), noCacheAddr[3], Size.WORD);

        memory.read(cacheAddr[4], Size.WORD);
        //[0] has been replaced in cache
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr[0], Size.WORD);
        checkCacheContents(MASTER, Optional.of(ADD0), noCacheAddr[1], Size.WORD);
        checkCacheContents(MASTER, Optional.of(CLRMAC), noCacheAddr[2], Size.WORD);
        checkCacheContents(MASTER, Optional.of(SETT), noCacheAddr[3], Size.WORD);
        checkCacheContents(MASTER, Optional.of(MACL), cacheAddr[4], Size.WORD);
    }

    private void testCacheDataArrayCacheOffInternal() {
        testCacheDataArray(false, false);
        testCacheDataArray(false, true);
    }


    private void testCacheDataArrayTwoWayCacheInternal() {
        testCacheDataArray(true, true);
        try {
            testCacheDataArray(true, false);
            Assertions.fail("Should throw an AssertionError");
        } catch (AssertionError ae) {/*ignore*/}
    }

    private void testCacheDataArray(boolean cacheOn, boolean twoWayCache) {
        MdRuntimeData.setAccessTypeExt(MASTER);
        initRam(0x100);
        int val = cacheOn ? 1 : 0;
        val |= (twoWayCache ? 1 : 0) << 3;
        Sh2Cache.CacheRegContext ctx = memory.cache[MASTER.ordinal()].updateState(val);
        Assertions.assertEquals(cacheOn, ctx.cacheEn > 0);
        Assertions.assertEquals(twoWayCache, ctx.twoWay > 0);
        clearCache(MASTER);
        Random r = new Random(0x12);
        int dataArrayStart = S32xDict.SH2_START_DATA_ARRAY;
        int dataArraySize = cacheOn && twoWayCache ? Sh2Cache.DATA_ARRAY_SIZE >> 1 : Sh2Cache.DATA_ARRAY_SIZE;
        int[] vals = new int[Sh2Cache.DATA_ARRAY_SIZE];

        for (int i = 0; i < Sh2Cache.DATA_ARRAY_SIZE; i++) {
            vals[i] = r.nextInt(0x100);
            memory.write(dataArrayStart + i, vals[i], Size.BYTE);
        }

        for (int i = 0; i < Sh2Cache.DATA_ARRAY_SIZE; i++) {
            if (i < dataArraySize) {
                Assertions.assertEquals(vals[i], memory.read(dataArrayStart + i, Size.BYTE), th(dataArrayStart + i));
            } else {
                Assertions.assertEquals(Size.BYTE.getMask(), memory.read(dataArrayStart + i, Size.BYTE), th(dataArrayStart + i));
            }
        }
    }

    private void testCacheWriteNoHitInternal(Size size) {
        MdRuntimeData.setAccessTypeExt(MASTER);
        initRam(0x100);
        int noCacheAddr = SH2_START_SDRAM | 0x8;
        int cacheAddr = SH2_START_SDRAM_CACHE | 0x8;
        int res = 0;

        int val = (int) ((CLRMAC << 16 | CLRMAC) & size.getMask());

        clearCache(MASTER);
        enableCache(MASTER, true);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, size);

        //write cache miss, writes to memory
        memory.write(cacheAddr, val, size);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, size);

        checkVal(MASTER, noCacheAddr, val, size);

        //cache still empty
        memory.read(noCacheAddr, size);
        checkCacheContents(MASTER, Optional.empty(), noCacheAddr, size);


        //cache gets populated
        memory.read(cacheAddr, size);
        checkCacheContents(MASTER, Optional.of(val), noCacheAddr, size);
    }

    protected void enableCache(CpuDeviceAccess cpu, boolean enabled) {
        memory.cache[cpu.ordinal()].updateState(enabled ? 1 : 0);
    }

    protected void clearCache(CpuDeviceAccess cpu) {
        memory.cache[cpu.ordinal()].cacheClear();
    }

    protected void checkCacheContents(CpuDeviceAccess cpu, Optional<Integer> expVal, int addr, Size size) {
        Sh2Cache cache = memory.cache[cpu.ordinal()];
        Assertions.assertTrue(cache instanceof Sh2CacheImpl);
        Sh2CacheImpl i = (Sh2CacheImpl) cache;
        Optional<Integer> optVal = Sh2CacheImpl.getCachedValueIfAny(i, addr & 0xFFF_FFFF, size);
        if (expVal.isPresent()) {
            Assertions.assertTrue(optVal.isPresent(), "Should NOT be empty: " + optVal);
            Assertions.assertEquals(expVal.get(), optVal.get());
        } else {
            Assertions.assertTrue(optVal.isEmpty(), "Should be empty: " + optVal);
        }
    }

    private void checkVal(CpuDeviceAccess cpu, int addr, int expVal, Size size) {
        MdRuntimeData.setAccessTypeExt(cpu);
        int val = memory.read(addr, size);
        Assertions.assertEquals(expVal, val, cpu + "," + th(addr));
    }

    protected Sh2Helper.FetchResult doCacheFetch(CpuDeviceAccess cpu, int cacheAddr) {
        Sh2Helper.FetchResult ft = new Sh2Helper.FetchResult();
        ft.block = Sh2Block.INVALID_BLOCK;
        ft.pc = cacheAddr;
        memory.fetch(ft, cpu);
        return ft;
    }
}