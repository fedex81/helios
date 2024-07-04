package mcd.bus;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.Device;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;

import static mcd.dict.MegaCdDict.RegSpecMcd.MCD_INT_MASK;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 * <p>
 */
public interface McdSubInterruptHandler extends Device {

    Logger LOG = LogHelper.getLogger(McdSubInterruptHandler.class.getSimpleName());

    boolean LOG_INTERRUPT_TRIGGER = true;

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

        private McdSubInterruptHandlerImpl(MegaCdMemoryContext c1, M68kProvider c) {
            this.subCpu = c;
            this.context = c1;
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
        public void handleInterrupts() {
            if (pendingMask == 0) {
                return;
            }
            final int mask = getRegMask();
            for (int i = 1; i < pendingInterrupts.length; i++) {
                if (pendingInterrupts[i]) {
                    boolean canRaise = ((1 << i) & mask) > 0;
                    if (canRaise && m68kInterrupt(i)) {
                        setPending(intVals[i], 0);
                        //TODO check if necessary
//                        if (intVals[i] == SubCpuInterrupt.INT_LEVEL2) {
//                            BufferUtil.setBit(context.getGateSysRegs(M68K), MCD_RESET.addr, 0, 0, Size.BYTE);
//                        }
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
            if (LOG_INTERRUPT_TRIGGER && raised) {
                LOG.info("SubCpu interrupt trigger: {} ({})", intVals[num], num);
            }
            //if the cpu is masking it, interrupt lost
            return true;
        }
    }
}
