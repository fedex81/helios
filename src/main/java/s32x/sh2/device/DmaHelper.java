package s32x.sh2.device;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.dict.S32xDict;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class DmaHelper {

    private static final Logger LOG = LogHelper.getLogger(DmaHelper.class.getSimpleName());

    private static final int FIFO_REG_SH2 = 0x2000_4000 + S32xDict.RegSpecS32x.SH2_FIFO_REG.addr;

    private final static DmaSrcDestMode[] modeVals = DmaSrcDestMode.values();
    private final static DmaTransferSize[] trnVals = DmaTransferSize.values();

    enum DmaTransferSize {
        BYTE(1), WORD(2), LONG(4), BYTE_16(4); //4 transfers of 4 bytes

        public final int byteSize;

        DmaTransferSize(int s) {
            this.byteSize = s;
        }
    }

    enum DmaSrcDestMode {
        FIXED(0),
        INCREMENT(1), DECREMENT(-1), ILLEGAL(0);

        public final int signMult;

        DmaSrcDestMode(int s) {
            this.signMult = s;
        }
    }

    public static DmaChannelSetup createChannel(int channel) {
        DmaChannelSetup d = new DmaChannelSetup();
        d.channel = channel;
        return d;
    }

    public static void updateChannelDmaor(DmaChannelSetup chan, int dmaor) {
        boolean dme = (dmaor & 1) > 0;
        chan.dmaor_dme = dme;
    }

    public static void updateChannelControl(DmaChannelSetup c, int chcr) {
        c.chcr_dmaEn = (chcr & 1) > 0;
        c.chcr_tranEndOk = (chcr & 2) > 0;
        c.chcr_intEn = (chcr & 4) > 0;
        c.chcr_autoReq = ((chcr >> 9) & 1) > 0;
        c.chcr_destMode = modeVals[(chcr >> 14) & 0x3];
        c.chcr_srcMode = modeVals[(chcr >> 12) & 0x3];
        c.chcr_transferSize = trnVals[(chcr >> 10) & 0x3];
        c.srcDelta = getAddressDelta(c.chcr_srcMode, c.chcr_transferSize, true);
        c.destDelta = getAddressDelta(c.chcr_destMode, c.chcr_transferSize, false);
        if (c.chcr_transferSize == DmaTransferSize.BYTE_16) {  //Sangokushi
            c.trnSize = Size.LONG;
            c.transfersPerStep = 4;
        } else {
            c.trnSize = Size.vals[c.chcr_transferSize.ordinal()];
            c.transfersPerStep = 1;
        }
    }

    private static int getAddressDelta(DmaSrcDestMode mode, DmaTransferSize transferSize, boolean isSrc) {
        int d = mode.signMult * transferSize.byteSize;
        //NOTE: FIXED -> +16 when SRC and 16 byte transfer size
        if (isSrc && mode == DmaSrcDestMode.FIXED && transferSize == DmaTransferSize.BYTE_16) {
            d = transferSize.byteSize;
        }
        return d;
    }

    public static class DmaChannelSetup {
        public int channel;
        public boolean chcr_dmaEn, chcr_intEn, chcr_autoReq, chcr_tranEndOk;
        public boolean dmaor_dme, dreqLevel;
        public DmaSrcDestMode chcr_destMode, chcr_srcMode;
        public DmaTransferSize chcr_transferSize;
        public boolean dmaInProgress;
        public int srcDelta, destDelta, transfersPerStep;
        public Size trnSize;

        @Override
        public String toString() {
            return "DmaChannelSetup{" +
                    "channel=" + channel +
                    ", chcr_dmaEn=" + chcr_dmaEn +
                    ", chcr_intEn=" + chcr_intEn +
                    ", chcr_autoReq=" + chcr_autoReq +
                    ", chcr_tranEndOk=" + chcr_tranEndOk +
                    ", dmaor_dme=" + dmaor_dme +
                    ", dreqLevel=" + dreqLevel +
                    ", chcr_destMode=" + chcr_destMode +
                    ", chcr_srcMode=" + chcr_srcMode +
                    ", chcr_transferSize=" + chcr_transferSize +
                    ", dmaInProgress=" + dmaInProgress +
                    ", srcDelta=" + srcDelta +
                    ", destDelta=" + destDelta +
                    ", transfersPerStep=" + transfersPerStep +
                    ", trnSize=" + trnSize +
                    '}';
        }
    }
}
