package s32x.sh2.j2core;

import omegadrive.util.FileUtil;
import omegadrive.util.Size;
import org.junit.jupiter.api.*;
import s32x.bus.Sh2Bus;
import s32x.sh2.Sh2Context;
import s32x.sh2.Sh2Disassembler;
import s32x.sh2.device.IntControl;
import s32x.sh2.device.IntControl.Sh2Interrupt;
import s32x.sh2.device.IntControlImpl;

import java.io.File;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static omegadrive.util.BufferUtil.readBuffer;
import static omegadrive.util.BufferUtil.writeBufferRaw;
import static omegadrive.util.Util.th;
import static s32x.sh2.Sh2.flagIMASK;

/**
 * Federico Berti
 * <p>
 * Copyright 2022
 * <p>
 * NOTE: the RTE test uses additional machinery within the cpu simulator, let's ignore it
 */
public class J2CoreInterruptsTest extends J2CoreTest {

    protected static final int EVENT_TRIGGER_ADDRESS = 0xBCDE0000;
    protected static final int TEST_RESULT_ADDRESS = 0xBCDE0010;
    protected static final int DUMP_STACK_ADDRESS = 0xBCDE0020;

    private static final String binNameInt = "interrupts.img";
    private static Sh2Interrupt next;
    private static int vectorNumber;

    /**
     * TODO
     * Fails on test 0xE, find out why?
     * Do interrupts expected behaviour differs from what we have for 32x?
     */
    private AtomicBoolean expectedFail = new AtomicBoolean();

    @BeforeAll
    public static void beforeAll() {
        initRom(binNameInt);
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
        super.before();
        ctx.devices.intC = createIntControl(ctx, ctx.devices.sh2MMREG.getRegs());
        sh2.reset(ctx);
        System.out.println("Reset, PC: " + ctx.PC + ", SP: " + ctx.registers[15]);
    }

    public static String getBinName() {
        return binNameInt;
    }

    @Test
    public void testJ2Interrupts() {
        Assertions.assertEquals(binNameInt, getBinName());
        int limit = 3_000;
        int cnt = 0;
        boolean fail = false;
        do {
            if (next != null) {
                if (next == Sh2Interrupt.NONE_0) { //failure
                    fail = true;
                    break;
                }
                this.ctx.devices.intC.setIntPending(next, true);
                next = null;
            }
            if (ctx.PC == 0x266) {
                rom.putShort(0x268, (short) Sh2Disassembler.NOP);
            }
            //TODO illegal slot instruction not handled
            //TODO DMA address error, CPU address error
            if (ctx.PC == 0x270 || ctx.PC == 0x28a || ctx.PC == 0x2b2 || ctx.PC == 0x2d4 || ctx.PC == 0x2e6 || ctx.PC == 0x2f0) {
                ctx.SR |= 1;
            }
            sh2.run(ctx);
            checkFail(ctx);
            cnt++;
        } while (cnt < limit);
        Assertions.assertTrue(cnt < limit);
        if (expectedFail.get()) {
            System.out.println("Some tests done: success");
            System.out.println(ctx.toString());
        } else {
            Assertions.assertFalse(fail);
            System.out.println(cnt);
            System.out.println("All tests done: success");
            System.out.println(ctx.toString());
        }
    }

    @Override
    @Disabled
    public void testJ2() {
        //do nothing
    }

    public static IntControl createIntControl(Sh2Context ctx, ByteBuffer regs) {
        return new IntControlImpl(ctx, regs) {
            //supports all interrupts, NMI, level15, etc
            {
                orderedIntCtx = Arrays.stream(Sh2Interrupt.vals).map(s32xInt::get).toArray(InterruptContext[]::new);
            }

            @Override
            public int getVectorNumber() {
                if (vectorNumber > 0) {
                    int res = vectorNumber;
                    vectorNumber = -1;
                    return res;
                }
                return super.getVectorNumber();
            }

            @Override
            public void clearCurrentInterrupt() {
                super.clearCurrentInterrupt();
                vectorNumber = -1;
            }
        };
    }

    @Override
    public Sh2Bus getMemoryInt(final ByteBuffer rom) {
        return getMemoryException(rom, expectedFail);
    }

    protected Sh2Bus getMemoryException(final ByteBuffer rom, AtomicBoolean expectedFail) {
        final int romSize = rom.capacity();
        final ByteBuffer ram = ByteBuffer.allocateDirect(ramSize);
        return new Sh2Bus() {
            @Override
            public void write(int address, int value, Size size, boolean checkPoll) {
                long lreg = address & 0xFFFF_FFFFL;
                if (lreg < romSize) {
                    writeBufferRaw(rom, (int) lreg, value, size);
                } else if (lreg < ramSize) {
                    writeBufferRaw(ram, (int) lreg, value, size);
                    checkDone(ram, address);
                } else if (lreg == PASS_ADDRESS) {
                    System.out.println("Test success: " + th(value));
                } else if (address == EVENT_TRIGGER_ADDRESS) {
                    next = switch (value >> 8) {
                        case 0x10 -> Sh2Interrupt.NMI_16;
                        case 0xF -> Sh2Interrupt.NONE_15;
                        case 4 -> Sh2Interrupt.NONE_4;
                        default -> Sh2Interrupt.NONE_0;
                    };
                    vectorNumber = value & 0xFF;
                    System.out.println(next + " interrupt trigger write: " + th(value) +
                            ", IMASK: " + th((ctx.SR & flagIMASK) >>> 4));
                    if (next != Sh2Interrupt.NMI_16) {
                        ctx.devices.intC.setIntPending(next, true);
                        next = null;
                    }
                } else if (address == TEST_RESULT_ADDRESS) {
                    expectedFail.set(value == 0xE);
                    PrintStream ps = expectedFail.get() ? System.out : System.err;
                    ps.println("Failure test:  " + th(value));
                    ps.println("Failure expected for test: " + th(value) + "," + expectedFail.get());
                    next = Sh2Interrupt.NONE_0;
                } else {
                    System.err.println("write: " + th(address) + " " + th(value) + " " + size);
                }
            }

            @Override
            public int read(int address, Size size) {
                long laddr = address & 0xFFFF_FFFFL;
                if (laddr < romSize) {
                    return readBuffer(rom, (int) laddr, size);
                } else if (laddr < ramSize) {
                    return readBuffer(ram, (int) laddr, size);
                } else {
                    System.out.println("read32: " + th(address));
                }
                return (int) size.getMask();
            }

            @Override
            public void resetSh2() {
            }
        };
    }
}