package s32x;

import omegadrive.cart.mapper.md.Ssf2Mapper;
import omegadrive.util.BufferUtil;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.Size;
import org.junit.Assume;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.bus.S32xBus;
import s32x.bus.Sh2Bus;
import s32x.util.MarsLauncherHelper;

import java.util.function.Consumer;

import static omegadrive.bus.model.GenesisBusProvider.SRAM_LOCK;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.*;
import static omegadrive.util.BufferUtil.assertionsEnabled;
import static omegadrive.util.Util.th;
import static s32x.MarsRegTestUtil.readBus;
import static s32x.MarsRegTestUtil.setRv;
import static s32x.dict.S32xDict.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class RomAccessTest {

    private MarsLauncherHelper.Sh2LaunchContext lc;
    private static int ROM_SIZE = 0x3100;

    @BeforeEach
    public void before() {
        byte[] rom = new byte[ROM_SIZE];
        MarsRegTestUtil.fillAsMdRom(rom, true);
        lc = MarsRegTestUtil.createTestInstance(rom);
        lc.s32XMMREG.aden = 1;
        DmaFifo68k.rv = false;
    }

    @Test
    public void testMdAccess() {
        testMdAccessInternal(M68K);
        testMdAccessInternal(Z80);
    }

    @Test
    public void testSwitch() {
        testMdAccess();
        testSh2Access();
        testMdAccess();
        testSh2Access();
    }

    private void testMdAccessInternal(CpuDeviceAccess cpu) {
        int res;
        setRv(lc, 0);
        res = readBus(lc, cpu, M68K_START_ROM_MIRROR + 0x200, Size.BYTE);
        //random values are guaranteed not be 0 or 0xFF
        Assertions.assertTrue(res != 0 && res != 0xFF, th(res));

        res = readBus(lc, cpu, M68K_START_ROM_MIRROR_BANK + 0x202, Size.BYTE);
        Assertions.assertTrue(res != 0 && res != 0xFF);

        res = readBus(lc, cpu, 0x208, Size.BYTE);
        Assertions.assertTrue(res == 0 || res == 0xFF);

        setRv(lc, 1);
        res = readBus(lc, cpu, M68K_START_ROM_MIRROR + 0x204, Size.BYTE);
        Assertions.assertTrue(res == 0 || res == 0xFF);

        res = readBus(lc, cpu, M68K_START_ROM_MIRROR_BANK + 0x206, Size.BYTE);
        Assertions.assertTrue(res == 0 || res == 0xFF);

        res = readBus(lc, cpu, 0x20a, Size.BYTE);
        Assertions.assertTrue(res != 0 && res != 0xFF);
    }

    @Test
    public void testSh2Access() {
        int res;
        setRv(lc, 0);
        res = readBus(lc, MASTER, SH2_START_ROM + 0x200, Size.BYTE);
        Assertions.assertTrue(res != 0 && res != 0xFF);
        res = readBus(lc, SLAVE, SH2_START_ROM + 0x200, Size.BYTE);
        Assertions.assertTrue(res != 0 && res != 0xFF);

        setRv(lc, 1);
        //TODO should stall
        res = readBus(lc, MASTER, SH2_START_ROM + 0x200, Size.BYTE);
        Assertions.assertTrue(res != 0 && res != 0xFF);
        res = readBus(lc, SLAVE, SH2_START_ROM + 0x200, Size.BYTE);
        Assertions.assertTrue(res != 0 && res != 0xFF);
    }

    @Test
    public void testBadAppleSonicAccess() {
        int oneMbit = 0x100_000; //1Mbit
        byte[] rom = new byte[5 * oneMbit]; //5Mbit
        MarsRegTestUtil.fillAsMdRom(rom, true);
        lc = MarsRegTestUtil.createTestInstance(rom);
        lc.s32XMMREG.aden = 1;
        final Sh2Bus sh2Mem = lc.memory;
        final S32xBus mdBus = lc.bus;
        final int mdMapperAddress = SRAM_LOCK + 2; //0xA130F3

        int baseAddr = 0x400;

        Consumer<Integer> checkerBank1 = addr -> {
            int mdVal, sh2Val;
            int otherAddr = Ssf2Mapper.BANK_SIZE | addr; //bank1
            for (int i = 0; i < 10; i++) {
                mdVal = readRomToggleRv(M68K, sh2Mem, mdBus, addr + i);
                sh2Val = readRomToggleRv(MASTER, sh2Mem, mdBus, addr + i);
                Assertions.assertEquals(mdVal, sh2Val);

                mdVal = readRomToggleRv(M68K, sh2Mem, mdBus, otherAddr + i);
                sh2Val = readRomToggleRv(MASTER, sh2Mem, mdBus, otherAddr + i);
                Assertions.assertEquals(mdVal, sh2Val);
            }
        };
        //create Ssf2Mapper, set bank1=1 ie 0x80400 -> 0x80400
        mdBus.write(mdMapperAddress, 1, Size.BYTE); //write to bank#1 (bank#0 is fixed)
        checkerBank1.accept(baseAddr);

        //set bank1=0 ie 0x80400 -> 0x400
        //BadAppleSonic expects SH2 to read 0x208_0400 -> 0x80_400 -> via ssf2Mapper -> 0x400
        mdBus.write(mdMapperAddress, 0, Size.BYTE);
        checkerBank1.accept(baseAddr);

        mdBus.write(mdMapperAddress, 1, Size.BYTE);
        checkerBank1.accept(baseAddr);
    }


    @Test
    public void testRvOn1073Address() {
        Assume.assumeTrue(assertionsEnabled);
        int res;
        int[] addrList = {0x1070, 0x2070, 0x3070};
        assert addrList[2] + 8 < ROM_SIZE;
        for (Size size : Size.vals) {
            for (int addr : addrList) {
                setRv(lc, 0);
                res = readBus(lc, M68K, M68K_START_ROM_MIRROR + addr, size);
                //random values are guaranteed not be 0 or 0xFF
                Assertions.assertTrue(res != 0 && res != size.getMask(), th(res));

                res = readBus(lc, M68K, M68K_START_ROM_MIRROR_BANK + addr, size);
                Assertions.assertTrue(res != 0 && res != size.getMask());

                //RV=1, 1070 - 1073 cannot be read correctly, let's assume 0xFF
                setRv(lc, 1);
                res = readBus(lc, M68K, addr, size);
                Assertions.assertTrue(res == size.getMask());

                //106c, 1074 are fine
                res = readBus(lc, M68K, addr - 4, size);
                Assertions.assertTrue(res != 0 && res != size.getMask());
                res = readBus(lc, M68K, addr + 4, size);
                Assertions.assertTrue(res != 0 && res != size.getMask());
            }
        }

        //check 0x70 works ok
        int addr = 0x70;
        Size size = Size.WORD;
        setRv(lc, 0);
        //hint vector
        res = readBus(lc, M68K, addr, size);
        //RV=1, 70 - 73 should be ok
        setRv(lc, 1);
        int res2 = readBus(lc, M68K, addr, size);
        Assertions.assertFalse(res == size.getMask());
        Assertions.assertEquals(res, res2);
    }


    private int readRomToggleRv(CpuDeviceAccess cpu, Sh2Bus sh2Mem, S32xBus mdBus, int addr) {
        int val;
        if (cpu.regSide == BufferUtil.S32xRegSide.SH2) {
            val = sh2Mem.read(SH2_START_ROM | addr, Size.BYTE) & 0xFF;
        } else {
            val = mdBus.readRom(addr, Size.BYTE) & 0xFF;
        }
        return val;
    }
}
