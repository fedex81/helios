/*
 * MekaStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 18:42
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

package omegadrive.savestate;

import omegadrive.bus.z80.Z80BusProvider;
import omegadrive.vdp.Tms9918aVdp;
import omegadrive.vdp.model.Tms9918a;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.IntStream;

public class TmsZ80StateHandler<T extends Z80BusProvider> extends Z80StateBaseHandler<T, Tms9918aVdp> {

    private static final Logger LOG = LogManager.getLogger(TmsZ80StateHandler.class.getSimpleName());

    @Override
    public void loadVdpContext(Tms9918aVdp vdp) {
        int[] vram = vdp.getVdpMemory().getVram();
        IntStream.range(0, Tms9918a.RAM_SIZE).forEach(i -> vram[i] = buffer.get() & 0xFF);
        IntStream.range(0, Tms9918a.REGISTERS).forEach(i -> vdp.updateRegisterData(i, buffer.get() & 0xFF));
    }

    @Override
    public void saveVdpContext(Tms9918aVdp vdp) {
        int[] vram = vdp.getVdpMemory().getVram();
        IntStream.range(0, Tms9918a.RAM_SIZE).forEach(i -> buffer.put((byte) (vram[i] & 0xFF)));
        IntStream.range(0, Tms9918a.REGISTERS).forEach(i -> buffer.put((byte) vdp.getRegisterData(i)));
    }

    @Override
    public void loadBusContext(T bus) {
        //do nothing
    }

    @Override
    public void saveBusContext(T bus) {
        //do nothing
    }
}
