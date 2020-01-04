package omegadrive.z80.disasm;

import omegadrive.util.FileLoader;
import omegadrive.z80.Z80Helper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Z80DecoderExt
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class Z80DecoderExt {

    public static final String UNKNOWN = "unknown";
    public final static Map<Integer, String> opcodeStringMap = new HashMap<>();
    public final static Map<Integer, Integer> opcodeToOpcodeMap = new HashMap<>();
    private final static Logger LOG = LogManager.getLogger(Z80Helper.class.getSimpleName());
    private final static String datFolder = "./res";
    private final static String datFilename = "Z80Opcodes.dat";

    static {
        try {
            List<String> l = FileLoader.readFileContent(Paths.get(datFolder, datFilename));
            l.forEach(s -> {
                if (s.contains(";")) {
                    String[] str = s.split(";");
                    opcodeStringMap.put(Integer.parseInt(str[0], 16), str[1]);
                } else if (s.contains(":")) {
                    String[] str = s.split(":");
                    opcodeToOpcodeMap.put(Integer.parseInt(str[0], 16), Integer.parseInt(str[1], 16));
                }
            });
        } catch (Exception e) {
            LOG.error("Unable to load {}", datFilename, e);
        }
    }

    public static String getMnemonic(int opcode) {
        return opcodeStringMap.getOrDefault(opcode, UNKNOWN);
    }

    public static int getMappingOpcode(int opcode) {
        return opcodeToOpcodeMap.getOrDefault(opcode, opcode);
    }

    public static void main(String[] args) {
        System.out.println(opcodeToOpcodeMap);
    }
}
