package s32x;

import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.sh2.device.IntControl;
import s32x.sh2.device.IntControl.Sh2Interrupt;
import s32x.util.MarsLauncherHelper;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static s32x.MarsRegTestUtil.*;
import static s32x.S32XMMREG.CART_INSERTED;
import static s32x.S32XMMREG.CART_NOT_INSERTED;
import static s32x.dict.S32xDict.*;
import static s32x.sh2.device.IntControl.Sh2Interrupt.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 */
public class S32xSharedRegsTest {

    private MarsLauncherHelper.Sh2LaunchContext lc;

    @BeforeEach
    public void before() {
        lc = createTestInstance();
    }

    @Test
    public void testFm() {
        int expFm, fm;
        checkFm(lc, 0);

        testFm(Z80, MD_ADAPTER_CTRL_REG);
        testFm(M68K, MD_ADAPTER_CTRL_REG);
        testFm(MASTER, SH2_INT_MASK);
        testFm(SLAVE, SH2_INT_MASK);
    }

    @Test
    public void testSh2IntMask() {
        testSh2IntMaskCpu(MASTER, SLAVE);
        testSh2IntMaskCpu(SLAVE, MASTER);
    }

    //not shared
    private void testSh2IntMaskCpu(CpuDeviceAccess cpu, CpuDeviceAccess other) {
        System.out.println(cpu);
        IntControl intC = cpu == MASTER ? lc.masterCtx.devices.intC : lc.slaveCtx.devices.intC;
        IntControl otherIntC = other == MASTER ? lc.masterCtx.devices.intC : lc.slaveCtx.devices.intC;
        Sh2Interrupt[] ints = {PWM_06, CMD_08, HINT_10, VINT_12};

        int reg = SH2_INT_MASK;
        int value = 0xF; //all valid ints
        writeBus(lc, cpu, reg, value, Size.WORD);
        writeBus(lc, other, reg, 0, Size.WORD);

        //set FM, check that intValid is not changed
        writeBus(lc, cpu, reg, 1 << 7, Size.BYTE);
        int res = readBus(lc, cpu, reg + 1, Size.BYTE);
        int otherRes = readBus(lc, other, reg + 1, Size.BYTE);
        Assertions.assertEquals(value, res);
        Assertions.assertEquals(0, otherRes);

        for (Sh2Interrupt inter : ints) {
            intC.setIntPending(inter, true);
            otherIntC.setIntPending(inter, true);
            Assertions.assertEquals(inter.level, intC.getInterruptLevel());
            Assertions.assertEquals(0, otherIntC.getInterruptLevel());
            intC.clearCurrentInterrupt();
            otherIntC.setIntPending(inter, false);
        }
    }


    private void testFm(CpuDeviceAccess cpu, int reg) {
        int expFm, fm;

        expFm = fm = 1;
        writeBus(lc, cpu, reg, fm << 7, Size.BYTE);
        checkFm(lc, expFm);

        expFm = fm = 0;
        writeBus(lc, cpu, reg, fm << 7, Size.BYTE);
        checkFm(lc, expFm);

        if (cpu != Z80) {
            expFm = fm = 1;
            writeBus(lc, cpu, reg, fm << 15, Size.WORD);
            checkFm(lc, expFm);

            expFm = fm = 0;
            writeBus(lc, cpu, reg, fm << 15, Size.WORD);
            checkFm(lc, expFm);
        }
    }

    @Test
    public void testCart() {
        int cartSize = 0x100;
        //defaults to 0
        checkCart(lc, CART_INSERTED);

        //cart inserted
        lc.s32XMMREG.setCart(cartSize);
        int exp = CART_INSERTED;

        checkCart(lc, exp);

        //cart removed, size = 0
        lc.s32XMMREG.setCart(0);
        checkCart(lc, CART_NOT_INSERTED);
    }

    @Test
    public void testAden01_BYTE_M68K() {
        testAden01_BYTE_internal(M68K);
    }

    @Test
    public void testAden01_BYTE_Z80() {
        testAden01_BYTE_internal(Z80);
    }

    private void testAden01_BYTE_internal(CpuDeviceAccess cpu) {
        int aden, expAden;
        //defaults to 0
        checkAden(lc, 0);

        //m68k sets Aden -> OK
        expAden = aden = 1;
        writeBus(lc, cpu, MD_ADAPTER_CTRL_REG + 1, aden, Size.BYTE);
        checkAden(lc, expAden);

        //m68k clears Aden, not supported
        aden = 0;
        expAden = 1;
        try {
            writeBus(lc, cpu, MD_ADAPTER_CTRL_REG + 1, aden, Size.BYTE);
            if (cpu != Z80) {
                writeBus(lc, cpu, MD_ADAPTER_CTRL_REG, aden, Size.WORD);
            }
        } catch (AssertionError expected) {
        }
        ;
        checkAden(lc, expAden);
    }

    @Test
    public void testAden02_WORD() {
        int aden, expAden;
        //defaults to 0
        checkAden(lc, 0);

        //m68k sets Aden -> OK
        expAden = aden = 1;
        writeBus(lc, M68K, MD_ADAPTER_CTRL_REG, aden, Size.WORD);
        checkAden(lc, expAden);
    }

    @Test
    public void testSh2SetAden() {
        testSh2SetAdenInternal(0);
        testSh2SetAdenInternal(1);
    }

    @Test
    public void testSh2SetCart() {
        testSh2SetCartInternal(0);
        testSh2SetCartInternal(0x100);
    }

    private void testSh2SetCartInternal(int cartSize) {
        int expCartBit = cartSize > 0 ? CART_INSERTED : CART_NOT_INSERTED;
        lc.s32XMMREG.setCart(cartSize);
        checkCart(lc, expCartBit);

        //sh2 cannot set Cart
        for (int cart = 0; cart < 2; cart++) {
            writeBus(lc, MASTER, SH2_INT_MASK, cart * SH2_nCART_BYTE, Size.BYTE);
            checkCart(lc, expCartBit);
            writeBus(lc, SLAVE, SH2_INT_MASK, cart * SH2_nCART_BYTE, Size.BYTE);
            checkCart(lc, expCartBit);
            writeBus(lc, MASTER, SH2_INT_MASK, SH2_nCART_WORD * cart, Size.WORD);
            checkCart(lc, expCartBit);
            writeBus(lc, SLAVE, SH2_INT_MASK, SH2_nCART_WORD * cart, Size.WORD);
        }
    }

    private void testSh2SetAdenInternal(int adenInit) {
        MarsRegTestUtil.setAdenMdSide(lc, adenInit);
        checkAden(lc, adenInit);
        int expAden = adenInit;
        System.out.println(adenInit);

        //sh2 cannot set Aden
        for (int aden = 0; aden < 2; aden++) {
            writeBus(lc, MASTER, SH2_INT_MASK, aden * SH2_ADEN_BYTE, Size.BYTE);
            checkAden(lc, expAden);
            writeBus(lc, SLAVE, SH2_INT_MASK, aden * SH2_ADEN_BYTE, Size.BYTE);
            checkAden(lc, expAden);
            writeBus(lc, MASTER, SH2_INT_MASK, SH2_ADEN_WORD * aden, Size.WORD);
            checkAden(lc, expAden);
            writeBus(lc, SLAVE, SH2_INT_MASK, SH2_ADEN_WORD * aden, Size.WORD);
        }
    }

    @Test
    public void testHEN() {
        //defaults to 0
        checkHen(lc, 0);

        int val = 1 << INTMASK_HEN_BIT_POS;
        writeBus(lc, MASTER, SH2_INT_MASK, val, Size.WORD);
        checkHen(lc, val);
        writeBus(lc, SLAVE, SH2_INT_MASK, val, Size.WORD);
        checkHen(lc, val);
        writeBus(lc, SLAVE, SH2_INT_MASK, 0, Size.WORD);
        checkHen(lc, 0);

        writeBus(lc, SLAVE, SH2_INT_MASK + 1, val, Size.BYTE);
        checkHen(lc, val);
        writeBus(lc, MASTER, SH2_INT_MASK + 1, val, Size.BYTE);
        checkHen(lc, val);
        writeBus(lc, MASTER, SH2_INT_MASK + 1, 0, Size.BYTE);
        checkHen(lc, 0);
    }

    @Test
    public void testREN() {
        //defaults to 1, read-only value
        checkRenBit(lc, true);

        //keep the sh2 reset
        int val = 0 << P32XS_REN | P32XS_nRES;
        writeBus(lc, M68K, MD_ADAPTER_CTRL_REG, val, Size.WORD);
        checkRenBit(lc, true);
        writeBus(lc, Z80, MD_ADAPTER_CTRL_REG, val, Size.WORD);
        checkRenBit(lc, true);

        writeBus(lc, M68K, MD_ADAPTER_CTRL_REG + 1, val, Size.BYTE);
        checkRenBit(lc, true);
        writeBus(lc, Z80, SH2_INT_MASK + 1, val, Size.BYTE);
        checkRenBit(lc, true);
    }
}
