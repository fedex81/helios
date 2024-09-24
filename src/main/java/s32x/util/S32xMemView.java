package s32x.util;

import com.google.common.collect.ObjectArrays;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.MdRuntimeData;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.util.MemView;
import omegadrive.vdp.util.UpdatableViewer;
import omegadrive.vdp.util.VdpDebugView;
import s32x.dict.S32xDict;

import java.util.function.BiFunction;

import static omegadrive.vdp.util.MemView.MemViewOwner.M68K;
import static omegadrive.vdp.util.MemView.MemViewOwner.SH2;
import static s32x.util.S32xMemView.S32xMemViewType.S32X_PALETTE;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class S32xMemView extends MemView {

    public static final MemViewData[] s32xMemViewData =
            ObjectArrays.concat(mdMemViewData, S32xMemViewType.values(), MemViewData.class);

    public static UpdatableViewer createInstance(GenesisBusProvider m, ReadableByteMemory s32x,
                                                 VdpMemoryInterface vdpMem) {
        return VdpDebugView.DEBUG_VIEWER_ENABLED ? new S32xMemView(m, s32x, vdpMem) : NO_MEMVIEW;
    }

    protected S32xMemView(GenesisBusProvider m, ReadableByteMemory s32x, VdpMemoryInterface vdpMem) {
        super(s32xMemViewData, m, s32x, vdpMem);
    }

    enum S32xMemViewType implements MemViewData {
        S32X_SDRAM(SH2, S32xDict.SH2_START_SDRAM, S32xDict.SH2_END_SDRAM),
        S32X_FRAMEBUFFER(SH2, S32xDict.START_DRAM, S32xDict.END_DRAM),
        S32X_PALETTE(SH2, S32xDict.START_32X_COLPAL, S32xDict.END_32X_COLPAL),
        M68K_ROM(M68K, S32xDict.M68K_START_ROM_MIRROR, S32xDict.M68K_END_ROM_MIRROR), //aden=1, in general
        M68K_VECTOR_ROM(M68K, 0, S32xDict.M68K_END_VECTOR_ROM),
        ;

        public final int start;
        public final int end;
        public final MemViewOwner owner;

        S32xMemViewType(MemViewOwner c, int s, int e) {
            start = s;
            end = e;
            owner = c;
        }

        @Override
        public int getStart() {
            return start;
        }

        @Override
        public int getEnd() {
            return end;
        }

        @Override
        public MemViewOwner getOwner() {
            return owner;
        }
    }

    @Override
    protected void doMemoryRead(MemViewData current, int len, BiFunction<MemViewData, Integer, Integer> readerFn) {
        int v = MdRuntimeData.getCpuDelayExt();
        if (current == S32X_PALETTE) {
            doMemoryRead_WordBE(current, len);
        } else {
            super.doMemoryRead(current, len, readerFn);
        }
        MdRuntimeData.resetCpuDelayExt(v);
    }
}
