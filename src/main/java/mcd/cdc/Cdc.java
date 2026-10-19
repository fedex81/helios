package mcd.cdc;


import mcd.bus.McdSubInterruptHandler;
import mcd.cdd.Cdd.CddStatus;
import mcd.cdd.ExtendedCueSheet;
import mcd.dict.MegaCdMemoryContext;
import mcd.pcm.McdPcm;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import static mcd.dict.MegaCdDict.RegSpecMcd;

/**
 * Cdc
 * Adapted from the Ares emulator
 * <p>
 * Sanyo LC8951x (CD controller)
 *
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public interface Cdc extends BufferUtil.StepDevice {

    Logger LOG = LogHelper.getLogger(Cdc.class.getSimpleName());

    int RAM_SIZE = 0x4000;
    /**
     * 695 steps at 32.55khz ~= 267000 cycles @ 12.5Mhz
     */
    int DECODER_ACTIVE_STEPS = 695;

    void write(RegSpecMcd regSpec, int address, int value, Size size);

    int read(RegSpecMcd regSpec, int address, Size size);

    void setMedia(ExtendedCueSheet extCueSheet);

    void poll();

    void dma();

    default void cdc_decoder_update(int sector, int track, CddStatus status) {
        assert false;
    }

    default void clearDecoderIrqIfExpired() {
    }

    CdcModel.CdcContext getContext();

    void recalcRegValue(RegSpecMcd regSpec);

    static Cdc createInstance(MegaCdMemoryContext memoryContext, McdSubInterruptHandler interruptHandler, McdPcm pcm) {
        return new CdcImpl(memoryContext, interruptHandler, pcm);
    }
}