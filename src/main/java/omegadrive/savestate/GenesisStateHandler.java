package omegadrive.savestate;

import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.MemoryProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.vdp.VdpProvider;
import omegadrive.z80.Z80Provider;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface GenesisStateHandler {

    GenesisStateHandler EMPTY_STATE = new GenesisStateHandler() {
        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public void loadFmState(FmProvider fm) {

        }

        @Override
        public void loadVdpState(VdpProvider vdp) {

        }

        @Override
        public void loadZ80(Z80Provider z80) {

        }

        @Override
        public void load68k(MC68000Wrapper m68kProvider, MemoryProvider memoryProvider) {

        }

    };

    String getFileName();

    void loadFmState(FmProvider fm);

    void loadVdpState(VdpProvider vdp);

    void loadZ80(Z80Provider z80);

    void load68k(MC68000Wrapper m68kProvider, MemoryProvider memoryProvider);

}
