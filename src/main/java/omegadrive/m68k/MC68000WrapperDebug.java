/*
 * MC68000WrapperDebug
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 12/07/19 20:51
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

import omegadrive.bus.gen.GenesisBusProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CyclicBarrier;

/**
 * TODO shouldnt be a separate class
 */
public class MC68000WrapperDebug extends MC68000Wrapper {

    private static Logger LOG = LogManager.getLogger(MC68000WrapperDebug.class.getSimpleName());

    private CyclicBarrier stepBarrier = new CyclicBarrier(1);

    public MC68000WrapperDebug(GenesisBusProvider busProvider) {
        super(busProvider);
    }

    @Override
    public int runInstruction() {
        int res = 0;
        try {
            printVerbose();
            printCpuStateIfVerbose("");
            res = super.runInstruction();
            //TODO partial fix JimPower
//            if(currentPC == 0x3f8e && getM68k().getAddrRegisterLong(6) == 0xffffec7e){
//                getM68k().setAddrRegisterLong(6, 0xffffec80);
//            }
            stepBarrier.await();
        } catch (Exception e) {
            LOG.error("68k error", e);
        }
        return res;
    }

    protected void handleException(int vector) {
        if (vector == LEV4_EXCEPTION && vector == LEV6_EXCEPTION) {
            return;
        }
        printCpuState("Exception: " + vector);
        if (vector == ILLEGAL_ACCESS_EXCEPTION) {
            if (STOP_ON_EXCEPTION) {
                setStop(true);
            }
        }
    }

    @Override
    public boolean raiseInterrupt(int level) {
        m68k.raiseInterrupt(level);
        boolean raise = m68k.getInterruptLevel() == level;
        if (raise) {
            LOG.info("M68K before INT, level: {}, newLevel: {}", m68k.getInterruptLevel(), level);
        }
        return raise;
    }

    public void doStep() {
        try {
            stepBarrier.await();
        } catch (Exception e) {
            LOG.error("barrier error", e);
        }
    }

    protected void printCpuState(String head) {
        MC68000Helper.printCpuState(m68k, Level.INFO, head, addressSpace.size());
    }

    protected void printCpuStateIfVerbose(String head) {
        if (!verbose) {
            return;
        }
        MC68000Helper.printCpuState(m68k, Level.INFO, head, addressSpace.size());
    }

    protected void printVerbose() {
        if (!verbose) {
            return;
        }
        try {
            String res = MC68000Helper.dumpOp(m68k);
            LOG.info(res);
            if (MC68000Helper.addToInstructionSet(m68k)) {
                LOG.info(MC68000Helper.dumpInstructionSet());
            }
        } catch (Exception e) {
            String pc = Long.toHexString(m68k.getPC() & 0xFF_FFFF);
            LOG.warn("Unable to dump the instruction: " + pc, e);
        }
    }
}
