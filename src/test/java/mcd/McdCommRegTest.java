package mcd;

import omegadrive.util.Size;
import omegadrive.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.Consumer;

import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_COMM_FLAGS;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.Util.random;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdCommRegTest extends McdRegTestBase {

    final Runnable regMatches = () -> {
        Assertions.assertEquals(readMainRegEven(MCD_COMM_FLAGS), readSubRegEven(MCD_COMM_FLAGS));
        Assertions.assertEquals(readMainRegOdd(MCD_COMM_FLAGS), readSubRegOdd(MCD_COMM_FLAGS));
        Assertions.assertEquals(readMainRegWord(MCD_COMM_FLAGS), readSubRegWord(MCD_COMM_FLAGS));
    };

    final Consumer<Integer> checkWordValue = val -> {
        int mmsbval = readMainRegWord(MCD_COMM_FLAGS);
        int smsbval = readSubRegWord(MCD_COMM_FLAGS);
        Assertions.assertEquals(val, mmsbval);
        Assertions.assertEquals(val, smsbval);
        regMatches.run();
    };


    @Test
    public void testCommRegs() {
        testRegsSize(Size.BYTE);
        testRegsSize(Size.WORD);
        testRegsSize(Size.LONG);
    }

    @Test
    public void testCommFlags() {
        int mval = readMainRegWord(MCD_COMM_FLAGS);
        int sval = readSubRegWord(MCD_COMM_FLAGS);

        Assertions.assertEquals(mval, sval);

        int val = random.nextInt(0x100);
        int mlsbval = readMainRegOdd(MCD_COMM_FLAGS);
        int slsbval = readSubRegOdd(MCD_COMM_FLAGS);
        Assertions.assertEquals(mlsbval, slsbval);
        writeMainRegEvenByte(MCD_COMM_FLAGS, val);
        mval = readMainRegEven(MCD_COMM_FLAGS);
        sval = readSubRegEven(MCD_COMM_FLAGS);
        Assertions.assertEquals(mval, sval);
        //lsb has not changed
        Assertions.assertEquals(mlsbval, readMainRegOdd(MCD_COMM_FLAGS));
        Assertions.assertEquals(slsbval, readSubRegOdd(MCD_COMM_FLAGS));
        //reg matches
        regMatches.run();

        val = random.nextInt(0x100);
        int mmsbval = readMainRegEven(MCD_COMM_FLAGS);
        int smsbval = readSubRegEven(MCD_COMM_FLAGS);
        Assertions.assertEquals(mmsbval, smsbval);
        writeSubRegOddByte(MCD_COMM_FLAGS, val);
        mval = readMainRegOdd(MCD_COMM_FLAGS);
        sval = readSubRegOdd(MCD_COMM_FLAGS);
        Assertions.assertEquals(mval, sval);
        //msb has not changed
        Assertions.assertEquals(mmsbval, readMainRegEven(MCD_COMM_FLAGS));
        Assertions.assertEquals(smsbval, readSubRegEven(MCD_COMM_FLAGS));
        //reg matches
        regMatches.run();
    }

    @Test
    public void testCommFlags2() {
        int mval = readMainRegWord(MCD_COMM_FLAGS);
        int sval = readSubRegWord(MCD_COMM_FLAGS);
        Assertions.assertEquals(mval, sval);
        Assertions.assertEquals(0, sval);

        writeMainRegWord(MCD_COMM_FLAGS, 0xAABB);
        checkWordValue.accept(0xBB00);

        writeSubRegWord(MCD_COMM_FLAGS, 0xCCDD);
        checkWordValue.accept(0xBBDD);

        writeMainRegOddByte(MCD_COMM_FLAGS, 0xEE);
        checkWordValue.accept(0xEEDD);

        writeMainRegEvenByte(MCD_COMM_FLAGS, 0xBB);
        checkWordValue.accept(0xBBDD);

        writeSubRegEvenByte(MCD_COMM_FLAGS, 0xFF);
        checkWordValue.accept(0xBBFF);

        writeSubRegOddByte(MCD_COMM_FLAGS, 0xAA);
        checkWordValue.accept(0xBBAA);
    }


    private void testRegsSize(Size size) {
        Arrays.fill(ctx.commonGateRegs, (byte) 0);
        int max = size.getMax() < 0 ? Integer.MAX_VALUE : size.getMax();
        for (int i = 0; i < END_MCD_MAIN_GA_COMM_R - START_MCD_MAIN_GA_COMM_R; i += size.getByteSize()) {
            System.out.println(th(i));
            int mreg = START_MCD_MAIN_GA_COMM_R + i;
            int sreg = START_MCD_SUB_GA_COMM_R + i;
            int res = readAddressSize(M68K, mreg, size);
            int sres = readAddressSize(SUB_M68K, mreg, size);
            Assertions.assertEquals(sres, res);
            Assertions.assertEquals(0, res);
            //master write
            int vm = 1 + Util.random.nextInt(max - 1);
            writeAddressSize(M68K, mreg, vm, size);
            res = readAddressSize(M68K, mreg, size);
            sres = readAddressSize(SUB_M68K, sreg, size);
            if (mreg < END_MCD_MAIN_GA_COMM_W) {
                Assertions.assertEquals(sres, res);
            } else {
                Assertions.assertNotEquals(vm, res);
                Assertions.assertNotEquals(vm, sres);
            }
            //slave write
            int vs = Math.max(1, vm + 1);
            writeAddressSize(SUB_M68K, sreg, vs, size);
            res = readAddressSize(M68K, mreg, size);
            sres = readAddressSize(SUB_M68K, sreg, size);
            if (sreg >= START_MCD_SUB_GA_COMM_W) {
                Assertions.assertEquals(sres, res);
            } else {
                Assertions.assertNotEquals(vs, res);
                Assertions.assertNotEquals(vs, sres);
            }

        }
    }
}
