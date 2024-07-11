package mcd;

import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.Util.readData;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdPrgRamProtectTest extends McdRegTestBase {

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
}