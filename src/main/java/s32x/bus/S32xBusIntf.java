package s32x.bus;

import omegadrive.bus.model.MdMainBusProvider;
import s32x.sh2.Sh2Context;
import s32x.util.BiosHolder;
import s32x.vdp.MarsVdp;

import java.nio.ByteBuffer;

/**
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public interface S32xBusIntf extends MdMainBusProvider, Sh2Bus.MdRomAccess {
    void setBios68k(BiosHolder.BiosData biosData);

    void setRom(ByteBuffer rom);

    void setSh2Context(Sh2Context master, Sh2Context slave);

    MarsVdp getMarsVdp();

    int getBankSetValue();
}
