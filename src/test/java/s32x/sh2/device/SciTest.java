package s32x.sh2.device;

import omegadrive.SystemLoader;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.MarsRegTestUtil;
import s32x.util.MarsLauncherHelper;
import s32x.util.Md32xRuntimeData;

import static omegadrive.util.Util.th;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class SciTest {

    private MarsLauncherHelper.Sh2LaunchContext lc;
    private SerialCommInterface msci, ssci;

    @BeforeEach
    public void before() {
        lc = MarsRegTestUtil.createTestInstance();
        lc.s32XMMREG.aden = 1;
        Md32xRuntimeData.releaseInstance();
        Md32xRuntimeData.newInstance(SystemLoader.SystemType.S32X);
        msci = lc.masterCtx.devices.sci;
        ssci = lc.slaveCtx.devices.sci;
    }

    /**
     * Mars check 01, SH serial test
     */
    @Test
    public void test01() {
        msci.reset();
        ssci.reset();

        msci.write(SCI_SCR, 0, Size.BYTE);
        msci.write(SCI_SMR, 0x30, Size.BYTE);
        msci.write(SCI_BRR, 0, Size.BYTE);
        msci.write(SCI_SCR, 1, Size.BYTE);
        msci.write(SCI_SCR, 0x70, Size.BYTE);

        ssci.write(SCI_SCR, 0, Size.BYTE);
        ssci.write(SCI_SMR, 0x30, Size.BYTE);
        ssci.write(SCI_BRR, 0, Size.BYTE);
        ssci.write(SCI_SCR, 3, Size.BYTE);
        ssci.write(SCI_SCR, 0x70, Size.BYTE);

        //MASTER SCI write SCI_TDR: aa BYTE
        //MASTER SCI write SCI_SSR: 7f BYTE
        msci.write(SCI_TDR, 0xaa, Size.BYTE);
        msci.write(SCI_SSR, 0x7f, Size.BYTE); //TDRE clear, runs sci master 1 step

        ssci.step(1);
        int data = ssci.read(SCI_RDR, Size.BYTE);
        System.out.println(th(data));
        Assertions.assertEquals(0xaa, data & 0xFF);
    }

    @Test
    public void testXMen() {
        msci.reset();
        ssci.reset();

        msci.write(SCI_SCR, 0, Size.BYTE);
        msci.write(SCI_SMR, 0xC0, Size.BYTE);
        ssci.write(SCI_SCR, 0, Size.BYTE);
        ssci.write(SCI_SMR, 0xC0, Size.BYTE);
        ssci.write(SCI_BRR, 0, Size.BYTE);
        ssci.write(SCI_SCR, 2, Size.BYTE);
        msci.write(SCI_BRR, 0, Size.BYTE);
        msci.write(SCI_SCR, 1, Size.BYTE);
        ssci.write(SCI_SCR, 0x52, Size.BYTE);
        msci.write(SCI_SCR, 0x21, Size.BYTE);

        msci.write(SCI_SSR, 0x4, Size.BYTE);
        int data = ssci.read(SCI_RDR, Size.BYTE);
        Assertions.assertEquals(0xFF, data & 0xFF);
        System.out.println(data);

        msci.write(SCI_SSR, 0x4, Size.BYTE);
        ssci.write(SCI_SSR, 0x84, Size.BYTE);
        msci.write(SCI_SSR, 0x4, Size.BYTE);
        data = ssci.read(SCI_RDR, Size.BYTE);
        Assertions.assertEquals(0xFF, data & 0xFF);
        System.out.println(data);
    }
}
