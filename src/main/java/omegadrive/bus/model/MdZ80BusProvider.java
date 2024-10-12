/*
 * MdZ80BusProvider
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 18/06/19 13:56
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

import omegadrive.bus.md.MdZ80BusProviderImpl;
import omegadrive.cpu.z80.Z80Memory;
import omegadrive.cpu.z80.Z80Provider;
import omegadrive.memory.IMemoryRam;
import omegadrive.util.LogHelper;
import org.slf4j.Logger;

public interface MdZ80BusProvider extends BaseBusProvider {

    Logger LOG = LogHelper.getLogger(MdZ80BusProvider.class.getSimpleName());

    int END_RAM = 0x3FFF;
    int START_YM2612 = 0x4000;
    int END_YM2612 = 0x5FFF;
    int START_ROM_BANK_ADDRESS = 0x6000;
    int END_ROM_BANK_ADDRESS = 0x60FF;
    int START_UNUSED = 0x6100;
    int END_UNUSED = 0x7EFF;
    int START_VDP = 0x7F00;
    int END_VDP_VALID = 0x7F1F;
    int END_VDP = 0x7FFF;
    int START_68K_BANK = 0x8000;
    int END_68K_BANK = 0xFFFF;

    int VDP_BASE_ADDRESS = 0xC00000;
    int M68K_BANK_MASK = 0x7FFF;

    int ROM_BANK_POINTER_SIZE = 9;

    //z80 should incur a 3.5 z80 cycles penalty when accessing 68k bus
    int Z80_CYCLE_PENALTY = 4;
    int M68K_CYCLE_PENALTY = 11;
    int Z80_RAM_MEMORY_SIZE = 0x2000;


    static MdZ80BusProvider createInstance(BaseBusProvider busProvider) {
        IMemoryRam ram = new Z80Memory(Z80_RAM_MEMORY_SIZE);
        MdZ80BusProvider b = new MdZ80BusProviderImpl();
        b.attachDevices(busProvider, ram);
        return b;
    }

    void setRomBank68kSerial(int romBank68kSerial);

    int getRomBank68kSerial();

    //Z80 for genesis doesnt do IO
    @Override
    default int readIoPort(int port) {
        //TF4 calls this by mistake
        //LOG.debug("inPort: {}", port);
        return 0xFF;
    }

    //Z80 for genesis doesnt do IO
    @Override
    default void writeIoPort(int port, int value) {
        LOG.warn("outPort: {}, data: {}", port, value);
    }

    static int getRomBank68kSerial(Z80Provider z80) {
        if (z80.getZ80BusProvider() instanceof MdZ80BusProvider g) {
            return g.getRomBank68kSerial();
        }
        return -1;
    }

    static void setRomBank68kSerial(Z80Provider z80, int romBank68kSerial) {
        BaseBusProvider bus = z80.getZ80BusProvider();
        if (bus instanceof MdZ80BusProvider genBus) {
            genBus.setRomBank68kSerial(romBank68kSerial);
        }
    }
}
