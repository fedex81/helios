package s32x.sh2.device;

import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.Sh2MMREG;
import s32x.dict.Sh2Dict;

import java.nio.ByteBuffer;

import static omegadrive.util.Util.th;
import static s32x.dict.Sh2Dict.RegSpecSh2.WDT_WTCNT;
import static s32x.dict.Sh2Dict.RegSpecSh2.WDT_WTCSR;
import static s32x.sh2.device.IntControl.Sh2Interrupt.WDTS;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * Abbreviation R/W        Initial Value  Address Write[1]  Address Read[2]
 * WTCSR        R/(W)[3]         H'18        H'FFFFFE80          H'FFFFFE80
 * WTCNT         R/W             H'00         H'FFFFFE80          H'FFFFFE81
 * RSTCSR        R/(W)[3]        H'1F         H'FFFFFE82          H'FFFFFE83
 * <p>
 * Notes:
 * 1. Write by word access. It cannot be written by byte or longword access.
 * 2. Read by byte access. The correct value cannot be read by word or longword access.
 * 3. Only 0 can be written in bit 7 to clear the flag.
 */
public class WatchdogTimer implements BufferUtil.Sh2Device {

    private static final Logger LOG = LogHelper.getLogger(WatchdogTimer.class.getSimpleName());

    private static final int ADDR_WRITE_80 = 0xFE80 & Sh2MMREG.SH2_REG_MASK;
    private static final int ADDR_WRITE_82 = 0xFE82 & Sh2MMREG.SH2_REG_MASK;
    private static final int WTCSR_ADDR_READ = 0xFE80 & Sh2MMREG.SH2_REG_MASK;
    private static final int WTCNT_ADDR_READ = 0xFE81 & Sh2MMREG.SH2_REG_MASK;
    private static final int RSTCSR_ADDR_READ = 0xFE83 & Sh2MMREG.SH2_REG_MASK;
    private static final int WRITE_MSB_5A = 0x5A;
    private static final int WRITE_MSB_A5 = 0xA5;

    private static final int WOVF_BIT_POS = 7;
    private static final int OVF_BIT_POS = 7;
    private static final int RSTE_BIT_POS = 6;
    private static final int RSTS_BIT_POS = 5;
    private static final int TME_BIT_POS = 5; //timer enable
    private static final int WT_IT_BIT_POS = 6; //watchdog vs timer mode

    //SH2_CLK = 23.01 Mhz, clockDivs = 4096, WDT_CLK = 5.617676 Khz
    //overflowPeriod = 256*1/WDT_CLK = 45.57 ms
    private static final int[] clockDivs = {2, 64, 128, 256, 512, 1024, 4096, 8192};

    private static final boolean verbose = false;

    private final ByteBuffer regs;
    private final BufferUtil.CpuDeviceAccess cpu;
    private final IntControl intControl;
    private boolean wdtTimerEnable, timerMode = true;
    private int count = 0, clockDivider = 2;
    private int sh2TicksToNextWdtClock;

    public WatchdogTimer(BufferUtil.CpuDeviceAccess cpu, IntControl intC, ByteBuffer regs) {
        this.cpu = cpu;
        this.regs = regs;
        this.intControl = intC;
        reset();
    }

    public int read(Sh2Dict.RegSpecSh2 regSpec, int address, Size size) {
        if (size != Size.BYTE) {
            LOG.error("{} WDT read {}: {}", cpu, regSpec.getName(), size);
            throw new RuntimeException();
        }
        assert address == regSpec.addr : th(address) + ", " + th(regSpec.addr);
        if (address == WTCNT_ADDR_READ) {
            BufferUtil.writeBufferRaw(regs, WTCNT_ADDR_READ, count, Size.BYTE);
        }
        return BufferUtil.readBuffer(regs, address, size);
    }

    public void write(Sh2Dict.RegSpecSh2 regSpec, int pos, int value, Size size) {
        if (size != Size.WORD) {
            LOG.error("{} WDT write {}: {}", cpu, regSpec.getName(), size);
            throw new RuntimeException();
        }
        assert pos == regSpec.addr : th(pos) + ", " + th(regSpec.addr);
        switch (regSpec.addr) {
            case ADDR_WRITE_80 -> handleWrite80(value);
            case ADDR_WRITE_82 -> handleWrite82(value);
        }
    }

    private void handleWrite82(int value) {
        int msb = value >> 8;
        switch (msb) {
            case WRITE_MSB_5A:
                if ((value & 0xFF) == 0) {
                    BufferUtil.setBit(regs, RSTCSR_ADDR_READ, WOVF_BIT_POS, 0, Size.BYTE);
                } else {
                    LOG.error("{} WDT write, addr {}, unexpected LSB, should be zero: {}",
                            cpu, th(ADDR_WRITE_82), th(value & 0xFF));
                }
                break;
            case WRITE_MSB_A5:
                int rste = (value >> 6) & 1;
                int rsts = (value >> 5) & 1;
                BufferUtil.setBit(regs, RSTCSR_ADDR_READ, RSTE_BIT_POS, rste, Size.BYTE);
                BufferUtil.setBit(regs, RSTCSR_ADDR_READ, RSTS_BIT_POS, rsts, Size.BYTE);
                assert rste == 0;
                break;
            default:
                LOG.error("{} WDT write, addr {}, unexpected MSB: {}", cpu, th(ADDR_WRITE_82), th(msb));
                break;
        }
    }

    private void handleWrite80(int value) {
        int msb = value >> 8;
        switch (msb) {
            case WRITE_MSB_A5 -> {
                if (verbose) LOG.info("{} WDT write {}: {} {}", cpu, WDT_WTCSR.getName(),
                        th(value), Size.WORD);
                BufferUtil.writeBufferRaw(regs, WTCSR_ADDR_READ, value & 0xFF, Size.BYTE);
                handleTimerEnable(value);
            }
            case WRITE_MSB_5A -> {
                if (verbose) LOG.info("{} WDT write {}: {} {}", cpu, WDT_WTCNT.getName(),
                        th(value), Size.WORD);
                BufferUtil.writeBufferRaw(regs, WTCNT_ADDR_READ, value & 0xFF, Size.BYTE);
                count = value & 0xFF;
            }
            default -> LOG.error("{} WDT write, addr {}, unexpected MSB: {}", cpu, ADDR_WRITE_80, msb);
        }
    }

    private void handleTimerEnable(int value) {
        timerMode = ((value >> WT_IT_BIT_POS) & 1) == 0;
        wdtTimerEnable = ((value >> TME_BIT_POS) & 1) > 0;
        clockDivider = clockDivs[value & 7];
        sh2TicksToNextWdtClock = clockDivider;
        if (verbose) LOG.info("WDT timer mode: {}, timer enable: {}, clock div: {}, overflow: {}",
                timerMode, wdtTimerEnable, clockDivider, (value >> 7) & 1);
        if (!wdtTimerEnable) {
            BufferUtil.writeBufferRaw(regs, WTCNT_ADDR_READ, 0, Size.BYTE);
        }
        assert !(wdtTimerEnable && !timerMode);
    }

    @Override
    public void step(int cycles) {
        if (wdtTimerEnable) {
            while (cycles-- > 0) {
                stepOne();
            }
        }
    }

    private void stepOne() {
        assert sh2TicksToNextWdtClock > 0;
        if (--sh2TicksToNextWdtClock == 0) {
            sh2TicksToNextWdtClock = clockDivider;
            int cnt = increaseCount();
            if (cnt == 0) { //overflow
                BufferUtil.setBit(regs, WTCSR_ADDR_READ, OVF_BIT_POS, 1, Size.BYTE);
                intControl.setOnChipDeviceIntPending(WDTS);
            }
        }
    }

    private int increaseCount() {
        count = (count + 1) & 0xFF;
        return count;
    }

    @Override
    public void reset() {
        BufferUtil.writeBufferRaw(regs, WTCSR_ADDR_READ, 0x18, Size.BYTE);
        BufferUtil.writeBufferRaw(regs, WTCNT_ADDR_READ, 0, Size.BYTE);
        BufferUtil.writeBufferRaw(regs, RSTCSR_ADDR_READ, 0x1F, Size.BYTE);
        handleTimerEnable(0x18);
        count = 0;
    }
}
