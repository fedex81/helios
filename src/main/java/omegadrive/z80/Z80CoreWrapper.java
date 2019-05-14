/*
 * Z80CoreWrapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 07/04/19 16:01
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

import emulib.plugins.cpu.DisassembledInstruction;
import omegadrive.bus.BaseBusProvider;
import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.bus.gen.GenesisZ80BusProvider;
import omegadrive.system.Genesis;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import omegadrive.z80.disasm.Z80Decoder;
import omegadrive.z80.disasm.Z80Disasm;
import omegadrive.z80.disasm.Z80MemContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80;
import z80core.Z80State;

import java.util.function.Function;

public class Z80CoreWrapper implements Z80Provider {

    public static boolean STOP_ON_EXCEPTION;

    static {
        STOP_ON_EXCEPTION =
            Boolean.valueOf(System.getProperty("z80.stop.on.exception", "false"));
    }
    public static boolean verbose = Genesis.verbose || false;

    private Z80 z80Core;
    private BaseBusProvider z80BusProvider;
    private Z80MemIoOps memIoOps;
    private Z80Disasm z80Disasm;

    private static Logger LOG = LogManager.getLogger(Z80CoreWrapper.class.getSimpleName());

    public static Z80CoreWrapper createSg1000Instance(BaseBusProvider busProvider) {
        Z80CoreWrapper w = new Z80CoreWrapper();
        w.z80BusProvider = busProvider;
        return createInstance(w, null);
    }

    public static Z80CoreWrapper createGenesisInstance(GenesisBusProvider busProvider) {
        Z80CoreWrapper w = new Z80CoreWrapper();
        w.z80BusProvider = GenesisZ80BusProvider.createInstance(busProvider, new Z80Memory());
        return createInstance(w, null);
    }

    private static Z80CoreWrapper createInstance(Z80CoreWrapper w, Z80State z80State) {
        w.memIoOps = Z80MemIoOps.createInstance(w.z80BusProvider);
        w.z80Core = new Z80(w.memIoOps, null);
        w.z80BusProvider.attachDevice(w);
        return initStateAndDisasm(w, z80State);
    }

    private static Z80CoreWrapper initStateAndDisasm(Z80CoreWrapper w, Z80State z80State) {
        if (z80State != null) {
            w.z80Core.setZ80State(z80State);
        }

        Z80MemContext memContext = Z80MemContext.createInstance(w.z80BusProvider);
        w.z80Disasm = new Z80Disasm(memContext, new Z80Decoder(memContext));
        return w;
    }

    //TEST
    public Z80CoreWrapper() {
    }

    //NOTE: halt sets PC = PC - 1
    @Override
    public int executeInstruction() {
        memIoOps.reset();
        try {
            printVerbose();
            z80Core.execute();
            if (verbose) {
                LOG.info("Z80State: " + toString(z80Core.getZ80State()));
            }
        } catch (Exception e) {
            LOG.error("z80 exception", e);
            LOG.error("Z80State: " + toString(z80Core.getZ80State()));
            LOG.error("Halting Z80");
            z80Core.setHalted(true);
            if(STOP_ON_EXCEPTION){
                Util.waitForever();
            }
        }
        return (int) (memIoOps.getTstates());
    }

    private static String toString(Z80State state) {
        String str = "\n";
        str += String.format("SP: %04x   PC: %04x  I : %02x   R : %02x  IX: %04x  IY: %04x\n",
                state.getRegSP(), state.getRegPC(), state.getRegI(), state.getRegR(), state.getRegIX(), state.getRegIY());
        str += String.format("A : %02x   B : %02x  C : %02x   D : %02x  E : %02x  F : %02x   L : %02x   H : %02x\n",
                state.getRegA(), state.getRegB(),
                state.getRegC(), state.getRegD(), state.getRegE(), state.getRegF(), state.getRegL(), state.getRegH());
        str += String.format("Ax: %02x   Bx: %02x  Cx: %02x   Dx: %02x  Ex: %02x  Fx: %02x   Lx: %02x   Hx: %02x\n",
                state.getRegAx(), state.getRegBx(),
                state.getRegCx(), state.getRegDx(), state.getRegEx(), state.getRegFx(), state.getRegLx(), state.getRegHx());
        str += String.format("AF : %04x   BC : %04x  DE : %04x   HL : %04x\n",
                state.getRegAF(), state.getRegBC(), state.getRegDE(), state.getRegHL());
        str += String.format("AFx: %04x   BCx: %04x  DEx: %04x   HLx: %04x\n",
                state.getRegAFx(), state.getRegBCx(), state.getRegDEx(), state.getRegHLx());
        str += String.format("IM: %s  iff1: %s  iff2: %s  memPtr: %04x  flagQ: %s\n",
                state.getIM().name(), state.isIFF1(), state.isIFF2(), state.getMemPtr(), state.isFlagQ());
        str += String.format("NMI: %s  INTLine: %s  pendingE1: %s\n", state.isNMI(), state.isINTLine(),
                state.isPendingEI());
        return str;
    }

    //From the Z80UM.PDF document, a reset clears the interrupt enable, PC and
    //registers I and R, then sets interrupt status to mode 0.
    @Override
    public void reset() {
        //from Exodus
        z80Core.setRegSP(0xFFFF);
        z80Core.setRegAF(0xFFFF);
        z80Core.setRegPC(0);
        z80Core.setRegI(0);
        z80Core.setRegR(0);
        z80Core.setIFF1(false);
        z80Core.setIFF2(false);
        z80Core.setIM(Z80.IntMode.IM0);
    }

    //If the Z80 has interrupts disabled when the frame interrupt is supposed
    //to occur, it will be missed, rather than made pending.
    @Override
    public boolean interrupt(boolean value) {
        z80Core.setINTLine(value);
        memIoOps.setActiveInterrupt(value);
        return true;
    }

    @Override
    public void triggerNMI() {
        z80Core.triggerNMI();
    }

    @Override
    public boolean isHalted() {
        return z80Core.isHalted();
    }

    @Override
    public int readMemory(int address) {
        return (int) z80BusProvider.read(address, Size.BYTE);
    }

    @Override
    public void writeMemory(int address, int data) {
        z80BusProvider.write(address, data, Size.BYTE);
        LogHelper.printLevel(LOG, Level.DEBUG, "Write Z80: {}, {}", address, data, verbose);
    }

    @Override
    public BaseBusProvider getZ80BusProvider() {
        return z80BusProvider;
    }

    @Override
    public void loadZ80State(Z80State z80State) {
        this.z80Core.setZ80State(z80State);
    }

    @Override
    public Z80State getZ80State() {
        return z80Core.getZ80State();
    }

    public static Function<DisassembledInstruction, String> disasmToString = d ->
            String.format("%08x   %12s   %s", d.getAddress(), d.getOpCode(), d.getMnemo());

    private void printVerbose() {
        if (verbose) {
            int address = z80Core.getRegPC();
            String str = disasmToString.apply(z80Disasm.disassemble(address));
            if (str.contains("nop")) {
                int opcode = this.memIoOps.fetchOpcode(address);
                if (opcode != 0) {
                    System.out.println("oops");
                }
            }
            LOG.info(str);
        }
    }
}