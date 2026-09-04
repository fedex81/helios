package omegadrive.cpu.z80;

import omegadrive.Device;
import omegadrive.SystemLoader.SystemType;
import omegadrive.cpu.CpuBusyLoopDetection;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.ArrayEndianUtil;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;
import z80core.Z80;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

import static omegadrive.bus.model.MdZ80BusProvider.Z80_RAM_MEMORY_SIZE;
import static omegadrive.cpu.z80.disasm.Z80OpcodeSpecHelper.Z80OpcodeSpec.*;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class Z80LoopHelper implements Device {

    private final static Logger LOG = LogHelper.getLogger(Z80LoopHelper.class.getSimpleName());

    enum LoopType {NONE, INFINITE_LOOP, BUSY_LOOP}

    static class Z80Block {
        public final int pc;
        public int len = 0;
        public int[] opcodes;
        public LoopType loopType = LoopType.NONE;
        public int hash;

        public static final Z80Block NO_BLOCK = new Z80Block(-1);

        public Z80Block(int pc) {
            this.pc = pc;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Z80Block.class.getSimpleName() + "[", "]")
                    .add("pc=" + th(pc))
                    .add("len=" + len)
                    .add("opcodes=" + Arrays.toString(opcodes))
                    .add("loopType=" + loopType)
                    .add("hash=" + hash)
                    .toString();
        }
    }

    private static final boolean verbose = false && BufferUtil.assertionsEnabled;

    private static final int BLOCK_MAX_LEN = 16;
    private static final int NUM_BLOCKS = 0x10000;


    private final int[] opcodeTemp = new int[BLOCK_MAX_LEN];

    private final Z80 z80;
    private final ReadableByteMemory bus;

    private final SystemType st;

    private final Z80Block[] blocks;

    private final int blocksLenMask;

    private Z80Block block;

    public Z80LoopHelper(SystemType st, Z80 z80Core, ReadableByteMemory bus) {
        this.z80 = z80Core;
        this.bus = bus;
        this.st = st;
        blocks = new Z80Block[st.isMdBased() ? Z80_RAM_MEMORY_SIZE : NUM_BLOCKS];
        this.blocksLenMask = blocks.length - 1;
        Arrays.fill(blocks, Z80Block.NO_BLOCK);
    }

    private int readRamByte(int addr) {
        int relAddr = addr - block.pc;
        block.len = Math.max(block.len, relAddr + 1);
        for (int i = addr; i >= block.pc; i--) {
            if (opcodeTemp[i - block.pc] < 0) {
                opcodeTemp[i - block.pc] = bus.readRamByte(i);
            }
        }
        return opcodeTemp[relAddr] & 0xFF;
    }

    private Z80Block getBlock(int pc) {
        assert pc == (pc & blocksLenMask);
        Z80Block block = blocks[pc];
        if (block != Z80Block.NO_BLOCK) {
            if (BufferUtil.assertionsEnabled) {
                assert block.pc == pc;
                int hc = block.len == 1 ? bus.readRamByte(pc) + 31 : BufferUtil.hashCode(bus, pc, pc + block.len);
                assert hc == block.hash : th(pc);
            }
            return block;
        }
        Arrays.fill(opcodeTemp, -1);
        block = new Z80Block(pc);
        blocks[pc] = block;
        return block;
    }

    public Z80Block checkLoops() {
        return checkLoops(z80.getRegPC() & blocksLenMask);
    }

    public Z80Block checkLoops(int pc) {
        block = getBlock(pc);
        if (block.len > 0) {
            return block;
        }
        var op = fromOpcode(readRamByte(pc));
        LoopType res = switch (op) {
            case OP_0x7E, OP_0x1A, OP_0x3A -> {
                boolean val = checkOpJump() || checkDoubleLoad(pc + op.getTotalWidthBytes());
                yield val ? LoopType.BUSY_LOOP : LoopType.NONE;
            }
            case OP_0x21 -> {
                boolean val = checkLogicalThenJump(pc + 6) ||
                        checkLogicalThenJump(pc + 4) ||
                        checkLogicalThenJump(pc + 3) ||
                        checkDoubleLoad(pc + 3);
                yield val ? LoopType.BUSY_LOOP : LoopType.NONE;
            }
            case OP_0x2A -> checkDoubleLoad(pc + 3) ? LoopType.BUSY_LOOP : LoopType.NONE;
            case OP_0x3E -> checkStoreAndJump(pc) ? LoopType.INFINITE_LOOP : LoopType.NONE;
            case OP_0x00 -> {
                //0000009a            00    nop
                //[...] more Nops
                //0000009b      C3 9A 00    jp $009A
                int lastNopIdx = consumeNops(pc);
                yield checkJump(lastNopIdx) ? LoopType.INFINITE_LOOP : LoopType.NONE;
            }
            //0000001e            A6    and (hl)
            //0000001f      C2 1E 00    jp nz,$001E
            case OP_0xA6, OP_0xB6, OP_0xBE, OP_0xCB ->
                    checkJump(pc + op.getTotalWidthBytes()) ? LoopType.BUSY_LOOP : LoopType.NONE;
            //00000048            76    halt
            case OP_0x76 -> LoopType.INFINITE_LOOP;
            //00000000            E9    jp (hl)
            case OP_0xE9 -> pc == z80.getRegHL() ? LoopType.INFINITE_LOOP : LoopType.NONE;
            //00000201      C3 01 02    jp $0201
            case OP_0xC3 -> checkJump(pc) ? LoopType.INFINITE_LOOP : LoopType.NONE;
            //00000003         18 FE    jr $0003
            case OP_0x18, OP_0x20 -> checkJump(pc) ? LoopType.INFINITE_LOOP : LoopType.NONE;
            default -> LoopType.NONE;
        };
        if (res == LoopType.NONE) {
            block.len = 1;
        }
        block.hash = BufferUtil.hashCode(opcodeTemp, 0, block.len);
        block.opcodes = Arrays.copyOf(opcodeTemp, block.len);
        block.loopType = res;
        if (verbose) System.out.println("New Block: " + block);
        var b = block;
        block = null;
        return b;
    }

    private boolean checkDoubleLoad(int load2Idx) {
        var op = fromOpcode(readRamByte(load2Idx));
        boolean res = false;
        if (op == OP_0x7D || op == OP_0x7C || op == OP_0x7E || op == OP_0x21) {
            int boolIdx = load2Idx + op.getTotalWidthBytes();
            res = checkLogicalThenJump(boolIdx);
        }
        return res;
    }

    private boolean checkStoreAndJump(int loadIdx) {
        var op = fromOpcode(readRamByte(loadIdx));
        boolean res = false;
        if (op == OP_0x3E) {
            int storeIdx = loadIdx + op.getTotalWidthBytes();
            var op2 = fromOpcode(readRamByte(storeIdx));
            if (op2 == OP_0x32) {
                int jumpIdx = storeIdx + op2.getTotalWidthBytes();
                res = checkJump(jumpIdx);
            }
        }
        return res;
    }

    private boolean checkOpJump() {
        boolean ok = false;
        int pc = block.pc;
        int orIdx = pc + 1;
        do {
            ok |= checkLogicalThenJump(orIdx);
            orIdx++;
        } while (!ok && orIdx - pc < 4);
        return ok;
    }

    private boolean checkLogicalThenJump(int boolIdx) {
        var op = fromOpcode(readRamByte(boolIdx));
        boolean res = false;
        int jmpIdx = boolIdx + 1;
        //ALU Immediate 1 byte
        boolean validOp = op == OP_0xE6 || op == OP_0xEE || op == OP_0xF6 || op == OP_0xFE;
        //ALU Reg
        validOp |= op == OP_0xB7 || op == OP_0xB8 || op == OP_0xB9 || op == OP_0xBA || op == OP_0xB5 || op == OP_0xBD
                || op == OP_0xA7 || op == OP_0xA2 || op == OP_0xBB || op == OP_0xBE;
        if (validOp) {
            jmpIdx += op.getImmSize();
            jmpIdx = consumeNops(jmpIdx);
            res = checkJump(jmpIdx);
            if (!res) {
                var op2 = fromOpcode(readRamByte(jmpIdx));
                //Double jump
                if (op2.isJumpOpcode()) {
                    res |= checkJump(jmpIdx + op2.getTotalWidthBytes());
                }
            }
        }
        return res;
    }

    private boolean checkJump(int firstOpcodeIdx) {
        var op = fromOpcode(readRamByte(firstOpcodeIdx));
        int b3 = readRamByte(firstOpcodeIdx + 1);
        if (op.isJumpOpcode() && op.getImmSize() == 1) {
            int pcDist = 0xFF - (firstOpcodeIdx - block.pc) - 1;
            boolean jumpOk = b3 == pcDist;
            //two jumps, the second loops back
            jumpOk |= checkSecondJump(firstOpcodeIdx + 2, pcDist);
            return jumpOk;
        } else if (op.isJumpOpcode() && op.getImmSize() == 2) {
            byte b4 = (byte) readRamByte(firstOpcodeIdx + 2);
            int dest = ArrayEndianUtil.getUShort16LE_int((byte) b3, b4);
            return dest == block.pc;
        } else {
//                        LOG.warn("{} Z80 check: {}", hits, Z80Helper.dumpInfo(z80Dasm, memIoOps, pc + 2));
//                System.out.println(Z80Helper.dumpInfo(z80Dasm, memIoOps, pc + 2));
        }
        return false;
    }

    private boolean checkSecondJump(int jumpIdx, int pcDist) {
        boolean jumpOk = false;
        var op = fromOpcode(readRamByte(jumpIdx));
        if (op.isJumpOpcode() && op.getImmSize() == 1) {
            int b5 = readRamByte(jumpIdx + 1);
            int pcDist1 = pcDist - 2;
            jumpOk = b5 == pcDist1;
        }
        return jumpOk;
    }

    private int consumeNops(int idx) {
        int start = idx;
        while (idx - start < 4 && readRamByte(idx) == OP_0x00.ordinal()) {
            idx++;
        }
        return idx;
    }

    private static final Set<String> missedLoops = new HashSet<>();

    public void checkMissedLoops(int loopPc, CpuBusyLoopDetection bld) {
        LoopType res = checkLoops(loopPc).loopType;
        if (res == LoopType.NONE) {
            if (missedLoops.add(bld.getInstListOnly())) {
                if (verbose) System.err.println("Missed Z80 loop: " + bld.getLoopInfoVerbose());
//                LogHelper.logWarnOnce(LOG, "Missed Z80 loop: {}", bld.getLoopInfoVerbose());
            }
        } else {
//            LogHelper.logWarnOnce(LOG, bld.getLoopInfo());
        }
    }

    public void writeMemory(int address, int data) {
        if (verbose) System.out.println("writeMem: " + th(address) + ", data: " + th(data));

        if (blocks[address] != Z80Block.NO_BLOCK) {
            reloadBlock(address, 0, data);
        }
        int cnt = 0;
        for (int i = Math.max(0, address - BLOCK_MAX_LEN); i < address; i++) {
            if (blocks[i] != Z80Block.NO_BLOCK) {
                var b = blocks[i];
                int startInc = b.pc;
                int endExc = b.pc + b.len;
                if (address >= startInc && address < endExc) {
                    Z80Block b1 = reloadBlock(i, address - i, data);
                    if (b != b1) {
                        cnt++;
                        if (!BufferUtil.assertionsEnabled) {
                            break;
                        }
                        LogHelper.logWarnOnce(LOG, "Slower check for further matching blocks, assertions : {}",
                                BufferUtil.assertionsEnabled);
                    }
                }
            }
        }
        /**
         * TODO test
         * 142: Shove It! - The Warehouse Game (U) [!].bin.zip
         * 2 block affected, writeMem: 6e, data: 0
         *
         * writeMem: 6e, data: 0
         * Remove block: Z80Block[pc=6e, len=1, opcodes=[195], loopType=NONE, hash=226, empty=false], writeMem: 6e, data: 0
         * New Block: Z80Block[pc=6e, len=1, opcodes=[0], loopType=NONE, hash=31, empty=false]
         * Remove block: Z80Block[pc=6d, len=4, opcodes=[0, 195, 109, 0], loopType=INFINITE_LOOP, hash=1114295, empty=false], writeMem: 6d, data: 0
         * New Block: Z80Block[pc=6d, len=1, opcodes=[0], loopType=NONE, hash=31, empty=false]
         */
        if (cnt > 1) {
            System.err.println(cnt + " blocks affected, writeMem: " + th(address) + ", data: " + th(data));
//            LOG.warn(cnt + " block affected, writeMem: " + th(address) + ", data: " + th(data));
        }
    }

    private Z80Block reloadBlock(int address, int idx, int data) {
        Z80Block b = blocks[address];
        int prev = b.opcodes[idx];
        if (prev != data) {
            if (verbose)
                System.out.println("Remove block: " + b + ", writeMem: " + th(address) + ", data: " + th(data));
            blocks[address] = Z80Block.NO_BLOCK;
            return checkLoops(b.pc);
        }
        return b;
    }

}
