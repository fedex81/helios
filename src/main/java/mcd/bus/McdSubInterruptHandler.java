package mcd.bus;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.Device;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.util.Arrays;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_LEVEL2;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_INT_MASK;
import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_RESET;
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
        NONE, INT_ASIC, INT_LEVEL2, INT_TIMER, INT_CDD, INT_CDC, INT_SUBCODE;
    }

    SubCpuInterrupt[] intVals = SubCpuInterrupt.values();

    void handleInterrupts();

    void raiseInterrupt(SubCpuInterrupt intp);

    void lowerInterrupt(SubCpuInterrupt intp);

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
        private M68kProvider subCpu;
        private MegaCdMemoryContext context;

        private boolean[] pendingInterrupts = new boolean[intVals.length];

        private int pendingMask = 0;

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

        private int getIFL2() {
            return Util.readBufferByte(context.getGateSysRegs(M68K), MCD_RESET.addr) & 1;
        }

        @Override
        public void handleInterrupts() {
            if (pendingMask == 0) {
                return;
            }
            final int mask = getRegMask();
            final int ifl2 = getIFL2();
            for (int i = 1; i < pendingInterrupts.length; i++) {
                if (pendingInterrupts[i]) {
                    boolean canRaise = ((1 << i) & mask) > 0;
                    //mcd-ver: if ifl2==0 INT#2 is not triggering
                    canRaise &= (i == INT_LEVEL2.ordinal() && ifl2 == 0) ? false : true;
                    if (canRaise && m68kInterrupt(i)) {
                        setPending(intVals[i], 0);
                        break;
                    }
                }
            }
        }

        private void setPending(SubCpuInterrupt sint, int val) {
            assert (val & 1) == val;
            int pending = (val << sint.ordinal());
            //clear bit, then set it
            pendingMask &= ~(1 << sint.ordinal());
            pendingMask |= pending;
            pendingInterrupts[sint.ordinal()] = pending > 0;
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
            //if the cpu is masking it, interrupt lost
            return true;
        }

        @Override
        public void reset() {
            pendingMask = 0;
            Arrays.fill(pendingInterrupts, false);
        }
    }
}
