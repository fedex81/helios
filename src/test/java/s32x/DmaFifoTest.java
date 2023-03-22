package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.sh2.device.DmaC;
import s32x.util.Md32xRuntimeData;

import java.util.Random;
import java.util.stream.IntStream;

import static s32x.dict.S32xDict.RegSpecS32x.MD_DMAC_CTRL;
import static s32x.dict.S32xDict.RegSpecS32x.MD_FIFO_REG;
import static s32x.dict.S32xDict.SH2_CACHE_THROUGH_OFFSET;
import static s32x.dict.S32xDict.SH2_START_SDRAM;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;
import static s32x.util.S32xUtil.CpuDeviceAccess.M68K;
import static s32x.util.S32xUtil.CpuDeviceAccess.MASTER;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 */
public class DmaFifoTest {

    private S32XMMREG s32XMMREG;
    private DmaC masterDmac;
    private int[] data = new int[0x80];

    public static final int FIFO_OFFSET = 0x4000 + MD_FIFO_REG.addr;
    public static final int DMA_CONTROL_OFFSET = 0x4000 + MD_DMAC_CTRL.addr;

    private static Random r;

    static {
        long ms = System.currentTimeMillis();
        System.out.println("Seed: " + ms);
        r = new Random(ms);
    }

    @BeforeEach
    public void beforeEach() {
        s32XMMREG = MarsRegTestUtil.createTestInstance().s32XMMREG;
        IntStream.range(0, data.length).forEach(i -> data[i] = i);
        masterDmac = s32XMMREG.dmaFifoControl.getDmac()[MASTER.ordinal()];
    }

    @Test
    public void testMultiFifoTransfer() {
        testDmaFifoBlocks();
        testFifoTransfer01();
        testDmaFifoBlocks();
        testFifoTransfer01();
    }

    @Test
    public void testFifoTransfer01() {
        int idx = 0, cnt = 0;
        int spin = 0;
        toggle68kFifo(false);
        setupSh2(masterDmac, data.length);
        toggle68kFifo(true);
        masterDmac.write(DMA_CHCR0, 0x44E1, Size.LONG);
        masterDmac.write(DMA_DMAOR, 1, Size.LONG);

        int pushFifo = r.nextInt(10) + 2;
        System.out.println("pushFifo: " + pushFifo);
        int limit = data.length * pushFifo;
        do {
            if ((cnt % pushFifo) == 0) {
                boolean full = isFifoFull();
                if (full) {
                    spin++;
                } else {
                    m68kWriteToFifo(data[idx++]);
                }
            }
            dmaStep(masterDmac);
            cnt++;
        } while (!isDmaDoneM68k() && spin < limit && idx < data.length);
        Assertions.assertEquals(data.length, idx);
    }

    @Test
    public void testDmaFifoBlocks() {
        int idx = 0, cnt = 0;
        toggle68kFifo(false);
        setupSh2(masterDmac, data.length);
        toggle68kFifo(true);
        //start Sh2 side
        masterDmac.write(DMA_CHCR0, 0x44E1, Size.LONG);
        masterDmac.write(DMA_DMAOR, 1, Size.LONG);
        Assertions.assertFalse(masterDmac.getDmaChannelSetup()[0].dreqLevel);
        Assertions.assertFalse(isFifoFull());
        Assertions.assertTrue(isFifoEmpty());

        do {
            //68k: fill one dma block
            m68kWriteToFifo(data[idx++]);
            m68kWriteToFifo(data[idx++]);
            m68kWriteToFifo(data[idx++]);
            m68kWriteToFifo(data[idx++]);
            Assertions.assertTrue(masterDmac.getDmaChannelSetup()[0].dreqLevel);
            Assertions.assertFalse(isFifoFull());
            Assertions.assertFalse(isFifoEmpty());

            //Sh2: empty one block
            dmaStep(masterDmac);
            dmaStep(masterDmac);
            dmaStep(masterDmac);
            dmaStep(masterDmac);
            Assertions.assertFalse(masterDmac.getDmaChannelSetup()[0].dreqLevel);
            Assertions.assertFalse(isFifoFull());
            Assertions.assertTrue(isFifoEmpty());
            cnt++;
        } while (!isDmaDoneM68k());
        Assertions.assertEquals(data.length, idx);
    }

    private void dmaStep(DmaC dmac) {
        Md32xRuntimeData.setAccessTypeExt(MASTER);
        dmac.step(1);
    }

    private void toggle68kFifo(boolean enable) {
        Md32xRuntimeData.setAccessTypeExt(M68K);
        s32XMMREG.write(DMA_CONTROL_OFFSET, enable ? 4 : 0, Size.WORD); // 68S <- 1
    }

    private void setupSh2(DmaC dmac, int len) {
        dmac.write(DMA_CHCR0, 0, Size.LONG);
        dmac.write(DMA_CHCR0, 0x44E0, Size.LONG);
        dmac.write(DMA_TCR0, len, Size.LONG);
        dmac.write(DMA_SAR0, SH2_CACHE_THROUGH_OFFSET + FIFO_OFFSET, Size.LONG);
        dmac.write(DMA_DAR0, SH2_START_SDRAM, Size.LONG);
        Assertions.assertFalse(dmac.getDmaChannelSetup()[0].dreqLevel);
    }

    private void m68kWriteToFifo(int data) {
        Md32xRuntimeData.setAccessTypeExt(M68K);
        s32XMMREG.write(FIFO_OFFSET, data, Size.WORD);
    }

    private boolean isDmaDoneM68k() {
        Md32xRuntimeData.setAccessTypeExt(M68K);
        int val = s32XMMREG.read(DMA_CONTROL_OFFSET, Size.WORD);
        return (val >> DmaFifo68k.M68K_68S_BIT_POS & 1) == 0;
    }

    private boolean isFifoFull() {
        Md32xRuntimeData.setAccessTypeExt(M68K);
        int val = s32XMMREG.read(DMA_CONTROL_OFFSET, Size.WORD);
        return (val >> DmaFifo68k.M68K_FIFO_FULL_BIT & 1) > 0;
    }

    private boolean isFifoEmpty() {
        Md32xRuntimeData.setAccessTypeExt(MASTER);
        int val = s32XMMREG.read(DMA_CONTROL_OFFSET, Size.WORD);
        return (val >> DmaFifo68k.SH2_FIFO_EMPTY_BIT & 1) > 0;
    }
}
