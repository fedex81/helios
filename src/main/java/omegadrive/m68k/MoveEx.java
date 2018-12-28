package omegadrive.m68k;

import m68k.cpu.*;
import omegadrive.bus.BusProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 * //            http://www.atari-forum.com/viewtopic.php?f=115&t=31029&start=400
 * //https://www.retrodev.com/repos/blastem/rev/771875b5f519?revcount=40
 */
public class MoveEx implements InstructionHandler {

    private static Logger LOG = LogManager.getLogger(MoveEx.class.getSimpleName());

    static final int[][] ShortExecutionTime = new int[][]{{4, 4, 8, 8, 8, 12, 14, 12, 16}, {4, 4, 8, 8, 8, 12, 14, 12, 16}, {8, 8, 12, 12, 12, 16, 18, 16, 20}, {8, 8, 12, 12, 12, 16, 18, 16, 20}, {10, 10, 14, 14, 14, 18, 20, 18, 22}, {12, 12, 16, 16, 16, 20, 22, 20, 24}, {14, 14, 18, 18, 18, 22, 24, 22, 26}, {12, 12, 16, 16, 16, 20, 22, 20, 24}, {16, 16, 20, 20, 20, 24, 26, 24, 28}, {12, 12, 16, 16, 16, 20, 22, 20, 24}, {14, 14, 18, 18, 18, 22, 24, 22, 26}, {8, 8, 12, 12, 12, 16, 18, 16, 20}};
    static final int[][] LongExecutionTime = new int[][]{{4, 4, 12, 12, 12, 16, 18, 16, 20}, {4, 4, 12, 12, 12, 16, 18, 16, 20}, {12, 12, 20, 20, 20, 24, 26, 24, 28}, {12, 12, 20, 20, 20, 24, 26, 24, 28}, {14, 14, 22, 22, 22, 26, 28, 26, 30}, {16, 16, 24, 24, 24, 28, 30, 28, 32}, {18, 18, 26, 26, 26, 30, 32, 30, 34}, {16, 16, 24, 24, 24, 28, 30, 28, 32}, {20, 20, 28, 28, 28, 32, 34, 32, 36}, {16, 16, 24, 24, 24, 28, 30, 28, 32}, {18, 18, 26, 26, 26, 30, 32, 30, 34}, {12, 12, 20, 20, 20, 24, 26, 24, 28}};

    private Cpu cpu;

    public MoveEx(Cpu cpu) {
        this.cpu = cpu;
    }


    @Override
    public void register(InstructionSet is) {
        for (int sz = 0; sz < 3; ++sz) {
            short base;
            Instruction i;
            if (sz == 0) {
                base = 4096;
                i = new Instruction() {
                    public int execute(int opcode) {
                        return MoveEx.this.move_byte(opcode);
                    }

                    public DisassembledInstruction disassemble(int address, int opcode) {
                        return MoveEx.this.disassembleOp(address, opcode, Size.Byte);
                    }
                };
            } else if (sz == 1) {
                base = 12288;
                i = new Instruction() {
                    public int execute(int opcode) {
                        return MoveEx.this.move_word(opcode);
                    }

                    public DisassembledInstruction disassemble(int address, int opcode) {
                        return MoveEx.this.disassembleOp(address, opcode, Size.Word);
                    }
                };
            } else {
                base = 8192;
                i = new Instruction() {
                    public int execute(int opcode) {
                        return MoveEx.this.move_long(opcode);
                    }

                    public DisassembledInstruction disassemble(int address, int opcode) {
                        return MoveEx.this.disassembleOp(address, opcode, Size.Long);
                    }
                };
            }

            for (int sea_mode = 0; sea_mode < 8; ++sea_mode) {
                for (int sea_reg = 0; sea_reg < 8 && (sea_mode != 7 || sea_reg <= 4); ++sea_reg) {
                    for (int dea_mode = 0; dea_mode < 8; ++dea_mode) {
                        if (dea_mode != 1) {
                            for (int dea_reg = 0; dea_reg < 8 && (dea_mode != 7 || dea_reg <= 1); ++dea_reg) {
                                is.addInstruction(base + (dea_reg << 9) + (dea_mode << 6) + (sea_mode << 3) + sea_reg, i);
                            }
                        }
                    }
                }
            }
        }
    }

    protected final int move_byte(int opcode) {
        Operand src = this.cpu.resolveSrcEA(opcode >> 3 & 7, opcode & 7, Size.Byte);
        Operand dst = this.cpu.resolveDstEA(opcode >> 6 & 7, opcode >> 9 & 7, Size.Byte);
        int s = src.getByte();
        dst.setByte(s);
        this.cpu.calcFlags(InstructionType.MOVE, s, s, s, Size.Byte);
        return ShortExecutionTime[src.index()][dst.index()];
    }

    protected final int move_word(int opcode) {
        Operand src = this.cpu.resolveSrcEA(opcode >> 3 & 7, opcode & 7, Size.Word);
        Operand dst = this.cpu.resolveDstEA(opcode >> 6 & 7, opcode >> 9 & 7, Size.Word);
        int s = src.getWord();
        dst.setWord(s);
        this.cpu.calcFlags(InstructionType.MOVE, s, s, s, Size.Word);
        return ShortExecutionTime[src.index()][dst.index()];
    }

    protected final int move_long(int opcode) {
        Operand src = this.cpu.resolveSrcEA(opcode >> 3 & 7, opcode & 7, Size.Long);
        int srcMode = opcode >> 3 & 7;
        int dstMode = opcode >> 6 & 7;
        Operand dst = this.cpu.resolveDstEA(dstMode, opcode >> 9 & 7, Size.Long);

        boolean swapBytes = dstMode == 4;
        int s = src.getLong();
        if (swapBytes) {
            int lsw = s & 0xFFFF;
            int msw = (s >> 16) & 0xFFFF;
            long dstAddr = dst.getComputedAddress() & 0xFF_FFFF;
            boolean vdpWrite = dstAddr >= BusProvider.VDP_ADDRESS_SPACE_START &&
                    dstAddr < BusProvider.VDP_ADDRESS_SPACE_END;
            if (vdpWrite) {
                StringBuilder sb = new StringBuilder();
                disassembleOp(0, opcode, Size.Long).shortFormat(sb);
                LOG.warn("{}, move.l swap bytes, destAddress: {}, data: {}", sb, Long.toHexString(dstAddr), Long.toHexString(s));
                dst.setWord(lsw);
                dst.setWord(msw);
            } else {
                dst.setLong(s);
            }
        } else {
            dst.setLong(s);
        }
        this.cpu.calcFlags(InstructionType.MOVE, s, s, s, Size.Long);
        return LongExecutionTime[src.index()][dst.index()];
    }

    protected final DisassembledInstruction disassembleOp(int address, int opcode, Size sz) {
        DisassembledOperand src = this.cpu.disassembleSrcEA(address + 2, opcode >> 3 & 7, opcode & 7, sz);
        DisassembledOperand dst = this.cpu.disassembleDstEA(address + 2 + src.bytes, opcode >> 6 & 7, opcode >> 9 & 7, sz);
        return new DisassembledInstruction(address, opcode, "move" + sz.ext(), src, dst);
    }
}
