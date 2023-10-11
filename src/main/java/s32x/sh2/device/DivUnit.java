package s32x.sh2.device;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;

import static omegadrive.util.Util.readBufferWord;
import static omegadrive.util.Util.th;
import static s32x.dict.Sh2Dict.RegSpecSh2;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;
import static s32x.sh2.device.IntControl.Sh2Interrupt.DIVU;
import static s32x.util.S32xUtil.readBufferRegLong;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 * <p>
 * TODO more accurate timings -> check branch perf_test
 */
public class DivUnit implements S32xUtil.Sh2Device {

    private static final Logger LOG = LogHelper.getLogger(DivUnit.class.getSimpleName());

    private static final int DIV_OVERFLOW_BIT = 0;
    private static final int DIV_OVERFLOW_INT_EN_BIT = 1;

    private static final int DIV_CYCLES = 39;
    private static final int DIV_OVF_CYCLES = 6;
    private static final String formatDiv = "%s div%d, dvd: %16X, dvsr: %08X, quotLong: %16X, quot32: %08x, rem: %08X";
    private static final String formatOvf = "%s div%d overflow, dvd: %16X, dvsr: %08X, quotLong: %16X, quot32: %08x, rem: %08X";
    private static final String formatDivBy0 = "%s div%d overflow (div by 0), dvd: %16X, dvsr: %08X";
    private static final boolean verbose = false;

    private final S32xUtil.CpuDeviceAccess cpu;
    private final ByteBuffer regs;
    private final IntControl intControl;

    public DivUnit(S32xUtil.CpuDeviceAccess cpu, IntControl intControl, ByteBuffer regs) {
        this.cpu = cpu;
        this.regs = regs;
        this.intControl = intControl;
    }

    @Override
    public void write(RegSpecSh2 reg, int pos, int value, Size size) {
        assert size == Size.LONG;
        reg.regSpec.write(regs, value, Size.LONG);
        if (verbose) LOG.info("{} Write {} value: {} {}", cpu, reg.getName(), th(value), size);
        switch (reg) {
            case DIV_DVDNTL -> div64Dsp();
            case DIV_DVDNT -> div32Dsp(value);
        }
    }

    @Override
    public int read(RegSpecSh2 regSpec, int reg, Size size) {
        if (verbose)
            LOG.info("{} Read {} value: {} {}", cpu, regSpec.getName(), th(S32xUtil.readBuffer(regs, reg, size)), size);
        return S32xUtil.readBuffer(regs, reg, size);
    }

    //64/32 -> 32 only
    @SuppressWarnings("NumericCastThatLosesPrecision")
    private void div64Dsp() {
        long dh = readBufferRegLong(regs, DIV_DVDNTH);
        long dl = readBufferRegLong(regs, DIV_DVDNTL);
        long dvd = ((dh << 32)) | (dl & 0xffffffffL);
        int dvsr = readBufferRegLong(regs, DIV_DVSR);
        if (dvsr == 0) {
            handleOverflow(0, true, String.format(formatDivBy0, cpu, 64, dvd, dvsr));
            return;
        }
        long quotL = dvd / dvsr;
        int quot = (int) quotL;
        int rem = (int) (dvd - quot * dvsr);
        S32xUtil.writeBuffersLong(regs, DIV_DVDNTH, DIV_DVDNTUH, rem);
        if (verbose) LOG.info(String.format(formatDiv, cpu, 64, dvd, dvsr, quotL, quot, rem));
        if (quot != quotL) {
            handleOverflow(quotL, false, String.format(formatOvf, cpu, 64, dvd, dvsr, quotL, quot, rem));
            return;
        }
        S32xUtil.writeBuffersLong(regs, DIV_DVDNT, DIV_DVDNTL, DIV_DVDNTUL, quot);
    }

    //32/32 -> 32
    private void div32Dsp(int value) {
        S32xUtil.writeBufferLong(regs, DIV_DVDNTH, value >> 31); //sign extend MSB into DVDNTH
        S32xUtil.writeBufferLong(regs, DIV_DVDNTL, value);
        int dvd = readBufferRegLong(regs, DIV_DVDNT);
        int dvsr = readBufferRegLong(regs, DIV_DVSR);
        if (dvsr == 0) {
            handleOverflow(0, true, String.format(formatDivBy0, cpu, 32, dvd, dvsr));
            return;
        }
        int quot = dvd / dvsr;
        int rem = (dvd - quot * dvsr);
        if (verbose) LOG.info(String.format(formatDiv, cpu, 32, dvd, dvsr, quot, quot, rem));
        S32xUtil.writeBuffersLong(regs, DIV_DVDNTH, DIV_DVDNTUH, rem);
        S32xUtil.writeBuffersLong(regs, DIV_DVDNT, DIV_DVDNTL, DIV_DVDNTUL, quot);
    }

    private void handleOverflow(long quot, boolean divBy0, String msg) {
        if (verbose) LOG.info(msg);
        S32xUtil.setBit(regs, DIV_DVCR.addr, DIV_OVERFLOW_BIT, 1, Size.LONG);
        int dvcr = readBufferWord(regs, DIV_DVCR.addr);
        int val = quot >= 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        S32xUtil.writeBuffersLong(regs, DIV_DVDNT, DIV_DVDNTL, DIV_DVDNTUL, val);
        if ((dvcr & DIV_OVERFLOW_INT_EN_BIT) > 0) {
            intControl.setOnChipDeviceIntPending(DIVU);
            LOG.info(msg);
            LOG.warn("{} DivUnit interrupt", cpu); //not used by any sw?
        }
    }

    @Override
    public void reset() {
        S32xUtil.writeBufferLong(regs, DIV_DVCR, 0);
    }
}
