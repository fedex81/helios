package omegadrive.savestate;

import omegadrive.m68k.MC68000Wrapper;
import omegadrive.memory.IMemoryProvider;
import omegadrive.sound.fm.FmProvider;
import omegadrive.vdp.model.BaseVdpProvider;
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
        public void loadZ80(Z80Provider z80) {

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
        public void saveZ80(Z80Provider z80) {

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

    void loadZ80(Z80Provider z80);

    void load68k(MC68000Wrapper m68kProvider, IMemoryProvider memoryProvider);

    void saveFm(FmProvider fm);

    void saveVdp(BaseVdpProvider vdp);

    void saveZ80(Z80Provider z80);

    void save68k(MC68000Wrapper mc68000Wrapper, IMemoryProvider memoryProvider);

}
