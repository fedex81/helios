package omegadrive.m68k;

import m68k.cpu.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class TasEx implements InstructionHandler {

    protected final Cpu cpu;

    public TasEx(Cpu cpu) {
        this.cpu = cpu;
    }

    public final void register(InstructionSet is) {
        int base = 0x4ac0;
        Instruction i = new Instruction() {
            public int execute(int opcode) {
                return tas(opcode);
            }

            public DisassembledInstruction disassemble(int address, int opcode) {
                return disassembleOp(address, opcode, Size.Byte);
            }
        };

        for (int ea_mode = 0; ea_mode < 8; ea_mode++) {
            if (ea_mode == 1)
                continue;

            for (int ea_reg = 0; ea_reg < 8; ea_reg++) {
                if (ea_mode == 7 && ea_reg > 1)
                    break;
                is.addInstruction(base + (ea_mode << 3) + ea_reg, i);
            }
        }
    }

    protected synchronized final int tas(int opcode) {
        //TODO: this is for multi-processor systems and provides an atomic read-modify-write - this isn't handled at the moment

        int mode = (opcode >> 3) & 0x07;
        int reg = (opcode & 0x07);
        Operand op = cpu.resolveSrcEA(mode, reg, Size.Byte);
        int v = op.getByte();

        if (v == 0) {
            cpu.setFlags(Cpu.Z_FLAG);
        } else {
            cpu.clrFlags(Cpu.Z_FLAG);
        }
        if ((v & 0x080) != 0) {
            cpu.setFlags(Cpu.N_FLAG);
        } else {
            cpu.clrFlags(Cpu.N_FLAG);
        }
        cpu.clrFlags(Cpu.C_FLAG | Cpu.V_FLAG);

        //NOTE: MD1 doesnt do the write
//            op.setByte(v | 0x80);

        return (op.isRegisterMode() ? 4 : 14 + op.getTiming());
    }

    protected final DisassembledInstruction disassembleOp(int address, int opcode, Size sz) {
        DisassembledOperand op = cpu.disassembleSrcEA(address + 2, (opcode >> 3) & 0x07, (opcode & 0x07), sz);
        return new DisassembledInstruction(address, opcode, "tas", op);
    }

}
