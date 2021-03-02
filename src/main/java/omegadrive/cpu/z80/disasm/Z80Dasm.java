package omegadrive.cpu.z80.disasm;
// license:BSD-3-Clause
// copyright-holders:Juergen Buchmueller

/**
 * Java translation
 * Federico Berti 2020
 */
import z80core.IMemIoOps;

import java.util.Arrays;

import static omegadrive.cpu.z80.disasm.Z80DasmIntf.e_mnemonics.zDB;


public class Z80Dasm extends Z80DasmIntf {


	private int[] opcodes = new int[5];

	private static String toOpcodesStr(int[] opcodes) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < opcodes.length; i++) {
			if (opcodes[i] == -1) {
				break;
			}
			sb.append(String.format("%02X", opcodes[i])).append(" ");
		}
		return sb.toString();
	}

	char sign(int offset) {
		return (offset < 0) ? '-' : '+';
	}

	int offs(int offset) {
		if (offset < 0)
			return -offset;
		return offset;
	}

	public String disassemble(int pc, IMemIoOps memIoOps) {
		int offset = 0, opIdx = 0;
		StringBuilder stream = new StringBuilder();
		Arrays.fill(opcodes, -1);
		int pos = pc;
		String ixy = "oops!!";

		z80dasmStruct d = null;
		int op = memIoOps.peek8(pos++);
		opcodes[opIdx++] = op;
		switch (op) {
			case 0xcb:
				op = memIoOps.peek8(pos++);
				opcodes[opIdx++] = op;
				d = mnemonic_cb[op];
				break;
			case 0xed:
				op = memIoOps.peek8(pos++);
				opcodes[opIdx++] = op;
				d = mnemonic_ed[op];
				if (d.mnemonic == zDB)
					pos--;
				break;
			case 0xdd: {
				ixy = "ix";
				int op1 = memIoOps.peek8(pos++);
				opcodes[opIdx++] = op1;
				if (op1 == 0xcb) {
					offset = memIoOps.peek8(pos++);
					op1 = memIoOps.peek8(pos++);
					opcodes[opIdx++] = op1;
					d = mnemonic_xx_cb[op1];
				} else {
					d = mnemonic_xx[op1];
					if (d.mnemonic == zDB)
						pos--;
				}
				break;
			}
			case 0xfd: {
				ixy = "iy";
				int op1 = memIoOps.peek8(pos++);
				opcodes[opIdx++] = op1;
				if (op1 == 0xcb) {
					offset = memIoOps.peek8(pos++);
					op1 = memIoOps.peek8(pos++);
					opcodes[opIdx++] = op1;
					d = mnemonic_xx_cb[op1];
				} else {
					d = mnemonic_xx[op1];
					if (d.mnemonic == zDB)
						pos--;
				}
				break;
			}
			default:
				d = mnemonic_main[op];
				break;
		}

		if (d.arguments != null) {
			stream.append(String.format("%-1s ", s_mnemonic[d.mnemonic.ordinal()]));
			char[] src = d.arguments.toCharArray();
			int i = 0;
			int data;
			while (i < src.length) {
				switch (src[i]) {
					case '?':   /* illegal opcode */
						stream.append(String.format("$%02x", op));
						break;
					case 'B':   /* Byte op arg */
					case 'P':   /* Port number */
						data = memIoOps.peek8(pos++);
						opcodes[opIdx++] = data & 0xFF;
						stream.append(String.format("$%02X", data));
						break;
					case 'A':
					case 'N':   /* Immediate 16 bit */
					case 'W':   /* Memory address word */
						data = memIoOps.peek16(pos);
						opcodes[opIdx++] = data & 0xFF;
						opcodes[opIdx++] = (data >> 8) & 0xFF;
						stream.append(String.format("$%04X", data));
						pos += 2;
						break;
					case 'O':   /* Offset relative to PC */
						data = memIoOps.peek8(pos++);
						opcodes[opIdx++] = data & 0xFF;
						stream.append(String.format("$%04X", (pc + (byte) data + 2) & 0xffff));
//				(pc + s8(params[pos++] & 0xFF) + 2) & 0xffff));
						break;
					case 'V':   /* Restart vector */
						stream.append(String.format("$%02X", op & 0x38));
						break;
					case 'X':
						offset = memIoOps.peek8(pos++);
						opcodes[opIdx++] = offset & 0xFF;
						/* fall through */
					case 'Y':
						stream.append(String.format("(%s%c$%02x)", ixy, sign(offset), offs(offset)));
						break;
					case 'I':
						stream.append(String.format("%s", ixy));
						break;
					default:
						stream.append(src[i]);
				}
				i++;
			}
		} else {
			stream.append(String.format("%s", s_mnemonic[d.mnemonic.ordinal()]));
		}
		return String.format("%08x   %12s   %s", pc, toOpcodesStr(opcodes), stream.toString());
	}
}


