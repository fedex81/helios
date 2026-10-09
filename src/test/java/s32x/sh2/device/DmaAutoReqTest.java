package s32x.sh2.device;

import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import s32x.DmaFifo68k;
import s32x.MarsRegTestUtil;
import s32x.S32XMMREG;
import s32x.util.MarsLauncherHelper;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.MASTER;
import static s32x.dict.S32xDict.RegSpecS32x.*;
import static s32x.dict.S32xDict.SH2_START_SDRAM;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class DmaAutoReqTest {

    private static final int BIT_68S = 1 << DmaFifo68k.M68K_68S_BIT_POS;

    private static final int CHCR_TE = 1 << 1; //transfer-end bit

    private static final int FIFO_OFFSET = 0x4000 + MD_FIFO_REG.addr;
    private static final int DREQ_LEN_OFFSET = 0x4000 + MD_DREQ_LEN.addr;
    private static final int DMA_CONTROL_OFFSET = 0x4000 + MD_DMAC_CTRL.addr;

    private MarsLauncherHelper.Sh2LaunchContext lc;
    private S32XMMREG s32XMMREG;
    private DmaC masterDmac;

    @BeforeEach
    public void beforeEach() {
        lc = MarsRegTestUtil.createTestInstance();
        s32XMMREG = lc.s32XMMREG;
        masterDmac = lc.mDevCtx.dmaC;
    }

    @Test
    public void test16ByteAutoReqCompleteInOneStep() {
        //DM_INC|SM_INC|TS_16BU|AR_ARM|DE
        int chcr = (1 << 14) | (1 << 12) | (3 << 10) | (1 << 9) | (1 << 0);
        //8 units * 4 longwords = 32 transfers.
        runAndAssertAtomic(chcr, 32, 8, 16);
    }

    @Test
    public void testLongAutoReqCompleteInOneStep() {
        //DM_INC|SM_INC|TS_LW(2<<10)|AR_ARM|DE
        int chcr = (1 << 14) | (1 << 12) | (2 << 10) | (1 << 9) | (1 << 0);
        //8 units * 4 longwords = 32 transfers.
        runAndAssertAtomic(chcr, 16, 16, 4);
    }

    private void runAndAssertAtomic(int chcr, int tcr, int units, int unitBytes) {
        int src = SH2_START_SDRAM + 0x1000;
        int dst = SH2_START_SDRAM + 0x3000;
        int totalLongs = (unitBytes / 4) * units;

        MdRuntimeData.setAccessTypeExt(MASTER);
        for (int i = 0; i < totalLongs; i++) {
            lc.memory.write(src + i * 4, 0xD00D_0000 | i, Size.LONG);
            lc.memory.write(dst + i * 4, 0, Size.LONG);
        }

        masterDmac.write(DMA_TCR0, tcr, Size.LONG);
        masterDmac.write(DMA_SAR0, src, Size.LONG);
        masterDmac.write(DMA_DAR0, dst, Size.LONG);
        masterDmac.write(DMA_CHCR0, chcr, Size.LONG);
        masterDmac.write(DMA_DMAOR, 1, Size.LONG); //DME -> starts autoreq DMA

        Assertions.assertTrue(masterDmac.isOneDmaInProgress());

        //exactly one step
        MdRuntimeData.setAccessTypeExt(MASTER);
        masterDmac.step(1);

        tcr = masterDmac.read(DMA_TCR0, Size.LONG) & 0xFF_FFFF;
        chcr = masterDmac.read(DMA_CHCR0, Size.LONG);

        Assertions.assertEquals(0, tcr);
        Assertions.assertFalse(masterDmac.isOneDmaInProgress());
        Assertions.assertTrue((chcr & CHCR_TE) != 0);

        //data integrity
        MdRuntimeData.setAccessTypeExt(MASTER);
        for (int i = 0; i < totalLongs; i++) {
            Assertions.assertEquals(0xD00D_0000 | i, lc.memory.read(dst + i * 4, Size.LONG));
        }
    }

    @Disabled("picodrive does this")
    @Test
    public void testFifoWriteDecrementsLenAndClear68S() {
        int dreqLen = 8;
        write(DMA_CONTROL_OFFSET, BIT_68S);
        write(DREQ_LEN_OFFSET, 0);

        Assertions.assertTrue(is68SOn());
        Assertions.assertEquals(0, readDreqLen());

        for (int w = 0; w <= dreqLen; w++) {
            writeFifo(0xA000 + w);
            int expectedLen = dreqLen - w;

            Assertions.assertEquals(expectedLen, readDreqLen());
            if (expectedLen == 0) {
                Assertions.assertFalse(is68SOn());
            } else {
                Assertions.assertTrue(is68SOn());
            }
        }

        writeFifo(0xB000);

        Assertions.assertEquals(0xFFFF, readDreqLen());
        Assertions.assertTrue(is68SOn());
    }

    @Disabled("picodrive does this")
    @Test
    public void testFifoWriteWhenLenZeroOverflows() {
        write(DMA_CONTROL_OFFSET, BIT_68S);
        write(DREQ_LEN_OFFSET, 0);

        Assertions.assertTrue(is68SOn());
        Assertions.assertEquals(0, readDreqLen());

        writeFifo(0xB000);

        Assertions.assertEquals(0xFFFF, readDreqLen());
        Assertions.assertTrue(is68SOn());
    }

    private void writeFifo(int val) {
        write(FIFO_OFFSET, val);
    }

    private void write(int offset, int value) {
        MdRuntimeData.setAccessTypeExt(M68K);
        s32XMMREG.write(offset, value, Size.WORD);
    }

    private boolean is68SOn() {
        MdRuntimeData.setAccessTypeExt(M68K);
        int val = s32XMMREG.read(DMA_CONTROL_OFFSET, Size.WORD);
        return (val & BIT_68S) > 0;
    }

    private int readDreqLen() {
        MdRuntimeData.setAccessTypeExt(M68K);
        return s32XMMREG.read(DREQ_LEN_OFFSET, Size.WORD) & 0xFFFF;
    }
}
