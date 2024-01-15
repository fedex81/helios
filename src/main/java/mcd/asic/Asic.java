package mcd.asic;

import mcd.asic.AsicModel.StampConfig;
import mcd.asic.AsicModel.StampMapSize;
import mcd.asic.AsicModel.StampPriorityMode;
import mcd.asic.AsicModel.StampSize;
import mcd.dict.MegaCdDict.RegSpecMcd;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import static mcd.dict.MegaCdDict.SUB_CPU_REGS_MASK;
import static mcd.util.FixedPointUtil.convert13p3FixedPointUnsigned;
import static mcd.util.FixedPointUtil.convert5p11FixedPointSigned;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.writeBufferRaw;
import static omegadrive.util.Util.readBufferWord;
import static omegadrive.util.Util.th;

/**
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class Asic {

    private static final Logger LOG = LogHelper.getLogger(Asic.class.getSimpleName());
    private StampConfig stampConfig = new StampConfig();

    private MegaCdMemoryContext memoryContext;

    public Asic(MegaCdMemoryContext memoryContext) {
        this.memoryContext = memoryContext;
    }

    public void write(RegSpecMcd regSpec, int address, int value, Size size) {
        writeBufferRaw(memoryContext.commonGateRegsBuf, address & SUB_CPU_REGS_MASK, value, size);
        if (regSpec != RegSpecMcd.MCD_IMG_STAMP_SIZE) {
            assert size == Size.WORD;
        }
        switch (regSpec) {
            case MCD_IMG_STAMP_SIZE -> {
                int val = readBufferWord(memoryContext.commonGateRegsBuf, regSpec.addr);
                assert (value >> 15) == 0; //cannot set GRON to 1
                stampConfig.stampRepeat = AsicModel.StampRepeat.vals[val & 1];
                stampConfig.stampSize = StampSize.vals[(val >> 1) & 1];
                stampConfig.stampMapSize = StampMapSize.vals[(val >> 2) & 1];
            }
            case MCD_IMG_STAMP_MAP_ADDR -> {
                stampConfig.stampStartLocation = value << 2;
            }
            case MCD_IMG_VCELL -> stampConfig.vCellSize = (value & 0x1F) + 1;
            case MCD_IMG_START_ADDR -> stampConfig.imgDestBufferLocation = value << 2;
            case MCD_IMG_OFFSET -> {
                stampConfig.hPixelOffset = value & 7;
                stampConfig.vPixelOffset = (value >> 3) & 7;
            }
            case MCD_IMG_HDOT -> stampConfig.imgWidthPx = value & 0x1FF;
            case MCD_IMG_VDOT -> stampConfig.imgHeightPx = value & 0xFF;
            case MCD_IMG_TRACE_VECTOR_ADDR -> {
                stampConfig.imgTraceTableLocation = (value & ~1) << 2;
                startRendering();
            }
            default -> LOG.error("Unhandled: {}, {} {}", regSpec, th(value), size);
        }
    }

    //from MCD_MEM_MODE
    public void setStampPriorityMode(int value) {
        StampPriorityMode spm = StampPriorityMode.vals[value & 3];
        assert spm != StampPriorityMode.ILLEGAL;
        if (spm != stampConfig.priorityMode) {
            LOG.info("StampPriorityMode: {} -> {}", stampConfig.priorityMode, spm);
            stampConfig.priorityMode = spm;
        }
    }

    private void startRendering() {
        LOG.info("Start rendering, config:\n{}", stampConfig);
        int totalBytes = stampConfig.imgHeightPx * stampConfig.imgWidthPx;
        //for each line
        for (int y = 0; y < stampConfig.imgHeightPx; y++) {
            //trace table 8 bytes per line, fixed point values
            int xposFP = memoryContext.readWordRam(SUB_M68K, stampConfig.imgTraceTableLocation + y, Size.WORD);
            int yposFP = memoryContext.readWordRam(SUB_M68K, stampConfig.imgTraceTableLocation + 1, Size.WORD);
            int xdeltaFP = memoryContext.readWordRam(SUB_M68K, stampConfig.imgTraceTableLocation + 2, Size.WORD);
            int ydeltaFP = memoryContext.readWordRam(SUB_M68K, stampConfig.imgTraceTableLocation + 3, Size.WORD);
            double xpos = convert13p3FixedPointUnsigned(xposFP);
            double ypos = convert13p3FixedPointUnsigned(yposFP);
            double xdelta = convert5p11FixedPointSigned(xdeltaFP);
            double ydelta = convert5p11FixedPointSigned(ydeltaFP);
            for (int x = 0; x < stampConfig.imgWidthPx; x++) {
                //LOG.info("Line: {}, px: {}", y, x);
//                    int stampstampConfig.stampStartLocation + (xpos * 1) + ypos;
            }
        }

        for (int i = 0; i < totalBytes; i++) {
            int val = memoryContext.readWordRam(SUB_M68K, stampConfig.stampStartLocation + i, Size.WORD);
            memoryContext.writeWordRam(SUB_M68K, i + stampConfig.imgDestBufferLocation, val, Size.WORD);
        }
    }
}