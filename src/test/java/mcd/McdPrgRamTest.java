package mcd;

import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.dict.MegaCdDict.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
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
            ctx.wramHelper.update(M68K, wp << 8);

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
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        subCpuBus.write(END_MCD_SUB_PRG_RAM - 2, 0x1122, Size.WORD);
        subCpuBus.write(0, 0x3344, Size.WORD);
        int res = subCpuBus.read(END_MCD_SUB_PRG_RAM - 2, Size.LONG);
        Assertions.assertEquals(0x11223344, res);
    }

    int mainResetReg = McdGateArrayRegTest.MAIN_RESET_REG;


    //Dungeon Explorer US
    @Test
    public void testPrgRamZ80Access() {
        MdRuntimeData.setAccessTypeExt(M68K);
        //SUB stopped
        int r0 = mainCpuBus.read(mainResetReg, Size.WORD) & ~3;
        mainCpuBus.write(mainResetReg, r0, Size.WORD);
        lc.subCpu.setStop(true);

        testPrgRamZ80AccessInternal(true);

        //SUB running
        MdRuntimeData.setAccessTypeExt(M68K);
        r0 = mainCpuBus.read(mainResetReg, Size.WORD) & ~3;
        mainCpuBus.write(mainResetReg, r0 | 1, Size.WORD);
        lc.subCpu.setStop(false);
        testPrgRamZ80AccessInternal(false);
    }

    void testPrgRamZ80AccessInternal(boolean subStopped) {
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        int val = 0x1122;
        int res = subCpuBus.read(START_MCD_MAIN_PRG_RAM, Size.WORD);
        Assertions.assertNotEquals(val, res);

        subCpuBus.write(START_MCD_SUB_PRG_RAM, val, Size.WORD);
        res = subCpuBus.read(START_MCD_SUB_PRG_RAM, Size.WORD);
        Assertions.assertEquals(val, res);

        //SUB running MAIN,Z80 should not be able to access PRG-RAM
        int expVal = subStopped ? ~(val + 2) : val;
        MdRuntimeData.setAccessTypeExt(M68K);
        mainCpuBus.write(START_MCD_MAIN_PRG_RAM, ~(val + 2), Size.WORD);
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        res = subCpuBus.read(START_MCD_SUB_PRG_RAM, Size.WORD);
        Assertions.assertEquals((short) expVal, (short) res);

        MdRuntimeData.setAccessTypeExt(Z80);
        expVal = subStopped ? ~(val + 1) : val;
        MdRuntimeData.setAccessTypeExt(M68K);
        mainCpuBus.write(START_MCD_MAIN_PRG_RAM, ~(val + 1), Size.WORD);
        MdRuntimeData.setAccessTypeExt(SUB_M68K);
        res = subCpuBus.read(START_MCD_SUB_PRG_RAM, Size.WORD);
        Assertions.assertEquals((short) expVal, (short) res);
    }
}