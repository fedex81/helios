package omegadrive.m68k;

/*
//  M68k - Java Amiga MachineCore
//  Copyright (c) 2008-2010, Tony Headford
//  All rights reserved.
//
//  Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
//  following conditions are met:
//
//    o  Redistributions of source code must retain the above copyright notice, this list of conditions and the
//       following disclaimer.
//    o  Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
//       following disclaimer in the documentation and/or other materials provided with the distribution.
//    o  Neither the name of the M68k Project nor the names of its contributors may be used to endorse or promote
//       products derived from this software without specific prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
//  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
//  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
//  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
//  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
*/

import m68k.cpu.Cpu;
import m68k.cpu.DisassembledInstruction;
import m68k.cpu.Instruction;
import m68k.cpu.MC68000;
import m68k.memory.AddressSpace;
import m68k.memory.MemorySpace;
import omegadrive.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * MC68000Monitor
 *
 * @author Federico Berti
 * @author Tony Headford
 * @link https://github.com/tonyheadford/m68k/blob/master/src/m68k/Monitor.java
 */
public class MC68000Monitor implements Runnable {
    private final Cpu cpu;
    private final AddressSpace memory;
    protected boolean running;
    private StringBuilder buffer;
    private PrintStream writer;
    private boolean showBytes;
    private boolean autoRegs;
    private ArrayList<Integer> breakpoints;


    public MC68000Monitor(Cpu cpu, AddressSpace memory) {
        this.cpu = cpu;
        this.memory = memory;
        this.buffer = new StringBuilder(128);
        this.showBytes = false;
        this.autoRegs = false;
        this.breakpoints = new ArrayList();
        this.writer = System.out;
    }

    public static void main(String[] args) {
        int mem_size = 512;
        if (args.length == 1) {
            try {
                mem_size = Integer.parseInt(args[0]);
            } catch (NumberFormatException var5) {
                System.err.println("Invalid number: " + args[0]);
                System.out.println("Usage: m68k.Monitor [memory size Kb]");
                System.exit(-1);
            }
        }

        System.out.println("m68k Monitor v0.1 - Copyright 2008-2010 Tony Headford");
        AddressSpace memory = new MemorySpace(mem_size);
        Cpu cpu = new MC68000();
        cpu.setAddressSpace(memory);
        cpu.reset();
        MC68000Monitor monitor = new MC68000Monitor(cpu, memory);
        monitor.run();
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        this.writer = System.out;
        this.running = true;

        while (this.running) {
            try {
                writer.print("> ");
                writer.flush();
                while (!scanner.hasNextLine()) {
                    Util.sleep(500);
                }
                this.handleCommand(scanner.nextLine());
                if (this.autoRegs) {
                    this.dumpInfo();
                }
            } catch (Exception var3) {
                var3.printStackTrace();
            }
        }

    }


    protected void handleCommand(String line) {
        String[] tokens = line.split(" ");
        String cmd = tokens[0].trim().toLowerCase();
        if (cmd.length() > 0) {
            if (cmd.equals("q")) {
                this.running = false;
            } else if (cmd.equals("r")) {
                this.dumpInfo();
            } else if (cmd.equals("pc")) {
                this.handlePC(tokens);
            } else if (cmd.equals("d")) {
                this.handleDisassemble(tokens);
            } else if (cmd.equals("b")) {
                this.handleBreakPoints(tokens);
            } else if (cmd.equals("sr")) {
                this.handleSR(tokens);
            } else if (cmd.equals("ccr")) {
                this.handleCCR(tokens);
            } else if (cmd.equals("usp")) {
                this.handleUSP(tokens);
            } else if (cmd.equals("ssp")) {
                this.handleSSP(tokens);
            } else if (cmd.equals("ml")) {
                this.handleMemLong(tokens);
            } else if (cmd.equals("mw")) {
                this.handleMemWord(tokens);
            } else if (cmd.equals("mb")) {
                this.handleMemByte(tokens);
            } else if (cmd.equals("m")) {
                this.handleMemDump(tokens);
            } else if (cmd.equals("s")) {
                this.handleStep(tokens);
            } else if (cmd.equals("g")) {
                this.handleGo(tokens);
            } else if (cmd.equals("autoregs")) {
                this.handleAutoRegs(tokens);
            } else if (cmd.equals("showbytes")) {
                this.handleShowBytes(tokens);
            } else if (cmd.equals("load")) {
                this.handleLoad(tokens);
            } else if (cmd.startsWith("d")) {
                this.handleDataRegs(tokens);
            } else if (cmd.startsWith("a")) {
                this.handleAddrRegs(tokens);
            } else if (!cmd.equals("?") && !cmd.equals("h") && !cmd.equals("help")) {
                this.writer.println("Unknown command: " + tokens[0]);
            } else {
                this.showHelp(tokens);
            }
        }

    }

    protected void handleGo(String[] tokens) {
        int count = 0;
        boolean going = true;

        while (this.running && going) {
            try {
                int time = this.cpu.execute();
                count += time;
                int addr = this.cpu.getPC();
                if (this.breakpoints.contains(addr)) {
                    this.writer.println("BREAKPOINT");
                    going = false;
                }
            } catch (Exception var6) {
                var6.printStackTrace();
                going = false;
            }
        }

        this.writer.printf("[Consumed %d ticks]\n", count);
    }

    protected void handleBreakPoints(String[] tokens) {
        if (tokens.length > 1) {
            try {
                int addr = this.parseInt(tokens[1]);
                if (this.breakpoints.contains(addr)) {
                    this.breakpoints.remove(new Integer(addr));
                } else {
                    this.breakpoints.add(addr);
                }
            } catch (NumberFormatException var4) {
                return;
            }
        }

        this.writer.println("Breakpoints:");
        Iterator var5 = this.breakpoints.iterator();

        while (var5.hasNext()) {
            int bp = (Integer) var5.next();
            this.writer.println(String.format("$%x", bp));
        }

    }

    protected void handleAutoRegs(String[] tokens) {
        if (tokens.length > 1) {
            if (tokens[1].equalsIgnoreCase("on")) {
                this.autoRegs = true;
            } else if (tokens[1].equalsIgnoreCase("off")) {
                this.autoRegs = false;
            }
        }

        this.writer.println("autoregs is " + (this.autoRegs ? "on" : "off"));
    }

    protected void handleShowBytes(String[] tokens) {
        if (tokens.length > 1) {
            if (tokens[1].equalsIgnoreCase("on")) {
                this.showBytes = true;
            } else if (tokens[1].equalsIgnoreCase("off")) {
                this.showBytes = false;
            }
        }

        this.writer.println("showbytes is " + (this.showBytes ? "on" : "off"));
    }

    protected void handleDataRegs(String[] tokens) {
        String reg = tokens[0].trim();
        if (reg.length() != 2) {
            this.writer.println("Bad identifier [" + reg + "]");
        } else {
            int r = reg.charAt(1) - 48;
            if (r >= 0 && r <= 7) {
                if (tokens.length == 2) {
                    int value;
                    try {
                        value = this.parseInt(tokens[1]);
                    } catch (NumberFormatException var6) {
                        this.writer.println("Bad value [" + tokens[1] + "]");
                        return;
                    }

                    this.cpu.setDataRegisterLong(r, value);
                } else {
                    this.writer.printf("D%d: %08x\n", r, this.cpu.getDataRegisterLong(r));
                }

            } else {
                this.writer.println("Bad identifier [" + reg + "]");
            }
        }
    }

    protected void handleAddrRegs(String[] tokens) {
        String reg = tokens[0].trim();
        if (reg.length() != 2) {
            this.writer.println("Bad identifier [" + reg + "]");
        } else {
            int r = reg.charAt(1) - 48;
            if (r >= 0 && r <= 7) {
                if (tokens.length == 2) {
                    int value;
                    try {
                        value = this.parseInt(tokens[1]);
                    } catch (NumberFormatException var6) {
                        this.writer.println("Bad value [" + tokens[1] + "]");
                        return;
                    }

                    this.cpu.setAddrRegisterLong(r, value);
                } else {
                    this.writer.printf("A%d: %08x\n", r, this.cpu.getAddrRegisterLong(r));
                }

            } else {
                this.writer.println("Bad identifier [" + reg + "]");
            }
        }
    }

    protected void handleStep(String[] tokens) {
        int time = this.cpu.execute();
        this.writer.printf("[Execute took %d ticks]\n", time);
    }

    protected void handleMemDump(String[] tokens) {
        if (tokens.length != 2) {
            this.writer.println("usage: m <start address>");
        } else {
            String address = tokens[1];
            int size = this.memory.size();

            try {
                int addr = this.parseInt(address);
                if (addr < 0 || addr >= size) {
                    this.writer.println("Address out of range");
                    return;
                }

                StringBuilder sb = new StringBuilder(80);
                StringBuilder asc = new StringBuilder(16);

                for (int y = 0; y < 8 && addr < size; ++y) {
                    sb.append(String.format("%08x", addr)).append("  ");

                    int n;
                    for (n = 0; n < 16 && addr < size; ++n) {
                        int b = this.cpu.readMemoryByte(addr);
                        sb.append(String.format("%02x ", b));
                        asc.append(this.getPrintable(b));
                        ++addr;
                    }

                    if (sb.length() < 48) {
                        for (n = sb.length(); n < 48; ++n) {
                            sb.append(" ");
                        }
                    }

                    sb.append("    ").append(asc);
                    this.writer.println(sb.toString());
                    sb.delete(0, sb.length());
                    asc.delete(0, asc.length());
                }
            } catch (NumberFormatException var10) {
                this.writer.println("Unknown address [" + address + "]");
            }

        }
    }

    protected String handleDisassemble(String[] tokens) {
        int num_instructions = 8;
        if (tokens.length > 2) {
            try {
                num_instructions = this.parseInt(tokens[2]);
            } catch (NumberFormatException var10) {
                this.writer.println("Invalid instruction count: " + tokens[2]);
                return "";
            }
        }

        int start;
        if (tokens.length > 1) {
            String address = tokens[1];

            try {
                start = this.parseInt(address);
            } catch (NumberFormatException var9) {
                this.writer.println("Unknown address [" + address + "]");
                return "";
            }
        } else {
            start = this.cpu.getPC();
        }

        int count = 0;
        StringBuilder res = new StringBuilder();
        for (StringBuilder buffer = new StringBuilder(80); start < this.memory.size() && count < num_instructions; ++count) {
            buffer.delete(0, buffer.length());
            int opcode = this.cpu.readMemoryWord(start);
            Instruction i = this.cpu.getInstructionFor(opcode);
            DisassembledInstruction di = i.disassemble(start, opcode);
            if (this.showBytes) {
                di.formatInstruction(buffer);
            } else {
                di.shortFormat(buffer);
            }

            res.append(buffer.toString()).append("\n");
            start += di.size();
        }
        return res.toString();
    }

    public static String dumpInfo(Cpu cpu, boolean showBytes, int memorySize) {
        StringBuilder sb = new StringBuilder("\n");
        int wrapPc = cpu.getPC() & 0xFF_FFFF; //PC is 24 bits

        sb.append(String.format("D0: %08x   D4: %08x   A0: %08x   A4: %08x     PC:  %08x\n",
                cpu.getDataRegisterLong(0), cpu.getDataRegisterLong(4), cpu.getAddrRegisterLong(0),
                cpu.getAddrRegisterLong(4), wrapPc));
        sb.append(String.format("D1: %08x   D5: %08x   A1: %08x   A5: %08x     SR:  %04x %s\n",
                cpu.getDataRegisterLong(1), cpu.getDataRegisterLong(5), cpu.getAddrRegisterLong(1),
                cpu.getAddrRegisterLong(5), cpu.getSR(), makeFlagView(cpu)));
        sb.append(String.format("D2: %08x   D6: %08x   A2: %08x   A6: %08x     USP: %08x\n",
                cpu.getDataRegisterLong(2), cpu.getDataRegisterLong(6), cpu.getAddrRegisterLong(2),
                cpu.getAddrRegisterLong(6), cpu.getUSP()));
        sb.append(String.format("D3: %08x   D7: %08x   A3: %08x   A7: %08x     SSP: %08x\n\n",
                cpu.getDataRegisterLong(3), cpu.getDataRegisterLong(7), cpu.getAddrRegisterLong(3),
                cpu.getAddrRegisterLong(7), cpu.getSSP()));
        StringBuilder sb2 = new StringBuilder();
        if (wrapPc >= 0 && wrapPc < memorySize) {
            int opcode = cpu.readMemoryWord(wrapPc);
            Instruction i = cpu.getInstructionFor(opcode);
            DisassembledInstruction di = i.disassemble(wrapPc, opcode);
            if (showBytes) {
                di.formatInstruction(sb2);
            } else {
                di.shortFormat(sb2);
            }
        } else {
            sb2.append(String.format("%08x   ????", wrapPc));
        }
        return sb.append(String.format("\n==> %s\n\n", sb2.toString())).toString();
    }

    protected String dumpInfo() {
        return dumpInfo(cpu, showBytes, memory.size());
    }

    public static String dumpOp(Cpu cpu) {
        StringBuilder builder = new StringBuilder();
        int wrapPc = cpu.getPC() & 0xFF_FFFF; //PC is 24 bits
        if (wrapPc >= 0) {
            int opcode = cpu.readMemoryWord(wrapPc);
            Instruction i = cpu.getInstructionFor(opcode);
            DisassembledInstruction di = i.disassemble(wrapPc, opcode);
            di.formatInstruction(builder);
//            di.shortFormat(this.buffer);
        } else {
            builder.append(String.format("%08x   ????", wrapPc));
        }
        return builder.toString();
    }

    private static Set<String> instSet = new TreeSet<>();

    public static boolean addToInstructionSet(MC68000 cpu) {
        int wrapPc = cpu.getPC() & 0xFF_FFFF; //PC is 24 bits
        int opcode = cpu.readMemoryWord(wrapPc);
        Instruction i = cpu.getInstructionFor(opcode);
        String name = i.getClass().getTypeName();
        String str = name.substring(name.lastIndexOf('.') + 1);
        return instSet.add(str);
    }

    public static String dumpInstructionSet() {
        StringBuilder sb = new StringBuilder();
        sb.append("Instruction set: " + instSet.size() + "\n").append(Arrays.toString(instSet.toArray()));
        return sb.toString();
    }

    protected static String makeFlagView(Cpu cpu) {
        StringBuilder sb = new StringBuilder(5);
        sb.append((char) (cpu.isFlagSet(16) ? 'X' : '-'));
        sb.append((char) (cpu.isFlagSet(8) ? 'N' : '-'));
        sb.append((char) (cpu.isFlagSet(4) ? 'Z' : '-'));
        sb.append((char) (cpu.isFlagSet(2) ? 'V' : '-'));
        sb.append((char) (cpu.isFlagSet(1) ? 'C' : '-'));
        return sb.toString();
    }

    protected String makeFlagView() {
        return makeFlagView(this.cpu);
    }

    protected char getPrintable(int val) {
        return val >= 32 && val <= 126 ? (char) val : '.';
    }

    protected int parseInt(String value) throws NumberFormatException {
        int v;
        if (value.startsWith("$")) {
            try {
                v = (int) (Long.parseLong(value.substring(1), 16) & 4294967295L);
            } catch (NumberFormatException var5) {
                this.writer.println("Not a valid hex number [" + value + "]");
                throw var5;
            }
        } else {
            try {
                v = (int) (Long.parseLong(value) & 4294967295L);
            } catch (NumberFormatException var4) {
                this.writer.println("Not a valid decimal number [" + value + "]");
                throw var4;
            }
        }

        return v;
    }

    protected void handlePC(String[] tokens) {
        if (tokens.length == 1) {
            int wrapPc = cpu.getPC() & 0xFF_FFFF; //PC is 24 bits
            this.writer.printf("PC: %08x\n", wrapPc);
        } else if (tokens.length == 2) {
            int value;
            try {
                value = this.parseInt(tokens[1]);
            } catch (NumberFormatException var4) {
                this.writer.println("Bad value [" + tokens[1] + "]");
                return;
            }

            this.cpu.setPC(value);
        } else {
            this.writer.println("usage: " + tokens[0] + " [value]");
        }

    }

    protected void handleSR(String[] tokens) {
        if (tokens.length == 1) {
            this.writer.printf("SR: %04x\n", this.cpu.getSR());
        } else if (tokens.length == 2) {
            int value;
            try {
                value = this.parseInt(tokens[1]);
            } catch (NumberFormatException var4) {
                this.writer.println("Bad value [" + tokens[1] + "]");
                return;
            }

            this.cpu.setSR(value);
        } else {
            this.writer.println("usage: " + tokens[0] + " [value]");
        }

    }

    protected void handleCCR(String[] tokens) {
        if (tokens.length == 1) {
            this.writer.printf("CCR: %02x  %s\n", this.cpu.getCCRegister(), this.makeFlagView());
        } else if (tokens.length == 2) {
            int value;
            try {
                value = this.parseInt(tokens[1]);
            } catch (NumberFormatException var4) {
                this.writer.println("Bad value [" + tokens[1] + "]");
                return;
            }

            this.cpu.setCCRegister(value);
        } else {
            this.writer.println("usage: " + tokens[0] + " [value]");
        }

    }

    protected void handleUSP(String[] tokens) {
        if (tokens.length == 1) {
            this.writer.printf("USP: %08x\n", this.cpu.getUSP());
        } else if (tokens.length == 2) {
            int value;
            try {
                value = this.parseInt(tokens[1]);
            } catch (NumberFormatException var4) {
                this.writer.println("Bad value [" + tokens[1] + "]");
                return;
            }

            this.cpu.setUSP(value);
        } else {
            this.writer.println("usage: " + tokens[0] + " [value]");
        }

    }

    protected void handleSSP(String[] tokens) {
        if (tokens.length == 1) {
            this.writer.printf("SSP: %08x\n", this.cpu.getSSP());
        } else if (tokens.length == 2) {
            int value;
            try {
                value = this.parseInt(tokens[1]);
            } catch (NumberFormatException var4) {
                this.writer.println("Bad value [" + tokens[1] + "]");
                return;
            }

            this.cpu.setSSP(value);
        } else {
            this.writer.println("usage: " + tokens[0] + " [value]");
        }

    }

    protected void handleMemLong(String[] tokens) {
        if (tokens.length != 2 && tokens.length != 3) {
            this.writer.println("usage: ml <address> [value]");
        } else {
            String address = tokens[1];
            if (tokens.length == 2) {
                try {
                    int addr = this.parseInt(address);
                    if (addr < 0 || addr >= this.memory.size()) {
                        this.writer.println("Address out of range");
                        return;
                    }

                    this.writer.printf("%08x  %08x\n", addr, this.cpu.readMemoryLong(addr));
                } catch (NumberFormatException var6) {
                    var6.printStackTrace();
                }
            } else {
                String value = tokens[2];

                try {
                    int addr = this.parseInt(address);
                    if (addr < 0 || addr >= this.memory.size()) {
                        this.writer.println("Address out of range");
                        return;
                    }

                    int v = this.parseInt(value);
                    this.cpu.writeMemoryLong(addr, v);
                } catch (NumberFormatException var7) {
                    var7.printStackTrace();
                }
            }
        }

    }

    protected void handleMemWord(String[] tokens) {
        if (tokens.length != 2 && tokens.length != 3) {
            this.writer.println("usage: mw <address> [value]");
        } else {
            String address = tokens[1];
            if (tokens.length == 2) {
                try {
                    int addr = this.parseInt(address);
                    if (addr < 0 || addr >= this.memory.size()) {
                        this.writer.println("Address out of range");
                        return;
                    }

                    this.writer.printf("%08x  %04x\n", addr, this.cpu.readMemoryWord(addr));
                } catch (NumberFormatException var6) {
                    var6.printStackTrace();
                }
            } else {
                String value = tokens[2];

                try {
                    int addr = this.parseInt(address);
                    if (addr < 0 || addr >= this.memory.size()) {
                        this.writer.println("Address out of range");
                        return;
                    }

                    int v = this.parseInt(value);
                    this.cpu.writeMemoryWord(addr, v);
                } catch (NumberFormatException var7) {
                    var7.printStackTrace();
                }
            }
        }

    }

    protected void handleMemByte(String[] tokens) {
        if (tokens.length != 2 && tokens.length != 3) {
            this.writer.println("usage: mb <address> [value]");
        } else {
            String address = tokens[1];
            if (tokens.length == 2) {
                try {
                    int addr = this.parseInt(address);
                    if (addr >= 0 && addr < this.memory.size()) {
                        this.writer.printf("%08x  %02x\n", addr, this.cpu.readMemoryByte(addr));
                    } else {
                        this.writer.println("Address out of range");
                    }
                } catch (NumberFormatException var6) {
                    var6.printStackTrace();
                }
            } else {
                String value = tokens[2];

                try {
                    int addr = this.parseInt(address);
                    if (addr < 0 || addr >= this.memory.size()) {
                        this.writer.println("Address out of range");
                        return;
                    }

                    int v = this.parseInt(value);
                    this.cpu.writeMemoryByte(addr, v);
                } catch (NumberFormatException var7) {
                    var7.printStackTrace();
                }
            }
        }

    }

    protected void handleLoad(String[] tokens) {
        if (tokens.length != 3) {
            this.writer.println("usage: load <address> <file>");
        } else {
            int address;
            try {
                address = this.parseInt(tokens[1]);
            } catch (NumberFormatException var8) {
                this.writer.println("Invalid address specified [" + tokens[1] + "]");
                return;
            }

            File f = new File(tokens[2]);
            if (!f.exists()) {
                this.writer.println("Cannot find file [" + tokens[2] + "]");
            } else if (address + (int) f.length() >= this.memory.size()) {
                this.writer.println("Need larger memory to load this file at " + tokens[1]);
            } else {
                try {
                    FileInputStream fis = new FileInputStream(f);
                    byte[] buffer = new byte[(int) f.length()];
                    int len = fis.read(buffer);
                    fis.close();

                    for (int n = 0; n < len; ++n) {
                        this.memory.writeByte(address, buffer[n]);
                        ++address;
                    }
                } catch (IOException var9) {
                    var9.printStackTrace();
                }

            }
        }
    }

    protected void showHelp(String[] tokens) {
        this.writer.println("Command Help:");
        this.writer.println("Addresses and values can be specified in hexadecimal by preceeding the value with '$'");
        this.writer.println("      eg. d0 $deadbeef  - Set register d0 to 0xDEADBEEF");
        this.writer.println("          m $10         - Memory dump starting at 0x10 (16 in decimal)");
        this.writer.println("          pc 10         - Set the PC register to 10 (0x0A in hexadecimal)");
        this.writer.println("General:");
        this.writer.println("  ?,h,help              - Show this help.");
        this.writer.println("  q                     - Quit.");
        this.writer.println("Registers:");
        this.writer.println("  r                     - Display all registers");
        this.writer.println("  d[0-9] [value]        - Set or view a data register");
        this.writer.println("  a[0-9] [value]        - Set or view an address register");
        this.writer.println("  pc [value]            - Set or view the PC register");
        this.writer.println("  sr [value]            - Set or view the SR register");
        this.writer.println("  ccr [value]           - Set or view the CCR register");
        this.writer.println("  usp [value]           - Set or view the USP register");
        this.writer.println("  ssp [value]           - Set or view the SSP register");
        this.writer.println("Memory:");
        this.writer.println("  m <address>           - View (128 byte) memory dump starting at the specified address");
        this.writer.println("  mb <address> [value]  - Set or view a byte (8-bit) value at the specified address");
        this.writer.println("  mw <address> [value]  - Set or view a word (16-bit) value at the specified address");
        this.writer.println("  ml <address> [value]  - Set or view a long (32-bit) value at the specified address");
        this.writer.println("  load <address> <file> - Load <file> into memory starting at <address>");
        this.writer.println("Execution & Disassembly:");
        this.writer.println("  s                     - Execute the instruction at the PC register");
        this.writer.println("  d <address> [count]   - Disassemble the memory starting at <address> for an optional");
        this.writer.println("                          <count> instructions. Default is 8 instructions.");
    }
}
