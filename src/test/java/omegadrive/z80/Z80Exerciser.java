/*
 * Copyright (c) 2018-2019 Federico Berti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.z80;

import omegadrive.memory.IMemoryRam;
import omegadrive.util.Util;
import omegadrive.z80.disasm.Z80Dasm;
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
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


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
    private Set<String> unknownCodes = new HashSet<>();
    private Z80Dasm z80Dasm = new Z80Dasm();

    private boolean finish = false;

    public Z80Exerciser() {
        memory = new Z80Memory(MEMORY_SIZE);
        memIo = new MemIoOps();
        bram = Util.unsignedToByteArray(memory.getRamData());
        memIo.setRam(bram);
        z80 = new Z80(memIo, this);
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
        String str;
        while (!finish) {
            counter++;
//            z80Dasm.disassemble(z80.getRegPC(), memIo);
            z80.execute();
        }
        System.out.println("Test " + testName + " ended, #inst: " + counter);
        System.out.println(unknownCodes.stream().collect(Collectors.joining("\n")));
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
