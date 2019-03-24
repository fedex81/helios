package omegadrive.z80.disasm;

import emulib.plugins.memory.AbstractMemoryContext;
import omegadrive.bus.BaseBusProvider;
import omegadrive.util.Size;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class Z80MemContext extends AbstractMemoryContext<Integer> {

    private BaseBusProvider busProvider;

    public static Z80MemContext createInstance(BaseBusProvider busProvider) {
        Z80MemContext c = new Z80MemContext();
        c.busProvider = busProvider;
        return c;
    }

    @Override
    public Integer read(int memoryPosition) {
        return (int) busProvider.read(memoryPosition, Size.BYTE);
    }

    @Override
    public Integer[] readWord(int memoryPosition) {
        return new Integer[]{read(memoryPosition), read(memoryPosition + 1)};
    }

    @Override
    public void write(int memoryPosition, Integer value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeWord(int memoryPosition, Integer[] value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?> getDataType() {
        return Integer.class;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSize() {
        return -1;
    }
}
