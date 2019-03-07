package omegadrive.util;

import com.google.common.collect.Range;
import omegadrive.Genesis;
import omegadrive.memory.MemoryProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.LockSupport;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class Util {

    private static Logger LOG = LogManager.getLogger(Util.class.getSimpleName());

    public static boolean verbose = Genesis.verbose || false;

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static long parkUntil(long targetNs) {
        long start = System.nanoTime();
        return parkFor(Math.max(0, targetNs - start), start);
    }

    private static long parkFor(long intervalNs, long startNs) {
        boolean done;
        long now;
        do {
            LockSupport.parkNanos(intervalNs);
            now = System.nanoTime();
            intervalNs -= now - startNs;
            done = intervalNs < 500_000; //within half a millis
        } while (!done);
        return now;
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

    public static void waitOnBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            LOG.warn("Error on barrier", e);
            barrier.reset();
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

    public static void waitOnObject(Object object) {
        synchronized (object) {
            try {
                object.wait();
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
        LogHelper.printLevel(LOG, Level.DEBUG, "Read ROM: {}, {}: {}", address, data, size, verbose);
        return data;
    }

    public static long readRam(MemoryProvider memory, Size size, long addressL) {
        long data;
        int address = (int) (addressL & 0xFFFF);

        if (size == Size.BYTE) {
            data = memory.readRamByte(address);
        } else if (size == Size.WORD) {
            data = memory.readRamByte(address) << 8;
            data |= memory.readRamByte(address + 1);
        } else {
            data = memory.readRamByte(address) << 24;
            data |= memory.readRamByte(address + 1) << 16;
            data |= memory.readRamByte(address + 2) << 8;
            data |= memory.readRamByte(address + 3);
        }
        LogHelper.printLevel(LOG, Level.DEBUG, "Read RAM: {}, {}: {}", address, data, size, verbose);
        return data;
    }

    public static long readSram(int[] sram, Size size, long address) {
        long data;
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
        LogHelper.printLevel(LOG, Level.DEBUG, "Read SRAM: {}, {}: {}", address, data, size, verbose);
        return data;
    }

    public static void writeRam(MemoryProvider memory, Size size, long addressL, long data) {
        int address = (int) (addressL & 0xFFFF);
        if (size == Size.BYTE) {
            memory.writeRamByte(address, data);
        } else if (size == Size.WORD) {
            memory.writeRamByte(address, (data >> 8));
            memory.writeRamByte(address + 1, (data & 0xFF));
        } else if (size == Size.LONG) {
            memory.writeRamByte(address, (data >> 24) & 0xFF);
            memory.writeRamByte(address + 1, (data >> 16) & 0xFF);
            memory.writeRamByte(address + 2, (data >> 8) & 0xFF);
            memory.writeRamByte(address + 3, (data & 0xFF));
        }
        LogHelper.printLevel(LOG, Level.DEBUG, "Write RAM: {}, {}: {}", address, data, size, verbose);
    }

    public static void writeSram(int[] sram, Size size, int address, long data) {
        if (size == Size.BYTE) {
            sram[address] = (int) (data & 0xFF);
        } else if (size == Size.WORD) {
            sram[address] = (int) ((data >> 8) & 0xFF);
            sram[address + 1] = (int) (data & 0xFF);
        } else {
            sram[address] = (int) ((data >> 24) & 0xFF);
            sram[address + 1] = (int) ((data >> 16) & 0xFF);
            sram[address + 2] = (int) ((data >> 8) & 0xFF);
            sram[address + 3] = (int) (data & 0xFF);
        }
        LogHelper.printLevel(LOG, Level.DEBUG, "Write SRAM: {}, {}: {}", address, data, size, verbose);
    }

    public static void arrayDataCopy(int[][] src, int[][] dest) {
        for (int i = 0; i < src.length; i++) {
            for (int j = 0; j < src[i].length; j++) {
                dest[i] = src[i];
            }
        }
    }

    public static long computeChecksum(MemoryProvider memoryProvider) {
        long res = 0;
        //checksum is computed starting from byte 0x200
        int i = 0x200;
        int size = memoryProvider.getRomSize();
        for (; i < size - 1; i += 2) {
            long val = memoryProvider.readCartridgeWord(i);
            res = (res + val) & 0xFFFF;
        }
        //read final byte ??
        res = size % 2 != 0 ? (res + memoryProvider.readCartridgeByte(i)) & 0xFFFF : res;
        return res;
    }

    public static int log2(int n) {
        if (n <= 0) throw new IllegalArgumentException();
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    public static int getUInt32(int... bytes) {
        int value = (bytes[0] & 0xFF) << 0;
        value = bytes.length > 1 ? value | ((bytes[1] & 0xFF) << 8) : value;
        value = bytes.length > 2 ? value | ((bytes[2] & 0xFF) << 16) : value;
        value = bytes.length > 3 ? value | ((bytes[3] & 0xFF) << 24) : value;
        return value;
    }

    public static void setUInt32(int value, int[] data, int startIndex) {
        data[startIndex + 3] = (value >> 24) & 0xFF;
        data[startIndex + 2] = (value >> 16) & 0xFF;
        data[startIndex + 1] = (value >> 8) & 0xFF;
        data[startIndex] = (value) & 0xFF;
    }

    public static String toStringValue(int... data) {
        String value = "";
        for (int i = 0; i < data.length; i++) {
            value += (char) (data[i] & 0xFF);
        }
        return value;
    }

    public static final String pad4(long reg) {
        String s = Long.toHexString(reg).toUpperCase();
        while (s.length() < 4) {
            s = "0" + s;
        }
        return s;
    }

    public static List<Range<Integer>> getRangeList(int... values) {
        List<Range<Integer>> list = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2) {
            list.add(Range.closed(values[i], values[i + 1]));
        }
        return list;
    }
}
