package mcd.cdc;


import mcd.bus.McdSubInterruptHandler;
import mcd.cdd.Cdd.CddStatus;
import mcd.cdd.ExtendedCueSheet;
import mcd.dict.MegaCdMemoryContext;
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
    void write(RegSpecMcd regSpec, int address, int value, Size size);

    int read(RegSpecMcd regSpec, int address, Size size);

    void setMedia(ExtendedCueSheet extCueSheet);
    void decode(int sector);

    void poll();

    void dma();

    void step75hz();

    default void cdc_decoder_update(int sector, int track, CddStatus status) {
        assert false;
    }

    CdcModel.CdcContext getContext();

    void recalcRegValue(RegSpecMcd regSpec);

    static Cdc createInstance(MegaCdMemoryContext memoryContext, McdSubInterruptHandler interruptHandler) {
        return new CdcImpl(memoryContext, interruptHandler);
    }
}