package omegadrive.z80;

import omegadrive.memory.IMemoryRam;
import omegadrive.util.Util;
import omegadrive.z80.disasm.Z80Decoder;
import omegadrive.z80.disasm.Z80DecoderExt;
import omegadrive.z80.disasm.Z80Disasm;
import omegadrive.z80.disasm.Z80MemContext;
import org.junit.Before;
import org.junit.Test;
import z80core.MemIoOps;
import z80core.Z80;

import java.util.Arrays;
import java.util.Map;

/**
 * Z80DisasmExtTest
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class Z80DisasmExtTest {

    public static final int MEMORY_SIZE = 0x10001;

    private Z80 z80;
    private IMemoryRam memory;
    private MemIoOps memIo;
    private Z80Disasm z80Disasm;
    private byte[] bram = new byte[MEMORY_SIZE];

    @Before
    public void before() {
        memory = new Z80Memory(MEMORY_SIZE);
        memIo = new MemIoOps();
        bram = Util.toByteArray(memory.getRamData());
        memIo.setRam(bram);
        z80 = new Z80(memIo, null);
        z80.reset();
        memIo.reset();
        Z80MemContext context = Z80Exerciser.createContext(memIo);
        z80Disasm = new Z80Disasm(context, new Z80Decoder(context));
    }

    @Test
    public void testToString() {
        z80.reset();
        Arrays.fill(bram, (byte) 0);
        int k = 0;
        for (Map.Entry<Integer, String> entry : Z80DecoderExt.opcodeStringMap.entrySet()) {
            memIo.poke8(k, entry.getKey() >> 8);
            memIo.poke8(k + 1, entry.getKey() & 0xFF);
            String dis = Z80Helper.dumpInfo(z80Disasm, memIo, z80.getRegPC());
//            System.out.println(entry.getKey() + "," + dis + "," + entry.getValue());
            z80.execute();
            k = z80.getRegPC();
            //reset state
            z80.reset();
            z80.setRegPC(k);
        }
    }

    @Test
    public void testToOpcode() {
        z80.reset();
        Arrays.fill(bram, (byte) 0);
        int k = 0;
        for (Map.Entry<Integer, Integer> entry : Z80DecoderExt.opcodeToOpcodeMap.entrySet()) {
            memIo.poke8(k, entry.getKey() >> 8);
            memIo.poke8(k + 1, entry.getKey() & 0xFF);
            String dis = Z80Helper.dumpInfo(z80Disasm, memIo, z80.getRegPC());
            System.out.println(Integer.toHexString(entry.getKey()).toUpperCase() + "," + dis + "," +
                    Integer.toHexString(entry.getValue()));
            z80.execute();
            k = z80.getRegPC();
            //reset state
            z80.reset();
            z80.setRegPC(k);
        }
    }
}