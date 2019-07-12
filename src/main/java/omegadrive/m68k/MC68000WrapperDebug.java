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

    public void doStep() {
        try {
            stepBarrier.await();
        } catch (Exception e) {
            LOG.error("barrier error", e);
        }
    }
}
