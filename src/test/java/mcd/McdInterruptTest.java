package mcd;

import m68k.cpu.CpuException;
import mcd.bus.McdSubInterruptHandler;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.McdGateArrayRegTest.SUB_INT_MASK_ODD;
import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_ASIC;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdInterruptTest extends McdRegTestBase {

    /**
     * ASIC interrupt cannot be made pending and triggered later.
     * If it is masked it is lost.
     */
    @Test
    public void testAsicIntPending() {
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        //subCpu ignores interrupts
        subCpu.getM68k().setSR(0x2700);

        McdSubInterruptHandler interruptHandler = subCpuBus.getInterruptHandler();
        subCpuBus.write(SUB_INT_MASK_ODD, 0xFF, Size.BYTE);

        interruptHandler.raiseInterrupt(INT_ASIC);
        assertNoInterruptTrigger(interruptHandler);

        //allow int
        subCpu.getM68k().setSR(0x2000);
        assertOneInterruptTrigger(interruptHandler);

        //mask all
        subCpuBus.write(SUB_INT_MASK_ODD, 0, Size.BYTE);
        interruptHandler.raiseInterrupt(INT_ASIC);
        assertNoInterruptTrigger(interruptHandler);

        //unmask all
        subCpuBus.write(SUB_INT_MASK_ODD, 0xFF, Size.BYTE);
        assertOneInterruptTrigger(interruptHandler);
    }

    @Test
    public void testAsicIntClearedOnIen1Disable() {
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        subCpu.getM68k().setSR(0x2700);

        var inthandler = subCpuBus.getInterruptHandler();
        subCpuBus.write(SUB_INT_MASK_ODD, 0xFF, Size.BYTE);

        //raise ASIC int while SR masked -> pending (not taken)
        inthandler.raiseInterrupt(INT_ASIC);
        assertNoInterruptTrigger(inthandler);

        //disable IEN1, pending state should be cleared
        subCpuBus.write(SUB_INT_MASK_ODD, 0, Size.BYTE);

        //allow ints and enable IEN1, nothing should fire, the pending ASIC INT was dropped
        subCpu.getM68k().setSR(0x2000);
        subCpuBus.write(SUB_INT_MASK_ODD, 0xFF, Size.BYTE);
        assertNoInterruptTrigger(inthandler);
    }

    public static void assertOneInterruptTrigger(McdSubInterruptHandler interruptHandler) {
        try {
            interruptHandler.handleInterrupts();
            Assertions.fail();
        } catch (CpuException e) {
            //expected
            //m68k.cpu.CpuException: Interrupt vector not set for uninitialised
            //interrupt vector while trapping uninitialised vector 26
        }
    }

    public static void assertNoInterruptTrigger(McdSubInterruptHandler interruptHandler) {
        try {
            interruptHandler.handleInterrupts();
        } catch (CpuException e) {
            Assertions.fail();
        }
    }
}
