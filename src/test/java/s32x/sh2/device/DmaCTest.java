package s32x.sh2.device;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import s32x.DmaFifo68k;
import s32x.MarsRegTestUtil;
import s32x.S32XMMREG;
import s32x.sh2.device.DmaHelper.DmaChannelSetup;
import s32x.util.Md32xRuntimeData;
import s32x.util.S32xUtil;

import java.util.concurrent.atomic.AtomicInteger;

import static s32x.DmaFifo68k.SH2_FIFO_FULL_BIT;
import static s32x.dict.S32xDict.RegSpecS32x.*;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;
import static s32x.util.S32xUtil.CpuDeviceAccess.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 * <p>
 */
public class DmaCTest {

    private static final Logger LOG = LogHelper.getLogger(DmaCTest.class.getSimpleName());

    private S32XMMREG s32XMMREG;
    private DmaC masterDmac, slaveDmac;
    private DmaFifo68k dmaFifo68k;

    private AtomicInteger fifoData = new AtomicInteger(0xFFFF);

    @BeforeEach
    public void beforeEach() {
        s32XMMREG = MarsRegTestUtil.createTestInstance().s32XMMREG;
        masterDmac = s32XMMREG.dmaFifoControl.getDmac()[MASTER.ordinal()];
        slaveDmac = s32XMMREG.dmaFifoControl.getDmac()[SLAVE.ordinal()];
        dmaFifo68k = s32XMMREG.dmaFifoControl;
    }

    //Metal Head, RBI baseball, Sangokushi 0x603d6dc
    @Test
    public void testDreqSarNonCached() {
        testDreqNonCachedInternal(0x4012, 0x2601d800);
        testDreqNonCachedInternal(0x6002a50, 0x24000200);
        testDreqNonCachedInternal(0x603d6dc, 0x603ffbc);
    }

    //Zaxxon, Knuckles, RBI Baseball, FIFA 96, Mars Check v2
    @Test
    public void testDreqDarNonCached() {
        testDreqNonCachedInternal(0x2000_4012, 0x6001220);
    }

    private void testDreqNonCachedInternal(int sar) {
        testDreqNonCachedInternal(sar, 0x600_0000);
    }

    //fifa
    @Test
    public void testDreqAndSh2DMA_01() {
        int len1 = 0x20;
        int len2 = 0x200;
        for (int i = 0; i < 4; i++) {
            testDreqAndSh2DMAInternal(len1, len2, (i >> 1) & 1, i & 1);
            beforeEach();
            testDreqAndSh2DMAInternal(len2, len1, (i >> 1) & 1, i & 1);
            beforeEach();
        }
    }

    @Test
    public void testBothSh2AutoReqOneDma() {
        int len1 = 0x20;
        int len2 = 0x200;
        for (int i = 0; i < 4; i++) {
            testBothSh2AutoReqOneDma(len1, len2, (i >> 1) & 1, i & 1);
            beforeEach();
            testBothSh2AutoReqOneDma(len2, len1, (i >> 1) & 1, i & 1);
            beforeEach();
        }
    }

    @Test
    public void testAllChannelsAutoReqDma() {
        int baseLen = 12;
        testAllChannelsAutoReqDmaInternal(baseLen, baseLen * 3, baseLen * 2, baseLen * 6);
        beforeEach();
        testAllChannelsAutoReqDmaInternal(baseLen * 3, baseLen, baseLen * 6, baseLen * 2);
    }

    private void testAllChannelsAutoReqDmaInternal(int m0Len, int m1Len, int s0Len, int s1Len) {
        DmaChannelSetup[] mc = masterDmac.getDmaChannelSetup();
        DmaChannelSetup[] sc = slaveDmac.getDmaChannelSetup();

        //Master autoReq DMA
        setupDmaAndStartChannel(mc[0], masterDmac, m0Len, 0x600_A010, 0x600_E010, false);
        setupDmaAndStartChannel(mc[1], masterDmac, m1Len, 0x600_B020, 0x600_D020, false);
        masterDmac.write(DMA_DMAOR, 0x4E1, Size.LONG);
        //Slave autoReq DMA in SDRAM
        setupDmaAndStartChannel(sc[0], slaveDmac, s0Len, 0x600_A030, 0x600_E030, false);
        setupDmaAndStartChannel(sc[1], slaveDmac, s1Len, 0x600_B040, 0x600_D010, false);
        slaveDmac.write(DMA_DMAOR, 0x4E1, Size.LONG);

        //verify that the both DMA end normally
        int[] mCurrLen = {Integer.MAX_VALUE, Integer.MAX_VALUE}, sCurrLen = {Integer.MAX_VALUE,
                Integer.MAX_VALUE};
        int res = 1;
        int limit = 0xFFFF;
        int cnt = 0;
        do {
            dmaStepOne(MASTER, masterDmac, false, mc[0].channel);
            mCurrLen[0] = getDmaLen(masterDmac, 0);
            mCurrLen[1] = getDmaLen(masterDmac, 1);
            dmaStepOne(SLAVE, slaveDmac, false, sc[0].channel);
            sCurrLen[0] = getDmaLen(slaveDmac, 0);
            sCurrLen[1] = getDmaLen(slaveDmac, 1);
            res = mCurrLen[0] + mCurrLen[1] + sCurrLen[0] + sCurrLen[1];
//            System.out.println(Arrays.toString(mCurrLen)  + "," + Arrays.toString(sCurrLen));
            cnt++;
        } while (res > 0 && cnt < limit);
        Assertions.assertTrue(cnt < limit);
    }

    private void testDreqAndSh2DMAInternal(int dreqLen, int dmaLen, int mChan, int sChan) {
        DmaChannelSetup mc = masterDmac.getDmaChannelSetup()[mChan];
        DmaChannelSetup sc = slaveDmac.getDmaChannelSetup()[sChan];

        //Master Dreq DMA with 68k, lots of data
        setupDmaAndStartChannel(mc, masterDmac, dreqLen, 0x2000_4012, 0x6001220, true);
        dmaFifo68k.write(MD_DMAC_CTRL, M68K, MD_DMAC_CTRL.addr, 4, Size.WORD);
        //Slave autoReq DMA in SDRAM, quick
        setupDmaAndStartChannel(sc, slaveDmac, dmaLen, 0x6000000, 0x6000E00, false);
        slaveDmac.write(DMA_DMAOR, 0x4E1, Size.LONG);

        //verify that the Slave completing DMA does NOT stop dreqDMA
        int mlen = Integer.MAX_VALUE, slen = Integer.MAX_VALUE;
        boolean fifoFull = false;
        do {
            if (mlen > 0) {
                Assertions.assertTrue(is68SOn(dmaFifo68k), mc.toString());
                fillFifo(dmaFifo68k, fifoData);
                dmaStepOne(MASTER, masterDmac, true, mc.channel);
                mlen = getDmaLen(masterDmac, mc.channel);
            }
            dmaStepOne(SLAVE, slaveDmac, false, sc.channel);
            slen = getDmaLen(slaveDmac, sc.channel);
        } while (mlen > 0 || slen > 0);
        Assertions.assertEquals(0, mlen);
        Assertions.assertEquals(0, slen);
    }

    private void testBothSh2AutoReqOneDma(int mLen, int sLen, int mChan, int sChan) {
        DmaChannelSetup mc = masterDmac.getDmaChannelSetup()[mChan];
        DmaChannelSetup sc = slaveDmac.getDmaChannelSetup()[sChan];

        //Master autoReq DMA
        setupDmaAndStartChannel(mc, masterDmac, mLen, 0x6000000, 0x6000E00, false);
        masterDmac.write(DMA_DMAOR, 0x4E1, Size.LONG);
        //Slave autoReq DMA in SDRAM
        setupDmaAndStartChannel(sc, slaveDmac, sLen, 0x6000010, 0x6000E10, false);
        slaveDmac.write(DMA_DMAOR, 0x4E1, Size.LONG);

        //verify that the both DMA end normally
        int mCurrLen = Integer.MAX_VALUE, sCurrLen = Integer.MAX_VALUE;
        do {
            dmaStepOne(MASTER, masterDmac, false, mc.channel);
            dmaStepOne(SLAVE, slaveDmac, false, sc.channel);
            mCurrLen = getDmaLen(masterDmac, mc.channel);
            sCurrLen = getDmaLen(slaveDmac, sc.channel);
        } while (mCurrLen > 0 || sCurrLen > 0);
        Assertions.assertEquals(0, mCurrLen);
        Assertions.assertEquals(0, sCurrLen);
    }


    private void testDreqNonCachedInternal(int sar, int dar) {
        int dmaLen = 1;
        int channel = 0;
        DmaChannelSetup c = masterDmac.getDmaChannelSetup()[channel];
        setupDmaAndStartChannel(c, masterDmac, dmaLen, sar, dar, true);
        c.chcr_dmaEn = c.dmaor_dme = true;
        c.chcr_tranEndOk = false;
        Md32xRuntimeData.setAccessTypeExt(MASTER);
        int len = Integer.MAX_VALUE;
        do {
            dmaStepOne(MASTER, masterDmac, true, channel);
            len = getDmaLen(masterDmac, channel);
        } while (len > 0);
    }

    private boolean isFifoFull() {
        return (dmaFifo68k.read(SH2_DREQ_CTRL, MASTER, SH2_DREQ_CTRL.addr, Size.WORD) & (1 << SH2_FIFO_FULL_BIT)) > 0;
    }

    private void fillFifo(DmaFifo68k dmaFifo68k, AtomicInteger val) {
        if (isFifoFull()) {
            return;
        }
        do {
            dmaFifo68k.write(MD_FIFO_REG, M68K, MD_FIFO_REG.addr, val.getAndDecrement(), Size.WORD);
        } while (!isFifoFull());
    }

    private void dmaStepOne(S32xUtil.CpuDeviceAccess cpu, DmaC dmaC, boolean dreq, int channel) {
        Md32xRuntimeData.setAccessTypeExt(cpu);
        if (dreq) {
            dmaC.dmaReqTrigger(channel, true);
        }
        dmaC.step(1);
    }

    private void setupDmaAndStartChannel(DmaChannelSetup c, DmaC dmaC, int dmaLen, int sar, int dar, boolean dreq) {
        dmaC.write(c.channel == 0 ? DMA_TCR0 : DMA_TCR1, dmaLen, Size.LONG);
        dmaC.write(c.channel == 0 ? DMA_SAR0 : DMA_SAR1, sar, Size.LONG);
        dmaC.write(c.channel == 0 ? DMA_DAR0 : DMA_DAR1, dar, Size.LONG);
        c.chcr_autoReq = !dreq;
        c.chcr_dmaEn = c.dmaor_dme = true;
        c.chcr_tranEndOk = false;
        c.trnSize = Size.WORD;
        c.srcDelta = dreq ? 0 : 2;
        c.destDelta = 2;
    }

    public int getDmaLen(DmaC dmaC, int channel) {
        return dmaC.read(channel == 0 ? DMA_TCR0 : DMA_TCR1, Size.LONG);
    }

    private boolean is68SOn(DmaFifo68k dmaFifo68k) {
        return (dmaFifo68k.read(MD_DMAC_CTRL, M68K, MD_DMAC_CTRL.addr, Size.WORD) & 4) > 0;
    }
}