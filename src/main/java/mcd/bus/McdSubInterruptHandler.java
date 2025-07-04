package mcd.bus;

import mcd.dict.MegaCdMemoryContext;
import omegadrive.Device;
import omegadrive.cpu.m68k.M68kProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector.Region;
import omegadrive.util.Util;
import org.slf4j.Logger;

import java.util.Arrays;

import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_ASIC;
import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_LEVEL2;
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

    @Deprecated
    void setRegion(Region region);

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

        private Region region = Region.USA;

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
        public void setRegion(Region region) {
            if (this.region != region) {
                LOG.info("Interrupt hack for region: {}", region);
                this.region = region;
            }
        }

        @Override
        public void handleInterrupts() {
            if (pendingMask == 0) {
                return;
            }
            final int mask = getRegMask();
            final int ifl2 = MegaCdMainCpuBus.ifl2Trigger;
            for (int i = 1; i < pendingInterrupts.length; i++) {
//            for (int i = pendingInterrupts.length - 1; i > 0; i--) {
                if (pendingInterrupts[i]) {
                    boolean canRaise = ((1 << i) & mask) > 0;
                    //mcd-ver: if ifl2==0 INT#2 is not triggering
                    canRaise &= (i == INT_LEVEL2.ordinal() && ifl2 == 0) ? false : true;
                    if (canRaise && m68kInterrupt(i)) {
                        setPending(intVals[i], 0);
                        if (intVals[i] == INT_LEVEL2) {
                            setBitDefInternal(context, M68K, IFL2, 0);
                        }
                        break;
                    }
                    //ASIC interrupt cannot be made pending and triggered later
                    if (i == INT_ASIC.ordinal()) {
                        setPending(INT_ASIC, 0);
                    }
                }
            }
        }
        private void setPending(SubCpuInterrupt sint, int val) {
            assert (val & 1) == val;
            pendingMask = Util.setBit(pendingMask, sint.ordinal(), val);
            pendingInterrupts[sint.ordinal()] = val > 0;
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
            //TODO HACK
            //if the cpu is masking it, interrupt lost
            //OK -> EU-bios 1.00 (f891e0ea651e2232af0c5c4cb46a0cae2ee8f356)
            //OK -> US-bios 1.00 (c5c24e6439a148b7f4c7ea269d09b7a23fe25075)
            //OK -> JP-bios 1.00H (aka 100s) (230ebfc49dc9e15422089474bcc9fa040f2c57eb)
            //for JP press start and then select CD-ROM
            LogHelper.logWarnOnce(LOG, "MegaCd interrupt hack active!!");
            return region == Region.EUROPE ? true : raised;
        }

        @Override
        public void reset() {
            pendingMask = 0;
            Arrays.fill(pendingInterrupts, false);
        }
    }
}
