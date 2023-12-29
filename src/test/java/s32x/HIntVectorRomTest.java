package s32x;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.dict.S32xDict;
import s32x.util.MarsLauncherHelper;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static s32x.MarsRegTestUtil.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class HIntVectorRomTest {

    private MarsLauncherHelper.Sh2LaunchContext lc;

    @BeforeEach
    public void before() {
        lc = createTestInstance();
    }

    //testpico expects 0x70 to be 0 when the hintvector is not set, ie. it is not reading the data stored in the bios.
    @Test
    public void testVector() {
        int addr = S32xDict.M68K_START_HINT_VECTOR_WRITEABLE;
        //defaults to 0
        checkAden(lc, 0);
        //enable 32x, RV = 1
        MarsRegTestUtil.setAdenMdSide(lc, 1);
        setRv(lc, 1);
        int res = readBus(lc, M68K, addr, Size.LONG);
        Assertions.assertEquals(0, res);
        int vector = 0xAABBCCDD;
        writeBus(lc, M68K, addr, vector, Size.LONG);
        res = readBus(lc, M68K, addr, Size.LONG);
        Assertions.assertEquals(vector, res);
        writeBus(lc, M68K, addr, 0, Size.LONG);
        res = readBus(lc, M68K, addr, Size.LONG);
        Assertions.assertEquals(0, res);
    }
}
