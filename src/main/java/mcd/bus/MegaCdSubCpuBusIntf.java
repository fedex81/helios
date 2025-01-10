package mcd.bus;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.bus.model.MdM68kBusProvider;
import omegadrive.util.BufferUtil;
import omegadrive.vdp.model.BaseVdpAdapterEventSupport;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public interface MegaCdSubCpuBusIntf extends MdM68kBusProvider, BufferUtil.StepDevice, BaseVdpAdapterEventSupport.VdpEventListener {

    int getLedState();

    McdSubInterruptHandler getInterruptHandler();

    void handleWramSetupChange(MegaCdMemoryContext.WramSetup prev, MegaCdMemoryContext.WramSetup ws);
}
