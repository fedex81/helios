package s32x;

import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.dict.S32xDict.RegSpecS32x;
import s32x.util.MarsLauncherHelper;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.Z80;
import static s32x.MarsRegTestUtil.*;
import static s32x.dict.S32xDict.M68K_START_32X_SYSREG;
import static s32x.dict.S32xDict.M68K_START_ROM_MIRROR_BANK;
import static s32x.dict.S32xDict.RegSpecS32x.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 */
public class MdRegsTest {

    private static final int mdBankSetAddr = M68K_START_32X_SYSREG | MD_BANK_SET.addr;
    private static final int mdIntCtrlByte0 = M68K_START_32X_SYSREG | MD_INT_CTRL.addr;
    private static final int mdIntCtrlByte1 = M68K_START_32X_SYSREG | (MD_INT_CTRL.addr + 1);
    private MarsLauncherHelper.Sh2LaunchContext lc;

    @BeforeEach
    public void before() {
        lc = createTestInstance();
    }

    @Test
    public void testBankSetAdenOff() {
        setAdenMdSide(false);
        testBankSetInternal();
    }

    @Test
    public void testBankSetAdenOn() {
        setAdenMdSide(true);
        testBankSetInternal();
    }

    @Test
    public void testZ80SysRegs() {
        RegSpecS32x[] regSpecs = {RegSpecS32x.MD_ADAPTER_CTRL, MD_INT_CTRL, MD_BANK_SET, RegSpecS32x.MD_DMAC_CTRL};
        testZ80RegsInternal(regSpecs);
    }

    @Test
    public void testZ80PwmRegs() {
        RegSpecS32x[] regSpecs = {PWM_CTRL, PWM_CYCLE, PWM_RCH_PW, PWM_LCH_PW, PWM_MONO};
        testZ80RegsInternal(regSpecs);
    }

    @Test
    public void testZ80CommRegs() {
        RegSpecS32x[] regSpecs = {COMM0, COMM1, COMM2, COMM3, COMM4, COMM5, COMM6, COMM7};
        testZ80RegsInternal(regSpecs);
    }

    //Blackthorne sound relies on this
    @Test
    public void testMdIntCtrlMasking() {
        setAdenMdSide(true);
        testMdIntCtrlMasking(M68K);
        testMdIntCtrlMasking(Z80);
    }

    @Test
    public void testMasking() {
        testMasking(M68K);
        testMasking(Z80);
    }

    private void testMasking(CpuDeviceAccess cpu) {
        setAdenMdSide(true);

        RegSpecS32x[] regSpecs = {MD_ADAPTER_CTRL, MD_INT_CTRL, MD_BANK_SET, MD_DMAC_CTRL};
        //ignore aden
        int[] ignoreBitmask = {0xFFFE, 0xFFFF, 0xFFFF, 0xFFFF};

        for (int k = 0; k < regSpecs.length; k++) {
            int andMask = regSpecs[k].regSpec.writableBitMask;
            int orMask = regSpecs[k].regSpec.preserveBitMask;
            int ignoreMask = ignoreBitmask[k];
            int regAddr = M68K_START_32X_SYSREG | regSpecs[k].addr;
            System.out.println(regSpecs[k]);
            writeBus(lc, cpu, regAddr, 0, Size.WORD);
            for (int i = 0; i <= 0xFFFF; i++) {
//                System.out.println(i);
                int res = 0, exp = 0;
                //word
                if (cpu == M68K) {
                    exp = ((i & andMask) | orMask) & ignoreMask;
                    writeBus(lc, cpu, regAddr, i, Size.WORD);
                    res = readBus(lc, cpu, regAddr, Size.WORD) & ignoreMask;
                    if ((exp & ignoreMask) != (res & ignoreMask)) {
                        System.out.println("here");
                    }
                    Assertions.assertEquals(exp & ignoreMask, res & ignoreMask);
                }
                writeBus(lc, cpu, regAddr, 0, Size.WORD);
                if (i < 0x100) {
                    exp = (((i >> 8) & andMask) | orMask) & ignoreMask;
                    //byte #0
                    writeBus(lc, cpu, regAddr, 0, Size.BYTE);
                    writeBus(lc, cpu, regAddr, i >> 8, Size.BYTE);
                    res = readBus(lc, M68K, regAddr, Size.WORD) & ignoreMask;
                    Assertions.assertEquals(exp, res);

                    //byte #1
                    exp = (((i & 0xFF) & andMask) | orMask) & ignoreMask;
                    writeBus(lc, cpu, regAddr + 1, 0, Size.BYTE);
                    writeBus(lc, cpu, regAddr + 1, i & 0xFF, Size.BYTE);
                    res = readBus(lc, M68K, regAddr, Size.WORD) & ignoreMask;
                    Assertions.assertEquals(exp, res);
                }
            }
        }
    }

    private void testZ80RegsInternal(RegSpecS32x[] regSpecs) {
        setAdenMdSide(true);
        MdRuntimeData.setAccessTypeExt(Z80);

        for (RegSpecS32x regSpec : regSpecs) {
            int byte0Addr = M68K_START_32X_SYSREG | regSpec.addr;
            int byte1Addr = M68K_START_32X_SYSREG | (regSpec.addr + 1);
            int wordAddr = byte0Addr;

            checkBytesVsWord(regSpec);

            writeBus(lc, Z80, byte0Addr, 2, Size.BYTE);
            checkBytesVsWord(regSpec);

            writeBus(lc, Z80, byte1Addr, 0xF0, Size.BYTE);
            checkBytesVsWord(regSpec);

            writeBus(lc, M68K, wordAddr, 1, Size.WORD);
            checkBytesVsWord(regSpec);

            writeBus(lc, Z80, byte1Addr, 0, Size.BYTE);
            checkBytesVsWord(regSpec);

            switch (regSpec) {
                case MD_ADAPTER_CTRL:
                    break;
                case PWM_RCH_PW, PWM_LCH_PW, PWM_MONO:
                    emptyPwmFifoAndCheck(regSpec);
                    break;
                default:
                    int w = readBus(lc, M68K, wordAddr, Size.WORD);
                    Assertions.assertEquals(0, w, regSpec.toString());
                    break;
            }
        }
    }

    private void emptyPwmFifoAndCheck(RegSpecS32x regSpec) {
        final int emptyFifo = 1 << 14;
        //empty the fifo
        readBus(lc, M68K, regSpec.addr, Size.WORD);
        readBus(lc, M68K, regSpec.addr, Size.WORD);
        readBus(lc, M68K, regSpec.addr, Size.WORD);
        int w = readBus(lc, M68K, M68K_START_32X_SYSREG | regSpec.addr, Size.WORD);
        Assertions.assertEquals(emptyFifo, w & emptyFifo, regSpec.toString());
    }

    private void checkBytesVsWord(RegSpecS32x regSpec) {
        int w = readBus(lc, M68K, M68K_START_32X_SYSREG | regSpec.addr, Size.WORD);
        int b0 = readBus(lc, Z80, M68K_START_32X_SYSREG | regSpec.addr, Size.BYTE);
        int b1 = readBus(lc, Z80, M68K_START_32X_SYSREG | (regSpec.addr + 1), Size.BYTE);
        Assertions.assertEquals(w & 0xFF, b1, regSpec.toString());
        Assertions.assertEquals(w >> 8, b0, regSpec.toString());
    }

    //Golf game
    @Test
    public void testSramAccessViaBankedMirrors() {
        setAdenMdSide(true);
        int bankSet = readBus(lc, M68K, mdBankSetAddr, Size.WORD);
        Assertions.assertEquals(0, bankSet & 3);

        //write to ROM address 0, ignored
        int romWord = readBus(lc, M68K, M68K_START_ROM_MIRROR_BANK, Size.WORD);
        writeBus(lc, M68K, M68K_START_ROM_MIRROR_BANK, 0xAABB, Size.WORD);
        int res2 = readBus(lc, M68K, M68K_START_ROM_MIRROR_BANK, Size.WORD);
        Assertions.assertEquals(romWord, res2);

        //set bank 20 0000h – 2F FFFFh at 90 0000h
        int sramVal = 0xCC;
        writeBus(lc, M68K, mdBankSetAddr, 2, Size.WORD);
        writeBus(lc, M68K, M68K_START_ROM_MIRROR_BANK, sramVal, Size.WORD);
        int res = readBus(lc, M68K, M68K_START_ROM_MIRROR_BANK, Size.WORD);
        Assertions.assertEquals(sramVal, res);
        Assertions.assertNotEquals(sramVal, romWord);

        //reset bank to 0
        writeBus(lc, M68K, mdBankSetAddr, 0, Size.WORD);
        res = readBus(lc, M68K, M68K_START_ROM_MIRROR_BANK, Size.WORD);
        Assertions.assertEquals(romWord, res);
    }

    private void testBankSetInternal() {
        for (int i = 0; i < 0x1_0000; i += 257) {
            writeBus(lc, M68K, mdBankSetAddr, i, Size.WORD);
            int res = readBus(lc, M68K, mdBankSetAddr, Size.WORD);
            Assertions.assertEquals(i & 3, res & 3);
            Assertions.assertEquals(i & 3, lc.bus.getBankSetValue() & 3);

            //NOTE Zaxxon uses byte access
            //ignore byte write to 0x4
            writeBus(lc, M68K, mdBankSetAddr, 0, Size.WORD);
            writeBus(lc, M68K, mdBankSetAddr, i, Size.BYTE);
            res = readBus(lc, M68K, mdBankSetAddr, Size.WORD);
            Assertions.assertEquals(0, res & 3);
            Assertions.assertEquals(0, lc.bus.getBankSetValue() & 3);

            //handle byte write to 0x5
            writeBus(lc, M68K, mdBankSetAddr, 0, Size.WORD);
            writeBus(lc, M68K, mdBankSetAddr + 1, i, Size.BYTE);
            res = readBus(lc, M68K, mdBankSetAddr, Size.WORD);
            Assertions.assertEquals(i & 3, res & 3);
            Assertions.assertEquals(i & 3, lc.bus.getBankSetValue() & 3);
        }
    }

    private void testMdIntCtrlMasking(CpuDeviceAccess cpu) {
        setAdenMdSide(true);
        int regAddr = M68K_START_32X_SYSREG | MD_INT_CTRL.addr;
        int res;
        writeBus(lc, M68K, regAddr, 0, Size.WORD);
        if (cpu == M68K) {
            writeBus(lc, cpu, regAddr, 0, Size.WORD);
            res = readBus(lc, cpu, regAddr, Size.WORD);
            Assertions.assertEquals(0, res);
            writeBus(lc, cpu, regAddr, 0x200, Size.WORD);
            res = readBus(lc, cpu, regAddr, Size.WORD);
            Assertions.assertEquals(0, res);
        }

        writeBus(lc, cpu, regAddr, 0x2, Size.BYTE);
        res = readBus(lc, M68K, regAddr, Size.WORD);
        Assertions.assertEquals(0, res);
        res = readBus(lc, M68K, regAddr, Size.BYTE);
        Assertions.assertEquals(0, res);

        writeBus(lc, cpu, regAddr + 1, 0x3, Size.BYTE);
        res = readBus(lc, M68K, regAddr, Size.WORD);
    }


    private void setAdenMdSide(boolean enable) {
        int val = enable ? 1 : 0;
        int md0 = readBus(lc, M68K, MD_ADAPTER_CTRL_REG, Size.WORD);
        writeBus(lc, M68K, MD_ADAPTER_CTRL_REG, md0 | val, Size.WORD);
        checkAden(lc, val);
    }
}
