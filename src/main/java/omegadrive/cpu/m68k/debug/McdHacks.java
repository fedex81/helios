package omegadrive.cpu.m68k.debug;

import m68k.cpu.Cpu;
import omegadrive.cpu.CpuFastDebug;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

import static mcd.cdd.CdBiosHelper.logCdPcInfo;
import static omegadrive.util.BufferUtil.CpuDeviceAccess;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class McdHacks {

    private static final Logger LOG = LogHelper.getLogger(McdHacks.class.getSimpleName());

    public static void runMcdHacks(CpuFastDebug fastDebug, CpuDeviceAccess cpu, int currentPC, Cpu m68k) {
        if (cpu == CpuDeviceAccess.SUB_M68K) {
            logCdPcInfo(currentPC, m68k);
//            McdHacks.biosUs_Errors(cpu, currentPC);
        }
        mcdVerHacks(fastDebug, cpu, currentPC, m68k);
    }

    //BIOS_2.00W_US.smd
    public static void biosUs_Errors(CpuDeviceAccess cpu, int currentPC) {
        if (cpu == CpuDeviceAccess.M68K) {
            return;
        }
        if (currentPC == 0xf20 || currentPC == 0xf32) {
            LOG.warn("BIOS US error: {}, PC:{}", "Abort CDD transfers", th(currentPC));
            assert false;
        } else if (currentPC == 0x197a) {
            LOG.warn("BIOS US error: {}, PC:{}", "_cdctrn timeout", th(currentPC));
            assert false;
        }
//        else if (currentPC == 0xebe && prevPc != 0xf4c) {
//            LOG.warn("BIOS US error: {}, PC:{}", "cddCommand checksum error", th(currentPC));
//            assert false;
//        }
        //us bios, shows planet
//            if(cpu == CpuDeviceAccess.M68K && currentPC == 0x1f32){
//                currentPC += 2;
//                m68k.setPC(currentPC);
//                fastDebug.printDebugMaybe();
//            }
    }

    /**
     * NOTE mcd_ver needs a DATA cd inserted
     */
    public static void mcdVerHacks(CpuFastDebug fastDebug, CpuDeviceAccess cpu, int currentPC, Cpu m68k) {
        boolean match = false;
        boolean sentinel = false;
        //mcd-ver, cdcFlags skip error 0x22
        if (cpu == CpuDeviceAccess.M68K && currentPC == 0x14156 && m68k.getDataRegisterLong(1) == 0x22) {
            LOG.warn("{} skipping code at {} -> {}", cpu, th(currentPC), th(currentPC + 4));
            match = true;
            assert sentinel;
        }
        //mcd-ver, cdcFlags skip error 0x26
        else if (cpu == CpuDeviceAccess.M68K && currentPC == 0x142e6 && m68k.getAddrRegisterLong(0) == 0xA12000) {
            LOG.warn("{} skipping code at {} -> {}", cpu, th(currentPC), th(currentPC + 4));
            match = true;
            m68k.setDataRegisterLong(7, 0);
            assert sentinel;
        }
        //mcd-ver, cdcFlags skip error 0x34, cdcFlags 0x35 has error
        else if (cpu == CpuDeviceAccess.M68K && currentPC == 0x144b2 && m68k.getAddrRegisterLong(1) == 0xA12000) {
            LOG.warn("{} skipping code at {} -> {}", cpu, th(currentPC), th(currentPC + 4));
            match = true;
            assert sentinel;
            //mcd-ver CDC DMA2 error 0x12
        } else if (cpu == CpuDeviceAccess.M68K && currentPC == 0x000126ac && m68k.getDataRegisterLong(1) != 9) {
            LOG.warn("{} skipping code at {} -> {}", cpu, th(currentPC), th(currentPC + 4));
            m68k.setDataRegisterLong(1, 9);
            assert sentinel;
            //mcd-ver CDC DMA3 error 4
        }
//        else if (cpu == CpuDeviceAccess.M68K && currentPC == 0x00012f04 && m68k.getDataRegisterLong(1) != 9) {
//            LOG.warn("{} skipping code at {} -> {}", cpu, th(currentPC), th(currentPC + 12));
//            currentPC += 8;
//            match = true;
//            assert sentinel;
//        }
        if (match) {
            currentPC += 4;
            m68k.setPC(currentPC);
            fastDebug.printDebugMaybe();
            assert sentinel;
        }
    }
}
