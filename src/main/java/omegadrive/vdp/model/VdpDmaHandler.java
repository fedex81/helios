package omegadrive.vdp.model;

import omegadrive.util.VideoMode;
import omegadrive.vdp.VdpProvider;

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

    DmaMode setupDma(VdpProvider.VramMode vramMode, long data, boolean m1);

    @Deprecated
    boolean doDma(VideoMode videoMode, boolean isBlanking);

    boolean doDmaSlot(VideoMode videoMode);

    void setupDmaDataPort(int dataWord);

    boolean dmaInProgress();
}
