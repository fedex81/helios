package omegadrive.m68k;

import m68k.memory.AddressSpace;
import omegadrive.bus.BusProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.util.Size;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class MC68000AddressSpace implements AddressSpace {

    private BusProvider busProvider;

    public static AddressSpace createInstance(BusProvider busProvider) {
        MC68000AddressSpace m = new MC68000AddressSpace();
        m.busProvider = busProvider;
        return m;
    }

    @Override
    public void reset() {
        //NOT USED - DO NOTHING
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
}
