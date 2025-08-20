package s32x.bus;

import omegadrive.util.*;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import org.slf4j.Logger;
import s32x.DmaFifo68k;
import s32x.S32XMMREG;
import s32x.Sh2MMREG;
import s32x.dict.S32xDict;
import s32x.dict.S32xMemAccessDelay;
import s32x.event.PollSysEventManager;
import s32x.savestate.Gs32xStateHandler;
import s32x.sh2.Sh2Helper;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.sh2.cache.Sh2Cache;
import s32x.sh2.prefetch.Sh2Prefetch;
import s32x.sh2.prefetch.Sh2PrefetchSimple;
import s32x.sh2.prefetch.Sh2Prefetcher;
import s32x.util.BiosHolder;
import s32x.util.debug.MemAccessStats;

import java.nio.ByteBuffer;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.MASTER;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SLAVE;
import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.LogHelper.logWarnOnceWhenEn;
import static omegadrive.util.Util.assertCheckBusOp;
import static omegadrive.util.Util.th;

public final class Sh2BusImpl implements Sh2Bus {

    private static final Logger LOG = LogHelper.getLogger(Sh2BusImpl.class.getSimpleName());

    private static final String ILLEGAL_ACCESS_STR = "{} sh2 {} access to {} when {}={}, addr: {} {}";

    public BiosHolder.BiosData[] bios = new BiosHolder.BiosData[2];
    public ByteBuffer sdram;
    public ByteBuffer rom;

    public final Sh2Cache[] cache = new Sh2Cache[2];
    private final Sh2Prefetcher prefetch;
    private final MemAccessStats memAccessStats = MemAccessStats.NO_STATS;

    public int romSize, romMask;

    private final Sh2MMREG[] sh2MMREGS = new Sh2MMREG[2];
    private final S32XMMREG s32XMMREG;
    private final MdRomAccess mdBus;
    private final MemoryDataCtx memoryDataCtx;
    private final Sh2Config config;

    public Sh2BusImpl(S32XMMREG s32XMMREG, ByteBuffer rom, BiosHolder biosHolder, MdRomAccess mdBus, Sh2Prefetch.Sh2DrcContext... drcCtx) {
        memoryDataCtx = new MemoryDataCtx();
        this.s32XMMREG = s32XMMREG;
        this.mdBus = mdBus;
        memoryDataCtx.rom = this.rom = rom;
        bios[MASTER.ordinal()] = biosHolder.getBiosData(MASTER);
        bios[SLAVE.ordinal()] = biosHolder.getBiosData(SLAVE);
        memoryDataCtx.bios = bios;
        memoryDataCtx.sdram = sdram = ByteBuffer.allocate(S32xDict.SH2_SDRAM_SIZE);
        Sh2Config sh2Config = Sh2Config.get();
        cache[MASTER.ordinal()] = Sh2Cache.createCacheInstance(MASTER, this);
        cache[SLAVE.ordinal()] = Sh2Cache.createCacheInstance(SLAVE, this);
        sh2MMREGS[MASTER.ordinal()] = new Sh2MMREG(MASTER, cache[MASTER.ordinal()]);
        sh2MMREGS[SLAVE.ordinal()] = new Sh2MMREG(SLAVE, cache[SLAVE.ordinal()]);

        memoryDataCtx.romSize = romSize = rom.capacity();
        memoryDataCtx.romMask = romMask = Util.getRomMask(romSize);
        prefetch = sh2Config.drcEn ? new Sh2Prefetch(this, cache, drcCtx) : new Sh2PrefetchSimple(this, cache);
        config = Sh2Config.get();
        Gs32xStateHandler.addDevice(this);
        LOG.info("Rom size: {}, mask: {}", th(romSize), th(romMask));
    }

    @Override
    public int read(int address, Size size) {
        CpuDeviceAccess cpuAccess = MdRuntimeData.getAccessTypeExt();
        assert assertCheckBusOp(address, size);
        int res = 0;
        if (SH2_MEM_ACCESS_STATS) {
            memAccessStats.addMemHit(true, address, size);
        }
        switch ((address >>> Sh2Cache.CACHE_ADDRESS_BITS) & 0xFF) {
            case Sh2Cache.CACHE_USE_H3:
            case Sh2Cache.CACHE_PURGE_H3: //chaotix, bit 27,28 are ignored -> 4
            case Sh2Cache.CACHE_ADDRESS_ARRAY_H3: //chaotix
            case Sh2Cache.CACHE_DATA_ARRAY_H3: //vr
                return cache[cpuAccess.ordinal()].cacheMemoryRead(address, size);
            case Sh2Cache.CACHE_THROUGH_H3:
                if (address >= S32xDict.SH2_START_ROM && address < S32xDict.SH2_END_ROM) {
                    //TODO RV bit, sh2 should stall
                    assert DmaFifo68k.rv ? logWarnIllegalAccess(cpuAccess, "read", "ROM", "rv",
                            DmaFifo68k.rv, address, size) : true;
                    res = mdBus.readRom(address & 0xFF_FFFF, size);
                    S32xMemAccessDelay.addReadCpuDelay(S32xMemAccessDelay.ROM);
                } else if (address >= S32xDict.START_32X_SYSREG && address < S32xDict.END_32X_COLPAL) {
                    if (BufferUtil.ENFORCE_FM_BIT_ON_READS && s32XMMREG.fm == 0 && address >= S32xDict.START_32X_VDPREG) {
                        logWarnIllegalAccess(cpuAccess, "read", "VDP regs", "FM",
                                s32XMMREG.fm, address, size);
                        return size.getMask();
                    }
                    res = s32XMMREG.read(address, size);
                } else if (address >= S32xDict.SH2_START_SDRAM && address < S32xDict.SH2_END_SDRAM) {
                    res = BufferUtil.readBuffer(sdram, address & S32xDict.SH2_SDRAM_MASK, size);
                    S32xMemAccessDelay.addReadCpuDelay(S32xMemAccessDelay.SDRAM);
                } else if (address >= S32xDict.START_DRAM && address < S32xDict.END_DRAM_OVER_MIRROR) {
                    if (BufferUtil.ENFORCE_FM_BIT_ON_READS && s32XMMREG.fm == 0) {
                        logWarnIllegalAccess(cpuAccess, "read", "FB/OVER", "FM",
                                s32XMMREG.fm, address, size);
                        return size.getMask();
                    }
                    res = s32XMMREG.read(address & S32xDict.DRAM_OVER_MIRROR_MASK, size);
                    S32xMemAccessDelay.addReadCpuDelay(S32xMemAccessDelay.FRAME_BUFFER);
                } else if (address >= S32xDict.SH2_START_BOOT_ROM && address < S32xDict.SH2_END_BOOT_ROM) {
                    res = bios[cpuAccess.ordinal()].readBuffer(address, size);
                    S32xMemAccessDelay.addReadCpuDelay(S32xMemAccessDelay.BOOT_ROM);
                } else {
                    logWarnOnce(LOG, "{} invalid read from addr: {}, {}", cpuAccess, th(address), size);
                }
                break;
            case Sh2Cache.CACHE_IO_H3: //0xF
                if ((address & S32xDict.SH2_ONCHIP_REG_MASK) == S32xDict.SH2_ONCHIP_REG_MASK) {
                    res = sh2MMREGS[cpuAccess.ordinal()].read(address & 0xFFFF, size);
                } else if (address >= S32xDict.SH2_START_DRAM_MODE && address < S32xDict.SH2_END_DRAM_MODE) {
                    res = sh2MMREGS[cpuAccess.ordinal()].readDramMode(address & 0xFFFF, size);
                } else {
                    logWarnOnce(LOG, "{} invalid read from addr: {}, {}", cpuAccess, th(address), size);
//                    throw new RuntimeException();
                }
                break;
            default:
                res = size.getMask();
                logWarnOnce(LOG, "{} invalid read from addr: {}, {}", cpuAccess, th(address), size);
                if (true) throw new RuntimeException();
                break;
        }
        return res & size.getMask();
    }

    @Override
    public void write(int address, int val, Size size) {
        CpuDeviceAccess cpuAccess = MdRuntimeData.getAccessTypeExt();
        assert assertCheckBusOp(address, size);
        val &= size.getMask();
        if (SH2_MEM_ACCESS_STATS) {
            memAccessStats.addMemHit(false, address, size);
        }
        //flag to check if code memory has been changed
        boolean hasMemoryChanged = false;
        switch ((address >>> Sh2Cache.CACHE_ADDRESS_BITS) & 0xFF) {
            case Sh2Cache.CACHE_USE_H3:
            case Sh2Cache.CACHE_DATA_ARRAY_H3: //vr
            case Sh2Cache.CACHE_PURGE_H3:
            case Sh2Cache.CACHE_ADDRESS_ARRAY_H3:
                //NOTE: vf slave writes to sysReg 0x401c, 0x4038 via cache
                hasMemoryChanged = cache[cpuAccess.ordinal()].cacheMemoryWrite(address, val, size);
                //NOTE if not in cache we need to invalidate any block containing it,
                //NOTE as the next cache access will reload the data from MEM
                break;
            case Sh2Cache.CACHE_THROUGH_H3:
                if (address >= S32xDict.START_DRAM && address < S32xDict.END_DRAM_OVER_MIRROR) {
                    if (s32XMMREG.fm == 0) {
                        logWarnIllegalAccess(cpuAccess, "write", "FB/OVER", "FM",
                                s32XMMREG.fm, address, size);
                        return;
                    }
                    s32XMMREG.write(address & S32xDict.DRAM_OVER_MIRROR_MASK, val, size);
                } else if (address >= S32xDict.SH2_START_SDRAM && address < S32xDict.SH2_END_SDRAM) {
                    hasMemoryChanged = BufferUtil.writeBufferRaw(sdram, address & S32xDict.SH2_SDRAM_MASK, val, size);
                    S32xMemAccessDelay.addWriteCpuDelay(S32xMemAccessDelay.SDRAM);
                } else if (address >= S32xDict.START_32X_SYSREG && address < S32xDict.END_32X_SYSREG) {
                    s32XMMREG.write(address, val, size);
                } else if (address >= S32xDict.START_32X_VDPREG && address < S32xDict.END_32X_COLPAL) {
                    if (s32XMMREG.fm == 0) {
                        logWarnIllegalAccess(cpuAccess, "write", " VDP regs", "FM",
                                s32XMMREG.fm, address, size);
                        return;
                    }
                    s32XMMREG.write(address, val, size);
                } else {
                    logWarnOnce(LOG, "{} invalid write to addr: {}, {} {}", cpuAccess, th(address), th(val), size);
                }
                break;
            case Sh2Cache.CACHE_IO_H3: //0xF
                if ((address & S32xDict.SH2_ONCHIP_REG_MASK) == S32xDict.SH2_ONCHIP_REG_MASK) {
                    sh2MMREGS[cpuAccess.ordinal()].write(address & 0xFFFF, val, size);
                } else if (address >= S32xDict.SH2_START_DRAM_MODE && address < S32xDict.SH2_END_DRAM_MODE) {
                    sh2MMREGS[cpuAccess.ordinal()].writeDramMode(address & 0xFFFF, val, size);
                } else {
                    logWarnOnce(LOG, "{} invalid write to addr: {}, {} {}", cpuAccess, th(address), th(val), size);
                }
                break;
            default:
                logWarnOnce(LOG, "{} invalid write to addr: {}, {} {}", cpuAccess, th(address), th(val), size);
                break;
        }
        if (hasMemoryChanged) {
            prefetch.dataWrite(cpuAccess, address, val, size);
        }
        if (config.pollDetectEn) {
            Sh2Prefetch.checkPoller(cpuAccess, PollSysEventManager.SysEvent.SDRAM, address, val, size);
        }
    }

    @Override
    public void invalidateCachePrefetch(Sh2Cache.CacheInvalidateContext ctx) {
        prefetch.invalidateCachePrefetch(ctx);
    }

    public void fetch(Sh2Helper.FetchResult fetchResult, CpuDeviceAccess cpu) {
        prefetch.fetch(fetchResult, cpu);
    }

    @Override
    public int fetchDelaySlot(int pc, Sh2Helper.FetchResult ft, CpuDeviceAccess cpu) {
        return prefetch.fetchDelaySlot(pc, ft, cpu);
    }

    @Override
    public Sh2MMREG getSh2MMREGS(CpuDeviceAccess cpu) {
        return sh2MMREGS[cpu.ordinal()];
    }

    @Override
    public MemoryDataCtx getMemoryDataCtx() {
        return memoryDataCtx;
    }

    @Override
    public void newFrame() {
        prefetch.newFrame();
    }

    @Override
    public void resetSh2() {
        sh2MMREGS[MASTER.ordinal()].reset();
        sh2MMREGS[SLAVE.ordinal()].reset();
    }

    @Override
    public void saveContext(ByteBuffer buffer) {
        Sh2Bus.super.saveContext(buffer);
        buffer.put(sdram.rewind());
    }

    @Override
    public void loadContext(ByteBuffer buffer) {
        Sh2Bus.super.loadContext(buffer);
        sdram.rewind().put(buffer);
    }

    private static boolean logWarnIllegalAccess(CpuDeviceAccess cpu, String rw, String memType, String accessType,
                                                Object val, int address, Size size) {
        logWarnOnceWhenEn(LOG, ILLEGAL_ACCESS_STR, cpu, rw, memType, accessType, val, th(address), size);
        return true;
    }
}