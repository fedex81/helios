package omegadrive.m68k;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * 9402 sub.b
 * 0402 subi.b
 * 5502 subq.b subi.b
 * <p>
 * 9442 sub.w
 * 0442 subi.w
 * 5542 subq.w subi.w
 */

import junit.framework.TestCase;
import m68k.cpu.Cpu;
import m68k.cpu.MC68000;
import m68k.memory.AddressSpace;
import m68k.memory.MemorySpace;

public class SUBTest extends TestCase {
    AddressSpace bus;
    Cpu cpu;

    public void setUp() {
        bus = new MemorySpace(1);    //create 1kb of memory for the cpu
        cpu = new MC68000();
        cpu.setAddressSpace(bus);
        cpu.reset();
        cpu.setAddrRegisterLong(7, 0x200);
    }

    public void testSUB_byte_zeroFlag() {
        bus.writeWord(4, 0x9402);    // sub.b d2,d2
        testSUB_byte_zeroFlag(cpu, true, 0xFFFF_FF80, 0xFFFF_FF00);
    }

    public void testSUBQ_byte_zeroFlag() {
        bus.writeWord(4, 0x5502);    // subi.b #2,d2
//        bus.writeWord(6, 2);
        testSUB_byte_zeroFlag(cpu, true, 0x0001_0102, 0x0001_0100);
    }

    public void testSUBI_byte_zeroFlag() {
        bus.writeWord(4, 0x0402);    // subi.b #2,d2
        bus.writeWord(6, 2);
        testSUB_byte_zeroFlag(cpu, true, 0x0001_0102, 0x0001_0100);
    }

    private void testSUB_byte_zeroFlag(Cpu cpu, boolean expectedZFlag, long d2_pre, long d2_post) {
        cpu.setPC(4);
        cpu.setDataRegisterLong(2, (int) d2_pre);
        cpu.execute();
        assertEquals(d2_post, cpu.getDataRegisterLong(2));
        assertEquals(0x00, cpu.getDataRegisterByte(2));
        assertEquals(expectedZFlag, cpu.isFlagSet(Cpu.Z_FLAG));
    }
}
