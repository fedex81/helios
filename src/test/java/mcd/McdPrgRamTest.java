package mcd;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.END_MCD_SUB_PRG_RAM;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.Util.readData;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdPrgRamTest extends McdRegTestBase {

    /**
     * From mcd-verificator
     */
    @Test
    public void testProgRamProtection() {
        int wp;
        for (int i = 0; i < 9; i++) {
            wp = (1 << i) - 1;
            ctx.update(M68K, wp << 8);

            int wp_size = 0;
            for (int u = 0; u < 0x80000; u += 256) {
                int val = (readData(ctx.prgRam, u, Size.BYTE) ^ 0xFF) & 0xFF;
                ctx.writeProgRam(u, val, Size.BYTE);

                int val2 = readData(ctx.prgRam, u, Size.BYTE) & 0xFF;
                if (val2 != val) {
                    wp_size = u + 256;
                }
            }
            Assertions.assertEquals(((1 << i) - 1) * 512, wp_size);
        }
    }

    //BcRacers E does this
    @Test
    public void testPrgRamLongRead() {
        subCpuBus.write(END_MCD_SUB_PRG_RAM - 2, 0x1122, Size.WORD);
        subCpuBus.write(0, 0x3344, Size.WORD);
        int res = subCpuBus.read(END_MCD_SUB_PRG_RAM - 2, Size.LONG);
        Assertions.assertEquals(0x11223344, res);
    }
}