package omegadrive.z80.disasm;

import emulib.plugins.memory.AbstractMemoryContext;
import omegadrive.z80.Z80Memory;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class Z80MemContext extends AbstractMemoryContext<Integer> {

    private Z80Memory memory;

    public static Z80MemContext createInstance(Z80Memory memory) {
        Z80MemContext c = new Z80MemContext();
        c.memory = memory;
        return c;
    }

    @Override
    public Integer read(int memoryPosition) {
        return memory.readByte(memoryPosition);
    }

    @Override
    public Integer[] readWord(int memoryPosition) {
        return new Integer[]{memory.readByte(memoryPosition), memory.readByte(memoryPosition + 1)};
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
        return memory.getMemory().length;
    }
}
