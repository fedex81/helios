package omegadrive.m68k;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */

import junit.framework.TestCase;
import m68k.cpu.Cpu;
import m68k.cpu.InstructionType;
import m68k.cpu.MC68000;
import m68k.cpu.Size;
import m68k.memory.AddressSpace;
import m68k.memory.MemorySpace;

public class ADDTest extends TestCase {
    AddressSpace bus;
    Cpu cpu;
    Cpu cpuFlagChange;

    public static MC68000 createCustomCpu() {
        return new MC68000() {
            @Override
            public void calcFlagsParam(InstructionType type, int src, int dst, int result, int extraParam, Size sz) {
                result = sz.byteCount() == 1 ? result & 0xFF : result;
                result = sz.byteCount() == 2 ? result & 0xFFFF : result;
                super.calcFlagsParam(type, src, dst, result, extraParam, sz);
            }
        };
    }

    public void setUp() {
        bus = new MemorySpace(1);    //create 1kb of memory for the cpu
        cpu = new MC68000();
        cpu.setAddressSpace(bus);
        cpu.reset();
        cpu.setAddrRegisterLong(7, 0x200);
        cpuFlagChange = createCustomCpu();
        cpuFlagChange.setAddressSpace(bus);
        cpuFlagChange.reset();
        cpuFlagChange.setAddrRegisterLong(7, 0x200);
    }

    public void testADD_byte_zeroFlag() {
        bus.writeWord(4, 0xd402);    // add.b d2,d2
        //this should be true as 0xFFFFFF00_byte -> 00
        testADD_byte_zeroFlag(cpu, false);

        testADD_byte_zeroFlag(cpuFlagChange, true);
    }

    public void testADD_word_zeroFlag() {
        bus.writeWord(4, 0xd442);    // add.w d2,d2
        //this should be true as 0xFFFFFF00_byte -> 00
        testADD_word_zeroFlag(cpu, false);

        testADD_word_zeroFlag(cpuFlagChange, true);
    }

    private void testADD_byte_zeroFlag(Cpu cpu, boolean expectedZFlag) {
        cpu.setPC(4);
        cpu.setDataRegisterLong(2, 0xFFFFFF80);
        cpu.execute();
        assertEquals(6, cpu.getPC());
        assertEquals(0xFFFFFF00, cpu.getDataRegisterLong(2));
        assertEquals(0x00, cpu.getDataRegisterByte(2));
        assertTrue(cpu.isFlagSet(Cpu.C_FLAG));
        assertEquals(expectedZFlag, cpu.isFlagSet(Cpu.Z_FLAG));
    }

    private void testADD_word_zeroFlag(Cpu cpu, boolean expectedZFlag) {
        cpu.setPC(4);
        cpu.setDataRegisterLong(2, 0xFFFF8000);
        cpu.execute();
        assertEquals(6, cpu.getPC());
        assertEquals(0xFFFF0000, cpu.getDataRegisterLong(2));
        assertEquals(0x0000, cpu.getDataRegisterWord(2));
        assertTrue(cpu.isFlagSet(Cpu.C_FLAG));
        assertEquals(expectedZFlag, cpu.isFlagSet(Cpu.Z_FLAG));
    }
}
