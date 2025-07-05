package mcd;

import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 * McdSubAddressTest
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2025
 */
public class McdSubAddressTest extends McdRegTestBase {

    int baseMask = 0x10_0000;
    int[] addresses = {
            START_MCD_SUB_PRG_RAM, START_MCD_SUB_PCM_AREA,
            START_MCD_SUB_BRAM_AREA + 1, START_MCD_SUB_GA_COMM_W
    };

    int[] masks = new int[0x10];

    {
        for (int i = 0; i < masks.length; i++) {
            masks[i] = baseMask * i;
        }
    }

    @Test
    public void testSubMirroring() {
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        for (int j = 0; j < addresses.length; j++) {
            int addr = addresses[j];
            int res = subCpuBus.read(addr, Size.BYTE);
//            System.out.println(th(addr) + " " + th(res));
            for (int i = 0; i < masks.length; i++) {
                int m = masks[i];
                int res2 = subCpuBus.read(m | addr, Size.BYTE);
                Assertions.assertEquals(res, res2);
                res = res2 + 1;
                subCpuBus.write(m | addr, res, Size.BYTE);
                res &= 0xFF;
            }
        }
    }
}
