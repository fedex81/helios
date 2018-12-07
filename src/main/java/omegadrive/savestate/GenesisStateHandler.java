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
        public void loadFmState(FmProvider fm, int[] stateData) {

        }

        @Override
        public void loadVdpState(VdpProvider vdp, int[] data) {

        }

        @Override
        public void loadZ80(Z80Provider z80, int[] data) {

        }

        @Override
        public void load68k(MC68000Wrapper m68kProvider, MemoryProvider memoryProvider, int[] data) {

        }

    };

    void loadFmState(FmProvider fm, int[] stateData);

    void loadVdpState(VdpProvider vdp, int[] data);

    void loadZ80(Z80Provider z80, int[] data);

    void load68k(MC68000Wrapper m68kProvider, MemoryProvider memoryProvider, int[] data);

}
