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

package omegadrive.m68k;

import m68k.cpu.MC68000;
import m68k.cpu.instructions.TAS;
import m68k.memory.AddressSpace;
import omegadrive.bus.gen.GenesisBusProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * NOTES: f-line emulator. Tecmo Cup
 *        a-line emulator. Mamono Hunter Yohko
 *        reset. Bug Hunt (homebrew)
 *        stop. Hardball III
 *        add z-flag. sgdk-partic.bin
 */
public class MC68000Wrapper implements M68kProvider {


    private final static Logger LOG = LogManager.getLogger(MC68000Wrapper.class.getSimpleName());
    public static final boolean STOP_ON_EXCEPTION;
    public static final boolean GENESIS_TAS_BROKEN;

    static {
        STOP_ON_EXCEPTION =
                Boolean.valueOf(System.getProperty("68k.stop.on.exception", "true"));
        GENESIS_TAS_BROKEN = Boolean.valueOf(System.getProperty("68k.broken.tas", "true"));
        if (GENESIS_TAS_BROKEN != TAS.EMULATE_BROKEN_TAS) {
            LOG.info("Overriding 68k TAS broken setting: " + GENESIS_TAS_BROKEN);
        }
    }

    protected MC68000 m68k;
    protected AddressSpace addressSpace;
    private GenesisBusProvider busProvider;
    private boolean stop;
    protected int currentPC;
    protected int instCycles = 0;

    public MC68000Wrapper(GenesisBusProvider busProvider) {
        this.m68k = createCpu();
        this.busProvider = busProvider;
        this.addressSpace = MC68000AddressSpace.createInstance(busProvider);
        m68k.setAddressSpace(addressSpace);
        TAS.EMULATE_BROKEN_TAS = GENESIS_TAS_BROKEN;
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
            handleException(ILLEGAL_ACCESS_EXCEPTION);
            if (STOP_ON_EXCEPTION) {
                MC68000Helper.printCpuState(m68k, Level.ERROR, "", addressSpace.size());
                throw e;
            }
        }
        return res;
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
    public long getPC() {
        return m68k.getPC();
    }

    protected void setStop(boolean value) {
//        LOG.debug("M68K stop: {}", value);
        this.stop = value;
    }

    @Override
    public boolean isStopped() {
        return stop;
    }

    @Override
    public boolean raiseInterrupt(int level) {
        m68k.raiseInterrupt(level);
        return m68k.getInterruptLevel() == level;
    }

    @Override
    public void reset() {
        m68k.reset();
    }

    @Override
    public String getInfo() {
        return MC68000Helper.dumpOp(m68k, currentPC);
    }

    public MC68000 getM68k() {
        return m68k;
    }

    private MC68000 createCpu() {
        return new MC68000() {
            @Override
            public void raiseException(int vector) {
                handleException(vector);
                super.raiseException(vector);
                handleException(vector);
                setStop(false);
            }

            @Override
            public void resetExternal() {
                LOG.info("Reset External");
                busProvider.resetFrom68k();
            }

            @Override
            public void reset() {
                LOG.info("Reset");
                super.reset();
                resetExternal(); //TODO why?
            }

            @Override
            public void stop() {
                setStop(true);
            }
        };
    }

    protected void handleException(int vector) {
        //DO NOTHING
    }
}
