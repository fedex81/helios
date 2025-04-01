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

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import omegadrive.Device;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.IMemoryRom;
import org.slf4j.Logger;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

public class Util {

    private final static Logger LOG = LogHelper.getLogger(Util.class.getSimpleName());

    public static boolean verbose = false;

    public static final int GEN_NTSC_MCLOCK_MHZ = 53693175;
    public static final int GEN_PAL_MCLOCK_MHZ = 53203424;

    public static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    public static final String NATIVE_SUBDIR = OS_NAME.contains("win") ? "windows" :
            (OS_NAME.contains("mac") ? "osx" : "linux");

    public static final long SECOND_IN_NS = Duration.ofSeconds(1).toNanos();
    public static final long MILLI_IN_NS = Duration.ofMillis(1).toNanos();

    public static final boolean randomInitRam =
            Boolean.parseBoolean(System.getProperty("helios.random.init.ram", "false"));

    public static final Random random;

    static final int CACHE_LIMIT = Short.MIN_VALUE;

    public static final VarHandle SHORT_BYTEARR_HANDLE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle INT_BYTEARR_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle SHORT_BYTEBUF_HANDLE = MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    public static final VarHandle INT_BYTEBUF_HANDLE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    static final Integer[] negativeCache = new Integer[Short.MAX_VALUE + 2];
    public static final ExecutorService executorService = Executors.newSingleThreadExecutor(new PriorityThreadFactory("util"));

    static {
        for (int i = 0, j = 0; i < negativeCache.length; i++) {
            negativeCache[i] = j--;
        }
        long seed = System.currentTimeMillis();
        random = new Random(seed);
        LOG.info("Creating Random with seed: {}", seed);
        LOG.info("Init RAM with random values: {}", randomInitRam);
        Thread.setDefaultUncaughtExceptionHandler(Util::uncaughtException);
    }

    private static void uncaughtException(Thread t, Throwable e) {
        LOG.error("uncaughtException fot thread: {}", t.getName(), e);
        e.printStackTrace();
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final Object lock = new Object();

    public static void waitForever() {
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void waitOnBarrier(CyclicBarrier barrier, boolean verbose) {
        try {
            barrier.await();
        } catch (Exception e) {
            if (verbose) {
                LOG.warn("Error on barrier", e);
            } else {
                LOG.info("Leaving barrier: {}", e.getClass().getName());
            }
            barrier.reset();
        }
    }

    public static void waitOnBarrier(CyclicBarrier barrier) {
        waitOnBarrier(barrier, true);
    }

    public static boolean trySignalCondition(Lock lock, Condition condition) {
        if (lock.tryLock()) {
            try {
                condition.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    public static void waitOnCondition(Lock lock, Condition condition) {
        try {
            lock.lock();
            try {
                condition.await();
            } catch (Exception e) {
                LOG.warn("Error on condition", e);
            }
        } finally {
            lock.unlock();
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

    public static byte[] initMemoryRandomBytes(byte[] mem) {
        if (!randomInitRam) {
            return mem;
        }
        for (int i = 0; i < mem.length; i++) {
            mem[i] = (byte) Util.random.nextInt(0x100);
        }
        return mem;
    }

    public static ByteBuffer initMemoryRandomBytes(ByteBuffer mem) {
        if (!randomInitRam) {
            return mem;
        }
        for (int i = 0; i < mem.capacity(); i++) {
            mem.put((byte) Util.random.nextInt(0x100));
        }
        return mem;
    }

    public static void registerJmx(Object object) {
        JmxBridge.registerJmx(object);
    }

    //bit 1 -> true
    public static boolean bitSetTest(long number, int position) {
        return ((number & (1 << position)) != 0);
    }



    public static int readBufferByte(ByteBuffer b, int pos) {
        return b.get(pos);
    }

    public static int readBufferWord(ByteBuffer b, int pos) {
        assert (pos & 1) == 0;
        return b.getShort(pos);
    }

    public static int readBufferLong(ByteBuffer b, int pos) {
        assert (pos & 1) == 0;
        return b.getInt(pos);
    }

    public static int readDataMask(byte[] src, int address, final int mask, Size size) {
        return readData(src, address & mask, size);
    }

    public static int readData(byte[] src, int address, Size size) {
//        assert size != Size.BYTE ? (address & 1) == 0 : true; //TODO sram word reads on odd addresses
        return switch (size) {
            case WORD -> ((src[address] & 0xFF) << 8) | (src[address + 1] & 0xFF);
            case LONG -> ((src[address] & 0xFF) << 24) | (src[address + 1] & 0xFF) << 16 |
                    (src[address + 2] & 0xFF) << 8 | (src[address + 3] & 0xFF);
            case BYTE -> src[address];
        };
    }

    public static void writeData(byte[] dest, int address, int data, Size size) {
//        assert size != Size.BYTE ? (address & 1) == 0 : true; //TODO sram word writes on odd addresses
        switch (size) {
            case WORD -> SHORT_BYTEARR_HANDLE.set(dest, address, (short) data);
            case LONG -> INT_BYTEARR_HANDLE.set(dest, address, data);
            case BYTE -> dest[address] = (byte) data;
        }
    }

    public static void writeDataMask(byte[] dest, int address, int data, final int mask, Size size) {
        writeData(dest, address & mask, data, size);
    }

    @Deprecated
    public static int computeChecksum(IMemoryProvider memoryProvider) {
        int res = 0;
        //checksum is computed starting from byte 0x200
        int i = 0x200;
        int size = memoryProvider.getRomSize();
        final int mask = memoryProvider.getRomMask();
        for (; i < size - 1; i += 2) {
            int val = Util.readDataMask(memoryProvider.getRomData(), i, mask, Size.WORD);
            res = (res + val) & 0xFFFF;
        }
        //read final byte ??
        res = size % 2 != 0 ? (res + memoryProvider.readRomByte(i)) & 0xFFFF : res;
        return res;
    }

    public static String computeSha1Sum(byte[] data) {
        Hasher h = Hashing.sha1().newHasher();
        IntStream.range(0, data.length).forEach(i -> h.putByte(data[i]));
        return BaseEncoding.base16().lowerCase().encode(h.hash().asBytes());
    }

    public static String computeSha1Sum(IMemoryRom rom){
        return computeSha1Sum(rom.getRomData());
    }

    public static String computeCrc32(byte[] data) {
        CRC32 crc32 = new CRC32();
        IntStream.range(0, data.length).forEach(i -> crc32.update(data[i]));
        return Long.toHexString(crc32.getValue());
    }

    public static String computeCrc32(IMemoryRom rom) {
        return computeCrc32(rom.getRomData());
    }

    public static int log2(int n) {
        if (n <= 0) throw new IllegalArgumentException();
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    public static String toStringValue(byte... data) {
        return new String(data);
    }

    public static String th(short pos) {
        return Integer.toHexString(((int) pos) & 0xFFFF);
    }
    public static String th(int pos) {
        return Integer.toHexString(pos);
    }

    public static String th(long pos) {
        return Long.toHexString(pos);
    }

    public static byte[] getPaddedRom(byte[] data) {
        int romSize = data.length;
        int mask = getRomMask(romSize);
        if (mask >= romSize) {
            byte[] newrom = new byte[mask + 1];
            Arrays.fill(newrom, (byte) 0xFF);
            System.arraycopy(data, 0, newrom, 0, romSize);
            return newrom;
        }
        return data;
    }

    public static int getRomMask(int size) {
        int log2 = Util.log2(size);
        int pow2 = (int) Math.pow(2, log2);
        if (size == pow2) {
            //size = 0x4000, mask = 0x3fff
            return size - 1;
        }
        return (int) Math.pow(2, log2 + 1) - 1;
    }

    public static Integer getFromIntegerCache(int val) {
        if (val < 0 && val >= CACHE_LIMIT) {
            return negativeCache[-val];
        }
        return val;
    }

    public static byte[] serializeObject(Serializable obj) {
        byte[] res = new byte[0];
        try (
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)
        ) {
            oos.writeObject(obj);
            oos.flush();
            res = bos.toByteArray();
        } catch (Exception e) {
            LOG.error("Unable to serialize object: {}, {}", obj.getClass().getSimpleName(), e.getMessage());
        }
        if (res.length == 0) {
            LOG.error("Unable to serialize object: {}", obj.getClass().getSimpleName());
        }
        return res;
    }

    public static Serializable deserializeObject(ByteBuffer data) {
        return deserializeObject(data.array(), 0, data.capacity());
    }

    public static Serializable deserializeObject(byte[] data) {
        return deserializeObject(data, 0, data.length);
    }

    public static Serializable deserializeObject(byte[] data, int offset, int len) {
        if (data == null || data.length == 0 || offset < 0 || len > data.length) {
            LOG.error("Unable to deserialize object of len: {}", data != null ? data.length : "null");
            return null;
        }
        Serializable res = null;
        try (
                ByteArrayInputStream bis = new ByteArrayInputStream(data, offset, len);
                ObjectInput in = new ObjectInputStream(bis)
        ) {
            res = (Serializable) in.readObject();
        } catch (Exception e) {
            LOG.error("Unable to deserialize object of len: {}, {}", data.length, e.getMessage());
        }
        return res;
    }

    public static <T, V extends Device> Optional<V> getDeviceIfAny(Collection<T> deviceSet, Class<V> clazz) {
        return deviceSet.stream().filter(t -> clazz.isAssignableFrom(t.getClass())).findFirst().map(clazz::cast);
    }

    public static <T, V extends Device> Set<V> getAllDevices(Collection<T> deviceSet, Class<V> clazz) {
        return deviceSet.stream().filter(t -> clazz.isAssignableFrom(t.getClass())).map(clazz::cast).collect(Collectors.toSet());
    }

    //from Guava, Files::getNameWithoutExtension
    public static String getNameWithoutExtension(String file) {
        Objects.requireNonNull(file);
        String fileName = new File(file).getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    public static int getBitFromByte(byte b, int bitPos) {
        assert bitPos >= 0 && bitPos < 8;
        return (b >> bitPos) & 1;
    }

    public static int getBitFromWord(short b, int bitPos) {
        assert bitPos >= 0 && bitPos < 16;
        return (b >> bitPos) & 1;
    }

    public static boolean assertCheckBusOp(int address, Size size) {
        //TODO doomFusion
        //68k LONG access, gets converted to 2 WORD accesses
//        assert (size != Size.BYTE ? (address & 1) == 0 : true) : (th(address) + "," + size);
        //TODO doomFusion
        return true;
    }

    public static Runnable wrapRunnableEx(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }
}
