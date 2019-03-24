package omegadrive.z80;

import omegadrive.memory.IMemoryRam;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.MemIoOps;
import z80core.NotifyOps;
import z80core.Z80;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * @author jsanchez
 */
public class Z80Exerciser implements NotifyOps {

    private static Logger LOG = LogManager.getLogger(Z80Exerciser.class.getSimpleName());

    private static String resourcesPath = "./src/test/java/omegadrive/z80/";

    public static final int MEMORY_SIZE = 0x10000;

    private Z80 z80;
    private IMemoryRam memory;
    private MemIoOps memIo;
    private byte[] bram = new byte[MEMORY_SIZE];

    private boolean finish = false;

    public Z80Exerciser() {
        memory = new Z80Memory(MEMORY_SIZE);
        memIo = new MemIoOps();
        toByteArray(memory.getRamData(), bram);
        memIo.setRam(bram);
        z80 = new Z80(memIo, this);
    }

    private static byte[] toByteArray(int[] in, byte[] out) {
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) in[i];
        }
        return out;
    }

    private void runTest(String testName) {
        byte[] fileBytes;
        Path file = Paths.get(".", resourcesPath + testName);
        try {
            fileBytes = Files.readAllBytes(file);
            System.out.println("Read " + fileBytes.length + " bytes from " + testName);
        } catch (IOException ex) {
            LOG.error(ex);
            return;
        }

        int k = 0;
        for (int i = 0x100; i < MEMORY_SIZE && k < fileBytes.length; i++) {
            memIo.poke8(i, fileBytes[k]);
            k++;
        }
        z80.reset();
        memIo.reset();
        finish = false;

        memIo.poke8(0, (byte) 0xC3);
        memIo.poke8(1, (byte) 0x00);
        memIo.poke8(2, (byte) 0x01);
        memIo.poke8(5, (byte) 0xC9);

        System.out.println("Starting test " + testName);
        long counter = 0;
        z80.setBreakpoint(0x0005, true);
        while (!finish) {
            counter++;
//            String str = Z80CoreWrapper.disasmToString.apply(z80Disasm.disassemble(z80.getRegPC()));
//            System.out.println(counter + ": " + str);
            z80.execute();

        }
        System.out.println("Test " + testName + " ended, #inst: " + counter);
    }

    public static void main(String[] args) {
        exerciseZexAll();
        exerciseZexDoc();
    }

    private static void exerciseZexDoc() {
        exercise("zexdoc.bin");
    }

    private static void exerciseZexAll() {
        exercise("zexall.bin");
    }

    private static void exercise(String fileName) {
        System.out.println(new File(".").getAbsolutePath());
        Z80Exerciser exerciser = new Z80Exerciser();
        long start = System.currentTimeMillis();
        exerciser.runTest(fileName);
        System.out.println(fileName + " executed in " + (System.currentTimeMillis() - start) + " ms.");
    }

    @Override
    public int breakpoint(int address, int opcode) {
        // Emulate CP/M Syscall at address 5
        switch (z80.getRegC()) {
            case 0: // BDOS 0 System Reset
                System.out.println("Z80 reset after " + memIo.getTstates() + " t-states");
                finish = true;
                break;
            case 2: // BDOS 2 console char output
                System.out.print((char) z80.getRegE());
                break;
            case 9: // BDOS 9 console string output (string terminated by "$")
                int strAddr = z80.getRegDE();
                while (memIo.peek8(strAddr) != '$') {
                    System.out.print((char) memIo.peek8(strAddr++));
                }
                break;
            default:
                System.out.println("BDOS Call " + z80.getRegC());
                finish = true;
        }
        return opcode;
    }

    @Override
    public void execDone() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
