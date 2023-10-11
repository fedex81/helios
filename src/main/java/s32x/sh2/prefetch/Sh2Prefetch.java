package s32x.sh2.prefetch;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.slf4j.Logger;
import s32x.bus.Sh2Bus;
import s32x.dict.S32xDict;
import s32x.dict.S32xMemAccessDelay;
import s32x.event.PollSysEventManager;
import s32x.sh2.Sh2;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Debug;
import s32x.sh2.Sh2Instructions;
import s32x.sh2.cache.Sh2Cache;
import s32x.sh2.drc.Ow2DrcOptimizer;
import s32x.sh2.drc.Sh2Block;
import s32x.util.BiosHolder;
import s32x.util.Md32xRuntimeData;
import s32x.util.S32xUtil;
import s32x.util.S32xUtil.CpuDeviceAccess;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static omegadrive.util.Util.readBufferWord;
import static omegadrive.util.Util.th;
import static s32x.sh2.Sh2Helper.*;
import static s32x.sh2.Sh2Helper.Sh2PcInfoWrapper.HASH_CODE_MASK;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 */
public class Sh2Prefetch implements Sh2Prefetcher {

    private static final Logger LOG = LogHelper.getLogger(Sh2Prefetch.class.getSimpleName());
    private final static boolean ENABLE_BLOCK_RECYCLING = true;
    private static final boolean SH2_LOG_PC_HITS = false;
    public static final int PC_CACHE_AREA_SHIFT = 28;

    private static final boolean verbose = false;
    private static final boolean collectStats = verbose || false;
    private final Stats[] stats = {new Stats(CpuDeviceAccess.MASTER), new Stats(CpuDeviceAccess.SLAVE)};

    private final Sh2Bus memory;
    private final Sh2Cache[] cache;
    private final Sh2DrcContext[] drcContext;
    private final Sh2Config sh2Config;
    private final int[] opcodeWords;

    public final int romSize, romMask;
    public final BiosHolder.BiosData[] bios;
    public final ByteBuffer sdram;
    public final ByteBuffer rom;

    //reusable instance to avoid creating too much GC
    private final Sh2Block baseBlock;

    public static class Sh2DrcContext {
        public CpuDeviceAccess cpu;
        public Sh2 sh2;
        public Sh2Context sh2Ctx;
        public Sh2Bus memory;
    }

    public static class BytecodeContext {
        public Sh2DrcContext drcCtx;
        public String classDesc;
        public LocalVariablesSorter mv;
        public int opcode, pc, branchPc;
        public Sh2Instructions.Sh2BaseInstruction sh2Inst;
        public BytecodeContext delaySlotCtx;
        public boolean delaySlot;
    }

    public Sh2Prefetch(Sh2Bus memory, Sh2Cache[] cache, Sh2DrcContext[] sh2Ctx) {
        this.cache = cache;
        this.memory = memory;
        this.drcContext = sh2Ctx;
        Sh2Bus.MemoryDataCtx mdc = memory.getMemoryDataCtx();
        romMask = mdc.romMask;
        romSize = mdc.romSize;
        sdram = mdc.sdram;
        rom = mdc.rom;
        bios = mdc.bios;
        opcodeWords = new int[Sh2Block.MAX_INST_LEN];
        sh2Config = Sh2Config.get();
        baseBlock = new Sh2Block(0, CpuDeviceAccess.MASTER);
    }

    private Sh2Block doPrefetch(Sh2PcInfoWrapper piw, int pc, CpuDeviceAccess cpu) {
        if (collectStats) stats[cpu.ordinal()].addMiss();
        Sh2Block baseBlock = doPrefetchInternal(pc, cpu);
        Sh2Block match = findMatchingBlockIfAny(baseBlock, piw);
        if (match != Sh2Block.INVALID_BLOCK) {
            return match;
        }
        Sh2Block block = new Sh2Block(pc, cpu);
        populate(baseBlock, block);
        assert block.getCpu() == block.drcContext.cpu && block.getCpu() == cpu;
        boolean addBlockToList = !piw.knownBlocks.isEmpty();
        if (addBlockToList) {
            Sh2Block prev = piw.addToKnownBlocks(block);
            assert prev == null;
        }
        if (verbose) LOG.info("{} prefetch block at pc: {}, len: {}\n{}", cpu,
                th(pc), block.prefetchLenWords, toListOfInst(block));
        return block;
    }

    private Sh2Block findMatchingBlockIfAny(Sh2Block baseBlock, Sh2PcInfoWrapper piw) {
        final boolean tryRecycleBlock = ENABLE_BLOCK_RECYCLING && !piw.knownBlocks.isEmpty();
        Sh2Block res = Sh2Block.INVALID_BLOCK; //new block, add it to the list
        if (tryRecycleBlock) {
            Sh2Block entry = piw.knownBlocks.getOrDefault(baseBlock.hashCodeWords & HASH_CODE_MASK, Sh2Block.INVALID_BLOCK);
            if (entry != Sh2Block.INVALID_BLOCK) {
                //check for collisions on the 16-bit hashcode
                if (entry.hashCodeWords != baseBlock.hashCodeWords) {
                    LOG.error("Hash collision:\n{}\n{}", baseBlock, entry);
                    return Sh2Block.INVALID_BLOCK;
                }
                assert Arrays.equals(entry.prefetchWords, 0, baseBlock.prefetchLenWords,
                        opcodeWords, 0, baseBlock.prefetchLenWords);
                entry.setValid();
                entry.nextBlock = Sh2Block.INVALID_BLOCK;
                if (verbose && entry.isPollingBlock()) {
                    LOG.info("{} recycle block at pc: {}, len: {}\n{}", piw.block.getCpu(),
                            th(piw.block.prefetchPc), entry.prefetchLenWords, toListOfInst(entry));
                    LOG.info("{}\n{}\n{}", th(piw.block.prefetchPc), entry, entry.poller);
                }
//                    assert !rec.isPollingBlock() : th(pc) + "\n" + rec + "\n" + rec.poller;
                res = entry;
            }
        }
        return res;
    }

    private void populate(Sh2Block from, Sh2Block to) {
        to.start = from.start;
        to.pcMasked = from.pcMasked;
        to.fetchBuffer = from.fetchBuffer;
        to.fetchMemAccessDelay = from.fetchMemAccessDelay;
        to.pcMasked = from.pcMasked;
        to.setCacheFetch(from.isCacheFetch());
        to.prefetchLenWords = from.prefetchLenWords;
        to.hashCodeWords = from.hashCodeWords;
        to.end = from.start + ((from.prefetchLenWords - 1) << 1);
        to.prefetchWords = Arrays.copyOf(opcodeWords, from.prefetchLenWords);
        to.drcContext = drcContext[to.getCpu().ordinal()];
        to.setNoJump(from.isNoJump());
        to.stage1(Sh2Instructions.generateInst(to.prefetchWords));
    }

    private Sh2Block doPrefetchInternal(int pc, CpuDeviceAccess cpu) {
        final Sh2Block block = baseBlock;
        final Sh2Cache sh2Cache = cache[cpu.ordinal()];
        final boolean isCache = (pc >>> PC_CACHE_AREA_SHIFT) == 0 && sh2Cache.getCacheContext().cacheEn > 0;
        block.setCacheFetch((pc >>> PC_CACHE_AREA_SHIFT) == 0);
        setupPrefetch(block, pc, cpu);
        if (verbose) LOG.info("{} prefetch at pc: {}", cpu, th(pc));
        //force a cache effect by fetching the current PC
        if (isCache) {
            sh2Cache.cacheMemoryRead(pc, Size.WORD);
        }
        int wordsCount = fillOpcodes(cpu, pc, block);
        assert wordsCount > 0 && wordsCount <= opcodeWords.length;
        block.prefetchLenWords = wordsCount;
        block.hashCodeWords = S32xUtil.hashCode(opcodeWords, wordsCount);
        return block;
    }

    private int fillOpcodes(CpuDeviceAccess cpu, int pc, Sh2Block block) {
        return fillOpcodes(cpu, pc, block.start, block.fetchBuffer, block, opcodeWords);
    }

    private int fillOpcodes(CpuDeviceAccess cpu, int pc, int blockStart, ByteBuffer fetchBuffer,
                            Sh2Block block, int[] opcodeWords) {
        final Sh2Cache sh2Cache = cache[cpu.ordinal()];
        final int pcLimit = pc + Sh2Block.SH2_DRC_MAX_BLOCK_LEN_BYTES - 2;
        final boolean isCache = (pc >>> PC_CACHE_AREA_SHIFT) == 0 && sh2Cache.getCacheContext().cacheEn > 0;
        final Sh2Instructions.Sh2InstructionWrapper[] op = Sh2Instructions.instOpcodeMap;
        boolean breakOnJump = false;
        int wordsCount = 0;
        int bytePos = blockStart;
        int currentPc = pc;
        do {
            int val = isCache ? sh2Cache.readDirect(currentPc, Size.WORD) : readBufferWord(fetchBuffer, bytePos) & 0xFFFF;
            final Sh2Instructions.Sh2BaseInstruction inst = op[val].inst;
            opcodeWords[wordsCount++] = val;
            if (inst.isIllegal) {
                LOG.error("{} Invalid fetch, start PC: {}, current: {} opcode: {}", cpu, th(pc), th(bytePos), th(val));
                break;
            }
            if (inst.isBranch) {
                if (inst.isBranchDelaySlot) {
                    int nextVal = isCache ? sh2Cache.readDirect(currentPc + 2, Size.WORD) :
                            readBufferWord(fetchBuffer, bytePos + 2) & 0xFFFF;
                    opcodeWords[wordsCount++] = nextVal;
                    assert Arrays.binarySearch(Sh2Instructions.illegalSlotOpcodes,
                            Sh2Instructions.instOpcodeMap[nextVal].inst) < 0;
                }
                breakOnJump = true;
                break;
            }
            bytePos += 2;
            currentPc += 2;
        } while (currentPc < pcLimit);
        assert currentPc == pcLimit ? !breakOnJump : true; //TODO test
        block.setNoJump(currentPc == pcLimit && !breakOnJump);
        return wordsCount;
    }

    private void setupPrefetch(final Sh2Block block, final int pc, CpuDeviceAccess cpu) {
        block.start = pc & 0xFF_FFFF;
        switch (pc >> S32xDict.SH2_PC_AREA_SHIFT) {
            case 6:
            case 0x26:
                block.pcMasked = pc & S32xDict.SH2_SDRAM_MASK;
                block.fetchMemAccessDelay = S32xMemAccessDelay.SDRAM;
                block.fetchBuffer = sdram;
                break;
            case 2:
            case 0x22:
                block.pcMasked = pc & romMask;
                block.fetchMemAccessDelay = S32xMemAccessDelay.ROM;
                block.fetchBuffer = rom;
                break;
            case 0:
            case 0x20:
                block.fetchBuffer = bios[cpu.ordinal()].buffer;
                block.pcMasked = pc;
                block.fetchMemAccessDelay = S32xMemAccessDelay.BOOT_ROM;
                break;
            default:
                if ((pc >>> 28) == 0xC) {
                    int twoWay = cache[cpu.ordinal()].getCacheContext().twoWay;
                    final int mask = Sh2Cache.DATA_ARRAY_MASK >> twoWay;
                    block.start = Math.max(0, block.start) & mask;
                    block.fetchMemAccessDelay = S32xMemAccessDelay.SYS_REG;
                    block.fetchBuffer = cache[cpu.ordinal()].getDataArray();
                    block.pcMasked = pc & mask;
                } else {
                    LOG.error("{} Unhandled prefetch: {}", cpu, th(pc));
                    throw new RuntimeException("Unhandled prefetch: " + th(pc));
                }
                break;
        }
        block.start = block.pcMasked;
    }


    private void checkBlock(FetchResult fetchResult, CpuDeviceAccess cpu) {
        final int pc = fetchResult.pc;
        Sh2PcInfoWrapper piw = get(pc, cpu);
        assert piw != null;
        if (piw == SH2_NOT_VISITED) {
            piw = getOrCreate(pc, cpu);
        }
        if (piw.block.isValid()) {
            assert fetchResult.pc == piw.block.prefetchPc : th(fetchResult.pc);
            piw.block.addHit();
            fetchResult.block = piw.block;
//            assert piw.block.isCacheFetch() ? cache[cpu.ordinal()].getCacheContext().cacheEn > 0 : true;
            return;
        }
        Sh2Block block = doPrefetch(piw, pc, cpu);
        Sh2Block prev = piw.block;
        assert prev != null;
        assert block != null && block.isValid();
        assert SH2_NOT_VISITED.block == Sh2Block.INVALID_BLOCK : cpu + "," + th(pc) + "," + SH2_NOT_VISITED.block;
        if (prev != Sh2Block.INVALID_BLOCK && !block.equals(prev)) {
            LOG.warn("{} New block generated at PC: {}\nPrev: {}\nNew : {}", cpu, th(pc), prev, block);
        }
        piw.setBlock(block);
        fetchResult.block = block;
    }

    @Override
    public void fetch(FetchResult fetchResult, CpuDeviceAccess cpu) {
        final int pc = fetchResult.pc;
        if (!sh2Config.prefetchEn) {
            fetchResult.opcode = memory.read(pc, Size.WORD);
            return;
        }
        final Sh2Block prev = fetchResult.block;
        boolean isPrevInvalid = !prev.isValid();
        assert fetchResult.pc != fetchResult.block.prefetchPc;
        checkBlock(fetchResult, cpu);
        final Sh2Block block = fetchResult.block;
        //block recycled, was invalid before
        assert (block == prev ? isPrevInvalid : true) : "\n" + block;
        block.poller.spinCount = 0;
        cacheOnFetch(pc, block.prefetchWords[0], cpu);
        if (collectStats) stats[cpu.ordinal()].pfTotal++;
        S32xMemAccessDelay.addReadCpuDelay(block.fetchMemAccessDelay);
        assert block != Sh2Block.INVALID_BLOCK && block.prefetchWords != null && block.prefetchWords.length > 0;
        fetchResult.opcode = block.prefetchWords[0];
        assert fetchResult.block != null;
        return;
    }

    @Override
    public int fetchDelaySlot(int pc, FetchResult ft, CpuDeviceAccess cpu) {
        if (!sh2Config.prefetchEn) {
            return memory.read(pc, Size.WORD);
        }
        final Sh2Block block = ft.block;
        //TODO test, if a block clears the cache, the block itself will be invalidated, leading to its PC becoming odd
        //TODO and breaking the fetching of the delaySlot, see Sangokushi blockPc = 0x200_6ACD
        int blockPc = block.prefetchPc;
        if (!block.isValid()) {
            blockPc &= ~1;
        }
        final int pcDeltaWords = (pc - blockPc) >> 1;
        assert pcDeltaWords < block.prefetchLenWords && pcDeltaWords >= 0 : th(pc) + "," + th(pcDeltaWords);
        if (collectStats) stats[cpu.ordinal()].pfTotal++;
        S32xMemAccessDelay.addReadCpuDelay(block.fetchMemAccessDelay);
        int res = block.prefetchWords[pcDeltaWords];
        cacheOnFetch(pc, res, cpu);
        return res;
    }

    @Override
    public void dataWrite(CpuDeviceAccess cpuWrite, int addr, int val, Size size) {
        if (Sh2Debug.pcAreaMaskMap[addr >>> S32xDict.SH2_PC_AREA_SHIFT] == 0) return;
        if (addr >= 0 && addr < 0x100) { //Doom res 2.2
            return;
        }
        invalidateMemoryRegion(addr, cpuWrite, addr + size.getByteSize() - 1, val);
    }

    public static void checkPoller(CpuDeviceAccess cpuWrite, S32xDict.S32xRegType type, int addr, int val, Size size) {
        checkPoller(cpuWrite, PollSysEventManager.SysEvent.valueOf(type.name()), addr, val, size);
    }

    public static void checkPollersVdp(S32xDict.S32xRegType type, int addr, int val, Size size) {
        //cpuWrite doesn't apply here...
        checkPoller(null, PollSysEventManager.SysEvent.valueOf(type.name()), addr, val, size);
    }

    public static void checkPoller(CpuDeviceAccess cpuWrite, PollSysEventManager.SysEvent type, int addr, int val, Size size) {
        int res = PollSysEventManager.instance.anyPollerActive();
        if (res == 0) {
            return;
        }
        if ((res & 1) > 0) {
            Ow2DrcOptimizer.PollerCtx c = PollSysEventManager.instance.getPoller(CpuDeviceAccess.MASTER);
            if (c.isPollingActive() && type == c.event) {
                checkPollerInternal(c, cpuWrite, type, addr, val, size);
            }
        }
        if ((res & 2) > 0) {
            Ow2DrcOptimizer.PollerCtx c = PollSysEventManager.instance.getPoller(CpuDeviceAccess.SLAVE);
            if (c.isPollingActive() && type == c.event) {
                checkPollerInternal(c, cpuWrite, type, addr, val, size);
            }
        }
    }

    private static void checkPollerInternal(Ow2DrcOptimizer.PollerCtx c, CpuDeviceAccess cpuWrite, PollSysEventManager.SysEvent type,
                                            int addr, int val, Size size) {
        final Ow2DrcOptimizer.BlockPollData bpd = c.blockPollData;
        //TODO check, cache vs cache-through
        addr = addr & S32xDict.SH2_CACHE_THROUGH_MASK;
        if (rangeIntersect(bpd.memLoadTarget & S32xDict.SH2_CACHE_THROUGH_MASK,
                bpd.memLoadTargetEnd & S32xDict.SH2_CACHE_THROUGH_MASK, addr, addr + size.getByteSize() - 1)) {
            if (verbose)
                LOG.info("{} Poll write addr: {} {}, target: {} {} {}, val: {}", cpuWrite,
                        th(addr), size, c.cpu, th(c.blockPollData.memLoadTarget),
                        c.blockPollData.memLoadTargetSize, th(val));
            boolean skipVdp = type == PollSysEventManager.SysEvent.VDP && c.pollValue == PollSysEventManager.readPollValue(c);
            if (!skipVdp) {
                PollSysEventManager.instance.fireSysEvent(c.cpu, type);
            }
        }
    }

    private void invalidateMemoryRegion(final int addr, final CpuDeviceAccess blockOwner, final int wend, final int val) {
        boolean ignore = addr >>> S32xDict.SH2_PC_AREA_SHIFT > 0xC0;
        if (ignore) {
            return;
        }
        final CpuDeviceAccess otherCpu = CpuDeviceAccess.cdaValues[(blockOwner.ordinal() + 1) & 1];
        final boolean isWriteThrough = addr >>> PC_CACHE_AREA_SHIFT == 2;
        final int addrEven = (addr & ~1);
        //find closest block, long requires starting at +2
        for (int i = addrEven + 2; i > addrEven - Sh2Block.SH2_DRC_MAX_BLOCK_LEN_BYTES; i -= 2) {
            Sh2PcInfoWrapper piw = getOrDefault(i, blockOwner);
            assert piw != null;
            if (!piw.block.isValid()) {
                continue;
            }
            final Sh2Block b = piw.block;
            final int bend = b.prefetchPc + ((b.prefetchLenWords - 1) << 1); //inclusive
            if (rangeIntersect(b.prefetchPc, bend, addr, wend)) {
                invalidateMemoryLocationForCpu(blockOwner, piw, addr, i, val);
                if (isWriteThrough) {
                    invalidateMemoryLocationForCpu(otherCpu, addr, i, val);
                }
            }
            //TODO slow, enabling this needs the nested block attribute implemented
//            break;
        }
    }

    private void invalidateMemoryLocationForCpu(CpuDeviceAccess cpu, Sh2PcInfoWrapper piw, int addr, int i, int val) {
        if (piw.block.isValid()) {
            invalidateWrapper(addr, piw, false, val);
        }
        final boolean isCpuCacheOff = cache[cpu.ordinal()].getCacheContext().cacheEn == 0;
        //TODO is anything using this? I don't think so
        if (isCpuCacheOff) {
            LOG.error("Check!");
            piw = getOrDefault(i & S32xDict.SH2_CACHE_THROUGH_MASK, cpu);
            if (piw.block.isValid()) {
                invalidateWrapper(addr, piw, false, val);
            }
        }
    }

    private void invalidateMemoryLocationForCpu(CpuDeviceAccess cpu, int addr, int i, int val) {
        invalidateMemoryLocationForCpu(cpu, getOrDefault(i, cpu), addr, i, val);
    }

    static final int RANGE_MASK = 0xFFF_FFFF;

    /**
     * Assumes closed ranges, does not work for the general case (32bit signed integer); use only for small ranges
     * [r1s, r1e] intersect [r2s, r2e]
     */
    public static boolean rangeIntersect(int r1start, int r1end, int r2start, int r2end) {
        return !(((r1start & RANGE_MASK) > (r2end & RANGE_MASK)) || ((r1end & RANGE_MASK) < (r2start & RANGE_MASK)));
    }

    private void invalidateWrapperFromCache(final int addr, final Sh2PcInfoWrapper pcInfoWrapper) {
        invalidateWrapper(addr, pcInfoWrapper, true, -1);
    }

    private void invalidateWrapper(final int addr, final Sh2PcInfoWrapper pcInfoWrapper, final boolean cacheOnly, final int val) {
        assert pcInfoWrapper != SH2_NOT_VISITED;
        if (cacheOnly && !pcInfoWrapper.block.isCacheFetch()) {
            return;
        }
        final Sh2Block block = pcInfoWrapper.block;
        assert block != Sh2Block.INVALID_BLOCK;
        if (verbose) {
            String s = LogHelper.formatMessage(
                    "{} write at addr: {} val: {}, {} invalidate block with start: {} blockLen: {}",
                    Md32xRuntimeData.getAccessTypeExt(), th(addr), th(val),
                    pcInfoWrapper.block.drcContext.cpu, Util.th(pcInfoWrapper.block.prefetchPc),
                    pcInfoWrapper.block.prefetchLenWords);
            LOG.info(s);
        }
        //motocross byte, vf word
        invalidateBlock(pcInfoWrapper);
    }

    private void invalidateBlock(Sh2PcInfoWrapper piw) {
        Sh2Block b = piw.block;
        //Blackthorne lots of SDRAM invalidation
        if (ENABLE_BLOCK_RECYCLING) {
            assert b.getCpu() != null;
            piw.addToKnownBlocks(b);
        }
        piw.invalidateBlock();
    }

    private void cacheOnFetch(int pc, int expOpcode, CpuDeviceAccess cpu) {
        boolean isCache = pc >>> PC_CACHE_AREA_SHIFT == 0;
        if (isCache && cache[cpu.ordinal()].getCacheContext().cacheEn > 0) {
            //NOTE necessary to trigger the cache hit on fetch
            int cached = cache[cpu.ordinal()].cacheMemoryRead(pc, Size.WORD);
            assert (cached & 0xFFFF) == expOpcode : th(pc) + "," + th(expOpcode) + "," + th(cached);
        }
    }

    //TODO should we check cache contents vs SDRAM to detect inconsistencies?
    //TODO test
    public void invalidateCachePrefetch(Sh2Cache.CacheInvalidateContext ctx) {
        int addr = ctx.prevCacheAddr;
        if (addr >= 0 && addr < 0x100) { //Sangokushi
            return;
        }
        boolean ignore = addr >>> S32xDict.SH2_PC_AREA_SHIFT > 0xC0;
        if (ignore) {
            return;
        }
        final int addrEven = ctx.prevCacheAddr + Sh2Cache.CACHE_BYTES_PER_LINE;
        ;
        for (int i = addrEven; i > addr - Sh2Block.SH2_DRC_MAX_BLOCK_LEN_BYTES; i -= 2) {
            Sh2PcInfoWrapper piw = getOrDefault(i, ctx.cpu);
            assert piw != null;
            if (!piw.block.isValid()) {
                continue;
            }
            invalidateWrapper(i, piw, true, -1);
        }
    }

    long cnt = 0;

    @Override
    public void newFrame() {
        if (SH2_LOG_PC_HITS && (++cnt & 0x2FF) == 0) {
            PrefetchUtil.logPcHits(CpuDeviceAccess.MASTER);
            PrefetchUtil.logPcHits(CpuDeviceAccess.SLAVE);
        }
    }
}