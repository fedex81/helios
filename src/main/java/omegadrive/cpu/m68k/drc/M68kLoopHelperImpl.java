package omegadrive.cpu.m68k.drc;

import com.google.common.annotations.VisibleForTesting;
import m68k.cpu.Cpu;
import m68k.cpu.DisassembledInstruction;
import m68k.cpu.M68kSimpleInst;
import omegadrive.SystemLoader.SystemType;
import omegadrive.cpu.CpuBusyLoopDetection;
import omegadrive.cpu.m68k.drc.M68kOpcodeSpecHelper.M68kOpcodeSpec;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.BufferUtil;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static omegadrive.cpu.m68k.drc.M68kOpcodeSpecHelper.M68kOpcodeSpec.find;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2026
 */
public class M68kLoopHelperImpl implements M68kLoopHelper {

    private final static Logger LOG = LogHelper.getLogger(M68kLoopHelperImpl.class.getSimpleName());

    private final int[] opcodeTemp = new int[BLOCK_MAX_LEN];

    private final M68kSimpleInst[] instTemp = new M68kSimpleInst[MAX_INST_PER_BLOCK];

    private final Cpu cpu;
    private final ReadableByteMemory bus;

    private final SystemType st;

    private final M68kBlock[] blocks;

    private final int blocksLenMask;

    private M68kBlock block;

    public M68kLoopHelperImpl(SystemType st, Cpu m68k, ReadableByteMemory bus) {
        this(st, m68k, bus, NUM_BLOCKS);
    }

    @VisibleForTesting
    public M68kLoopHelperImpl(SystemType st, Cpu m68k, ReadableByteMemory bus, int numBlocks) {
        assert st.isMdBased() : st;
        cpu = m68k;
        M68kOpcodeSpecHelper.generateOnce(cpu);
        this.bus = bus;
        this.st = st;
        blocks = new M68kBlock[numBlocks];
        this.blocksLenMask = numBlocks - 1;
        reset();
    }

    private int readWord(int addr) {
        return bus.read(addr, Size.WORD);
    }

    private M68kSimpleInst getSimpleInst(int idx, int op) {
        DisassembledInstruction di = null;
        var inst = cpu.getInstructionFor(op);
        try {
            di = inst.disassemble(idx, op);
        } catch (IllegalArgumentException ignored) {
            LogHelper.logWarnOnce(LOG, "Unable to disasm opcode: {}, {}", th(op), inst);
        }
        return di;
    }

    private M68kBlock getBlockFromPc(int pc) {
        return blocks[pc >> 1];
    }

    private void setBlockAtPc(int pc, M68kBlock block) {
        blocks[pc >> 1] = block;
    }

    private M68kBlock getBlock(int pc) {
        assert pc == (pc & blocksLenMask);
        M68kBlock block = getBlockFromPc(pc);
        if (block != M68kBlock.NO_BLOCK) {
            if (BufferUtil.assertionsEnabled) {
                assert block.pc == pc;
                //TODO
//                int hc = block.len == 1 ? bus.readRamByte(pc) + 31 : BufferUtil.hashCode(bus, pc, pc + block.len);
//                assert hc == block.hash : th(pc);
            }
            return block;
        }

        block = new M68kBlock(pc);
        setBlockAtPc(pc, block);
        Arrays.fill(opcodeTemp, -1);
        Arrays.fill(instTemp, null);
        return block;
    }

    @Override
    public M68kBlock checkLoops(int pc) {
        block = getBlock(pc);
        if (block.instLen > 0) {
            return block;
        }
        M68kOpcodeSpecHelper.generateOnce(cpu);
        populateBlock(pc);
        LoopType res = analyseBlock(pc);
        if (res == LoopType.NONE) {
            block.opcodeLen = 1;
            block.instLen = 1;
        }
        block.hash = BufferUtil.hashCode(opcodeTemp, 0, block.opcodeLen);
        block.opcodes = Arrays.copyOf(opcodeTemp, block.opcodeLen);
        block.instructions = Arrays.copyOf(instTemp, block.instLen);
        block.loopType = res;
        assert Arrays.stream(block.opcodes).noneMatch(v -> v == -1) : th(pc);
        if (verbose) System.out.println("New Block: " + block);
        var b = block;
        block = null;
        return b;
    }

    private void populateBlock(int pc) {
        boolean process = true;
        int relIdx = 0;
        int instIdx = 0;
        do {
            int absIdx = pc + (relIdx << 1);
            int op = readWord(absIdx);
            process &= M68kOpcodeSpecHelper.isValidOpcodeForLooping(op);
            var di = getSimpleInst(absIdx, op);
            opcodeTemp[relIdx] = op;
            instTemp[instIdx] = di;
            block.instLen++;
            block.opcodeLen++;
            int firsInstIdx = relIdx;
            int wordsLoad = di.immSizeWords();
            relIdx++;
            instIdx++;
            var opc = find(op);
            for (int j = 1; j <= wordsLoad; j++) {
                int idx = (j << 1) + absIdx;
                op = bus.read(idx, Size.WORD);
                opcodeTemp[firsInstIdx + j] = op;
                block.opcodeLen++;
                relIdx++;
            }
            if (opc == null || opc.isBranch()) {
                break;
            }
        } while (process && instIdx < 5);
    }

    private LoopType analyseBlock(int pc) {
        LoopType res = LoopType.NONE;
        boolean process = block.instLen > 0;
        if (process) {
            int baseIdx = 0;
            boolean val = false;
            do {
                val = checkJump(pc, instTemp[baseIdx]);
                if (val) {
                    res = LoopType.BUSY_LOOP;
                    if (baseIdx == 0) {
//                        System.out.println("infLoop");
                        res = LoopType.INFINITE_LOOP;
                    }
                }
                baseIdx++;
            } while (baseIdx < block.instLen && !val);
        }
        return res;
    }

    private boolean checkJump(int pc, M68kSimpleInst di) {
        var opc2 = M68kOpcodeSpec.find(di.getOpcode());
        boolean isBranch = opc2 != null && opc2.isBranch();
        if (isBranch) {
            return pc == getJumpDestAddress(di, pc);
        }
        return false;
    }

    private int getJumpDestAddress(M68kSimpleInst di, int pc) {
        final int op2 = di.getOpcode();
        final int jmpIdx = di.getAddress();
        int jmpImmSizeWords = di.immSizeWords();
        return switch (jmpImmSizeWords) {
            case 0 -> {
                int jmpOffset = 0xFF - (op2 & 0xFF) - 1;
                yield jmpIdx - jmpOffset;
            }
            case 1 -> {         //6600 fff6               bne.w
                int immIdx = (jmpIdx + 2 - pc) >> 1;
                if (immIdx < opcodeTemp.length) {
                    int op3 = opcodeTemp[immIdx];
                    int jmpOffset = 0xFF - (op3 & 0xFF) - 1;
                    yield jmpIdx - jmpOffset;
                }
                yield -1;
            }
            case 2 -> { //4ef9 00000128           jmp      $00000128
                if (jmpIdx + 2 - pc < opcodeTemp.length) {
                    int op3 = opcodeTemp[jmpIdx + 1 - pc];
                    int op4 = opcodeTemp[jmpIdx + 2 - pc];
                    yield (op3 << 16) | (op4 & 0xFFFF);
                }
                yield -1;
            }
            default -> -1;
        };
    }

    private static Set<String> missedLoops = new HashSet<>();
    private static Set<String> dedupSet = new HashSet<>();

    public void checkMissedLoops(int loopPc, CpuBusyLoopDetection bld) {
        M68kBlock block = checkLoops(loopPc);
        LoopType lt = block.loopType;
        String str = Arrays.stream(block.instructions).filter(Objects::nonNull).
                map(di -> di.getOpcode()).sorted().map(i -> "" + i).collect(Collectors.joining(","));
        if (lt != LoopType.NONE && dedupSet.add(str)) {
            System.out.println("68k loop (loopHelper), " + block + "\n" + bld.getLoopInfo());
        }
        if (lt == LoopType.NONE) {
            if (missedLoops.add(bld.getInstListOnly())) {
                System.err.println("Missed 68k loop: " + bld.getLoopInfoVerbose());
//                LogHelper.logWarnOnce(LOG, "Missed Z80 loop: {}", bld.getLoopInfoVerbose());
            }
        } else {
//            LogHelper.logWarnOnce(LOG, bld.getLoopInfo());
        }
    }


    public void writeMemory(int address, int data) {
        if (verbose) System.out.println("writeMem: " + th(address) + ", data: " + th(data));

        if (getBlockFromPc(address) != M68kBlock.NO_BLOCK) {
            reloadBlock(address, 0, data);
        }
        int cnt = 0;
        for (int i = Math.max(0, address - BLOCK_MAX_LEN); i < address; i++) {
            var b = getBlockFromPc(i);
            if (b != M68kBlock.NO_BLOCK) {
                int startInc = b.pc;
                int endExc = b.pc + b.opcodeLen;
                if (address >= startInc && address < endExc) {
                    M68kBlock b1 = reloadBlock(i, address - i, data);
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
        if (cnt > 1) {
            System.err.println(cnt + " blocks affected, writeMem: " + th(address) + ", data: " + th(data));
//            LOG.warn(cnt + " block affected, writeMem: " + th(address) + ", data: " + th(data));
        }
    }

    public void reset() {
        Arrays.fill(blocks, M68kBlock.NO_BLOCK);
    }

    private M68kBlock reloadBlock(int address, int idx, int data) {
        M68kBlock b = getBlockFromPc(address);
        int prev = b.opcodes[idx];
        if (prev != data) {
            if (verbose)
                System.out.println("Remove block: " + b + ", writeMem: " + th(address) + ", data: " + th(data));
            setBlockAtPc(address, M68kBlock.NO_BLOCK);
            return checkLoops(b.pc);
        }
        return b;
    }
}