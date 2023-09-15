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
        S_NONE,
        DMA_C0, //DMA Channel#0 interrupt requests
        DMA_C1, //DMA Channel#1 interrupt requests
        ERI,  //SCI receive-error interrupt requests
        RXI,  //SCI Receive-data-full interrupt requests
        TXI, //SCI transmit-data-empty interrupt request
        TEI, //SCI transmit-end interrupt request
        ICI, //FRT
        OCI, //FRT
        OVI; //FRT Timer Overflow Flag interrupt
    }

    //NOTE ignores
    //NMI, USER_BREAK, IRL{N}
    enum Sh2Interrupt {
        NONE_0(0, 0), NMI_16(16, 0), NONE_15(15, 0), NONE_4(4, 0),
        VRES_14(14), VINT_12(12), HINT_10(10), CMD_08(8), PWM_06(6),
        DIVU(DIV), DMAC0(DMA, DMA_C0), DMAC1(DMA, DMA_C1), WDTS(WDT),
        REF(BSC), SCIE(SCI, ERI), SCIR(SCI, RXI), SCIT(SCI, TXI),
        SCITE(SCI, TEI), FRTI(FRT, ICI), FRTO(FRT, OCI), FRTOV(FRT, OVI),
        ;

        public final Sh2DeviceType deviceType;
        public final OnChipSubType subType;
        public final int external;
        public final int level;
        public final int supported;

        public static final Sh2Interrupt[] vals = Sh2Interrupt.values();

        Sh2Interrupt(int level) {
            this(NONE, S_NONE, 1, level, 1);
        }

        Sh2Interrupt(int level, int supported) {
            this(NONE, S_NONE, 1, level, supported);
        }

        Sh2Interrupt(Sh2DeviceType deviceType) {
            this(deviceType, S_NONE, 0, 0, 1);
        }

        Sh2Interrupt(Sh2DeviceType t, OnChipSubType s) {
            this(t, s, 0, 0, 1);
        }

        Sh2Interrupt(Sh2DeviceType t, OnChipSubType s, int external, int level, int supported) {
            this.deviceType = t;
            this.subType = s;
            this.external = external;
            this.level = level;
            this.supported = supported;
        }
    }

    class InterruptContext {
        public Sh2Interrupt source;
        public int level = 0;
        /**
         * bit #0: valid
         * bit #1: pending
         * bit #2: trigger
         */
        public int intState = 0;

        @Override
        public String toString() {
            return "IntCtx{" +
                    "source=" + source +
                    ", level=" + level +
                    ", intState=" + intState +
                    '}';
        }

        public void clearLevel() {
            if (source.external == 0) {
                level = 0;
            }
        }
    }

    Sh2Interrupt[] intVals = {NONE_0, NONE_0, NONE_0, NONE_0, NONE_0, NONE_0, PWM_06, NONE_0, CMD_08, NONE_0,
            HINT_10, NONE_0, VINT_12, NONE_0, VRES_14, NONE_0, NONE_0};
    InterruptContext LEV_0 = new InterruptContext();

    void setOnChipDeviceIntPending(Sh2Interrupt onChipInt);

    void setIntPending(Sh2Interrupt interrupt, boolean isPending);

    int readSh2IntMaskReg(int pos, Size size);

    void reloadSh2IntMask();

    ByteBuffer getSh2_int_mask_regs();

    void setIntsMasked(int value);

    void clearExternalInterrupt(Sh2Interrupt intType);

    void clearCurrentInterrupt();

    int getVectorNumber();

    InterruptContext getInterruptContext();

    default int getInterruptLevel() {
        return getInterruptContext().level;
    }
}