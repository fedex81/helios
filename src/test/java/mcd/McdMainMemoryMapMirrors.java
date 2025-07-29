package mcd;

import mcd.dict.MegaCdDict;
import omegadrive.util.BufferUtil;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static mcd.dict.MegaCdDict.MCD_MAIN_PRG_RAM_WINDOW_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_COMM0;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_COMM3;
import static omegadrive.bus.model.MdMainBusProvider.MEGA_CD_EXP_START;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdMainMemoryMapMirrors extends McdRegTestBase {

    @BeforeEach
    public void setup() {
        super.setupBase();
        MdRuntimeData.setAccessTypeExt(BufferUtil.CpuDeviceAccess.M68K);
    }

    //$440000-$5FFFFF repeatedly mirrors the $400000-$43FFFF area. (7 copies of Boot ROM / PRG RAM)
    @Test
    public void testBootRomPrgRamMirrors() {
        ByteBuffer biosbb = ByteBuffer.allocate(0x1000);
        Arrays.fill(biosbb.array(), (byte) 0xAB);
        lc.mainBus.setBios(biosbb);
        testMirrorsInternal(MegaCdDict.START_MCD_MAIN_PRG_RAM_MODE1 | 0x144);

        //test boot rom mirrors
        int addr2 = MegaCdDict.MCD_MAIN_MODE1_MASK + 0x348;
        int val = mainCpuBus.read(addr2, Size.BYTE);
        for (int j = 1; j < 8; j++) {
            int b = 0x40_000 * j;
//            System.out.println(th(addr2 + b));
            int res2 = mainCpuBus.read(addr2 + b, Size.BYTE);
            Assertions.assertEquals(val, res2);
            //write to boot rom, ignored
            int c = addr2 + b + 0xFE;
            int exp = mainCpuBus.read(c, Size.BYTE);
            mainCpuBus.write(c, 0x13, Size.BYTE);
            res2 = mainCpuBus.read(c, Size.BYTE);
            Assertions.assertEquals(exp, res2);
            //check that the write did NOT end up in PRGRAM
            int pramAddr = MegaCdDict.START_MCD_MAIN_PRG_RAM_MODE1 | (c & MCD_MAIN_PRG_RAM_WINDOW_MASK);
//            System.out.println(th(addr2 + b) + "," + th(c) + "," + th(pramAddr));
            Assertions.assertNotEquals(exp, mainCpuBus.read(pramAddr, Size.BYTE));
        }
    }

    //$640000-$7FFFFF repeatedly mirrors the $600_000-$63FFFF area. (7 copies of Word RAM)
    @Test
    public void testWramMirrors() {
        testMirrorsInternal(MegaCdDict.START_MCD_MAIN_WORD_RAM_MODE1 + 0x244);
    }

    //$A12040-$A120FF repeatedly mirrors the $A12000-$A1203F area. (3 copies of SCD registers)

    @Test
    public void testGateArrayRegsMirror() {
        int addr = MEGA_CD_EXP_START + MCD_COMM3.addr;
        int val = 0x44556677;
        mainCpuBus.write(addr, val, Size.LONG);
        Assertions.assertEquals(val, mainCpuBus.read(addr, Size.LONG));
        for (int i = 1; i < 4; i++) {
            int a = 0x40 * i;
            int res = mainCpuBus.read(addr + a, Size.LONG);
//            System.out.println(th(addr + a));
            Assertions.assertEquals(val, res);
            int addr2 = MEGA_CD_EXP_START + MCD_COMM0.addr;
            mainCpuBus.write(addr2, (res * i) & 0xFF, Size.BYTE);
            for (int j = 0; j < 4; j++) {
                int b = 0x40 * i;
                int res2 = mainCpuBus.read(addr2 + b, Size.BYTE);
                Assertions.assertEquals(res2, (res * i) & 0xFF);
            }
        }
    }

    private void testMirrorsInternal(int addr) {
        mainCpuBus.write(addr, 0x11223344, Size.LONG);
        Assertions.assertEquals(0x11223344, mainCpuBus.read(addr, Size.LONG));

        int diff = 31;

        for (int i = 1; i < 8; i++) {
            int a = 0x40_000 * i;
//            System.out.println(th(addr + a));
            int res = mainCpuBus.read(addr + a, Size.LONG);
            Assertions.assertEquals(0x11223344, res);
            int addr2 = addr + a + (diff * i);
            mainCpuBus.write(addr2, (diff * i) & 0xFF, Size.BYTE);
            for (int j = 0; j < 8; j++) {
                int b = 0x40_000 * i;
                int res2 = mainCpuBus.read(addr + b + (diff * i), Size.BYTE);
                Assertions.assertEquals(res2, (diff * i) & 0xFF);
            }
        }
    }
}
