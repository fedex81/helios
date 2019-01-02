package omegadrive.vdp;

import omegadrive.vdp.model.IVdpFifo;

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

    public static void runVdpSlot(VdpProvider vdp) {
        vdp.run(VDP_SLOT_CYCLES);
    }

    public static void runVdpUntilFifoEmpty(VdpProvider vdp) {
        IVdpFifo fifo = vdp.getFifo();
        do {
            vdp.run(VDP_SLOT_CYCLES);
        } while (!fifo.isEmpty());
    }

    public static void runVdpUntilDmaDone(VdpProvider vdp) {
        boolean dmaDone;
        do {
            vdp.run(VDP_SLOT_CYCLES);
            dmaDone = (vdp.readControl() & 0x2) == 0;
        } while (!dmaDone);
    }

    public static boolean isVBlank(VdpProvider vdp) {
        return (vdp.readControl() & 0x8) == 8;
    }

    public static boolean isHBlank(VdpProvider vdp) {
        return (vdp.readControl() & 0x4) == 4;
    }
}
