package omegadrive.bus.megacd;

import omegadrive.util.Size;
import omegadrive.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static omegadrive.bus.megacd.MegaCdMainCpuBus.MEGA_CD_EXP_START;
import static omegadrive.bus.megacd.MegaCdMainCpuBus.*;
import static omegadrive.bus.megacd.MegaCdSubCpuBus.*;
import static omegadrive.util.Util.random;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdCommRegTest extends McdRegTestBase {

    @Test
    public void testCommRegs() {
        testRegsSize(Size.BYTE);
        testRegsSize(Size.WORD);
        testRegsSize(Size.LONG);
    }

    @Test
    public void testCommFlags() {
        int mreg = MEGA_CD_EXP_START + 0xE;
        int sreg = START_MCD_SUB_GATE_ARRAY_REGS + 0xE;
        int mval = mainCpuBus.read(mreg, Size.WORD);
        int sval = subCpuBus.read(sreg, Size.WORD);
        Assertions.assertEquals(mval, sval);

        //main can write to MSB only
        Assertions.assertThrowsExactly(AssertionError.class, () -> mainCpuBus.write(mreg, 1, Size.WORD));
        Assertions.assertThrowsExactly(AssertionError.class, () -> mainCpuBus.write(mreg, 2, Size.LONG));
        Assertions.assertThrowsExactly(AssertionError.class, () -> mainCpuBus.write(mreg + 1, 3, Size.BYTE));

        int val = random.nextInt(0x100);
        int mlsbval = mainCpuBus.read(mreg + 1, Size.BYTE);
        int slsbval = subCpuBus.read(sreg + 1, Size.BYTE);
        Assertions.assertEquals(mlsbval, slsbval);
        mainCpuBus.write(mreg, val, Size.BYTE);
        mval = mainCpuBus.read(mreg, Size.BYTE);
        sval = subCpuBus.read(sreg, Size.BYTE);
        Assertions.assertEquals(mval, sval);
        //lsb has not changed
        Assertions.assertEquals(mlsbval, mainCpuBus.read(mreg + 1, Size.BYTE));
        Assertions.assertEquals(slsbval, subCpuBus.read(sreg + 1, Size.BYTE));

        //sub can write to LSB only
        Assertions.assertThrowsExactly(AssertionError.class, () -> subCpuBus.write(sreg, 1, Size.WORD));
        Assertions.assertThrowsExactly(AssertionError.class, () -> subCpuBus.write(sreg, 2, Size.LONG));
        Assertions.assertThrowsExactly(AssertionError.class, () -> subCpuBus.write(sreg, 3, Size.BYTE));

        val = random.nextInt(0x100);
        int mmsbval = mainCpuBus.read(mreg, Size.BYTE);
        int smsbval = subCpuBus.read(sreg, Size.BYTE);
        Assertions.assertEquals(mmsbval, smsbval);
        subCpuBus.write(sreg + 1, val, Size.BYTE);
        mval = mainCpuBus.read(mreg + 1, Size.BYTE);
        sval = subCpuBus.read(sreg + 1, Size.BYTE);
        Assertions.assertEquals(mval, sval);
        //msb has not changed
        Assertions.assertEquals(mlsbval, mainCpuBus.read(mreg, Size.BYTE));
        Assertions.assertEquals(slsbval, subCpuBus.read(sreg, Size.BYTE));
    }


    private void testRegsSize(Size size) {
        Arrays.fill(ctx.gateRegs, (byte) 0);
        int max = size.getMax() < 0 ? Integer.MAX_VALUE : size.getMax();
        for (int i = 0; i < END_MCD_MAIN_GA_COMM_R - START_MCD_MAIN_GA_COMM_R; i += size.getByteSize()) {
            System.out.println(th(i));
            int mreg = START_MCD_MAIN_GA_COMM_R + i;
            int sreg = START_MCD_SUB_GA_COMM_R + i;
            int res = mainCpuBus.read(mreg, size);
            int sres = subCpuBus.read(sreg, size);
            Assertions.assertEquals(sres, res);
            Assertions.assertEquals(0, res);
            //master write
            int v = Util.random.nextInt(max);
            mainCpuBus.write(mreg, v, size);
            res = mainCpuBus.read(mreg, size);
            sres = subCpuBus.read(sreg, size);
            if (mreg < END_MCD_MAIN_GA_COMM_W) {
                Assertions.assertEquals(sres, res);
            } else {
                Assertions.assertNotEquals(v, res);
                Assertions.assertNotEquals(v, sres);
            }
            //slave write
            v = Util.random.nextInt(max);
            subCpuBus.write(sreg, v, size);
            res = mainCpuBus.read(mreg, size);
            sres = subCpuBus.read(sreg, size);
            if (sreg >= START_MCD_SUB_GA_COMM_W) {
                Assertions.assertEquals(sres, res);
            } else {
                Assertions.assertNotEquals(v, res);
                Assertions.assertNotEquals(v, sres);
            }

        }
    }
}
