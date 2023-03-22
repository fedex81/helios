package s32x.sh2.device;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.dict.Sh2Dict;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;

import static omegadrive.util.Util.th;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 **/
public class FreeRunningTimer implements S32xUtil.Sh2Device {

    private static final Logger LOG = LogHelper.getLogger(FreeRunningTimer.class.getSimpleName());

    /**
     * Looks like 32x sw is not using it as a timer.
     * Disable it due to perf impact.
     */
    public static final boolean SH2_ENABLE_FRT = Boolean.parseBoolean(System.getProperty("helios.32x.sh2.frt", "false"));

    public static final int FTCSR_CCLRA_BIT = 0;
    public static final int FTCSR_OVF_BIT = 1;
    public static final int FTCSR_OCFB_BIT = 2;
    public static final int FTCSR_OCFA_BIT = 3;
    public static final int FTCSR_CCLRA_MASK = 1 << FTCSR_CCLRA_BIT;
    public static final int FTCSR_OVF_MASK = 1 << FTCSR_OVF_BIT;
    public static final int FTCSR_OCFB_MASK = 1 << FTCSR_OCFB_BIT;
    public static final int FTCSR_OCFA_MASK = 1 << FTCSR_OCFA_BIT;

    public static final int TOCR_OCRS_BIT = 4;
    public static final int TOCR_OCRS_MASK = 1 << TOCR_OCRS_BIT;

    public static final int TIER_OVIE_BIT = 1;
    public static final int TIER_OCIBE_BIT = 2;
    public static final int TIER_OCIAE_BIT = 3;
    public static final int TIER_OVIE_MASK = 1 << TIER_OVIE_BIT;
    public static final int TIER_OCIAE_MASK = 1 << TIER_OCIAE_BIT;
    public static final int TIER_OCIBE_MASK = 1 << TIER_OCIBE_BIT;

    public static final int TOCR_DEFAULT = 0xE0;
    public static final int OCRAB_DEFAULT = 0xFFFF;

    private static final int[] clockDivs = {8, 32, 128, 1};

    private static final boolean verbose = false;

    private final ByteBuffer regs;
    private final S32xUtil.CpuDeviceAccess cpu;
    private final IntControl intControl;

    private int ocra, ocrb;
    private boolean isOcra;
    private int count = 0, clockDivider = 0;
    private int sh2TicksToNextFrtClock;

    public FreeRunningTimer(S32xUtil.CpuDeviceAccess cpu, IntControl intC, ByteBuffer regs) {
        this.cpu = cpu;
        this.regs = regs;
        this.intControl = intC;
        reset();
    }

    public int read(Sh2Dict.RegSpecSh2 regSpec, int address, Size size) {
        assert address == regSpec.addr : th(address) + ", " + th(regSpec.addr);
        if (verbose) LOG.info("{} FRT read {}: {}", cpu, regSpec.getName(), size);
        switch (regSpec) {
            case FRT_OCRAB_H -> {
                int ref = isOcra ? ocra : ocrb;
                return size == Size.WORD ? ref : ref >> 8;
            }
            case FRT_OCRAB_L -> {
                assert size == Size.BYTE;
                int refl = isOcra ? ocra : ocrb;
                return refl & 0xFF;
            }
        }
        return S32xUtil.readBuffer(regs, address, size);
    }

    public void write(Sh2Dict.RegSpecSh2 regSpec, int pos, int value, Size size) {
        assert pos == regSpec.addr : th(pos) + ", " + th(regSpec.addr);
        if (verbose) LOG.info("{} FRT write {}: {} {}", cpu, regSpec.getName(), th(value), size);
        S32xUtil.writeBufferRaw(regs, pos, value, size);
        switch (regSpec) {
            case FRT_TOCR -> {
                assert size == Size.BYTE;
                isOcra = (value & TOCR_OCRS_MASK) == 0;
                S32xUtil.writeBufferRaw(regs, pos, value | TOCR_DEFAULT, size);
            }
            case FRT_OCRAB_H, FRT_OCRAB_L -> {
                int val = S32xUtil.readBuffer(regs, FRT_OCRAB_H.addr, Size.WORD);
                if (isOcra) {
                    ocra = val;
                } else {
                    ocrb = val;
                }
            }
            case FRT_FRCH, FRT_FRCL -> count = S32xUtil.readBuffer(regs, FRT_FRCH.addr, Size.WORD);
            case FRT_TCR -> {
                assert size == Size.BYTE;
                clockDivider = clockDivs[value & 3];
                sh2TicksToNextFrtClock = clockDivider;
            }
            case FRT_TIER -> {
                assert size == Size.BYTE;
                //x000xxx1
                S32xUtil.writeBufferRaw(regs, pos, (value & 0x8e) | 1, size);
            }
            case FRT_FTCSR -> {
                assert size == Size.BYTE && value <= 1;
                LOG.info("write FTCSR: {} {}", th(value), size);
            }
        }
    }

    @Override
    public void step(int cycles) {
        if (SH2_ENABLE_FRT) {
            while (cycles-- > 0) {
                stepOne();
            }
        }
    }

    private void stepOne() {
        if (--sh2TicksToNextFrtClock == 0) {
            sh2TicksToNextFrtClock = clockDivider;
            int cnt = increaseCount();
            if (cnt == 0) { //overflow
                S32xUtil.setBit(regs, FRT_FTCSR.addr, FTCSR_OVF_BIT, 1, Size.BYTE);
                boolean ovie = (read(FRT_TIER, Size.BYTE) & TIER_OVIE_MASK) > 0;
                if (ovie) {
                    System.out.println("ovie");
//                    intControl.setExternalIntPending(FRT, 0, true);
                }
            }
            int ocra = read(FRT_OCRAB_H, Size.WORD);
            if (cnt == ocra) {
                S32xUtil.setBit(regs, FRT_FTCSR.addr, FTCSR_OCFA_BIT, 1, Size.BYTE);
                boolean ociae = (read(FRT_TIER, Size.BYTE) & TIER_OCIAE_MASK) > 0;
                if (ociae) {
                    System.out.println("ociae");
//                    intControl.setExternalIntPending(FRT, 0, true);
                }
                boolean cclra = (read(FRT_FTCSR, Size.BYTE) & FTCSR_CCLRA_MASK) > 0;
                if (cclra) {
//                    System.out.println("cclra");
                    write(FRT_FRCH, 0, Size.WORD);
                }
            }
            int ocrb = read(FRT_OCRAB_H, Size.WORD);
            if (cnt == ocrb) {
                S32xUtil.setBit(regs, FRT_FTCSR.addr, FTCSR_OCFB_BIT, 1, Size.BYTE);
                boolean ocibe = (read(FRT_TIER, Size.BYTE) & TIER_OCIBE_MASK) > 0;
                if (ocibe) {
                    System.out.println("ocibe");
//                    intControl.setExternalIntPending(FRT, 0, true);
                }
            }
        }
    }

    private int increaseCount() {
        count = (count + 1) & 0xFFFF;
        write(FRT_FRCH, count, Size.WORD);
        return count;
    }

    @Override
    public void reset() {
        write(FRT_TIER, 1, Size.BYTE);
        write(FRT_FTCSR, 0, Size.BYTE);
        write(FRT_FRCH, 0, Size.WORD);
        write(FRT_OCRAB_H, OCRAB_DEFAULT, Size.WORD);
        write(FRT_TCR, 0, Size.BYTE);
        write(FRT_TOCR, TOCR_DEFAULT, Size.BYTE);
        write(FRT_ICR_H, 0, Size.WORD);
        ocra = ocrb = OCRAB_DEFAULT;
    }
}
