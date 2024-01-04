package mcd.util;

import com.google.common.collect.ObjectArrays;
import mcd.McdDeviceHelper.McdLaunchContext;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.memory.ReadableByteMemory;
import omegadrive.util.BufferUtil;
import omegadrive.util.Util;
import omegadrive.vdp.model.VdpMemoryInterface;
import omegadrive.vdp.util.MemView;
import omegadrive.vdp.util.UpdatableViewer;
import omegadrive.vdp.util.VdpDebugView;
import s32x.util.Md32xRuntimeData;

import java.util.function.BiFunction;

import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdMemoryContext.*;
import static mcd.pcm.McdPcm.PCM_WAVE_DATA_SIZE;
import static omegadrive.vdp.util.MemView.MemViewOwner.MCD_SUB_CPU;

/**
 * Federico Berti
 * <p>
 * Copyright 2023
 */
public class McdMemView extends MemView {

    public static final MemViewData[] mcdMemViewData =
            ObjectArrays.concat(mdMemViewData, McdMemViewType.values(), MemViewData.class);

    public static UpdatableViewer createInstance(GenesisBusProvider m, McdLaunchContext ctx,
                                                 VdpMemoryInterface vdpMem) {
        return VdpDebugView.DEBUG_VIEWER_ENABLED ? new McdMemView(m, ctx, vdpMem) : NO_MEMVIEW;
    }

    protected McdMemView(GenesisBusProvider m, McdLaunchContext ctx, VdpMemoryInterface vdpMem) {
        super(mcdMemViewData, m, getReader(ctx), vdpMem);

    }

    private final static int WAVE_DATA_OFFSET = 0xAA00_0000;
    private final static int WAVE_DATA_START = WAVE_DATA_OFFSET;
    private final static int WAVE_DATA_END = WAVE_DATA_START + PCM_WAVE_DATA_SIZE;
    private final static int WAVE_DATA_MASK = PCM_WAVE_DATA_SIZE - 1;

    private static ReadableByteMemory getReader(final McdLaunchContext ctx) {
        return (a, size) -> {
            int res = 0;
            if (a >= START_MCD_SUB_WORD_RAM_1M && a < END_MCD_SUB_WORD_RAM_1M) { //bank0
                res = Util.readDataMask(ctx.memoryContext.wordRam01[0], size, a, MCD_WORD_RAM_1M_MASK);
            } else if (a >= START_MCD_SUB_WORD_RAM_2M && a < START_MCD_SUB_WORD_RAM_2M + MCD_WORD_RAM_1M_SIZE) {
                res = Util.readDataMask(ctx.memoryContext.wordRam01[1], size, a, MCD_WORD_RAM_1M_MASK);
            } else if (a >= START_MCD_SUB_PRG_RAM && a < END_MCD_SUB_PRG_RAM) {
                res = Util.readDataMask(ctx.memoryContext.prgRam, size, a, MCD_PRG_RAM_SIZE - 1);
            } else if (a >= WAVE_DATA_START && a < WAVE_DATA_END) {
                res = BufferUtil.readBuffer(ctx.pcm.getWaveData(), a & WAVE_DATA_MASK, size);
            }
            return res;
        };
    }

    enum McdMemViewType implements MemViewData {
        MCD_PRG_RAM(MCD_SUB_CPU, START_MCD_SUB_PRG_RAM, END_MCD_SUB_PRG_RAM),
        MCD_WRAM0(MCD_SUB_CPU, START_MCD_SUB_WORD_RAM_1M, END_MCD_SUB_WORD_RAM_1M),
        MCD_WRAM1(MCD_SUB_CPU, START_MCD_SUB_WORD_RAM_2M, START_MCD_SUB_WORD_RAM_2M + MCD_WORD_RAM_1M_SIZE),
        MCD_PCM_WAVE_DATA(MCD_SUB_CPU, WAVE_DATA_START, WAVE_DATA_END),
        ;

        public final int start;
        public final int end;
        public final MemViewOwner owner;

        McdMemViewType(MemViewOwner c, int s, int e) {
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
        int v = Md32xRuntimeData.getCpuDelayExt();
//        if (current == S32X_PALETTE) {
//            doMemoryRead_WordBE(current, len);
//        } else {
        super.doMemoryRead(current, len, readerFn);
//        }
//        Md32xRuntimeData.resetCpuDelayExt(v);
    }
}
