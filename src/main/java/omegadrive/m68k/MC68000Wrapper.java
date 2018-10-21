package omegadrive.m68k;

import m68k.cpu.Instruction;
import m68k.cpu.InstructionType;
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
 */
public class MC68000Wrapper implements M68kProvider {

    private static Logger LOG = LogManager.getLogger(MC68000Wrapper.class.getSimpleName());

    public static boolean verbose = Genesis.verbose || false;

    private static int ILLEGAL_ACCESS_EXCEPTION = 4;

    private MC68000 m68k;
    private AddressSpace addressSpace;
    private MC68000Monitor monitor;
    private boolean stop;

    public MC68000Wrapper(BusProvider busProvider) {
        m68k = new MC68000() {
            @Override
            public void raiseException(int vector) {
                handleException(vector);
                super.raiseException(vector);
                handleException(vector);
                //Hardball III
                setStop(false);
            }

            @Override
            public int execute() {
                //save the PC address
                currentInstructionAddress = reg_pc;
                int opcode = fetchPCWord();

                Instruction i = i_table[opcode];
                if (i != null) {
                    return i.execute(opcode);
                } else {
                    //TODO a-line and f-line: this seems to be necessary
                    reg_pc = currentInstructionAddress;
                    return unknown.execute(opcode);
                }
            }

            @Override
            public void stop() {
                MC68000Wrapper.this.setStop(true);
            }



            @Override
            public void calcFlagsParam(InstructionType type, int src, int dst, int result, int extraParam, m68k.cpu.Size sz) {
                boolean Sm = (src & sz.msb()) != 0;
                boolean Dm = (dst & sz.msb()) != 0;
                boolean Rm = (result & sz.msb()) != 0;
                boolean Zm = result == 0;
                //TODO this breaks Lotus II
//                        sz.byteCount() == 4 ? result == 0 :
//                        (result & omegadrive.util.Size.getMaxFromByteCount(sz.byteCount())) == 0;


                switch (type) {
                    case ADD:    //ADD, ADDI, ADDQ
                    {
                        Zm = sz.byteCount() == 4 ? result == 0 :
                                (result & omegadrive.util.Size.getMaxFromByteCount(sz.byteCount())) == 0;
                        if ((Sm && Dm && !Rm) || (!Sm && !Dm && Rm)) {
                            reg_sr |= V_FLAG;
                        } else {
                            reg_sr &= ~(V_FLAG);
                        }

                        if ((Sm && Dm) || (!Rm && Dm) || (Sm && !Rm)) {
                            reg_sr |= (C_FLAG | X_FLAG);
                        } else {
                            reg_sr &= ~(C_FLAG | X_FLAG);
                        }

                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }
                        break;
                    }

                    case ADDX: {
                        if ((Sm && Dm && !Rm) || (!Sm && !Dm && Rm)) {
                            reg_sr |= V_FLAG;
                        } else {
                            reg_sr &= ~(V_FLAG);
                        }

                        if ((Sm && Dm) || (!Rm && Dm) || (Sm && !Rm)) {
                            reg_sr |= (C_FLAG | X_FLAG);
                        } else {
                            reg_sr &= ~(C_FLAG | X_FLAG);
                        }

                        if (!Zm) {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }
                        break;
                    }

                    case ASL: {
                        //params are different here!
                        if (src != 0)    // shift count
                        {
                            if (dst != 0)    // last bit out
                            {
                                reg_sr |= (C_FLAG | X_FLAG);
                            } else {
                                reg_sr &= ~(C_FLAG | X_FLAG);
                            }
                        }

                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }

                        if (extraParam != 0)    // msb changed
                        {
                            reg_sr |= V_FLAG;
                        } else {
                            reg_sr &= ~V_FLAG;
                        }

                        break;
                    }

                    case ASR: {
                        //params are different here!
                        if (src != 0)    // shift count
                        {
                            if (dst != 0)    // last bit out
                            {
                                reg_sr |= (C_FLAG | X_FLAG);
                            } else {
                                reg_sr &= ~(C_FLAG | X_FLAG);
                            }
                        }

                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }

                        // always cleared
                        reg_sr &= ~V_FLAG;

                        break;
                    }

                    case CMP:    // CMP, CMPA, CMPI CMPM
                    {
                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if ((!Sm && Dm && !Rm) || (Sm && !Dm && Rm)) {
                            reg_sr |= V_FLAG;
                        } else {
                            reg_sr &= ~(V_FLAG);
                        }

                        if ((Sm && !Dm) || (Rm && !Dm) || (Sm && Rm)) {
                            reg_sr |= C_FLAG;
                        } else {
                            reg_sr &= ~(C_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }
                        break;
                    }
                    case LSL:
                    case LSR:
                    case ROXL:
                    case ROXR: {
                        if (src > 0)    //shift count
                        {
                            if (dst != 0)    //last bit out
                            {
                                reg_sr |= (C_FLAG | X_FLAG);
                            } else {
                                reg_sr &= ~(C_FLAG | X_FLAG);
                            }
                        }

                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }

                        reg_sr &= ~(V_FLAG);

                        break;
                    }
                    case AND:
                    case EOR:
                    case MOVE:
                    case NOT:
                    case OR: {
                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }

                        reg_sr &= ~(V_FLAG | C_FLAG);
                        break;
                    }
                    case NEG: {
                        if (Sm && Rm) {
                            reg_sr |= V_FLAG;
                        } else {
                            reg_sr &= ~(V_FLAG);
                        }

                        if (Zm) {
                            reg_sr |= Z_FLAG;
                            reg_sr &= ~(X_FLAG | C_FLAG);
                        } else {
                            reg_sr &= ~(Z_FLAG);
                            reg_sr |= (X_FLAG | C_FLAG);
                        }
                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }
                        break;
                    }
                    case NEGX: {
                        if (Sm && Rm) {
                            reg_sr |= V_FLAG;
                        } else {
                            reg_sr &= ~(V_FLAG);
                        }
                        if (Sm || Rm) {
                            reg_sr |= (X_FLAG | C_FLAG);
                        } else {
                            reg_sr &= ~(X_FLAG | C_FLAG);
                        }
                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }
                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }
                        break;
                    }
                    case ROL:
                    case ROR: {
                        if (src > 0)    //shift count
                        {
                            if (dst != 0)    //last bit out
                            {
                                reg_sr |= C_FLAG;
                            } else {
                                reg_sr &= ~(C_FLAG);
                            }
                        }

                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }

                        reg_sr &= ~(V_FLAG);

                        break;
                    }

                    case SUB: {
                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if ((!Sm && Dm && !Rm) || (Sm && !Dm && Rm)) {
                            reg_sr |= V_FLAG;
                        } else {
                            reg_sr &= ~(V_FLAG);
                        }

                        if ((Sm && !Dm) || (Rm && !Dm) || (Sm && Rm)) {
                            reg_sr |= (C_FLAG | X_FLAG);
                        } else {
                            reg_sr &= ~(C_FLAG | X_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }
                        break;
                    }

                    case SUBX: {
                        if (!Zm) {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if ((!Sm && Dm && !Rm) || (Sm && !Dm && Rm)) {
                            reg_sr |= V_FLAG;
                        } else {
                            reg_sr &= ~(V_FLAG);
                        }

                        if ((Sm && !Dm) || (Rm && !Dm) || (Sm && Rm)) {
                            reg_sr |= (C_FLAG | X_FLAG);
                        } else {
                            reg_sr &= ~(C_FLAG | X_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }
                        break;
                    }

                    // swap also affects the SR
                    case SWAP: {
                        if (Zm) {
                            reg_sr |= Z_FLAG;
                        } else {
                            reg_sr &= ~(Z_FLAG);
                        }

                        if (Rm) {
                            reg_sr |= N_FLAG;
                        } else {
                            reg_sr &= ~(N_FLAG);
                        }
                        reg_sr &= ~(V_FLAG);            // these are always set to 0
                        reg_sr &= ~(C_FLAG);
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("No flags handled for " + type);
                    }
                }
            }
        };
        this.addressSpace = getAddressSpace(busProvider);
        m68k.setAddressSpace(addressSpace);
        if (verbose) {
            startMonitor();
        }
    }

    @Override
    public void startMonitor() {
        if (monitor == null) {
            monitor = new MC68000Monitor(m68k, addressSpace);
            monitor.running = true;
            LOG.warn("Starting 68k instruction monitor");
        }
    }

    private static AddressSpace getAddressSpace(BusProvider busProvider) {
        MemoryProvider memoryProvider = busProvider.getMemory();
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
                return (int) busProvider.read(addr, Size.BYTE);
            }

            @Override
            public int readWord(int addr) {
                return (int) busProvider.read(addr, Size.WORD);
            }

            @Override
            public int readLong(int addr) {
                return (int) busProvider.read(addr, Size.LONG);
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
        LOG.debug("M68K stop: " + value);
        this.stop = value;
    }

    @Override
    public boolean isStopped() {
        return stop;
    }

    @Override
    public void raiseInterrupt(int level) {
        printCpuState("Before INT: " + level);
        m68k.raiseInterrupt(level);
        printCpuState("After INT: " + level);
    }

    @Override
    public void reset() {
        m68k.reset();
    }


    @Override
    public void initialize() {
        reset();
    }

    @Override
    public int runInstruction() {
        int res = 0;
        try {
            res = m68k.execute();
            //TODO check SSP vs a7 sync
//            if(m68k.getSSP() != m68k.getAddrRegisterLong(7)){
//                LOG.info(str);
//                m68k.setSSP(m68k.getAddrRegisterLong(7));
//            }
        } catch (Exception e) {
            verbose = true;
            LOG.error("68k error", e);
            printVerbose();
            handleException(ILLEGAL_ACCESS_EXCEPTION);
            verbose = false;
        }
        return res;
    }

    private void printVerbose() {
        if (!verbose) {
            return;
        }
        String res = MC68000Monitor.dumpOp(m68k);
        LOG.info(res);
        if (MC68000Monitor.addToInstructionSet(m68k)) {
            LOG.info(MC68000Monitor.dumpInstructionSet());
        }
    }

    private void handleException(int vector) {
        if (vector == ILLEGAL_ACCESS_EXCEPTION) {
            printCpuState("Exception: " + vector);
            setStop(true);
        }
    }

    private void printCpuState(String head) {
        if (!verbose) {
            return;
        }
        startMonitor();
        String str = monitor.dumpInfo();
        LOG.info(head + str);
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
