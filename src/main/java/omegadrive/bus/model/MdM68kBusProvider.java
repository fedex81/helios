/*
 * MdBusProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:16
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

package omegadrive.bus.model;

import com.google.common.base.MoreObjects;
import omegadrive.system.SystemProvider;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

/**
 * A bus with a M68K cpu connected
 */
public interface MdM68kBusProvider extends BaseBusProvider {

    Logger LOG = LogHelper.getLogger(MdM68kBusProvider.class.getSimpleName());

    abstract class BusWriteRunnable implements Runnable {
        public CpuDeviceAccess cpu;
        public int address;
        public int data;
        public Size size;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("address", address)
                    .add("data", data)
                    .add("size", size)
                    .toString();
        }
    }

    void handleVdpInterrupts68k();

    void ackInterrupt68k(int level);

    /**
     * VRES is fed to 68000 for 128 VCLKs (16.7us); ZRES is fed
     * to the z80 and ym2612, and remains asserted until the 68000 does something to
     * deassert it; VDP and IO chip are unaffected.
     */
    void resetFrom68k();

    boolean is68kRunning();

    SystemProvider getSystem();
}
