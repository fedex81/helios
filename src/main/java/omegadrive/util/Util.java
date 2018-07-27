package omegadrive.util;

import com.google.common.collect.Range;
import omegadrive.Genesis;
import omegadrive.memory.MemoryProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class Util {

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void waitForever() {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void waitOnObject(Object object, long ms) {
        synchronized (object) {
            try {
                object.wait(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void registerJmx(Object object) {
        JmxBridge.registerJmx(object);
    }

    //bit 1 -> true
    public static boolean bitSetTest(long number, int position) {
        return ((number & (1 << position)) != 0);
    }

    public static long readRom(MemoryProvider memory, Size size, long address) {
        long data;
        if (size == Size.BYTE) {
            data = memory.readCartridgeByte(address);
        } else if (size == Size.WORD) {
            data = memory.readCartridgeWord(address);
        } else {
            data = memory.readCartridgeWord(address) << 16;
            data |= memory.readCartridgeWord(address + 2);
        }
        return data;
    }

    public static long readRam(MemoryProvider memory, Size size, long address) {
        long data;
        address = address & 0xFFFF;
        if (size == Size.BYTE) {
            data = memory.readRam(address);
        } else if (size == Size.WORD) {
            data = memory.readRam(address) << 8;
            data |= memory.readRam(address + 1);
        } else {
            data = memory.readRam(address) << 24;
            data |= memory.readRam(address + 1) << 16;
            data |= memory.readRam(address + 2) << 8;
            data |= memory.readRam(address + 3);
        }
        return data;
    }

    public static long readSram(int[] sram, Size size, long address, long SRAM_START_ADDRESS) {
        long data;
        address = address - SRAM_START_ADDRESS;
        if (size == Size.BYTE) {
            data = sram[(int) address];
        } else if (size == Size.WORD) {
            data = sram[(int) address] << 8;
            data |= sram[(int) address + 1];
        } else {
            data = sram[(int) address] << 24;
            data |= sram[(int) address + 1] << 16;
            data |= sram[(int) address + 2] << 8;
            data |= sram[(int) address + 3];
        }
        return data;
    }

    public static void writeZ80(Z80Provider z80, Size size, int addressInt, long data) {
        if (size == Size.BYTE) {
            z80.writeByte(addressInt, data);
        } else if (size == Size.WORD) {
            z80.writeWord(addressInt, data);
        } else {
            z80.writeWord(addressInt, data >> 16);
            z80.writeWord(addressInt + 2, data & 0xFFFF);
        }
    }

    public static void writeRam(MemoryProvider memory, Size size, long addr, long data) {
        if (size == Size.BYTE) {
            memory.writeRam(addr, data);
        } else if (size == Size.WORD) {
            memory.writeRam(addr, (data >> 8));
            memory.writeRam(addr + 1, (data & 0xFF));
        } else if (size == Size.LONG) {
            memory.writeRam(addr, (data >> 24) & 0xFF);
            memory.writeRam(addr + 1, (data >> 16) & 0xFF);
            memory.writeRam(addr + 2, (data >> 8) & 0xFF);
            memory.writeRam(addr + 3, (data & 0xFF));
        }
    }

    public static void writeSram(int[] sram, Size size, long addressL, long data) {
        if (size == Size.BYTE) {
            sram[(int) addressL] = (int) data;
        } else if (size == Size.WORD) {
            sram[(int) addressL] = (int) (data >> 8) & 0xFF;
            sram[(int) addressL + 1] = (int) data & 0xFF;
        } else {
            sram[(int) addressL] = (int) (data >> 24) & 0xFF;
            sram[(int) addressL + 1] = (int) (data >> 16) & 0xFF;
            sram[(int) addressL + 2] = (int) (data >> 8) & 0xFF;
            sram[(int) addressL + 3] = (int) data & 0xFF;
        }
    }

    public static void arrayDataCopy(int[][] src, int[][] dest) {
        for (int i = 0; i < src.length; i++) {
            for (int j = 0; j < src[i].length; j++) {
                dest[i] = src[i];
            }
        }
    }

    public static void printLevelIfVerbose(Logger LOG, Level level, String str, Object... args) {
        if (Genesis.verbose) {
            LOG.log(level, str, args);
        }
    }

    public static List<Range<Integer>> getRangeList(int... values) {
        List<Range<Integer>> list = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2) {
            list.add(Range.closed(values[i], values[i + 1]));
        }
        return list;
    }
}
