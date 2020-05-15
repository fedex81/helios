/*
 * Z80CoreWrapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 11/10/19 15:05
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

import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.bus.gen.GenesisZ80BusProvider;
import omegadrive.z80.disasm.Z80Decoder;
import omegadrive.z80.disasm.Z80Disasm;
import omegadrive.z80.disasm.Z80MemContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80State;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class Z80CoreWrapperDebug extends Z80CoreWrapper {

    private final static Logger LOG = LogManager.getLogger(Z80CoreWrapperDebug.class.getSimpleName());
    public static boolean verbose = false;

    protected Z80Disasm z80Disasm;
    protected int logAddressAccess = -1;
    protected StringBuilder sb = new StringBuilder();
    private int lastN = 100;
    private int index = 0;
    private Z80Helper.Z80StateExt current = new Z80Helper.Z80StateExt();
    private Z80Helper.Z80StateExt[] traceArray = new Z80Helper.Z80StateExt[lastN];
    private Map<Integer, String> disasmMap = new HashMap<>();
    private Z80MemIoOps.Z80MemIoOpsDbg memIoOpsDbg;
    private boolean m68kActivity = false;

    public static Z80CoreWrapper createGenesisInstance(GenesisBusProvider busProvider) {
        Z80CoreWrapperDebug w = new Z80CoreWrapperDebug();
        w.z80BusProvider = GenesisZ80BusProvider.createInstance(busProvider);
        w.memIoOps = Z80MemIoOps.createDebugGenesisInstance(w.z80BusProvider, w.sb, w.logAddressAccess);
        w.memIoOpsDbg = (Z80MemIoOps.Z80MemIoOpsDbg) w.memIoOps;
        IntStream.range(0, w.lastN).forEach(i -> w.traceArray[i] = new Z80Helper.Z80StateExt());
        return setupInternalDbg(w, null);
    }

    private static Z80CoreWrapper setupInternalDbg(Z80CoreWrapperDebug w, Z80State z80State) {
        setupInternal(w, z80State);
        Z80MemContext memContext = Z80MemContext.createInstance(w.z80BusProvider);
        w.z80Disasm = new Z80Disasm(memContext, new Z80Decoder(memContext));
        return w;
    }

    //NOTE: halt sets PC = PC - 1
    @Override
    public int executeInstruction() {
//        handlePreRunState();
        //      dumpHistory();
        // dumpCurrent()
        sb.setLength(0); //avoid mem leak
        LOG.info(disasmMap.computeIfAbsent(z80Core.getRegPC(), k -> Z80Helper.dumpInfo(z80Disasm, memIoOps, k)));
        int res = super.executeInstruction();
        handlePostRunState();
        return res;
    }

    private void handlePreRunState() {
        traceArray[index].memAccess = sb.toString(); //save mem access between prev instr and now
        if (m68kActivity) {
//            dumpCurrent();
            m68kActivity = false;
        }
        sb.setLength(0);
        Z80Helper.getZ80State(z80Core, current);
    }

    private void handlePostRunState() {
        index = (index + 1) % lastN;
        Z80Helper.copyState(current, traceArray[index]);
    }

    private void dumpHistory() {
        IntStream.range(0, lastN).forEach(i -> {
            int idx = (lastN + (index - i)) % lastN;
            System.out.println(idx + ":" + Z80Helper.toStringExt(traceArray[idx], z80Disasm, memIoOps));
        });
    }

    private void dumpCurrent() {
        System.out.println(index + "\n" + Z80Helper.toStringExt(traceArray[index], z80Disasm, memIoOps));
    }

    @Override
    public int readMemory(int address) {
        m68kActivity = true;
        return memIoOpsDbg.peek8Ext(address);
    }

    @Override
    public void writeMemory(int address, int data) {
        memIoOpsDbg.poke8Ext(address, data);
        m68kActivity = true;
    }
}