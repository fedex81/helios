package s32x.sh2;

import omegadrive.util.BufferUtil.CpuDeviceAccess;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.util.Md32xRuntimeData;

import static s32x.dict.S32xDict.SH2_START_SDRAM;
import static s32x.dict.S32xDict.SH2_START_SDRAM_CACHE;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class TasTest extends Sh2BaseTest {

    //TAS 0100nnnn00011011
    int baseCode = 0x401b; //TAS.b @R0
    int cacheAddr = SH2_START_SDRAM_CACHE + 1;
    int ctAddr = SH2_START_SDRAM + 1;

    @BeforeEach
    public void before() {
        super.before();
        Md32xRuntimeData.newInstance();
        Md32xRuntimeData.setAccessTypeExt(CpuDeviceAccess.MASTER);
    }


    @Test
    public void testTas() {
        int ctVal = 0x6A;
        int expVal = ctVal | 0x80;
        //fill cache
        memory.read8(cacheAddr);
        //write to both cache and SDRAM
        memory.write8(cacheAddr, (byte) 0x7B);
        memory.write8(cacheAddr, (byte) 0);
        //write to SDRAM only
        memory.write8(ctAddr, (byte) 0x6A);

        ctx.SR = 0;
        ctx.registers[0] = cacheAddr;
        check(cacheAddr, ctAddr, ctVal, expVal);
    }

    @Test
    public void testTas2() {
        int ctVal = 0;
        int expVal = ctVal | 0x80;
        //fill cache
        memory.read8(cacheAddr);
        //write to both cache and SDRAM
        memory.write8(cacheAddr, (byte) 0x7B);
        //write to SDRAM only
        memory.write8(ctAddr, (byte) ctVal);

        ctx.SR = 0;
        ctx.registers[0] = cacheAddr;
        check(cacheAddr, ctAddr, ctVal, expVal);
    }

    @Test
    public void testTas3() {
        int ctVal = 0;
        int expVal = ctVal | 0x80;
        //fill cache
        memory.read8(cacheAddr);
        //write to both cache and SDRAM
        memory.write8(cacheAddr, (byte) 0x7B);
        //write to SDRAM only
        memory.write8(ctAddr, (byte) 0);

        ctx.SR = 0;
        ctx.registers[0] = cacheAddr;
        check(cacheAddr, ctAddr, ctVal, expVal);
    }

    private void check(int cacheAddr, int ctAddr, int ctVal, int expVal) {
        //TAS read bypass the cache -> goes to SDRAM directly
        //TAS write uses the cache - if there is a match
        sh2.TAS(baseCode);
        Assertions.assertEquals(ctVal == 0 ? 1 : 0, ctx.SR & Sh2.flagT);
        Assertions.assertEquals(cacheAddr, ctx.registers[0]);
        Assertions.assertEquals(expVal, memory.read8(cacheAddr));
        Assertions.assertEquals(expVal, memory.read8(ctAddr));
    }
}
