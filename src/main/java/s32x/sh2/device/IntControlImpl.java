package s32x.sh2.device;

import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.slf4j.Logger;
import s32x.dict.S32xDict;
import s32x.dict.Sh2Dict.RegSpecSh2;
import s32x.event.PollSysEventManager;
import s32x.sh2.drc.Sh2DrcBlockOptimizer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static omegadrive.util.LogHelper.logWarnOnce;
import static omegadrive.util.Util.th;
import static s32x.dict.Sh2Dict.RegSpecSh2.*;
import static s32x.sh2.device.Sh2DeviceHelper.Sh2DeviceType.*;
import static s32x.sh2.drc.Sh2DrcBlockOptimizer.NO_POLLER;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 * <p>
 */
public class IntControlImpl implements IntControl {

    private static final Logger LOG = LogHelper.getLogger(IntControlImpl.class.getSimpleName());

    private static final boolean verbose = false;

    private final Map<Sh2DeviceHelper.Sh2DeviceType, Integer> onChipDevicePriority;
    protected final Map<Sh2Interrupt, InterruptContext> s32xInt;

    protected InterruptContext[] orderedIntCtx;

    static final int VALID_BIT_POS = 0;
    static final int PENDING_BIT_POS = 1;
    static final int TRIGGER_BIT_POS = 2;

    static final int INT_VALID_MASK = 1 << VALID_BIT_POS;
    static final int INT_PENDING_MASK = 1 << PENDING_BIT_POS;
    static final int INT_TRIGGER_MASK = 1 << TRIGGER_BIT_POS;

    private InterruptContext currentInterrupt = LEV_0;

    // V, H, CMD and PWM each possesses exclusive address on the master side and the slave side.
    private final ByteBuffer sh2_int_mask;
    private final ByteBuffer regs;
    private final BufferUtil.CpuDeviceAccess cpu;

    public static IntControl createInstance(BufferUtil.CpuDeviceAccess cpu, ByteBuffer regs) {
        return new IntControlImpl(cpu, regs);
    }

    public IntControlImpl(BufferUtil.CpuDeviceAccess cpu, ByteBuffer regs) {
        sh2_int_mask = ByteBuffer.allocate(2);
        this.regs = regs;
        this.cpu = cpu;
        this.s32xInt = new HashMap<>(Sh2Interrupt.values().length);
        this.onChipDevicePriority = new HashMap<>();
        init();
    }

    @Override
    public void init() {
        s32xInt.clear();
        onChipDevicePriority.clear();
        Arrays.stream(Sh2DeviceHelper.Sh2DeviceType.values()).forEach(d -> onChipDevicePriority.put(d, 0));
        Arrays.stream(Sh2Interrupt.values()).forEach(s -> {
            InterruptContext intCtx = new InterruptContext();
            intCtx.source = s;
            intCtx.level = s.level;
            //OnChipDeviceInt are always valid
            setBit(intCtx, VALID_BIT_POS, 1);
            s32xInt.put(s, intCtx);
        });
        orderedIntCtx = Arrays.stream(Sh2Interrupt.vals).filter(si -> si.supported > 0).
                map(s32xInt::get).toArray(InterruptContext[]::new);
        setIntsMasked(0);
    }

    @Override
    public void write(RegSpecSh2 regSh2, int pos, int value, Size size) {
        boolean changed = regSh2.regSpec.write(regs, pos, value, size);
        if (!changed) {
            return;
        }
        int val = read(regSh2, Size.WORD);
        switch (regSh2) {
            case INTC_IPRA -> {
                onChipDevicePriority.put(DIV, (val >> 12) & 0xF);
                onChipDevicePriority.put(DMA, (val >> 8) & 0xF);
                onChipDevicePriority.put(WDT, (val >> 4) & 0xF);
                logExternalIntLevel(regSh2, val);
            }
            case INTC_IPRB -> {
                onChipDevicePriority.put(SCI, (val >> 12) & 0xF);
                onChipDevicePriority.put(FRT, (val >> 8) & 0xF);
                logExternalIntLevel(regSh2, val);
            }
            case INTC_ICR -> {
                if ((val & 1) > 0) {
                    LOG.error("{} Not supported: IRL Interrupt vector mode: External Vector", cpu);
                }
            }
            case INTC_VCRA, INTC_VCRB, INTC_VCRC, INTC_VCRD ->
                    LOG.info("{} uncommon: {}, val {} {}", cpu, regSh2.getName(), th(value), size);
        }
    }

    @Override
    public int read(RegSpecSh2 regSpec, int reg, Size size) {
        if (verbose)
            LOG.info("{} Read {} value: {} {}", cpu, regSpec.getName(), Util.th(BufferUtil.readBuffer(regs, reg, size)), size);
        return BufferUtil.readBuffer(regs, reg, size);
    }

    private InterruptContext getContextFromExternalInterrupt(Sh2Interrupt intp) {
        InterruptContext ctx = s32xInt.get(intp);
        assert ctx != null && ctx.source == intp && ctx.level == intp.level && intp.external > 0 : ctx;
        return ctx;
    }

    private void setIntMasked(int ipt, int isValid) {
        Sh2Interrupt sh2Interrupt = intVals[ipt];
        InterruptContext source = getContextFromExternalInterrupt(sh2Interrupt);
        boolean change = (source.intState & INT_VALID_MASK) != isValid;
        if (change) {
            setBit(source, VALID_BIT_POS, isValid);
            //TODO check
//            if (!isTrigger || ipt == CMD_8.ordinal()) {
            if (ipt == Sh2Interrupt.CMD_08.level) {
                setBit(source, TRIGGER_BIT_POS, (isValid > 0) && (source.intState & INT_PENDING_MASK) > 0);
            }
            resetInterruptLevel();
            logInfo("MASK", source);
        }
    }

    @Override
    public void setIntsMasked(int value) {
        for (int i = 0; i < 4; i++) {
            int imask = value & (1 << i);
            //0->PWM_6, 1->CMD_8, 2->HINT_10, 3->VINT_12
            int sh2Int = 6 + (i << 1);
            setIntMasked(sh2Int, imask > 0 ? 1 : 0);
        }
    }

    @Override
    public void reloadSh2IntMask() {
        int newVal = BufferUtil.readBuffer(sh2_int_mask, S32xDict.RegSpecS32x.SH2_INT_MASK.addr, Size.WORD);
        setIntsMasked(newVal & 0xF);
    }

    @Override
    public void setOnChipDeviceIntPending(Sh2Interrupt source) {
        assert source != null;
        InterruptContext intCtx = s32xInt.get(source);
        assert intCtx != null;
        intCtx.source = source;
        intCtx.level = onChipDevicePriority.get(source.deviceType);
        if (intCtx.level > 0) {
            setBit(intCtx, PENDING_BIT_POS, 1);
            setBit(intCtx, TRIGGER_BIT_POS, 1);
            if (verbose)
                LOG.info("{} {}{} interrupt pending: {}", cpu, source.deviceType, source.subType, intCtx.level);
            resetInterruptLevel();
        }
    }

    public int readSh2IntMaskReg(int pos, Size size) {
        return BufferUtil.readBuffer(sh2_int_mask, pos, size);
    }

    @Override
    public void setIntPending(Sh2Interrupt intpt, boolean isPending) {
        assert intpt.external > 0;
        InterruptContext source = getContextFromExternalInterrupt(intpt);
        boolean val = (source.intState & INT_PENDING_MASK) > 0;
        if (val != isPending) {
            boolean valid = (source.intState & INT_VALID_MASK) > 0;
            if (valid) {
                setBit(source, PENDING_BIT_POS, isPending);
                if (valid && isPending) {
                    setBit(source, TRIGGER_BIT_POS, 1);
                    source.level = intpt.level;
                    resetInterruptLevel();
                } else {
                    setBit(source, TRIGGER_BIT_POS, 0);
                }
                logInfo("PENDING", source);
            }
        }
    }

    private void checkMultiInterrupt() {
        int cnt = (int) Arrays.stream(orderedIntCtx).filter(c -> c.intState > INT_TRIGGER_MASK).count();
        if (cnt > 1) {
            String str =
                    Arrays.stream(orderedIntCtx).filter(c -> c.intState > INT_TRIGGER_MASK).
                            map(InterruptContext::toString).collect(Collectors.joining(","));
            logWarnOnce(LOG, "Multiple interrupts: " + str);
        }
    }
    private InterruptContext getCurrentInterrupt() {
        InterruptContext current = LEV_0;
        if (BufferUtil.assertionsEnabled) {
            checkMultiInterrupt();
        }
        //order is important, higher first
        //when onChipDevice ints have the same level, follow the hardcoded order
        int max = 0;
        for (int i = 0; i < orderedIntCtx.length; i++) {
            InterruptContext ctx = orderedIntCtx[i];
            if (ctx.intState > INT_TRIGGER_MASK && ctx.level > max) {
                current = ctx;
                max = ctx.level;
            }
        }
        return current;
    }

    private void resetInterruptLevel() {
        InterruptContext prev = currentInterrupt;
        InterruptContext current = getCurrentInterrupt();
        assert current != LEV_0 ? current.level > 0 : true;
        if (verbose && currentInterrupt != current && currentInterrupt.level > 0) {
            LOG.info("{} Level change: {} -> {}", cpu, currentInterrupt, current);
        }
        currentInterrupt = current;
        fireInterruptSysEventMaybe(prev);
    }

    private void fireInterruptSysEventMaybe(InterruptContext prev) {
        if (currentInterrupt.level != prev.level && currentInterrupt.level > 0) {
            Sh2DrcBlockOptimizer.PollerCtx ctx = PollSysEventManager.instance.getPoller(cpu);
            if (ctx != NO_POLLER && (ctx.isPollingActive() || ctx.isPollingBusyLoop())) {
                PollSysEventManager.instance.fireSysEvent(cpu, PollSysEventManager.SysEvent.INT);
            }
        }
    }

    @Override
    public void clearExternalInterrupt(Sh2Interrupt intType) {
        clearInterrupt(getContextFromExternalInterrupt(intType));
    }

    private void clearInterrupt(InterruptContext source) {
        source.intState &= ~(INT_PENDING_MASK | INT_TRIGGER_MASK);
        source.clearLevel();
        resetInterruptLevel();
        logInfo("CLEAR", source);
    }

    public void clearCurrentInterrupt() {
        //only autoclear onChip (ie.DMA,SCI, etc) interrupts? NO
        //36 Great Holes Starring Fred Couples (Prototype - Nov 05, 1994) (32X).32x
        //doesn't clear VINT=12
        clearInterrupt(currentInterrupt);
    }

    public InterruptContext getInterruptContext() {
        return currentInterrupt;
    }

    public int getVectorNumber() {
        if (currentInterrupt.source.external == 0) {
            return getOnChipDeviceVectorNumber(currentInterrupt);
        }
        return 64 + (currentInterrupt.level >> 1);
    }

    private int getOnChipDeviceVectorNumber(InterruptContext ctx) {
        int vn = -1;
        //TODO the vector number should be coming from the device itself
        switch (ctx.source.deviceType) {
            case DMA:
                //byte #3
                int offset = ctx.source.subType == OnChipSubType.DMA_C0 ? 0 : 1;
                vn = BufferUtil.readBuffer(regs, INTC_VCRDMA0.addr + (offset << 3), Size.LONG) & 0xFF;
                break;
            case WDT:
                //we only support Watchdog Timer (WDT) Interval Interrupt Vector Number (WITV)
                vn = BufferUtil.readBuffer(regs, INTC_VCRWDT.addr, Size.BYTE); //byte #0
                break;
            case DIV:
                vn = BufferUtil.readBuffer(regs, INTC_VCRDIV.addr, Size.LONG) & 0x7F; //byte #3
                break;
            case SCI:
                //TODO fix
                int pos = ctx.source.subType == OnChipSubType.RXI ? INTC_VCRA.addr + 1 : INTC_VCRB.addr;
                vn = BufferUtil.readBuffer(regs, pos, Size.BYTE);
                break;
            case FRT:
                vn = BufferUtil.readBuffer(regs, INTC_VCRD.addr, Size.BYTE) & 0x7F; //byte #0
                break;
            case NONE:
                break;
            default:
                LOG.error("{} Unhandled interrupt for device: {}, level: {}", cpu, ctx.source.deviceType, ctx.level);
                break;
        }
        if (verbose) LOG.info("{} {} interrupt exec: {}, vector: {}", cpu, ctx.source.deviceType, ctx.level, th(vn));
        return vn;
    }

    public ByteBuffer getSh2_int_mask_regs() {
        return sh2_int_mask;
    }

    private void logInfo(String action, InterruptContext source) {
        if (verbose) {
            LOG.info("{}: {} {} valid (unmasked): {}, pending: {}, willTrigger: {}, intLevel: {}",
                    action, cpu, source, (source.intState & INT_VALID_MASK) > 0,
                    (source.intState & INT_PENDING_MASK) > 0, (source.intState & INT_TRIGGER_MASK) > 0,
                    source.level);
        }
    }

    private void logExternalIntLevel(RegSpecSh2 regSpec, int val) {
        if (regSpec == INTC_IPRA) {
            LOG.info("{} set IPRA levels, {}:{}, {}:{}, {}:{}", cpu, DIV, val >> 12,
                    DMA, (val >> 8) & 0xF, WDT, (val >> 4) & 0xF);
        } else if (regSpec == INTC_IPRB) {
            LOG.info("{} set IPRB levels, {}:{}, {}:{}", cpu, SCI, val >> 12,
                    FRT, (val >> 8) & 0xF);
        }
    }

    private static void setBit(InterruptContext ctx, int bitPos, boolean bitValue) {
        setBit(ctx, bitPos, bitValue ? 1 : 0);
    }

    private static void setBit(InterruptContext ctx, int bitPos, int bitValue) {
        ctx.intState = Util.setBit(ctx.intState, bitPos, bitValue);
    }

    @Override
    public void reset() {
        BufferUtil.writeRegBuffer(INTC_IPRA, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_IPRB, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_VCRA, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_VCRB, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_VCRC, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_VCRD, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_VCRWDT, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_VCRDIV, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_VCRDMA0, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_VCRDMA1, regs, 0, Size.WORD);
        BufferUtil.writeRegBuffer(INTC_ICR, regs, 0, Size.WORD);
    }
}