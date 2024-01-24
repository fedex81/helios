package mcd.asic;

import mcd.asic.AsicModel.StampConfig;
import mcd.asic.AsicModel.StampMapSize;
import mcd.asic.AsicModel.StampPriorityMode;
import mcd.asic.AsicModel.StampSize;
import mcd.bus.MegaCdSubCpuBus;
import mcd.dict.MegaCdDict.RegSpecMcd;
import mcd.dict.MegaCdMemoryContext;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import org.slf4j.Logger;

import java.util.function.Function;

import static mcd.asic.AsicModel.StampRepeat.REPEAT_MAP;
import static mcd.asic.AsicModel.StampRepeat.vals;
import static mcd.dict.MegaCdDict.SUB_CPU_REGS_MASK;
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
                stampConfig.hPixelOffset = value & 7;
                stampConfig.vPixelOffset = (value >> 3) & 7;
                stampConfig.imgOffset = value & 0x3F;
            }
            case MCD_IMG_HDOT -> stampConfig.imgWidthPx = value & 0x1FF;
            case MCD_IMG_VDOT -> stampConfig.imgHeightPx = value & 0xFF;
            case MCD_IMG_TRACE_VECTOR_ADDR -> {
                stampConfig.imgTraceTableLocation = (value & ~1) << 2;
                int v = stampConfig.imgHeightPx;
                int traceAddr = stampConfig.imgTraceTableLocation;
                // image.address = (image.base << 1) + image.offset;
                int addr = (stampConfig.imgDestBufferLocation << 2) + stampConfig.imgOffset;
                assert stampConfig.imgOffset == 0; //TODO check
                MegaCdSubCpuBus.asicEvent(memoryContext.commonGateRegsBuf, 1);
                for (; ; addr += 4) {
                    startRendering(addr, stampConfig.imgWidthPx, traceAddr);
                    traceAddr += 8;
                    if (--v == 0) {
                        //done + irq
                        break;
                    }
                }
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

    private void startRendering(int address, int width, int traceAddress) {
        final Function<Integer, Integer> r16 = addr -> memoryContext.readWordRam(SUB_M68K, addr, Size.WORD);
//        Util.sleep(60_000);

        int stampMapAddr = stampConfig.stampStartLocation << 1;
        if (stampConfig.stampMapSize.ordinal() == 0 && stampConfig.stampSize.ordinal() == 0)
            stampMapAddr &= 0x1ff00;  // A9-A17
        if (stampConfig.stampMapSize.ordinal() == 0 && stampConfig.stampSize.ordinal() == 1)
            stampMapAddr &= 0x1ffc0;  // A5-A17
        if (stampConfig.stampMapSize.ordinal() == 1 && stampConfig.stampSize.ordinal() == 0)
            stampMapAddr &= 0x10000;  //    A17
        if (stampConfig.stampMapSize.ordinal() == 1 && stampConfig.stampSize.ordinal() == 1)
            stampMapAddr &= 0x1c000;  //A15-A17

        int stampShift = 4;
        int mapShift = 4 << stampConfig.stampMapSize.ordinal();
        int indexMask = 0x7FF; //n11
        int pixelOffsetMask = 0xf;

        if (stampConfig.stampSize == StampSize._32x32) {
            stampShift++;
            mapShift--;
            indexMask &= ~3;
            pixelOffsetMask |= 0x10;
        }

        int imageWidth = stampConfig.vCellSize << 6; //n19
        int mapMask = stampConfig.stampMapSize == StampMapSize._256x256 ? 0x07ffff : 0x7fffff;

        //13.3 -> 13.11
        int x = r16.apply(traceAddress) << 8;
        int y = r16.apply(traceAddress + 2) << 8;

        //5.11
        int xstep = r16.apply(traceAddress + 4);
        int ystep = r16.apply(traceAddress + 6);

//        System.out.println(traceAddress + "\t" + x);
//        System.out.println((traceAddress+2) + "\t" + y);
//        System.out.println((traceAddress+4) + "\t" + xstep);
//        System.out.println((traceAddress+6) + "\t" + ystep);

//        sleep(1);
        int output = 0; //uint4
        while (width-- > 0) {
            if (stampConfig.stampRepeat == REPEAT_MAP) {
                x &= mapMask;
                y &= mapMask;
            }
            //            if(bool outside = (x | y) & ~mapMask; !outside) { ??
            boolean outside = ((x | y) & ~mapMask) > 0;
            if (!outside) {
                int xtrunc = x >>> 11;
                int ytrunc = y >>> 11;
                int xstamp = xtrunc >>> stampShift;
                int ystamp = ytrunc >>> stampShift;

                int val = (stampMapAddr + xstamp + (ystamp << mapShift)) << 1;
                int mapEntry = r16.apply(val);
//            System.out.println(width + "\t" + val + "\t" + mapEntry);
                int index = mapEntry & indexMask;
                int lroll = (mapEntry >> 13) & 1;  //0 = 0 degrees; 1 =  90 degrees
                int hroll = (mapEntry >> 14) & 1;  //0 = 0 degrees; 1 = 180 degrees
                int hflip = (mapEntry >> 15) & 1;

                assert index >= 0;
                //stamp index 0 is not rendered
                if (index > 0) {
                    if (hflip > 0) {
                        xtrunc = ~xtrunc;
                    }
                    if (hroll > 0) {
                        xtrunc = ~xtrunc;
                        ytrunc = ~ytrunc;
                    }
                    if (lroll > 0) {
                        int temp = xtrunc;
                        xtrunc = ~ytrunc;
                        ytrunc = temp;
                    }

                    int xpixel = xtrunc & pixelOffsetMask; //n5
                    int ypixel = ytrunc & pixelOffsetMask; //n5
                    //A stamp ID is calculated by taking a stamp's location
                    //relative to the start of Word RAM and dividing it by 0x80
                    int outIdx = (index << 8) | ((xpixel & ~7) << stampShift) | (ypixel << 3) | (xpixel & 7);
                    outIdx >>= 1;
                    output = readNibble(outIdx, r16.apply(outIdx));
//                    System.out.println(width + "\t" + outIdx + "\t" + output);
                }
            }
            //TODO check this, only PM_OFF works?
            int input = readNibble(address, r16.apply(address)); //n4
//            System.out.println(width + "\t" + address + "\t" + input);
            assert stampConfig.priorityMode == StampPriorityMode.PM_OFF;
            output = switch (stampConfig.priorityMode) {
                case PM_OFF -> output;
                case UNDERWRITE -> input > 0 ? input : output;
                case OVERWRITE -> output > 0 ? output : input;
                case ILLEGAL -> input;
            };
            writeNibble(address, output);
//            System.out.println(width + "\t" + th(address) + "\t" + getLowBitNibble(address) + "\t" + th(output));
            if (width == 0xFF) {
//                System.out.println(width + "\t" + address + "\t" + output);
            }
            if ((++address & 7) == 0) address += (imageWidth >> 1) - 8;

            x += xstep;
            y += ystep;
        }
    }

    public static int getLowBitNibble(int address) {
        int lowBit = 12 - ((address & 3) << 2);
        if ((address & 1) > 0 && lowBit > 4) {
            lowBit -= 8;
        } else if ((address & 1) > 0) {
        } else {
            lowBit = (address & 2) > 0 ? lowBit + 8 : lowBit;
        }
        assert lowBit >= 0 && lowBit <= 12;
        return lowBit;
    }

    public static int readNibble(int address, int wramWord) {
        int lb = getLowBitNibble(address);
        return (wramWord >> lb) & 0xF;
    }

    private void writeNibble(int address, int nibbleValue) {
        final int nibblePos = getLowBitNibble(address);
        int val = memoryContext.readWordRamWord(SUB_M68K, address);
        int mask = switch (nibblePos) {
            case 0 -> 0xFFF0;
            case 4 -> 0xFF0F;
            case 8 -> 0xF0FF;
            case 12 -> 0x0FFF;
            default -> 0;
        };
        val = (val & mask) | (nibbleValue << nibblePos);
        memoryContext.writeWordRamWord(SUB_M68K, address, val);
    }
}