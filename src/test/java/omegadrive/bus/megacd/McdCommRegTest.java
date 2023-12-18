package omegadrive.bus.megacd;

import omegadrive.util.Size;
import omegadrive.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static omegadrive.bus.megacd.MegaCdMainCpuBus.*;
import static omegadrive.bus.megacd.MegaCdSubCpuBus.START_MCD_SUB_GA_COMM_R;
import static omegadrive.bus.megacd.MegaCdSubCpuBus.START_MCD_SUB_GA_COMM_W;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdCommRegTest extends McdRegTestBase {

    @Test
    public void testRegs() {
        testRegsSize(Size.BYTE);
        testRegsSize(Size.WORD);
        testRegsSize(Size.LONG);
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
