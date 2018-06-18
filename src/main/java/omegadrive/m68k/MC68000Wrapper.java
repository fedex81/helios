package omegadrive.m68k;

import omegadrive.bus.BusProvider;
import omegadrive.m68k.tonyheadford.cpu.MC68000;
import omegadrive.m68k.tonyheadford.memory.AddressSpace;
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
 */
public class MC68000Wrapper implements M68kProvider {

    private static Logger LOG = LogManager.getLogger(MC68000Wrapper.class.getSimpleName());

    private MC68000 m68k;

    public MC68000Wrapper(BusProvider busProvider) {
        m68k = new MC68000();
        m68k.setAddressSpace(getAddressSpace(busProvider));
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
                return MemoryProvider.M68K_RAM_SIZE;
            }
        };
    }

    @Override
    public long getPC() {
        return m68k.getPC();
    }

    @Override
    public void setStop(boolean value) {
        LOG.warn("M68K stop: " + value);
        m68k.stop();
    }

    @Override
    public boolean isStopped() {
        return false; //TODO
    }

    @Override
    public int getInterruptMask() {
        return m68k.getInterruptLevel();
    }

    @Override
    public void raiseInterrupt(int level) {
        m68k.raiseInterrupt(level);
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
        return m68k.execute();
    }
}
