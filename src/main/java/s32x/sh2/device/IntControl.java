package s32x.sh2.device;

import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;
import s32x.sh2.device.Sh2DeviceHelper.Sh2DeviceType;
import s32x.util.S32xUtil;

import java.nio.ByteBuffer;

import static s32x.sh2.device.IntControl.OnChipSubType.*;
import static s32x.sh2.device.IntControl.Sh2Interrupt.*;
import static s32x.sh2.device.Sh2DeviceHelper.Sh2DeviceType.*;

/**
 * Federico Berti
 * <p>
 * Copyright 2021
 * <p>
 */
public interface IntControl extends S32xUtil.Sh2Device {

    Logger LOG = LogHelper.getLogger(IntControl.class.getSimpleName());

    enum OnChipSubType {
        S_NONE, RIE, TIE, DMA_C0, DMA_C1, D_OVFI, ERI, RXI, TXI, TEI, ICI, OCI, OVI;
    }

    enum Sh2InterruptSource {
        NMI, USER_BREAK, IRL15, VRES14(VRES_14), IRL13, VINT12(VINT_12), IRL11, HINT10(HINT_10), IRL09,
        CMD08(CMD_8), IRL07, PWM06(PWM_6), IRL05, IRL04,
        IRL03, IRL02, IRL01, DIVU(DIV), DMAC0(DMA, DMA_C0), DMAC1(DMA, DMA_C1), WDTS(WDT),
        REF(BSC), SCIE(SCI, ERI), SCIR(SCI, RXI), SCIT(SCI, TXI),
        SCITE(SCI, TEI), FRTI(FRT, ICI), FRTO(FRT, OCI), FRTOV(FRT, OVI);

        public final Sh2DeviceType deviceType;
        public final OnChipSubType subType;
        public final Sh2Interrupt externalInterrupt;

        public static final Sh2InterruptSource[] vals = Sh2InterruptSource.values();

        Sh2InterruptSource(Sh2Interrupt externalInterrupt) {
            this(NONE, S_NONE, externalInterrupt);
        }

        Sh2InterruptSource() {
            this(NONE, S_NONE, NONE_0);
        }

        Sh2InterruptSource(Sh2DeviceType deviceType) {
            this(deviceType, S_NONE, NONE_0);
        }

        Sh2InterruptSource(Sh2DeviceType t, OnChipSubType s) {
            this(t, s, NONE_0);
        }

        Sh2InterruptSource(Sh2DeviceType t, OnChipSubType s, Sh2Interrupt externalInterrupt) {
            this.deviceType = t;
            this.subType = s;
            this.externalInterrupt = externalInterrupt;
        }

        public static Sh2InterruptSource getSh2InterruptSource(Sh2DeviceType deviceType, OnChipSubType subType) {
            for (var s : vals) {
                if (s.deviceType == deviceType && s.subType == subType) {
                    return s;
                }
            }
            LOG.error("Unknown interrupt source: {}, {}", deviceType, subType);
            return null;
        }
    }

    enum Sh2Interrupt {
        NONE_0(0), NONE_1(0), NONE_2(0), NONE_3(0), NONE_4(0), NONE_5(0),
        PWM_6(1), NONE_7(1), CMD_8(1), NONE_9(0), HINT_10(1), NONE_11(0), VINT_12(1),
        NONE_13(0), VRES_14(1), NONE_15(0), NMI_16(1);

        public final int internal;

        Sh2Interrupt(int i) {
            this.internal = i;
        }
    }

    class InterruptContext {
        public Sh2InterruptSource source;
        public Sh2Interrupt interrupt;
        public int level = 0;
        public int intState = 0;

        @Override
        public String toString() {
            return "IntCtx{" +
                    "source=" + source +
                    ", interrupt=" + interrupt +
                    ", level=" + level +
                    ", intState=" + intState +
                    '}';
        }
    }


    Sh2Interrupt[] intVals = Sh2Interrupt.values();
    InterruptContext LEV_0 = new InterruptContext();

    int MAX_LEVEL = 17; //[0-16]

    default void setOnChipDeviceIntPending(Sh2DeviceType deviceType) {
        setOnChipDeviceIntPending(deviceType, S_NONE);
    }

    void setOnChipDeviceIntPending(Sh2DeviceType deviceType, OnChipSubType subType);

    void setIntPending(Sh2Interrupt interrupt, boolean isPending);

    int readSh2IntMaskReg(int pos, Size size);

    void reloadSh2IntMask();

    ByteBuffer getSh2_int_mask_regs();

    void setIntsMasked(int value);

    void clearInterrupt(Sh2Interrupt intType);

    void clearCurrentInterrupt();

    int getVectorNumber();

    InterruptContext getInterruptContext();

    default int getInterruptLevel() {
        return getInterruptContext().level;
    }
}