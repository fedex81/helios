package mcd.asic;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.bus.megacd.McdRegTestBase;
import omegadrive.bus.megacd.McdWordRamTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static mcd.asic.Asic.readNibble;
import static mcd.dict.MegaCdMemoryContext.WramSetup.W_2M_MAIN;
import static mcd.dict.MegaCdMemoryContext.WramSetup.W_2M_SUB;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class AsicTest extends McdRegTestBase {

    @Test
    public void testAsic() {
        res(47, 0x4100, 0x1331, 1); //00
        res(242, 0x997d, 0x1CCC, 0xC); //01
        res(238, 0x98FD, 0x5552, 2);  //01
        res(230, 0x6082, 0x2555, 2);  //10
        res(243, 0x997f, 0xCCCC, 0xC); //11
        res(169, 0x9783, 0xCCCE, 0xE);
        res(22, 0x6002, 0x4444, 4);
    }

    //TODO check/fix
    @Test
    public void testWramMappings() {
        McdWordRamTest.setWramMain2M(lc);
        assert lc.memoryContext.wramSetup == W_2M_MAIN;
        for (int i = 0xa800; i < 0xa800 + 5; i++) {
            int bank = MegaCdMemoryContext.getBank(W_2M_SUB, SUB_M68K, i);
            int addr = MegaCdMemoryContext.getAddress(W_2M_SUB, i, bank);
            int nibble = Asic.getLowBitNibble(i);
            System.out.println(th(i) + "," + bank + "," + th(addr) + "," + nibble);
        }
    }

    private static void res(int w, int address, int wramWord, int expected) {
        int output = readNibble(address, wramWord);
        final int lowBit = 12 - ((address & 3) << 2);
        System.out.println(w + "," + address + "," + output + "," + lowBit + "," + th(wramWord));
        if (expected != Integer.MAX_VALUE) {
            Assertions.assertEquals(expected, output);
        }
    }
}