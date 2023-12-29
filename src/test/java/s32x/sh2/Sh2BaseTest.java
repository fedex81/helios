package s32x.sh2;

import omegadrive.util.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import s32x.S32XMMREG;
import s32x.bus.S32xBus;
import s32x.bus.Sh2Bus;
import s32x.bus.Sh2BusImpl;
import s32x.util.BiosHolder;

import java.nio.ByteBuffer;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public abstract class Sh2BaseTest {

    protected Sh2Impl sh2;
    protected Sh2Context ctx;
    protected Sh2Bus memory;

    @BeforeEach
    public void before() {
        memory = new Sh2BusImpl(new S32XMMREG(), ByteBuffer.allocate(0xFF), BiosHolder.NO_BIOS, new S32xBus());
        sh2 = new Sh2Impl(memory);
        ctx = new Sh2Context(BufferUtil.CpuDeviceAccess.MASTER);
        sh2.setCtx(ctx);
    }
}
