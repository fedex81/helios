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

package omegadrive.cpu.m68k;

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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

import static omegadrive.cpu.m68k.M68kProvider.MD_PC_MASK;

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
                    this.breakpoints.remove(addr);
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
            this.writer.printf("$%x%n", bp);
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
        int wrapPc = cpu.getPC() & MD_PC_MASK; //PC is 24 bits

        sb.append(String.format("D0: %08x   D4: %08x   A0: %08x   A4: %08x     PC:  %08x\n",
                cpu.getDataRegisterLong(0), cpu.getDataRegisterLong(4), cpu.getAddrRegisterLong(0),
                cpu.getAddrRegisterLong(4), wrapPc));
        sb.append(String.format("D1: %08x   D5: %08x   A1: %08x   A5: %08x     SR:  %04x %s\n",
                cpu.getDataRegisterLong(1), cpu.getDataRegisterLong(5), cpu.getAddrRegisterLong(1),
                cpu.getAddrRegisterLong(5), cpu.getSR(), MC68000Helper.makeFlagView(cpu)));
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
            int wrapPc = cpu.getPC() & MD_PC_MASK; //PC is 24 bits
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
            this.writer.printf("CCR: %02x  %s\n", this.cpu.getCCRegister(), MC68000Helper.makeFlagView(cpu));
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
