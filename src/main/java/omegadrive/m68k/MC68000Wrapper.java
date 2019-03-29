package omegadrive.m68k;

import m68k.cpu.MC68000;
import m68k.cpu.instructions.TAS;
import m68k.memory.AddressSpace;
import omegadrive.Genesis;
import omegadrive.bus.gen.GenesisBusProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 *
 * NOTES: f-line emulator. Tecmo Cup
 *        a-line emulator. Mamono Hunter Yohko
 *        reset. Bug Hunt (homebrew)
 *        stop. Hardball III
 *        add z-flag. sgdk-partic.bin
 */
public class MC68000Wrapper implements M68kProvider {

    private static Logger LOG = LogManager.getLogger(MC68000Wrapper.class.getSimpleName());

    public static boolean verbose = Genesis.verbose || false;
    public static boolean STOP_ON_EXCEPTION;
    public static boolean GENESIS_TAS_BROKEN;

    private static int ILLEGAL_ACCESS_EXCEPTION = 4;

    static {
        STOP_ON_EXCEPTION =
                Boolean.valueOf(System.getProperty("68k.stop.on.exception", "false"));
        GENESIS_TAS_BROKEN = Boolean.valueOf(System.getProperty("68k.broken.tas", "true"));
        if (GENESIS_TAS_BROKEN != TAS.EMULATE_BROKEN_TAS) {
            LOG.info("Overriding 68k TAS broken setting: " + GENESIS_TAS_BROKEN);
        }
    }

    private MC68000 m68k;
    private AddressSpace addressSpace;
    private GenesisBusProvider busProvider;
    private boolean stop;
    protected int currentPC;

    public MC68000Wrapper(GenesisBusProvider busProvider) {
        this.m68k = createCpu();
        this.busProvider = busProvider;
        this.addressSpace = MC68000AddressSpace.createInstance(busProvider);
        m68k.setAddressSpace(addressSpace);
        TAS.EMULATE_BROKEN_TAS = GENESIS_TAS_BROKEN;
    }

    @Override
    public int runInstruction() {
        int res = 0;
        try {
            printVerbose();
            printCpuState("");
            currentPC = m68k.getPC();
            res = m68k.execute();
        } catch (Exception e) {
            LOG.error("68k error", e);
            printVerbose();
            handleException(ILLEGAL_ACCESS_EXCEPTION);
        }
        return res;
    }

    @Override
    public long getPC() {
        return m68k.getPC();
    }


    private void setStop(boolean value) {
        LOG.debug("M68K stop: {}", value);
        this.stop = value;
    }

    @Override
    public boolean isStopped() {
        return stop;
    }

    @Override
    public boolean raiseInterrupt(int level) {
        m68k.raiseInterrupt(level);
        boolean raise = m68k.getInterruptLevel() == level;
        if (raise) {
//            LOG.info("M68K before INT, level: {}, newLevel: {}", m68k.getInterruptLevel(),  level);
        }
        return raise;
    }

    @Override
    public void reset() {
        m68k.reset();
    }

    public MC68000 getM68k() {
        return m68k;
    }

    private MC68000 createCpu() {
        return new MC68000() {
            @Override
            public void raiseException(int vector) {
                handleException(vector);
                super.raiseException(vector);
                handleException(vector);
                setStop(false);
            }

            @Override
            public void resetExternal() {
                LOG.info("Reset External");
                busProvider.resetFrom68k();
            }

            @Override
            public void reset() {
                LOG.info("Reset");
                super.reset();
                resetExternal(); //TODO why?
            }
        };
    }

    private void printVerbose() {
        if (!verbose) {
            return;
        }
        try {
            String res = MC68000Helper.dumpOp(m68k);
            LOG.info(res);
            if (MC68000Helper.addToInstructionSet(m68k)) {
                LOG.info(MC68000Helper.dumpInstructionSet());
            }
        } catch (Exception e) {
            String pc = Long.toHexString(m68k.getPC() & 0xFF_FFFF);
            LOG.warn("Unable to dump the instruction: " + pc, e);
        }
    }

    private void handleException(int vector) {
        if (vector == ILLEGAL_ACCESS_EXCEPTION) {
            printCpuState("Exception: " + vector);
            if (STOP_ON_EXCEPTION) {
                setStop(true);
            }
        }
    }

    private void printCpuState(String head) {
        if (!verbose) {
            return;
        }
        try {
            String str = MC68000Helper.dumpInfo(m68k, true, addressSpace.size());
            LOG.info(head + str);
        } catch (Exception e) {
            String pc = Long.toHexString(m68k.getPC() & 0xFF_FFFF);
            LOG.warn("Unable to dump the state: " + pc, e);
        }
    }
}
