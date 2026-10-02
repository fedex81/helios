package mcd;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.McdGateArrayRegTest.MAIN_RESET_REG;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdResetTest extends McdRegTestBase {

    public static final int BUS_REQ_MASK = 2;
    public static final int RESET_MASK = 1;

    public static final int MAIN_RESET_REG_ODD = MAIN_RESET_REG + 1;

    /**
     * - system starts with SRES = 0, SBRK = 1
     * subCpu reset and bus assigned to main
     * <p>
     * MAIN:
     * mcdBusReq();
     * clears PRG RAM
     * mcdBusRelease();
     */
    @Test
    public void testBusReq() {
        int mreg = mainCpuBus.read(MAIN_RESET_REG_ODD, Size.BYTE);
        Assertions.assertEquals(BUS_REQ_MASK, mreg);
        //subCpu is halted, toggling SBRK doesn't change that
        testBusReqInternal(mreg, new boolean[]{true, true});

        //SRES = 1, SBRK = 0
        //subCpu is running, but MAIN has the bus
        mreg = 1;
        mainCpuBus.write(MAIN_RESET_REG_ODD, mreg, Size.BYTE);
        testBusReqInternal(mreg, new boolean[]{false, false});

        //SRES = 1, SBRK = 1
        //subCpu is running, SUB has the bus
        mreg = 3;
        mainCpuBus.write(MAIN_RESET_REG_ODD, mreg, Size.BYTE);
        testBusReqInternal(mreg, new boolean[]{false, false});
    }

    /**
     * SRES = 0 forces SBRK to 1
     */
    @Test
    public void testResetAndBusReq() {
        int mreg = mainCpuBus.read(MAIN_RESET_REG_ODD, Size.BYTE);
        if (mreg != 0) {
//            System.out.println(th(mreg));
            //reset=1, busReq to 0
            mainCpuBus.write(MAIN_RESET_REG_ODD, RESET_MASK, Size.BYTE);
            waitForBusReq(0);
        }
        //now SRES = 1, SBRK = 0
        mreg = mainCpuBus.read(MAIN_RESET_REG_ODD, Size.BYTE);
        Assertions.assertEquals(RESET_MASK, mreg);

        //SRES = 0 -> SBRK goes to 1
        mainCpuBus.write(MAIN_RESET_REG_ODD, 0, Size.BYTE);
        waitForBusReq(BUS_REQ_MASK);
    }
    private void testBusReqInternal(int mreg, boolean[] exp) {
        //set busReq to 0
        mainCpuBus.write(MAIN_RESET_REG_ODD, mreg & (~BUS_REQ_MASK), Size.BYTE);
        waitForBusReq(0);
        //subCpu state
        Assertions.assertEquals(exp[0], subCpu.isStopped());
        //main bus request
        mainCpuBus.write(MAIN_RESET_REG_ODD, mreg | BUS_REQ_MASK, Size.BYTE);
        waitForBusReq(BUS_REQ_MASK);
        //subCpu state
        Assertions.assertEquals(exp[1], subCpu.isStopped());
    }

    private void waitForBusReq(int expVal) {
        int mreg;
        int cnt = 1000;
        do {
            mreg = mainCpuBus.read(MAIN_RESET_REG + 1, Size.BYTE);
            cnt--;
        } while (cnt > 0 && (mreg & BUS_REQ_MASK) != expVal);
        Assertions.assertNotEquals(0, cnt);
    }
}
