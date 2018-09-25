/* Auto-generated file. Do not modify. */
package omegadrive.z80.disasm;

import emulib.plugins.cpu.DecodedInstruction;
import emulib.plugins.cpu.Decoder;
import emulib.plugins.cpu.DisassembledInstruction;
import emulib.plugins.cpu.Disassembler;
import emulib.plugins.memory.MemoryContext;
import emulib.runtime.RadixUtils;
import emulib.runtime.exceptions.InvalidInstructionException;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.UnaryOperator;

import static omegadrive.z80.disasm.Z80Decoder.*;

/**
 * The disassembler implementation.
 */
public class Z80Disasm implements Disassembler {
    /**
     * An instruction mnemonic format string with associated parameters.
     */
    private static class MnemonicFormat {
        private final String format;
        private final Parameter[] parameters;

        public MnemonicFormat(String format, Parameter[] parameters) {
            this.format = format;
            this.parameters = parameters;
        }

        public String getFormat() {
            return format;
        }

        public Parameter[] getParameters() {
            return parameters;
        }
    }

    /**
     * A parameter of a format (a rule and a constant-decoding strategy).
     */
    private static class Parameter {
        private final int ruleCode;
        private final UnaryOperator<byte[]> strategy;

        public Parameter(int ruleCode, UnaryOperator<byte[]> strategy) {
            this.ruleCode = ruleCode;
            this.strategy = strategy;
        }

        public int getRuleCode() {
            return ruleCode;
        }

        public UnaryOperator<byte[]> getStrategy() {
            return strategy;
        }
    }

    /**
     * A class with constant-decoding strategies.
     * <p>
     * If the value represents a number, a decoding method should return bytes in
     * the big-endian order since they will be used in Java methods accepting big endian.
     */
    private static class Strategy {
        public static byte[] little_endian(byte[] value) {
            byte[] result = new byte[value.length];

            for (int i = 0; i < result.length; i++) {
                result[i] = value[value.length - i - 1];
            }

            return result;
        }

        public static byte[] big_endian(byte[] value) {
            return value;
        }

        public static byte[] bit_reverse(byte[] value) {
            byte[] result = new byte[value.length];

            for (int octet = 0; octet < result.length; octet++) {
                for (int bit = 0; bit < 8; bit++) {
                    result[octet] |= (value[octet] & (1 << bit)) >>> bit << (8 - bit - 1);
                }
            }

            return result;
        }
    }

    private static final Map<Set<Integer>, MnemonicFormat> formatMap;
    private final MemoryContext memory;
    private final Decoder decoder;

    static {
        String[] formats = {
                "%s %s, %s",
                "%s %s, %s",
                "%s %s, %s",
                "%s (HL), %s",
                "%s (HL), %s",
                "%s (HL), %s",
                "%s A,(%X)",
                "%s (%X),A",
                "%s A, (%s)",
                "%s (%s), A",
                "%s %s, %X",
                "%s %s, %X",
                "%s (%X),HL",
                "%s HL,(%X)",
                "%s %s, %X",
                "%s (%X),A",
                "%s %s",
                "%s %X",
                "%s %s",
                "%s %s",
                "%s %s",
                "%s %s",
                "%s %X",
                "%s %s,(IX+%X)",
                "%s %s,(IX+%X)",
                "%s A,(IX+%X)",
                "%s (IX+%X),%s",
                "%s (IX+%X),%s",
                "%s (IX+%X),A",
                "%s (IX+%X),%X",
                "%s (IX+%X)",
                "%s (IX+%X)",
                "%s IX,%s",
                "%s IX,%X",
                "%s IX,(%X)",
                "%s (%X),IX",
                "%s %d,(IX+%X)",
                "%s",
                "%s %s,(IY+%X)",
                "%s %s,(IY+%X)",
                "%s A,(IY+%X)",
                "%s (IY+%X),%s",
                "%s (IY+%X),%s",
                "%s (IY+%X),A",
                "%s (IY+%X),%X",
                "%s (IY+%X)",
                "%s (IY+%X)",
                "%s IY,%s",
                "%s IY,%X",
                "%s IY,(%X)",
                "%s (%X),IY",
                "%s %d,(IY+%X)",
                "%s",
                "%s %s",
                "%s %d, %s",
                "%s %s,%X",
                "%s %s,%X",
                "%s (%X),%s",
                "%s (%X),%s",
                "%s%s",
                "%s%s",
                "%s %s,(C)",
                "%s (C),%s",
                "%s",
                "%s"

        };

        Parameter[][] parameters = {
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(REG_BCDE, Strategy::little_endian),
                        new Parameter(REG, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(REG_HL, Strategy::little_endian),
                        new Parameter(REG, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(REG_A, Strategy::little_endian),
                        new Parameter(REG, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(REG_BCDE, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(REG_HL, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(REG_A, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(IMM16_LDA, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(IMM16_STA, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(BCDE_LDAX, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(BCDE_STAX, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(BCDE, Strategy::little_endian),
                        new Parameter(IMM16, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(HLSP, Strategy::little_endian),
                        new Parameter(IMM16, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(IMM16_SHLD, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(IMM16_LHLD, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(REG, Strategy::little_endian),
                        new Parameter(IMM8, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(IMM8_OUT, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(NUMBER, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(ADDRESS, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(REG, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(HLPSW, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(HLSP, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(BCDE, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian),
                        new Parameter(IMM8, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(REG_BCDE, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(REG_HL, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(REG_A, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian),
                        new Parameter(REG_BCDE, Strategy::little_endian),
                        new Parameter(M, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian),
                        new Parameter(REG_HL, Strategy::little_endian),
                        new Parameter(M, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian),
                        new Parameter(REG_A, Strategy::little_endian),
                        new Parameter(M, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian),
                        new Parameter(IMM8, Strategy::little_endian)},
                {new Parameter(DDCBINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(BCDEIXSP, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(IMM16, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(ADDRESS, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian),
                        new Parameter(ADDRESS_LDIX, Strategy::little_endian)},
                {new Parameter(DDCBINSTR, Strategy::little_endian),
                        new Parameter(BIT, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(DDINSTR, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(REG_BCDE, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(REG_HL, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(REG_A, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian),
                        new Parameter(REG_BCDE, Strategy::little_endian),
                        new Parameter(M, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian),
                        new Parameter(REG_HL, Strategy::little_endian),
                        new Parameter(M, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian),
                        new Parameter(REG_A, Strategy::little_endian),
                        new Parameter(M, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian),
                        new Parameter(IMM8, Strategy::little_endian)},
                {new Parameter(FDCBINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(BCDEIYSP, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(IMM16, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(ADDRESS, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian),
                        new Parameter(ADDRESS_LDIX, Strategy::little_endian)},
                {new Parameter(FDCBINSTR, Strategy::little_endian),
                        new Parameter(BIT, Strategy::little_endian),
                        new Parameter(INDEX, Strategy::little_endian)},
                {new Parameter(FDINSTR, Strategy::little_endian)},
                {new Parameter(CBINSTR, Strategy::little_endian),
                        new Parameter(REG, Strategy::little_endian)},
                {new Parameter(CBINSTR, Strategy::little_endian),
                        new Parameter(BIT, Strategy::little_endian),
                        new Parameter(REG, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian),
                        new Parameter(BCDE, Strategy::little_endian),
                        new Parameter(IMM16, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian),
                        new Parameter(HLSP, Strategy::little_endian),
                        new Parameter(IMM16, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian),
                        new Parameter(ADDRESS, Strategy::little_endian),
                        new Parameter(BCDE, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian),
                        new Parameter(ADDRESS, Strategy::little_endian),
                        new Parameter(HLSP, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian),
                        new Parameter(BCDE, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian),
                        new Parameter(HLSP, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian),
                        new Parameter(REG, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian),
                        new Parameter(REG_OUT, Strategy::little_endian)},
                {new Parameter(EDINSTR, Strategy::little_endian)},
                {new Parameter(INSTRUCTION, Strategy::little_endian)}
        };

        formatMap = new HashMap<Set<Integer>, MnemonicFormat>();

        for (int i = 0; i < formats.length; i++) {
            Set<Integer> ruleCodes = new HashSet<Integer>();

            for (Parameter parameter : parameters[i]) {
                ruleCodes.add(parameter.getRuleCode());
            }

            formatMap.put(ruleCodes, new MnemonicFormat(formats[i], parameters[i]));
        }
    }

    /**
     * The constructor.
     *
     * @param memory  the memory context which will be used to read instructions
     * @param decoder the decoder to use to decode instructions
     */
    public Z80Disasm(MemoryContext memory, Decoder decoder) {
        this.decoder = Objects.requireNonNull(decoder);
        this.memory = Objects.requireNonNull(memory);
    }

    /**
     * Disassembles an instruction.
     *
     * @param memoryPosition the starting address of the instruction
     * @return the disassembled instruction
     */
    @Override
    public DisassembledInstruction disassemble(int memoryPosition) {
        String mnemonic;
        String code;

        try {
            DecodedInstruction instruction = decoder.decode(memoryPosition);
            MnemonicFormat format = formatMap.get(instruction.getKeys());

            if (format == null) {
                mnemonic = "undisassemblable";
            } else {
                mnemonic = createMnemonic(instruction, format);
            }

            StringBuilder codeBuilder = new StringBuilder();

            for (byte number : instruction.getImage()) {
                codeBuilder.append(String.format("%02X ", number));
            }

            code = codeBuilder.toString();
        } catch (InvalidInstructionException ex) {
            mnemonic = "unknown";
            code = String.format("%02X", memory.read(memoryPosition));
        }

        return new DisassembledInstruction(memoryPosition, mnemonic, code);
    }

    /**
     * Returns an address of the instruction located right after the current
     * instruction.
     *
     * @param memoryPosition the starting address of the current instruction
     * @return the starting address of the next instruction
     */
    @Override
    public int getNextInstructionPosition(int memoryPosition) {
        try {
            return memoryPosition + decoder.decode(memoryPosition).getLength();
        } catch (InvalidInstructionException ex) {
            return memoryPosition + 1;
        }
    }

    /**
     * Returns the instruction mnemonic.
     *
     * @param instruction the decoded instruction
     * @param format      the formatting string + rule codes
     * @return the instruction mnemonic
     */
    private String createMnemonic(DecodedInstruction instruction, MnemonicFormat format) {
        StringBuilder mnemonic = new StringBuilder(format.getFormat());
        int position = 0;

        for (Parameter parameter : format.getParameters()) {
            position = mnemonic.indexOf("%", position);
            if (position == -1 || position == mnemonic.length()) {
                break;
            }

            byte[] value = instruction.getBits(parameter.getRuleCode());
            if (value != null) {
                value = parameter.getStrategy().apply(value);
            } else {
                value = instruction.getString(parameter.getRuleCode()).getBytes();
            }

            String replaced = format(mnemonic.charAt(position + 1), value);
            mnemonic.replace(position, position += 2, replaced);
        }

        return mnemonic.toString();
    }

    /**
     * Transforms the bytes into a meaningful string using the formatting
     * character.
     *
     * @param format the formatting character ('s' for a string, etc.)
     * @param value  the array of bytes
     * @return the resulting string
     */
    private String format(char format, byte[] value) {
        switch (format) {
            case 'c':
                String string = new String(value);
                return (string.length() != 0) ? string.substring(0, 1) : "?";
            case 'd':
                return new BigInteger(value).toString();
            case 'f':
                switch (value.length) {
                    case 4:
                        return Float.toString(ByteBuffer.wrap(value).getFloat());
                    case 8:
                        return Double.toString(ByteBuffer.wrap(value).getDouble());
                    default:
                        return "NaN";
                }
            case 's':
                return new String(value);
            case 'x':
                return RadixUtils.convertToRadix(value, 16, false).toLowerCase();
            case 'X':
                return RadixUtils.convertToRadix(value, 16, false);
            case '%':
                return "%";
            default:
                return Character.toString(format);
        }
    }
}
