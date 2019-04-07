/*
 * GenesisStateHandler
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

package omegadrive.savestate;

import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;

public interface GenesisStateHandler {

    GenesisStateHandler EMPTY_STATE = new GenesisStateHandler() {
        @Override
        public Type getType() {
            return null;
        }

        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public int[] getData() {
            return new int[0];
        }

        @Override
        public void loadFmState(FmProvider fm) {

        }

        @Override
        public void loadVdpState(BaseVdpProvider vdp) {

        }

        @Override
        public void loadZ80(Z80Provider z80, GenesisBusProvider bus) {

        }

        @Override
        public void load68k(MC68000Wrapper m68kProvider, IMemoryProvider memoryProvider) {

        }

        @Override
        public void saveFm(FmProvider fm) {

        }

        @Override
        public void saveVdp(BaseVdpProvider vdp) {

        }

        @Override
        public void saveZ80(Z80Provider z80, GenesisBusProvider bus) {

        }

        @Override
        public void save68k(MC68000Wrapper mc68000Wrapper, IMemoryProvider memoryProvider) {

        }

    };

    enum Type {SAVE, LOAD}

    Type getType();

    String getFileName();

    int[] getData();

    void loadFmState(FmProvider fm);

    void loadVdpState(BaseVdpProvider vdp);

    void loadZ80(Z80Provider z80, GenesisBusProvider bus);

    void load68k(MC68000Wrapper m68kProvider, IMemoryProvider memoryProvider);

    void saveFm(FmProvider fm);

    void saveVdp(BaseVdpProvider vdp);

    void saveZ80(Z80Provider z80, GenesisBusProvider bus);

    void save68k(MC68000Wrapper mc68000Wrapper, IMemoryProvider memoryProvider);

}
