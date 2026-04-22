package omegadrive.cpu.m68k.drc;

import m68k.cpu.Cpu;
import m68k.cpu.M68kSimpleInst;
import omegadrive.Device;
import omegadrive.SystemLoader.SystemType;
import omegadrive.cpu.CpuBusyLoopDetection;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.BufferUtil;

import java.util.Arrays;
import java.util.StringJoiner;

import static omegadrive.bus.model.MdMainBusProvider.ADDRESS_UPPER_LIMIT;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public interface M68kLoopHelper extends Device {

    enum LoopType {NONE, INFINITE_LOOP, BUSY_LOOP}

    int MAX_INST_PER_BLOCK = 5;
    // M68k inst can be 10 bytes long
    int BLOCK_MAX_LEN = MAX_INST_PER_BLOCK * 10;

    class M68kBlock {
        public final int pc;
        public int instLen = 0;
        public int opcodeLen = 0;
        public int[] opcodes;
        public M68kSimpleInst[] instructions;
        public LoopType loopType = LoopType.NONE;
        public int hash;

        public static final M68kBlock NO_BLOCK = new M68kBlock(-1);

        public M68kBlock(int pc) {
            this.pc = pc;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", M68kBlock.class.getSimpleName() + "[", "]")
                    .add("pc=" + th(pc))
                    .add("instLen=" + instLen)
                    .add("opcodeLen=" + opcodeLen)
                    .add("opcodes=" + Arrays.toString(opcodes))
                    .add("loopType=" + loopType)
                    .add("hash=" + hash)
                    .toString();
        }
    }

    boolean verbose = false && BufferUtil.assertionsEnabled;

    M68kLoopHelper NO_OP = new M68kLoopHelper() {
        @Override
        public M68kBlock checkLoops(int pc) {
            assert false;
            return null;
        }

        @Override
        public void checkMissedLoops(int pc, CpuBusyLoopDetection busyLoopDetect) {
            assert false;
        }
    };


    //0x100_0000 >> 1, M68k only uses even PCs
    //TODO this should be refined by SystemType, we don't need the entire range.
    int NUM_BLOCKS = (ADDRESS_UPPER_LIMIT + 1) >> 1;

    static M68kLoopHelper createInstance(SystemType st, Cpu p, ReadableByteMemory m, boolean enabled) {
        return enabled ? new M68kLoopHelperImpl(st, p, m) : NO_OP;
    }

    M68kBlock checkLoops(int pc);

    void checkMissedLoops(int pc, CpuBusyLoopDetection busyLoopDetect);
}