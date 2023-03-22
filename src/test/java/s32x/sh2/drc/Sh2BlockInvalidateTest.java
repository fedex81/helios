package s32x.sh2.drc;

import com.google.common.collect.Range;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import s32x.bus.Sh2Bus;
import s32x.sh2.Sh2;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Helper;
import s32x.sh2.Sh2MultiTestBase;
import s32x.util.Md32xRuntimeData;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Stream;

import static s32x.dict.S32xDict.*;
import static s32x.sh2.drc.DrcUtil.RUNNING_IN_GITHUB;
import static s32x.sh2.drc.DrcUtil.triggerDrcBlocks;
import static s32x.sh2.drc.Sh2Block.INVALID_BLOCK;
import static s32x.util.S32xUtil.CpuDeviceAccess.MASTER;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class Sh2BlockInvalidateTest extends Sh2MultiTestBase {

    private int pc = 0x100;
    private final boolean verbose = false;


    static {
        config = configCacheEn;
    }

    protected static Stream<Sh2.Sh2Config> fileProvider() {
        return Arrays.stream(configList).filter(c -> c.prefetchEn && c.drcEn);//.limit(1);
    }

    @ParameterizedTest
    @MethodSource("fileProvider")
    public void testDrc(Sh2.Sh2Config c) {
        System.out.println("Testing: " + c);
        resetCacheConfig(c);
        testDrcTrace(Sh2DrcDecodeTest.trace1, Sh2DrcDecodeTest.trace1Ranges);
        resetCacheConfig(c);
        testDrcTrace(Sh2DrcDecodeTest.trace2, Sh2DrcDecodeTest.trace2Ranges);
        resetCacheConfig(c);
        testDrcTrace(Sh2DrcDecodeTest.trace3, Sh2DrcDecodeTest.trace3Ranges);
    }

    @ParameterizedTest
    @MethodSource("fileProvider")
    public void testInstructionRewrite(Sh2.Sh2Config c) {
        Assumptions.assumeFalse(RUNNING_IN_GITHUB);
        resetCacheConfig(c);
        testAfterBurner(c);
    }

    /**
     * 00006bd2	339c	add R9, R3
     * 00006bd4	e420	mov H'20, R4
     * 00006be6	8fa5	bf/s H'00006b34
     * 00006be8	0009	nop
     * <p>
     * <p>
     * 00006b24	2020	mov.b R2, @R0 //writes to 00006bd5, R2 = 0x20 or 0x40
     * 00006b28	a7fe	bra 00006b06
     * 00006b2a	0009	nop
     * <p>
     * TODO test cache disabled, test both wt and cache blocks
     */
    private void testAfterBurner(Sh2.Sh2Config c) {
        int[] trace1 = {0x339c, 0xe420, 0xbfa5, 0x9};
        int[] trace2 = {0x2020, 0xa054, 0x9};

        int t1Start = 0x06006bd2;
        int t2Start = 0x06006b24;
        int overwriteRam = 0x06006bd5;
        int expectedRamByte = 0x20;

        memory.getMemoryDataCtx().bios[0].buffer.putInt(0, t1Start);
        masterCtx.registers[0] = overwriteRam;
        masterCtx.registers[2] = expectedRamByte;
        setTrace(t1Start & 0xFFFF, trace1, masterCtx);
        setTrace(t2Start & 0xFFFF, trace2, masterCtx);
        Md32xRuntimeData.resetCpuDelayExt();

        masterCtx.cycles = 1;
        triggerDrcBlocks(sh2, masterCtx, t1Start, t2Start);
        Sh2Helper.Sh2PcInfoWrapper w1 = Sh2Helper.get(t1Start, MASTER);
        Sh2Helper.Sh2PcInfoWrapper w2 = Sh2Helper.get(t2Start, MASTER);
        Assertions.assertNotEquals(Sh2Helper.SH2_NOT_VISITED, w1);
        Assertions.assertNotEquals(Sh2Helper.SH2_NOT_VISITED, w2);
        Assertions.assertNotEquals(INVALID_BLOCK, w1.block);
        Assertions.assertNotEquals(INVALID_BLOCK, w2.block);

        Assertions.assertArrayEquals(trace1, w1.block.prefetchWords);
        Assertions.assertEquals(expectedRamByte, memory.read8(SH2_CACHE_THROUGH_OFFSET | overwriteRam));
        Assertions.assertEquals(expectedRamByte, readCache(MASTER, overwriteRam, Size.BYTE));

        //remove block1 from cache, then write 0x40 to cache address
        memory.cache[0].cacheClear();
        expectedRamByte = 0x40;
        masterCtx.registers[2] = expectedRamByte;
        sh2.run(masterCtx);
        sh2.run(masterCtx);
        Assertions.assertNotEquals(INVALID_BLOCK, w1.block);
        Assertions.assertNotEquals(INVALID_BLOCK, w2.block);

        Assertions.assertNotEquals(trace1[1], w1.block.prefetchWords[1]);
        Assertions.assertEquals(expectedRamByte, w1.block.prefetchWords[1] & 0xFF);
        Assertions.assertEquals(expectedRamByte, memory.read8(SH2_CACHE_THROUGH_OFFSET | overwriteRam));
        Assertions.assertEquals(expectedRamByte, readCache(MASTER, overwriteRam, Size.BYTE));

        //block1 is back in cache showing 0x40, write 0x20 directly to SDRAM,
        //block1 stays valid as it is reading from cache
        expectedRamByte = 0x20;
        memory.write8(SH2_CACHE_THROUGH_OFFSET | overwriteRam, (byte) expectedRamByte);

        Assertions.assertNotEquals(INVALID_BLOCK, w1.block);
        Assertions.assertNotEquals(INVALID_BLOCK, w2.block);

        //now write 0x20 via cache, block1 becomes invalid
        memory.write8(overwriteRam, (byte) expectedRamByte);
        Assertions.assertEquals(INVALID_BLOCK, w1.block);
        Assertions.assertNotEquals(INVALID_BLOCK, w2.block);
    }

    private int readCache(S32xUtil.CpuDeviceAccess cpu, int address, Size size) {
        return memory.cache[cpu.ordinal()].readDirect(address, Size.BYTE);
    }

    private int cnt = 0;

    private void testDrcTrace(int[] trace, Range<Integer>[] blockRanges) {
        Range<Integer>[] blockRangesSdram =
                (Range<Integer>[]) Arrays.stream(blockRanges).map(br -> Range.closed(SH2_START_SDRAM | br.lowerEndpoint(),
                        SH2_START_SDRAM | br.upperEndpoint())).toArray(Range[]::new);
        System.out.println("Trace: " + Arrays.toString(trace));
        for (var br : blockRangesSdram) {
            System.out.println("Block: " + br);
            int blockEndExclude = br.upperEndpoint();
            for (int writeAddr = br.lowerEndpoint() - 5; writeAddr < blockEndExclude + 2; writeAddr++) {
                for (Size size : Size.vals) {
                    //avoid addressError
                    boolean skip = (writeAddr & 1) == 1 && size != Size.BYTE || (writeAddr & 3) != 0 && size == Size.LONG;
                    if (skip) {
                        continue;
                    }
                    Range<Integer> writeRange = Range.closed(writeAddr, writeAddr + (size.getByteSize() - 1));
                    setTrace(pc, trace, masterCtx);
                    sh2.run(masterCtx);
                    sh2.run(masterCtx);
                    sh2.run(masterCtx);

                    int memMapPc = br.lowerEndpoint();
                    Sh2Helper.Sh2PcInfoWrapper wrapper = Sh2Helper.getOrDefault(memMapPc, MASTER);
                    Sh2DrcDecodeTest.checkWrapper(wrapper, memMapPc, SH2_SDRAM_MASK);
                    Sh2Block preBlock = wrapper.block;
                    boolean blockStillValid = noOverlapRanges(br, writeRange);

                    if (verbose)
                        System.out.println(cnt + ", blockPc: " + memMapPc + ", write: " + writeAddr + " " + size +
                                ",blockRange: " + br + ",writeRange: " + writeRange +
                                ",invalidate: " + !blockStillValid + ",block: " + preBlock);

                    Md32xRuntimeData.setAccessTypeExt(MASTER);
                    memory.write(writeAddr, (0xFF << 16) | 0xFF, size);

                    //previous block becomes invalid
                    Assertions.assertEquals(blockStillValid, preBlock.isValid());
                    //current block gets reset to the INVALID_BLOCK
                    Assertions.assertEquals(blockStillValid ? preBlock : INVALID_BLOCK, wrapper.block);

                    //check all other blocks
                    for (var br1 : blockRangesSdram) {
                        if (br1 == br) {
                            continue;
                        }
                        Sh2Helper.Sh2PcInfoWrapper w = Sh2Helper.getOrDefault(br1.lowerEndpoint(), MASTER);
                        blockStillValid = noOverlapRanges(br1, writeRange);
                        if (verbose)
                            System.out.println(cnt + ", blockPc: " + br1.lowerEndpoint() + ", write: " + writeAddr + " " + size +
                                    ",blockRange: " + br1 + ",writeRange: " + writeRange + ",invalidate: " + !blockStillValid +
                                    ",block: " + w.block);
                        Assertions.assertEquals(blockStillValid, w.block.isValid());
                        if (!blockStillValid) {
                            Assertions.assertEquals(INVALID_BLOCK, w.block);
                        }
                    }
                    cnt++;
                }
            }
        }
    }

    @Override
    @BeforeEach
    public void before() {
        super.before();
        Sh2Bus.MemoryDataCtx mdc = lc.memory.getMemoryDataCtx();
        int sp = mdc.rom.capacity() - 4;
        ByteBuffer bios = mdc.bios[MASTER.ordinal()].buffer;
        bios.putInt(0, SH2_START_SDRAM | pc);
        bios.putInt(4, SH2_START_SDRAM | sp);
    }

    private boolean noOverlapRanges(Range blockRange, Range writeRange) {
        boolean noOverlap = true;
        if (blockRange.isConnected(writeRange)) {
            noOverlap = blockRange.intersection(writeRange).isEmpty();
        }
        return noOverlap;
    }

    private void setTrace(int pc, int[] trace, Sh2Context context) {
        ByteBuffer ram = memory.getMemoryDataCtx().sdram;
        for (int i = 0; i < trace.length; i++) {
            ram.putShort(pc + (i << 1), (short) trace[i]);
        }
        sh2.reset(context);
    }
}
