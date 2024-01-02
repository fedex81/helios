package omegadrive.bus.megacd;

import omegadrive.util.Size;
import omegadrive.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static mcd.bus.MegaCdMainCpuBus.MEGA_CD_EXP_START;
import static mcd.dict.MegaCdDict.*;
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

        Runnable regMatches = () -> {
            Assertions.assertEquals(mainCpuBus.read(mreg, Size.BYTE), subCpuBus.read(sreg, Size.BYTE));
            Assertions.assertEquals(mainCpuBus.read(mreg + 1, Size.BYTE), subCpuBus.read(sreg + 1, Size.BYTE));
            Assertions.assertEquals(mainCpuBus.read(mreg, Size.WORD), subCpuBus.read(sreg, Size.WORD));
        };

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
        //reg matches
        regMatches.run();

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
        Assertions.assertEquals(mmsbval, mainCpuBus.read(mreg, Size.BYTE));
        Assertions.assertEquals(smsbval, subCpuBus.read(sreg, Size.BYTE));
        //reg matches
        regMatches.run();
    }


    private void testRegsSize(Size size) {
        Arrays.fill(ctx.commonGateRegs, (byte) 0);
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
            int vm = 1 + Util.random.nextInt(max - 1);
            mainCpuBus.write(mreg, vm, size);
            res = mainCpuBus.read(mreg, size);
            sres = subCpuBus.read(sreg, size);
            if (mreg < END_MCD_MAIN_GA_COMM_W) {
                Assertions.assertEquals(sres, res);
            } else {
                Assertions.assertNotEquals(vm, res);
                Assertions.assertNotEquals(vm, sres);
            }
            //slave write
            int vs = Math.max(1, vm + 1);
            subCpuBus.write(sreg, vs, size);
            res = mainCpuBus.read(mreg, size);
            sres = subCpuBus.read(sreg, size);
            if (sreg >= START_MCD_SUB_GA_COMM_W) {
                Assertions.assertEquals(sres, res);
            } else {
                Assertions.assertNotEquals(vs, res);
                Assertions.assertNotEquals(vs, sres);
            }

        }
    }
}
