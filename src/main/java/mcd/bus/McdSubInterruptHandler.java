package mcd.bus;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.Device;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.Util;
import org.slf4j.Logger;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 * <p>
 * TODO interrupt priority, more than one at the same time
 * TODO what happens to masked interrupts?
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

    void m68kInterrupt(int num);

    boolean checkInterruptEnabled(int m68kLevel);

    default boolean checkInterruptEnabled(SubCpuInterrupt intp) {
        return checkInterruptEnabled(intp.ordinal());
    }

    default boolean m68kInterruptWhenNotMasked(SubCpuInterrupt intp) {
        return m68kInterruptWhenNotMasked(intp.ordinal());
    }

    /**
     * @return true - non masked, false - masked
     */
    default boolean m68kInterruptWhenNotMasked(int m68kLevel) {
        if (checkInterruptEnabled(m68kLevel)) {
            m68kInterrupt(m68kLevel);
            return true;
        }
        return false;
    }

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

        private McdSubInterruptHandlerImpl(MegaCdMemoryContext c1, M68kProvider c) {
            this.subCpu = c;
            this.context = c1;
        }

        @Override
        public boolean checkInterruptEnabled(int m68kLevel) {
            int reg = Util.readBufferByte(context.commonGateRegsBuf, 0x33);
            return McdSubInterruptHandler.checkInterruptEnabled(reg, m68kLevel);
        }

        @Override
        public void m68kInterrupt(int num) {
            subCpu.raiseInterrupt(num);
            if (LOG_INTERRUPT_TRIGGER) {
                LOG.info("SubCpu interrupt trigger: {} ({})", intVals[num], num);
            }
        }
    }
}
