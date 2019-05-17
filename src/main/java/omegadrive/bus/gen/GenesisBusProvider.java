/*
 * GenesisBusProvider
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

package omegadrive.bus.gen;

import omegadrive.bus.BaseBusProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.sound.psg.PsgProvider;
import omegadrive.system.SystemProvider;
import omegadrive.vdp.model.GenesisVdpProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface GenesisBusProvider extends BaseBusProvider {

    int Z80_ADDRESS_SPACE_START = 0xA00000;
    int Z80_ADDRESS_SPACE_END = 0xA0FFFF;
    int IO_ADDRESS_SPACE_START = 0xA10000;
    int IO_ADDRESS_SPACE_END = 0xA10FFF;
    int INTERNAL_REG_ADDRESS_SPACE_START = 0xA11000;
    int INTERNAL_REG_ADDRESS_SPACE_END = 0xBFFFFF;
    int VDP_ADDRESS_SPACE_START = 0xC00000;
    int VDP_ADDRESS_SPACE_END = 0xDFFFFF;
    int ADDRESS_UPPER_LIMIT = 0xFFFFFF;
    int ADDRESS_RAM_MAP_START = 0xE00000;
    int M68K_TO_Z80_MEMORY_MASK = 0x7FFF;

    long DEFAULT_ROM_END_ADDRESS = 0x3F_FFFF;

    int FIFO_FULL_MASK = 0x01;
    int DMA_IN_PROGRESS_MASK = 0x02;

    Logger LOG = LogManager.getLogger(GenesisBusProvider.class.getSimpleName());

    static GenesisBusProvider createBus() {
        return new GenesisBus();
    }

    void handleVdpInterrupts68k();

    void handleVdpInterruptsZ80();


    /**
     * VRES is fed to 68000 for 128 VCLKs (16.7us); ZRES is fed
     * to the z80 and ym2612, and remains asserted until the 68000 does something to
     * deassert it; VDP and IO chip are unaffected.
     */
    void resetFrom68k();

    boolean shouldStop68k();

    //VDP setting this
    void setStop68k(int mask);

    boolean isZ80Running();

    boolean isZ80ResetState();

    boolean isZ80BusRequested();

    void setZ80ResetState(boolean z80ResetState);

    void setZ80BusRequested(boolean z80BusRequested);

    PsgProvider getPsg();

    FmProvider getFm();

    SystemProvider getSystem();

    GenesisVdpProvider getVdp();

    //Z80 for genesis doesnt do IO
    @Override
    default int readIoPort(int port) {
        //TF4 calls this by mistake
        LOG.debug("inPort: {}", port);
        return 0xFF;
    }

    //Z80 for genesis doesnt do IO
    @Override
    default void writeIoPort(int port, int value) {
        LOG.warn("outPort: " + port + ", data: " + value);
        return;
    }
}
