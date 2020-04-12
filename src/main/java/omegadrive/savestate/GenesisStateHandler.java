/*
 * GenesisStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 20:37
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
import omegadrive.m68k.M68kProvider;
import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.SoundProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

public interface GenesisStateHandler extends BaseStateHandler {

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
        public byte[] getData() {
            return new byte[0];
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

    void loadFmState(FmProvider fm);

    void loadVdpState(BaseVdpProvider vdp);

    void loadZ80(Z80Provider z80, GenesisBusProvider bus);

    void load68k(MC68000Wrapper m68kProvider, IMemoryProvider memoryProvider);

    void saveFm(FmProvider fm);

    void saveVdp(BaseVdpProvider vdp);

    void saveZ80(Z80Provider z80, GenesisBusProvider bus);

    void save68k(MC68000Wrapper mc68000Wrapper, IMemoryProvider memoryProvider);

    static GenesisStateHandler createLoadInstance(String fileName) {
        GshStateHandler h = new GshStateHandler();
        h.type = Type.LOAD;
        h.init(fileName);
        GenesisStateHandler res = h.detectStateFileType();
        return res;
    }

    static GenesisStateHandler createSaveInstance(String fileName) {
        GshStateHandler h = new GshStateHandler();
        h.type = Type.SAVE;
        h.init(fileName);
        return h;
    }

    default void processState(BaseVdpProvider vdp, Z80Provider z80, GenesisBusProvider bus,
                              SoundProvider sound, M68kProvider cpu, IMemoryProvider mem) {
        Level prev = LogManager.getRootLogger().getLevel();
        try {
            Configurator.setRootLevel(Level.ERROR);
            if (getType() == Type.LOAD) {
                loadFmState(sound.getFm());
                loadVdpState(vdp);
                loadZ80(z80, bus);
                load68k((MC68000Wrapper) cpu, mem);
            } else {
                saveFm(sound.getFm());
                saveZ80(z80, bus);
                save68k((MC68000Wrapper) cpu, mem);
                saveVdp(vdp);
            }
        } finally {
            Configurator.setRootLevel(prev);
        }
        if (getType() == Type.LOAD) {
            LOG.info("Savestate loaded from: {}", getFileName());
        }
    }

}
