/*
 * GenesisZ80BusProvider
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

package omegadrive.bus.gen;

import omegadrive.bus.BaseBusProvider;
import omegadrive.memory.IMemoryRam;
import omegadrive.z80.Z80Memory;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface GenesisZ80BusProvider extends BaseBusProvider {

    Logger LOG = LogManager.getLogger(GenesisZ80BusProvider.class.getSimpleName());

    int Z80_RAM_MEMORY_SIZE = 0x2000;


    static GenesisZ80BusProvider createInstance(GenesisBusProvider genesisBusProvider) {
        IMemoryRam ram = new Z80Memory(Z80_RAM_MEMORY_SIZE);
        GenesisZ80BusProvider b = new GenesisZ80BusProviderImpl();
        b.attachDevice(genesisBusProvider).attachDevice(ram);
        return b;
    }

    void setRomBank68kSerial(int romBank68kSerial);

    int getRomBank68kSerial();

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

    static int getRomBank68kSerial(Z80Provider z80) {
        if (z80.getZ80BusProvider() instanceof GenesisZ80BusProvider) {
            GenesisZ80BusProvider g = (GenesisZ80BusProvider) z80.getZ80BusProvider();
            return g.getRomBank68kSerial();
        }
        return -1;
    }

    static void setRomBank68kSerial(Z80Provider z80, int romBank68kSerial) {
        BaseBusProvider bus = z80.getZ80BusProvider();
        if (bus instanceof GenesisZ80BusProvider) {
            GenesisZ80BusProvider genBus = (GenesisZ80BusProvider) bus;
            genBus.setRomBank68kSerial(romBank68kSerial);
        }
    }
}
