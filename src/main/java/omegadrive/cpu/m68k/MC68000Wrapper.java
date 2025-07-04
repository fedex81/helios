/*
 * MC68000Wrapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:37
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

import m68k.cpu.CpuConfig;
import m68k.cpu.MC68000;
import m68k.memory.AddressSpace;
import omegadrive.bus.model.MdM68kBusProvider;
import omegadrive.cpu.m68k.debug.MC68000WrapperFastDebug;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import org.slf4j.Logger;

import static omegadrive.util.Util.th;

/**
 *
 * NOTES: f-line emulator. Tecmo Cup
 *        a-line emulator. Mamono Hunter Yohko
 *        reset. Bug Hunt (homebrew)
 *        stop. Hardball III
 *        add z-flag. sgdk-partic.bin
 */
public class MC68000Wrapper implements M68kProvider {

    private final static Logger LOG = LogHelper.getLogger(MC68000Wrapper.class.getSimpleName());

    protected final MC68000 m68k;
    protected final AddressSpace addressSpace;
    protected final MdM68kBusProvider busProvider;
    protected final CpuDeviceAccess cpu;
    private boolean stop;
    protected int currentPC;
    protected int instCycles = 0;

    public static boolean subCpuBusHalt = false;

    private static final CpuConfig tasBrokenConfig = new CpuConfig(true, false, false);

    public MC68000Wrapper(CpuDeviceAccess cpu, MdM68kBusProvider busProvider) {
        this.cpu = cpu;
        this.m68k = createCpu(cpu == CpuDeviceAccess.M68K ? tasBrokenConfig : CpuConfig.DEFAULT_CONFIG);
        this.busProvider = busProvider;
        this.addressSpace = createAddressSpace();
        m68k.setAddressSpace(addressSpace);
    }

    public static MC68000Wrapper createInstance(MdM68kBusProvider busProvider) {
        return createInstance(CpuDeviceAccess.M68K, busProvider);
    }

    public static MC68000Wrapper createInstance(CpuDeviceAccess cpu, MdM68kBusProvider busProvider) {
        return MC68000Helper.M68K_DEBUG ? new MC68000WrapperFastDebug(cpu, busProvider) : new MC68000Wrapper(cpu, busProvider);
    }

    @Override
    public int runInstruction() {
        int res = 0;
        try {
            currentPC = m68k.getPC();
            res = m68k.execute() + instCycles;
            instCycles = 0;
        } catch (Exception e) {
            LOG.error("68k error", e);
            m68k.raiseException(ILLEGAL_ACCESS_EXCEPTION); //avoid the intack dance
            if (MC68000Helper.STOP_ON_EXCEPTION) {
                LOG.error(MC68000Helper.getCpuState(m68k, ""));
                throw e;
            }
        }
        return res >> MC68000Helper.OVERCLOCK_FACTOR;
    }

    protected AddressSpace createAddressSpace() {
        return MC68000AddressSpace.createInstance(busProvider);
    }

    @Override
    public int getPrefetchWord() {
        return addressSpace.readWord(m68k.getPC()); //TODO do it properly
    }

    @Override
    public void addCyclePenalty(int value) {
        instCycles += value;
    }

    //this is the next instr PC
    @Override
    public int getPC() {
        return m68k.getPC();
    }

    public void setStop(boolean value) {
        if (stop != value) {
            LogHelper.logWarnOnceWhenEn(LOG, "{} stop: {}", cpu, value);
        }
        this.stop = value;
    }

    @Override
    public boolean isStopped() {
        return stop;
    }

    @Override
    public boolean raiseInterrupt(int level) {
        int ilev = m68k.getInterruptLevel();
        m68k.raiseInterrupt(level);
        return ilev != level && m68k.getInterruptLevel() == level;
    }

    @Override
    public void reset() {
        CpuDeviceAccess prev = MdRuntimeData.setAccessTypeExt(cpu);
        m68k.reset();
        MdRuntimeData.setAccessTypeExt(prev);
    }

    //X-men uses it
    @Override
    public void softReset() {
        reset();
        instCycles += 132;
        setStop(false);
    }

    @Override
    public String getInfo() {
        return MC68000Helper.dumpOp(m68k, currentPC);
    }

    public MC68000 getM68k() {
        return m68k;
    }

    private MC68000 createCpu(CpuConfig config) {
        return new MC68000(config) {
            @Override
            public void raiseException(int vector) {
                super.raiseException(vector);
                handleIntAck(vector);
                setStop(false);
            }

            //http://gendev.spritesmind.net/forum/viewtopic.php?t=1262
            @Override
            public void resetExternal() {
                //m68k asserts ZRES that cause z80, fm to be reset
                LOG.info("{} Reset External", cpu);
                busProvider.resetFrom68k();
            }

            @Override
            public void reset() {
                super.reset();
                resetExternal();
                LOG.info("{} Reset, PC: {}, SP: {}", cpu, th(m68k.getPC()), th(m68k.getSSP()));
            }

            @Override
            public void stop() {
                setStop(true);
                LogHelper.logWarnOnce(LOG, "{} Stop: true", cpu);
            }
        };
    }

    /**
     * Only for LEV4, LEV6 interrupts
     */
    private void handleIntAck(int vector) {
        if (vector == LEV4_EXCEPTION || vector == LEV6_EXCEPTION) {
            //interrupt processing time, the Ack happens after ~10cycles, we make it happen immediately
            instCycles += 44;
            busProvider.ackInterrupt68k(vector - EXCEPTION_OFFSET);
            stop = false;
        }
    }
}
