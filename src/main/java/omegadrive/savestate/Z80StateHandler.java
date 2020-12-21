/*
 * SmsStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 18:30
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
import omegadrive.memory.IMemoryProvider;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;

public interface Z80StateHandler<T extends Z80BusProvider, V extends BaseVdpProvider> extends BaseStateHandler {

    Z80StateHandler EMPTY_STATE = new Z80StateHandler() {
        @Override
        public void loadVdpContext(BaseVdpProvider vdp) {

        }

        @Override
        public void loadZ80Context(Z80Provider z80) {

        }

        @Override
        public void loadBusContext(Z80BusProvider bus) {

        }

        @Override
        public void saveBusContext(Z80BusProvider bus) {

        }

        @Override
        public void saveVdpContext(BaseVdpProvider vdp) {

        }

        @Override
        public void saveZ80Context(Z80Provider z80) {

        }

        @Override
        public void loadMemory(IMemoryProvider mem) {

        }

        @Override
        public void saveMemory(IMemoryProvider mem) {

        }

        @Override
        public Type getType() {
            return null;
        }

        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public byte[] getData() {
            return new byte[0];
        }
    };

    default void processState(V vdp, Z80Provider z80, T bus, IMemoryProvider mem) {
        if (getType() == Type.LOAD) {
            loadZ80Context(z80);
            loadVdpContext(vdp);
            loadMemory(mem);
            loadBusContext(bus);
            LOG.info("Savestate loaded from: {}", getFileName());
        } else {
            saveZ80Context(z80);
            saveVdpContext(vdp);
            saveMemory(mem);
            saveBusContext(bus);
        }
    }

    void loadVdpContext(V vdp);

    void loadZ80Context(Z80Provider z80);

    void loadBusContext(T bus);

    void saveBusContext(T bus);

    void saveVdpContext(V vdp);

    void saveZ80Context(Z80Provider z80);

    void loadMemory(IMemoryProvider mem);

    void saveMemory(IMemoryProvider mem);
}
