package mcd.bus;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.Device;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_LEVEL2;
import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_SUBCODE;
import static mcd.dict.MegaCdDict.BitRegDef.IFL2;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_INT_MASK;
import static mcd.util.McdRegBitUtil.setBitDefInternal;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 * <p>
 */
public interface McdSubInterruptHandler extends Device {

    Logger LOG = LogHelper.getLogger(McdSubInterruptHandler.class.getSimpleName());

    boolean verbose = false;

    /**
     * INT_ASIC = LEVEL 1
     * ...
     * INT_SUBCODE = LEVEL 6;
     */
    enum SubCpuInterrupt {
        NONE, INT_ASIC, INT_LEVEL2, INT_TIMER, INT_CDD, INT_CDC, INT_SUBCODE
    }

    SubCpuInterrupt[] intVals = SubCpuInterrupt.values();

    void handleInterrupts();

    void raiseInterrupt(SubCpuInterrupt intp);

    void lowerInterrupt(SubCpuInterrupt intp);

    void setIFL2Asserted(boolean asserted);

    boolean isIFL2Asserted();

    static McdSubInterruptHandler create(MegaCdMemoryContext context, M68kProvider c) {
        return new McdSubInterruptHandlerImpl(context, c);
    }

    static boolean checkInterruptEnabled(int reg, int m68kLevel) {
        return (reg & (1 << (m68kLevel))) > 0;
    }

    static void printEnabledInterrupts(int reg33) {
        if (!verbose) {
            return;
        }
        StringBuilder sb = new StringBuilder("SubCpu interrupts non-masked: ");
        for (var i : intVals) {
            if (McdSubInterruptHandler.checkInterruptEnabled(reg33, i.ordinal())) {
                sb.append(i + " ");
            }
        }
        LOG.info(sb.toString());
    }

    class McdSubInterruptHandlerImpl implements McdSubInterruptHandler {
        private final M68kProvider subCpu;
        private final MegaCdMemoryContext context;

        private int pendingMask = 0;

        private boolean ifl2Asserted = false;

        private McdSubInterruptHandlerImpl(MegaCdMemoryContext c, M68kProvider subCpu) {
            this.subCpu = subCpu;
            this.context = c;
        }

        @Override
        public void raiseInterrupt(SubCpuInterrupt sint) {
            setPending(sint, 1);
        }

        @Override
        public void lowerInterrupt(SubCpuInterrupt intp) {
            setPending(intp, 0);
        }

        @Override
        public void setIFL2Asserted(boolean asserted) {
            ifl2Asserted = asserted;
        }

        @Override
        public boolean isIFL2Asserted() {
            return ifl2Asserted;
        }

        @Override
        public void handleInterrupts() {
            if (pendingMask == 0) {
                return;
            }
            final int mask = getRegMask();
            for (int i = INT_SUBCODE.ordinal(); i > 0; i--) {
                if (Util.getBitFromByte((byte) pendingMask, i) != 0) {
                    boolean canRaise = checkInterruptEnabled(mask, i);
                    //mcd-ver: if ifl2==0 INT#2 is not triggering
                    canRaise &= (i != INT_LEVEL2.ordinal() || ifl2Asserted);
                    if (canRaise && m68kInterrupt(i)) {
                        setPending(intVals[i], 0);
                        break;
                    }
                }
            }
        }

        /**
         * TODO genPlusGx for subcodeInt:
         * - check reg33 mask -> is Enabled?
         * - if yes set pending, otherwise missed
         */
        private void setPending(SubCpuInterrupt sint, int val) {
            assert (val & 1) == val;
//            boolean pending = ((1 << sint.ordinal()) & getRegMask()) > 0;
//            if(pending) {
            pendingMask = Util.setBit(pendingMask, sint.ordinal(), val);
//            }
        }

        private int getRegMask() {
            return Util.readBufferByte(context.commonGateRegsBuf, MCD_INT_MASK.addr + 1);
        }

        private boolean m68kInterrupt(int num) {
            assert num > 0;
            boolean raised = subCpu.raiseInterrupt(num);
            if (verbose && raised) {
                LOG.info("SubCpu interrupt trigger: {} ({})", intVals[num], num);
            }
            if (raised && intVals[num] == INT_LEVEL2) {
                setBitDefInternal(context, M68K, IFL2, 0);
                ifl2Asserted = false;
                if (verbose) LOG.info("SubCpu IFL2 set to 0");
            }
            return raised;
        }

        @Override
        public void reset() {
            pendingMask = 0;
            ifl2Asserted = false;
        }
    }
}
