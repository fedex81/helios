package mcd.asic;

import mcd.asic.AsicModel.*;
import mcd.bus.McdSubInterruptHandler;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import static mcd.asic.AsicModel.StampRepeat.REPEAT_MAP;
import static mcd.asic.AsicModel.StampRepeat.vals;
import static mcd.bus.McdSubInterruptHandler.SubCpuInterrupt.INT_ASIC;
import static mcd.dict.MegaCdDict.*;
import static mcd.dict.MegaCdDict.RegSpecMcd.*;
import static omegadrive.util.BufferUtil.CpuDeviceAccess.SUB_M68K;
import static omegadrive.util.BufferUtil.*;
import static omegadrive.util.Util.readBufferWord;
import static omegadrive.util.Util.th;

/**
 *
 *
 * Federico Berti
 * <p>
 * Copyright 2024
 */
public class Asic implements AsicOp {

    private static final Logger LOG = LogHelper.getLogger(Asic.class.getSimpleName());

    private final boolean verbose = false;
    private StampConfig stampConfig = new StampConfig();

    private MegaCdMemoryContext memoryContext;
    private McdSubInterruptHandler interruptHandler;

    private AsicEvent asicEvent = AsicEvent.AS_STOP;

    public Asic(MegaCdMemoryContext memoryContext, McdSubInterruptHandler ih) {
        this.memoryContext = memoryContext;
        this.interruptHandler = ih;
    }

    @Override
    public int read(RegSpecMcd regSpec, int address, Size size) {
        return readBuffer(memoryContext.getRegBuffer(SUB_M68K, regSpec), address & MDC_SUB_GATE_REGS_MASK, size);
    }

    @Override
    public void write(RegSpecMcd regSpec, int address, int value, Size size) {
        writeBufferRaw(memoryContext.commonGateRegsBuf, address & MDC_SUB_GATE_REGS_MASK, value, size);
        if (regSpec != MCD_IMG_STAMP_SIZE && regSpec != MCD_IMG_OFFSET) {
            assert size == Size.WORD : regSpec + "," + size;
        }
        switch (regSpec) {
            case MCD_IMG_STAMP_SIZE -> {
                int val = readBufferWord(memoryContext.commonGateRegsBuf, regSpec.addr);
                assert (value >> 15) == 0; //cannot set GRON to 1
                stampConfig.stampRepeat = vals[val & 1];
                stampConfig.stampSize = StampSize.vals[(val >> 1) & 1];
                stampConfig.stampMapSize = StampMapSize.vals[(val >> 2) & 1];
            }
            case MCD_IMG_STAMP_MAP_ADDR -> {
                stampConfig.stampStartLocation = value;
            }
            case MCD_IMG_VCELL -> stampConfig.vCellSize = (value & 0x1F) + 1;
            case MCD_IMG_START_ADDR -> stampConfig.imgDestBufferLocation = value;
            case MCD_IMG_OFFSET -> {
                assert size == Size.BYTE ? (address & 1) == 1 : true;
                stampConfig.hPixelOffset = value & 7;
                stampConfig.vPixelOffset = (value >> 3) & 7;
                stampConfig.imgOffset = value & 0x3F;
            }
            case MCD_IMG_HDOT -> stampConfig.imgWidthPx = value & 0x1FF;
            case MCD_IMG_VDOT -> stampConfig.imgHeightPx = value & 0xFF;
            case MCD_IMG_TRACE_VECTOR_ADDR -> {
                stampConfig.imgTraceTableLocation = (value & ~1) << 2;
                cd_graphics_dst_y = stampConfig.vPixelOffset;
                asicEvent(AsicEvent.AS_START);
                gfxCycleCost();
            }
            default -> {
                LOG.error("Unhandled: {}, {} {}", regSpec, th(value), size);
                assert false;
            }
        }
    }


    @Override    //from MCD_MEM_MODE
    public void setStampPriorityMode(int value) {
        StampPriorityMode spm = StampPriorityMode.vals[value & 3];
        assert spm != StampPriorityMode.ILLEGAL; //Ecco does this
        if (spm != stampConfig.priorityMode) {
            if (verbose) LOG.info("StampPriorityMode: {} -> {}", stampConfig.priorityMode, spm);
            stampConfig.priorityMode = spm;
        }
    }

    @Override
    public StampPriorityMode getStampPriorityMode() {
        return stampConfig.priorityMode;
    }

    int cd_graphics_x, cd_graphics_dst_x, cd_graphics_y, cd_graphics_dst_y, cd_graphics_dx, cd_graphics_dy;
    int[] cd_graphics_pixels = new int[4];
    int cycles;

    private void doRenderLines(int num) {
        int target = Math.max(0, stampConfig.imgHeightPx - num);
        int line = stampConfig.imgHeightPx;
        do {
            doRenderingSlot();
            if (line != stampConfig.imgHeightPx) {
//                LOG.info("Line: {}", stampConfig.imgHeightPx);
            }
        } while (target != stampConfig.imgHeightPx);
    }

    private boolean doFetch = true;

    /**
     * Actual impl lifted from Blastem
     */
    private void doRenderingSlot() {
        cycles = 0;
        int hLimit = stampConfig.imgWidthPx + stampConfig.hPixelOffset;
        for (int i = 0; i < 4; i++) {
            if (doFetch) {
                int tvb = (readBuffer(memoryContext.commonGateRegsBuf, MCD_IMG_TRACE_VECTOR_ADDR.addr, Size.WORD) & Size.WORD.getMask()) << 2;
                //FETCH X
                cd_graphics_x = memoryContext.wramHelper.readWordRam(SUB_M68K, tvb, Size.WORD) << 8;
                cycles += 4 * 3;
                cd_graphics_dst_x = stampConfig.hPixelOffset;

                //FETCH Y
                cd_graphics_y = memoryContext.wramHelper.readWordRam(SUB_M68K, tvb + 2, Size.WORD) << 8;
                cycles += 4 * 2;

                //FETCH DX
                cd_graphics_dx = memoryContext.wramHelper.readWordRam(SUB_M68K, tvb + 4, Size.WORD);
                if ((cd_graphics_dx & 0x8000) > 0) {
                    cd_graphics_dx |= 0xFFFF_0000;
                }
                cycles += 4 * 2;

                //FETCH DY
                cd_graphics_dy = memoryContext.wramHelper.readWordRam(SUB_M68K, tvb + 6, Size.WORD);
                if ((cd_graphics_dy & 0x8000) > 0) {
                    cd_graphics_dy |= 0xFFFF_0000;
                }
                cycles += 4 * 2;
            }

            //PIXEL_I
            cd_graphics_pixels[i] = get_src_pixel();
            if ((cd_graphics_dst_x & 3) == 3 - i || cd_graphics_dst_x + i + 1 == hLimit) {
                drawPixel();
                break;
            }
            cycles += 4 * 2;
        }
    }

    private void drawPixel() {
        int to_draw = 4 - (cd_graphics_dst_x & 3);
        int x_end = stampConfig.imgWidthPx + stampConfig.hPixelOffset;
        if (cd_graphics_dst_x + to_draw > x_end) {
            to_draw = stampConfig.imgWidthPx + stampConfig.hPixelOffset - cd_graphics_dst_x;
        }
        for (int i = 0; i < to_draw; i++) {
            int dst_address = stampConfig.imgDestBufferLocation << 1;
            dst_address += cd_graphics_dst_y << 1;
            dst_address += (cd_graphics_dst_x >> 2) & 1;
            dst_address += ((cd_graphics_dst_x >>> 3) * (stampConfig.vCellSize)) << 4;
            int pixel_shift = 12 - 4 * (cd_graphics_dst_x & 3);
            int pixel = cd_graphics_pixels[i] << pixel_shift;
            int src_mask_check = 0xf << pixel_shift;
            int src_mask_keep = ~src_mask_check;
            pixel &= src_mask_check;
            int wramVal = memoryContext.wramHelper.readWordRam(SUB_M68K, dst_address << 1, Size.WORD);
            wramVal = switch (stampConfig.priorityMode) {
                case PM_OFF -> (wramVal & src_mask_keep) | pixel;
                case UNDERWRITE -> {
                    if (pixel > 0 && (wramVal & src_mask_check) == 0) {
                        wramVal = (wramVal & src_mask_keep) | pixel;
                    }
                    yield wramVal;
                }
                case OVERWRITE -> {
                    if (pixel > 0) {
                        wramVal = (wramVal & src_mask_keep) | pixel;
                    }
                    yield wramVal;
                }
                case ILLEGAL -> {
                    assert false;
                    yield wramVal;
                }
            };
            memoryContext.wramHelper.writeWordRam(SUB_M68K, dst_address << 1, wramVal, Size.WORD);
            cd_graphics_dst_x++;
        }
        doFetch = false;
        if (cd_graphics_dst_x == x_end) {
            cd_graphics_dst_y++;
            stampConfig.imgHeightPx--;
            writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_IMG_VDOT.addr, stampConfig.imgHeightPx, Size.WORD);
            int tvb = readBuffer(memoryContext.commonGateRegsBuf, MCD_IMG_TRACE_VECTOR_ADDR.addr, Size.WORD);
            writeBufferRaw(memoryContext.commonGateRegsBuf, MCD_IMG_TRACE_VECTOR_ADDR.addr, tvb + 2, Size.WORD);
            doFetch = true;
            if (stampConfig.imgHeightPx == 0) {
                asicEvent(AsicEvent.AS_STOP);
//                    printWram(memoryContext);
            }
        }
    }

    public static void printWram(MegaCdMemoryContext mc) {
        StringBuilder sb = new StringBuilder();
        for (int i = START_MCD_SUB_WORD_RAM_2M; i < END_MCD_SUB_WORD_RAM_2M; i += 2) {
            sb.append((mc.wramHelper.readWordRam(SUB_M68K, i, Size.WORD) & 0xFFFF) + ",");
            if (((i + 2) >> 1) % 16 == 0) {
                sb.append("\n");
            }
        }
        System.out.println(sb);
    }

    private int get_src_pixel() {
        int x = cd_graphics_x >>> 11;
        int y = cd_graphics_y >>> 11;
        cd_graphics_x += cd_graphics_dx;
        cd_graphics_x &= 0xFF_FFFF;
        cd_graphics_y += cd_graphics_dy;
        cd_graphics_y &= 0xFF_FFFF;
        int stamp_shift, pixel_mask, stamp_num_mask;
        if (stampConfig.stampSize == StampSize._32x32) {
            stamp_shift = 5;
            pixel_mask = 0x1f;
            stamp_num_mask = 0x7fc;
        } else {
            stamp_shift = 4;
            pixel_mask = 0xf;
            stamp_num_mask = 0x7ff;
        }
        int stamp_x = x >>> stamp_shift; //uint16_t
        int stamp_y = y >>> stamp_shift; //uint16_t
        int max, base_mask;   //uint16_t
        int row_shift; //uint32_t
        if (stampConfig.stampMapSize == StampMapSize._4096x4096) {
            max = 4096 >> stamp_shift;
            base_mask = 0xE000 << ((5 - stamp_shift) << 1);
            //128 stamps in 32x32 mode, 256 stamps in 16x16 mode
            row_shift = 12 - stamp_shift;
        } else {
            max = 256 >> stamp_shift;
            base_mask = 0xFFE0 << ((5 - stamp_shift) << 1);
            //8 stamps in 32x32 mode, 16 stamps in 16x16 mode
            row_shift = 8 - stamp_shift;
        }
        if (stamp_x >= max || stamp_y >= max) {
            if (stampConfig.stampRepeat == REPEAT_MAP) {
                stamp_x &= max - 1;
                stamp_y &= max - 1;
            } else {
                return 0;
            }
        }
        int address = (stampConfig.stampStartLocation & base_mask) << 1;
        address += (stamp_y << row_shift) + stamp_x;
        int stamp_def = memoryContext.wramHelper.readWordRam(SUB_M68K, address << 1, Size.WORD);
        int stamp_num = stamp_def & stamp_num_mask;
        if (stamp_num == 0) {
            //manual says stamp 0 can't be used, I assume that means it's treated as transparent
            return 0;
        }
        int pixel_x = x & pixel_mask;
        int pixel_y = y & pixel_mask;
        if ((stamp_def & 0x8000) > 0) { //HFLIP
            pixel_x = pixel_mask - pixel_x;
        }
        int tmp;
        switch ((stamp_def >> 13) & 3) {
            case 1 -> {
                tmp = pixel_y;
                pixel_y = pixel_x;
                pixel_x = pixel_mask - tmp;
            }
            case 2 -> {
                pixel_y = pixel_mask - pixel_y;
                pixel_x = pixel_mask - pixel_x;
            }
            case 3 -> {
                tmp = pixel_y;
                pixel_y = pixel_mask - pixel_x;
                pixel_x = tmp;
            }
        }
        int cell_x = pixel_x >> 3;
        assert cell_x >= 0;
        int pixel_address = stamp_num << 6;
        pixel_address += (pixel_y << 1) + (cell_x << (stamp_shift + 1)) + ((pixel_x >> 2) & 1);
        int word = memoryContext.wramHelper.readWordRam(SUB_M68K, pixel_address << 1, Size.WORD);
        return switch (pixel_x & 3) {
            case 1 -> (word >> 8) & 0xF;
            case 2 -> (word >> 4) & 0xF;
            case 3 -> word & 0xF;
            default -> (word >> 12) & 0xF; //case 0
        };
    }

    int cycleCost;

    public int gfxCycleCost() {
        // vsize * (13 + 2 * hoffset + 9 * (hdots + hoffset - 1))
        //with an additional 13? cycle setup cost per line
        cycleCost = 4 * stampConfig.imgHeightPx *
                (13 + 2 * stampConfig.hPixelOffset + 9 * (stampConfig.imgWidthPx + stampConfig.hPixelOffset - 1));
//        System.out.println(cycleCost);
        return cycleCost;
    }

    private void asicEvent(AsicEvent event) {
        if (event == asicEvent) {
            assert (readBufferWord(memoryContext.commonGateRegsBuf, MCD_IMG_STAMP_SIZE.addr) >>> 15) == event.ordinal();
            return;
        }
        setBit(memoryContext.commonGateRegsBuf, MCD_IMG_STAMP_SIZE.addr, 15, event.ordinal(), Size.WORD);
        if (asicEvent != event && event == AsicEvent.AS_STOP) {
            interruptHandler.raiseInterrupt(INT_ASIC);
        }
        asicEvent = event;
    }

    //called at 32.5Khz, 0.0307 ms
    //12_500_000/32.5Khz = 384
    //max 32_500 lines/s, 521 lines/frame
    @Override
    public void step(int cycles) {
        if (asicEvent != AsicEvent.AS_START || stampConfig.imgHeightPx == 0) {
            return;
        }
//        printWram(memoryContext);
        //bios_EU likes 75
        doRenderLines(75);
    }
}