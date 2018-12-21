package omegadrive.m68k;

import m68k.cpu.Instruction;
import m68k.cpu.MC68000;
import m68k.memory.AddressSpace;
import omegadrive.Genesis;
import omegadrive.bus.BusProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.Size;
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
    public static boolean STOP_ON_EXCEPTION =
            Boolean.valueOf(System.getProperty("68k.stop.on.exception", "false"));

    private static int ILLEGAL_ACCESS_EXCEPTION = 4;

    private MC68000 m68k;
    private AddressSpace addressSpace;
    private BusProvider busProvider;
    private MC68000Monitor monitor;
    private boolean stop;

    public MC68000Wrapper(BusProvider busProvider) {
        m68k = new MC68000() {
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
                resetExternal();
            }

            @Override
            public void addInstruction(int opcode, Instruction i) {
                this.i_table[opcode] = i;
            }

            @Override
            public void stop() {
                MC68000Wrapper.this.setStop(true);
            }
        };
        new MoveEx(m68k).register(m68k);
        new TasEx(m68k).register(m68k);
        this.busProvider = busProvider;
        this.addressSpace = getAddressSpace(this, busProvider);
        m68k.setAddressSpace(addressSpace);
    }

    protected int memoryRead(int address, Size size) {
        return (int) busProvider.read(address, size);
    }

    private static AddressSpace getAddressSpace(MC68000Wrapper wrapper, BusProvider busProvider) {
        return new AddressSpace() {
            @Override
            public void reset() {
                //TODO
            }

            @Override
            public int getStartAddress() {
                return 0;
            }

            @Override
            public int getEndAddress() {
                return MemoryProvider.M68K_RAM_SIZE / 1024;
            }

            @Override
            public int readByte(int addr) {
                return wrapper.memoryRead(addr, Size.BYTE);
            }

            @Override
            public int readWord(int addr) {
                return wrapper.memoryRead(addr, Size.WORD);
            }

            @Override
            public int readLong(int addr) {
                return wrapper.memoryRead(addr, Size.LONG);
            }

            @Override
            public void writeByte(int addr, int value) {
                busProvider.write(addr, value, Size.BYTE);
            }

            @Override
            public void writeWord(int addr, int value) {
                busProvider.write(addr, value, Size.WORD);
            }

            @Override
            public void writeLong(int addr, int value) {
                busProvider.write(addr, value, Size.LONG);
            }

            @Override
            public int internalReadByte(int addr) {
                return readByte(addr);
            }

            @Override
            public int internalReadWord(int addr) {
                return readWord(addr);
            }

            @Override
            public int internalReadLong(int addr) {
                return readLong(addr);
            }

            @Override
            public void internalWriteByte(int addr, int value) {
                writeByte(addr, value);
            }

            @Override
            public void internalWriteWord(int addr, int value) {
                writeWord(addr, value);
            }

            @Override
            public void internalWriteLong(int addr, int value) {
                writeLong(addr, value);
            }

            @Override
            public int size() {
                //NOTE: used for debugging
                return BusProvider.ADDRESS_UPPER_LIMIT + 1;
            }
        };
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


    @Override
    public void initialize() {
        reset();
    }


    public MC68000 getM68k() {
        return m68k;
    }

    @Override
    public int runInstruction() {
        int res = 0;
        try {
//            if(m68k.getPC() == 0x506){
//                verbose = true;
//            }
            printVerbose();
            printCpuState("");
            int preIntMask = getM68k().getInterruptLevel();
            res = m68k.execute();
            int postIntMask = getM68k().getInterruptLevel();
//            if(preIntMask != postIntMask){
//                LOG.info("IntMask from: {} to: {}", preIntMask, postIntMask);
//            }
        } catch (Exception e) {
            LOG.error("68k error", e);
            printVerbose();
            handleException(ILLEGAL_ACCESS_EXCEPTION);
        }
        return res;
    }

    private void printVerbose() {
        if (!verbose) {
            return;
        }
        try {
            String res = MC68000Monitor.dumpOp(m68k);
            LOG.info(res);
            System.out.println(res);
            if (MC68000Monitor.addToInstructionSet(m68k)) {
                LOG.info(MC68000Monitor.dumpInstructionSet());
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
            String str = MC68000Monitor.dumpInfo(m68k, true, addressSpace.size());
            LOG.info(head + str);
        } catch (Exception e) {
            String pc = Long.toHexString(m68k.getPC() & 0xFF_FFFF);
            LOG.warn("Unable to dump the state: " + pc, e);
        }
    }

    private void startMonitor() {
        if (monitor == null) {
            monitor = new MC68000Monitor(m68k, addressSpace);
            monitor.running = true;
            LOG.warn("Starting 68k instruction monitor");
        }
    }


    private void printCpuStateLong() {
        if (!verbose) {
            return;
        }
        startMonitor();
        String str = monitor.dumpInfo();
        try {
            str += monitor.handleDisassemble(new String[]{"d", "" + (m68k.getPC() - 8), "16"});
        } catch (Exception e) {
            LOG.error("Unable to disassemble", e);
        }
        LOG.info(str);
    }
}
