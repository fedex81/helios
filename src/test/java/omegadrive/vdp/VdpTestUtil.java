package omegadrive.vdp;

import omegadrive.util.VideoMode;
import omegadrive.vdp.model.IVdpFifo;
import omegadrive.vdp.model.VdpDmaHandler;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class VdpTestUtil {


    static int VDP_SLOT_CYCLES = 2;


    public static void runCounterToStartFrame(VdpInterruptHandler h) {
        boolean isStart;
        do {
            h.increaseHCounter();
            isStart = h.gethCounterInternal() == 0 && h.getvCounterInternal() == 0;
        } while (!isStart);
        h.setHIntPending(false);
        h.setvIntPending(false);
    }

    public static void runVdpWhileFifoEmpty(VdpProvider vdp) {
        IVdpFifo fifo = vdp.getFifo();
        do {
            vdp.run(VDP_SLOT_CYCLES);
        } while (!fifo.isEmpty());
    }

    public static void runVdpWhileDmaDone(VdpProvider vdp) {
        boolean dmaDone = false;
        do {
            vdp.run(VDP_SLOT_CYCLES);
            dmaDone = (vdp.readControl() & 0x2) == 0;
        } while (!dmaDone);
    }


    public static void doDmaUntilDone(VdpProvider vdp, VdpDmaHandler dmaHandler) {
        IVdpFifo fifo = vdp.getFifo();
        boolean dmaDone = false;
        do {
            if (!fifo.isFull()) {
                dmaDone = dmaHandler.doDmaSlot(VideoMode.PAL_H40_V30);
            }
            vdp.run(VDP_SLOT_CYCLES);
        } while (!dmaDone);
    }

}
