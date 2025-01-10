package mcd.bus;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;

import java.nio.ByteBuffer;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public interface MegaCdMainCpuBusIntf extends MdMainBusProvider {

    void setSubDevices(MC68000Wrapper subCpu, MegaCdSubCpuBusIntf subBus);

    boolean isEnableMode1();

    boolean isBios();

    //TODO: TEST only
    void setBios(ByteBuffer buffer);
}
