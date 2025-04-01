package s32x.sh2.j2core;

import omegadrive.SystemLoader;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil;
import omegadrive.util.FileUtil;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import s32x.Sh2MMREG;
import s32x.bus.Sh2Bus;
import s32x.sh2.Sh2;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Debug;
import s32x.sh2.Sh2Helper.Sh2Config;
import s32x.sh2.Sh2Impl;
import s32x.sh2.cache.Sh2Cache;
import s32x.sh2.device.Sh2DeviceHelper;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * Binary file generated, with modifications, from:
 * https://github.com/j-core/jcore-cpu/tree/master/testrom
 * <p>
 * License: see j2tests.lic
 * <p>
 */
public class J2CoreTest {

    protected static final int EVENT_TRIGGER_ADDRESS = 0xBCDE0000;
    protected static final int TEST_RESULT_ADDRESS = 0xBCDE0010;
    protected static final int DUMP_STACK_ADDRESS = 0xBCDE0020;

    protected static final int PASS_ADDRESS = 0xABCD0000;

    private static final String binFileName = "j2tests.bin";

    protected static AtomicReference<String> binNameLocal = new AtomicReference<>(binFileName);

    static final int FAIL_VALUE = 0x8888_8888;
    final static int ramSize = 0x8000;
    static ByteBuffer rom;
    private static boolean done = false;
    protected static boolean sh2Debug = false;

    public static final Path baseDataFolder = Paths.get(new File(".").getAbsolutePath(),
            "src", "test", "resources", "j2core");

    protected Sh2 sh2;
    protected Sh2Context ctx;
    private static Sh2Config config = new Sh2Config(false, false, false,
            false, 0);

    @BeforeAll
    public static void beforeAll() {
        initRom(binNameLocal.get());
    }

    protected static void initRom(String name) {
        System.out.println(new File(".").getAbsolutePath());
        Path binPath = Paths.get(baseDataFolder.toAbsolutePath().toString(), name);
        System.out.println("Bin file: " + binPath.toAbsolutePath());
        rom = ByteBuffer.wrap(FileUtil.loadBiosFile(binPath));
        Assertions.assertTrue(rom.capacity() > 0, "File missing: " + binPath.toAbsolutePath());
    }

    @BeforeEach
    public void before() {
        Sh2Config.reset(config);
        Sh2Bus memory = getMemoryInt(rom);
        sh2 = getSh2Interpreter(memory, sh2Debug);
        ctx = createContext(BufferUtil.CpuDeviceAccess.MASTER, memory);
        sh2.reset(ctx);
        System.out.println("Reset, PC: " + ctx.PC + ", SP: " + ctx.registers[15]);
    }

    @Test
    public void testJ2() {
        int limit = 3_000;
        int cnt = 0;
        do {
            sh2.run(ctx);
            checkFail(ctx);
            cnt++;
        } while (!done && cnt < limit);
        Assertions.assertTrue(cnt < limit);
        System.out.println(cnt);
        System.out.println("All tests done: success");
        System.out.println(ctx.toString());
    }

    public static Sh2Context createContext(BufferUtil.CpuDeviceAccess cpu, Sh2Bus memory) {
        Sh2Cache cache = Sh2Cache.createCacheInstance(cpu, memory);
        Sh2MMREG sh2MMREG = new Sh2MMREG(cpu, cache);
        Sh2Context context = new Sh2Context(BufferUtil.CpuDeviceAccess.MASTER, sh2Debug);
        context.devices = Sh2DeviceHelper.createDevices(cpu, memory, sh2MMREG);
        sh2MMREG.init(context.devices);
        MdRuntimeData.newInstance(SystemLoader.SystemType.S32X, SystemProvider.NO_CLOCK);
        return context;
    }

    protected static void checkDone(ByteBuffer ram, int addr) {
        char c1 = (char) ram.get(addr & ~1);
        char c2 = (char) ram.get((addr & ~1) + 1);
        if (c1 == 'O' && c2 == 'K') {
            done = true;
        }
    }

    protected static void checkFail(Sh2Context ctx) {
        if (ctx.registers[0] == FAIL_VALUE && ctx.registers[0] == ctx.registers[1]) {
            Assertions.fail(ctx.toString());
        }
    }

    public static Sh2 getSh2Interpreter(Sh2Bus memory, boolean debug) {
        return sh2Debug ? new Sh2Debug(memory) : new Sh2Impl(memory);
    }

    protected Sh2Bus getMemoryInt(final ByteBuffer rom) {
        return J2CoreTest.getMemory(rom);
    }


    public static Sh2Bus getMemory(final ByteBuffer rom) {
        final int romSize = rom.capacity();
        final ByteBuffer ram = ByteBuffer.allocateDirect(ramSize);
        return new Sh2Bus() {
            @Override
            public void write(int address, int value, Size size) {
                long lreg = address & 0xFFFF_FFFFL;
                if (lreg < romSize) {
                    writeBufferRaw(rom, (int) lreg, value, size);
                } else if (lreg < ramSize) {
                    writeBufferRaw(ram, (int) lreg, value, size);
                    checkDone(ram, address);
                } else if (lreg == PASS_ADDRESS) {
                    System.out.println("Test success: " + th(value));
                } else {
                    System.out.println("write: " + th(address) + " " + th(value) + " " + size);
                }
            }

            @Override
            public int read(int address, Size size) {
                long laddr = address & 0xFFFF_FFFFL;
                int res = size.getMask();
                if (laddr < romSize) {
                    res = readBuffer(rom, (int) laddr, size);
                } else if (laddr < ramSize) {
                    res = readBuffer(ram, (int) laddr, size);
                } else {
                    System.out.println("read32: " + th(address));
                }
                return res & size.getMask();
            }

            @Override
            public void resetSh2() {
            }
        };
    }
}
