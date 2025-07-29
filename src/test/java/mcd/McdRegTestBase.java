package mcd;

import mcd.bus.MegaCdMainCpuBusIntf;
import mcd.bus.MegaCdSubCpuBusIntf;
import mcd.dict.MegaCdDict.RegSpecMcd;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.SystemLoader;
import omegadrive.bus.model.BaseBusProvider;
import omegadrive.cpu.m68k.MC68000Wrapper;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import static mcd.dict.MegaCdDict.START_MCD_SUB_GATE_ARRAY_REGS;
import static omegadrive.bus.model.MdMainBusProvider.MEGA_CD_EXP_START;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdRegTestBase {

    protected McdDeviceHelper.McdLaunchContext lc;
    MegaCdMemoryContext ctx;
    MegaCdMainCpuBusIntf mainCpuBus;
    MegaCdSubCpuBusIntf subCpuBus;

    MC68000Wrapper subCpu;
    @BeforeEach
    public void setupBase() {
        MdRuntimeData.releaseInstance();
        MdRuntimeData.newInstance(SystemLoader.SystemType.MEGACD, SystemProvider.NO_CLOCK);
        MdRuntimeData.setAccessTypeExt(M68K);
        lc = McdDeviceHelper.setupDevicesTest();
        ctx = lc.memoryContext;
        mainCpuBus = lc.mainBus;
        subCpuBus = lc.subBus;
        subCpu = lc.subCpu;
        Assertions.assertEquals(false, mainCpuBus.isEnableMode1(), "MCD boot mode should not be Mode1");
    }


    private BaseBusProvider getBus(CpuDeviceAccess cpu) {
        assert cpu == M68K || cpu == SUB_M68K;
        return cpu == M68K ? mainCpuBus : subCpuBus;
    }

    private int getMemMapAddress(CpuDeviceAccess cpu, int regAddr) {
        assert cpu == M68K || cpu == SUB_M68K;
        return (cpu == M68K ? MEGA_CD_EXP_START : START_MCD_SUB_GATE_ARRAY_REGS) + regAddr;
    }

    protected int readRegWord(CpuDeviceAccess cpu, RegSpecMcd regSpec) {
        MdRuntimeData.setAccessTypeExt(cpu);
        return getBus(cpu).read(getMemMapAddress(cpu, regSpec.addr), Size.WORD);
    }

    protected int readRegSize(CpuDeviceAccess cpu, RegSpecMcd regSpec, boolean even, Size size) {
        assert size != Size.LONG; //TODO check
        MdRuntimeData.setAccessTypeExt(cpu);
        return getBus(cpu).read(getMemMapAddress(cpu, regSpec.addr) + (even ? 0 : 1), size);
    }

    protected int readAddressSize(CpuDeviceAccess cpu, int addr, Size size) {
        MdRuntimeData.setAccessTypeExt(cpu);
        return getBus(cpu).read(addr, size);
    }

    protected void writeRegWord(CpuDeviceAccess cpu, RegSpecMcd regSpec, int value) {
        writeRegSize(cpu, regSpec, true, value, Size.WORD);
    }

    protected void writeRegSize(CpuDeviceAccess cpu, RegSpecMcd regSpec, boolean even, int value, Size size) {
        assert size != Size.LONG; //TODO check
        MdRuntimeData.setAccessTypeExt(cpu);
        getBus(cpu).write(getMemMapAddress(cpu, regSpec.addr) + (even ? 0 : 1), value, size);
    }

    protected void writeAddressSize(CpuDeviceAccess cpu, int addr, int value, Size size) {
        MdRuntimeData.setAccessTypeExt(cpu);
        getBus(cpu).write(addr, value, size);
    }

    protected void writeSubRegWord(RegSpecMcd regSpec, int value) {
        writeRegSize(SUB_M68K, regSpec, true, value, Size.WORD);
    }

    protected void writeMainRegWord(RegSpecMcd regSpec, int value) {
        writeRegSize(M68K, regSpec, true, value, Size.WORD);
    }

    protected void writeSubRegEvenByte(RegSpecMcd regSpec, int value) {
        writeRegSize(SUB_M68K, regSpec, true, value, Size.BYTE);
    }

    protected void writeSubRegOddByte(RegSpecMcd regSpec, int value) {
        writeRegSize(SUB_M68K, regSpec, false, value, Size.BYTE);
    }

    protected void writeMainRegEvenByte(RegSpecMcd regSpec, int value) {
        writeRegSize(M68K, regSpec, true, value, Size.BYTE);
    }

    protected void writeMainRegOddByte(RegSpecMcd regSpec, int value) {
        writeRegSize(M68K, regSpec, false, value, Size.BYTE);
    }

    protected int readSubRegWord(RegSpecMcd regSpec) {
        return readRegWord(SUB_M68K, regSpec);
    }

    protected int readMainRegWord(RegSpecMcd regSpec) {
        return readRegWord(M68K, regSpec);
    }

    protected int readSubRegEven(RegSpecMcd regSpec) {
        return readRegSize(SUB_M68K, regSpec, true, Size.BYTE);
    }

    protected int readSubRegOdd(RegSpecMcd regSpec) {
        return readRegSize(SUB_M68K, regSpec, false, Size.BYTE);
    }

    protected int readMainRegEven(RegSpecMcd regSpec) {
        return readRegSize(M68K, regSpec, true, Size.BYTE);
    }

    protected int readMainRegOdd(RegSpecMcd regSpec) {
        return readRegSize(M68K, regSpec, false, Size.BYTE);
    }

}
