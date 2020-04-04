/*
 * Util
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 10:50
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.util;

import com.google.common.base.Strings;
import com.google.common.collect.Range;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.IMemoryRom;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import java.util.zip.CRC32;

public class Util {

    private final static Logger LOG = LogManager.getLogger(Util.class.getSimpleName());

    public static boolean verbose = false;

    public static final int GEN_NTSC_MCLOCK_MHZ = 53693175;
    public static final int GEN_PAL_MCLOCK_MHZ = 53203424;

    public static final long SECOND_IN_NS = Duration.ofSeconds(1).toNanos();
    public static final long MILLI_IN_NS = Duration.ofMillis(1).toNanos();
    public static final long SLEEP_LIMIT_NS = 10_000;

    static final int CACHE_LIMIT = Short.MIN_VALUE;
    static Integer[] negativeCache = new Integer[Short.MAX_VALUE + 2];
    public static ExecutorService executorService = Executors.newSingleThreadExecutor(new PriorityThreadFactory("util"));

    static {
        for (int i = 0, j = 0; i < negativeCache.length; i++) {
            negativeCache[i] = Integer.valueOf(j--);
        }
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void park(long intervalNs) {
        boolean done;
        long start = System.nanoTime();
        do {
            LockSupport.parkNanos(intervalNs);
            long now = System.nanoTime();
            intervalNs -= now - start;
            done = intervalNs < SLEEP_LIMIT_NS;
        } while (!done);
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

    public static long readRom(IMemoryProvider memory, Size size, int address) {
        long data;
        if (size == Size.BYTE) {
            data = memory.readRomByte(address);
        } else if (size == Size.WORD) {
            data = memory.readRomByte(address) << 8;
            data |= memory.readRomByte(address + 1);
        } else {
            data = memory.readRomByte(address) << 24;
            data |= memory.readRomByte(address + 1) << 16;
            data |= memory.readRomByte(address + 2) << 8;
            data |= memory.readRomByte(address + 3);
        }
        LogHelper.printLevel(LOG, Level.DEBUG, "Read ROM: {}, {}: {}", address, data, size, verbose);
        return data;
    }

    public static long readRam(IMemoryProvider memory, Size size, long addressL) {
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

    public static void writeRam(IMemoryProvider memory, Size size, int address, long data) {
        if (size == Size.BYTE) {
            memory.writeRamByte(address, (int) data);
        } else if (size == Size.WORD) {
            memory.writeRamByte(address, (int) (data >> 8));
            memory.writeRamByte(address + 1, (int) (data & 0xFF));
        } else if (size == Size.LONG) {
            memory.writeRamByte(address, (int) ((data >> 24) & 0xFF));
            memory.writeRamByte(address + 1, (int) ((data >> 16) & 0xFF));
            memory.writeRamByte(address + 2, (int) ((data >> 8) & 0xFF));
            memory.writeRamByte(address + 3, (int) (data & 0xFF));
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

    public static long computeChecksum(IMemoryProvider memoryProvider) {
        long res = 0;
        //checksum is computed starting from byte 0x200
        int i = 0x200;
        int size = memoryProvider.getRomSize();
        for (; i < size - 1; i += 2) {
            long val = Util.readRom(memoryProvider, Size.WORD, i);
            res = (res + val) & 0xFFFF;
        }
        //read final byte ??
        res = size % 2 != 0 ? (res + memoryProvider.readRomByte(i)) & 0xFFFF : res;
        return res;
    }

    public static String computeSha1Sum(int[] data){
        Hasher h = Hashing.sha1().newHasher();
        Arrays.stream(data).forEach(d -> h.putByte((byte) d));
        return BaseEncoding.base16().lowerCase().encode(h.hash().asBytes());
    }

    public static String computeSha1Sum(IMemoryRom rom){
        return computeSha1Sum(rom.getRomData());
    }

    public static String computeCrc32(int[] data) {
        CRC32 crc32 = new CRC32();
        Arrays.stream(data).forEach(d -> crc32.update(d));
        return Long.toHexString(crc32.getValue());
    }

    public static String computeCrc32(IMemoryRom rom) {
        return computeCrc32(rom.getRomData());
    }

    public static int log2(int n) {
        if (n <= 0) throw new IllegalArgumentException();
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    public static int getUInt32LE(byte... bytes) {
        int value = (bytes[0] & 0xFF) << 0;
        value = bytes.length > 1 ? value | ((bytes[1] & 0xFF) << 8) : value;
        value = bytes.length > 2 ? value | ((bytes[2] & 0xFF) << 16) : value;
        value = bytes.length > 3 ? value | ((bytes[3] & 0xFF) << 24) : value;
        return value;
    }

    public static int getUInt32LE(int... bytes) {
        int value = (bytes[0] & 0xFF) << 0;
        value = bytes.length > 1 ? value | ((bytes[1] & 0xFF) << 8) : value;
        value = bytes.length > 2 ? value | ((bytes[2] & 0xFF) << 16) : value;
        value = bytes.length > 3 ? value | ((bytes[3] & 0xFF) << 24) : value;
        return value;
    }

    public static void setUInt32LE(int value, int[] data, int startIndex) {
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

    public static int[] toIntArray(byte[] bytes) {
        int[] data = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = bytes[i] & 0xFF;
        }
        return data;
    }

    public static byte[] toByteArray(int[] bytes) {
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) (bytes[i] & 0xFF);
        }
        return data;
    }

    public static final String toHex(long val) {
        return Strings.padStart(Long.toHexString(val & 0xFF_FFFF), 8, '0');
    }

    public static final String toHex(long val, int digits) {
        return Strings.padStart(Long.toHexString(val & 0xFF_FFFF), digits, '0');
    }

    public static Integer getFromIntegerCache(int val) {
        if (val < 0 && val >= CACHE_LIMIT) {
            return negativeCache[-val];
        }
        return Integer.valueOf(val);
    }

    public static List<Range<Integer>> getRangeList(int... values) {
        List<Range<Integer>> list = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2) {
            list.add(Range.closed(values[i], values[i + 1]));
        }
        return list;
    }

    public static byte[] serializeObject(Serializable obj) {
        byte[] res = new byte[0];
        try (
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
        ) {
            oos.writeObject(obj);
            oos.flush();
            res = bos.toByteArray();
        } catch (Exception e) {
            LOG.error("Unable to serialize object: " + obj.getClass().getSimpleName());
        }
        if (res == null || res.length == 0) {
            LOG.error("Unable to serialize object: " + obj.getClass().getSimpleName());
        }
        return res;
    }

    public static Serializable deserializeObject(byte[] data) {
        if (data == null || data.length == 0) {
            LOG.error("Unable to deserialize object of len: " + data.length);
        }
        Serializable res = null;
        try (
                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                ObjectInput in = new ObjectInputStream(bis);
        ) {
            res = (Serializable) in.readObject();
        } catch (Exception e) {
            LOG.error("Unable to deserialize object of len: " + data.length);
        }
        return res;
    }
}
