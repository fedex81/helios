package omegadrive.vdp.model;

import omegadrive.util.VideoMode;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface VdpDmaHandler {

    enum DmaMode {
        MEM_TO_VRAM, VRAM_FILL, VRAM_COPY;
    }

    DmaMode getDmaMode();

    DmaMode setupDma(GenesisVdpProvider.VramMode vramMode, long data, boolean m1);

    boolean doDmaSlot(VideoMode videoMode);

    void setupDmaDataPort(int dataWord);

    boolean dmaInProgress();

    default String getDmaStateString() {
        return "Not implemented";
    }
}
