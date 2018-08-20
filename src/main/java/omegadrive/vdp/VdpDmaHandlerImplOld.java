package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.bus.BusProvider;
import omegadrive.util.Size;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.VdpDmaHandler;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 *
 */
@Deprecated
public class VdpDmaHandlerImplOld implements VdpDmaHandler {

    private static Logger LOG = LogManager.getLogger(VdpDmaHandlerImplOld.class.getSimpleName());

    public static boolean verbose = false || Genesis.verbose;

    private VdpProvider vdpProvider;
    private VdpMemoryInterface memoryInterface;
    private BusProvider busProvider;

    private int dmaLen;
    private int sourceAddress;
    private int sourceAddressLowerBound;
    private int sourceAddressWrap;
    private int destAddress;
    private int dmaFillData;
    private int destAddressIncrement;
    private GenesisVdp.VdpRamType vramDestination;

    private static int DMA_ROM_SOURCE_ADDRESS_WRAP = 0x20000; //128Kbytes
    private static int DMA_RAM_SOURCE_ADDRESS_WRAP = 0x10000; //64Kbytes


    private VdpDmaHandler.DmaMode dmaMode = null;
    private boolean dmaFillReady;

    public static VdpDmaHandlerImplOld createInstance(VdpProvider vdpProvider, VdpMemoryInterface memoryInterface,
                                                      BusProvider busProvider) {
        VdpDmaHandlerImplOld d = new VdpDmaHandlerImplOld();
        d.vdpProvider = vdpProvider;
        d.busProvider = busProvider;
        d.memoryInterface = memoryInterface;
        return d;
    }

    private boolean checkSetup(boolean m1, long data) {
        if (!m1 && dmaMode != null) {
            //Andre Agassi needs it
            LOG.warn("Attempting DMA but m1 not set: " + dmaMode + ", data: " + data);
        } else if (!m1) {
            LOG.warn("Attempting DMA but m1 not set: " + dmaMode + ", data: " + data);
            return false;
        } else if (dmaMode == null) {
            return false;
        }
        return true;
    }

    public DmaMode setupDma(VdpProvider.VramMode vramMode, long data, boolean m1) {
        dmaMode = getDmaMode(vdpProvider.getRegisterData(23), vramMode);
        if (!checkSetup(m1, data)) {
            return null;
        }
        switch (dmaMode) {
            case MEM_TO_VRAM:
                //fall-through
            //on DMA Fill, busy flag is actually immediately (?) set after the CTRL port write,
            //not the DATA port write that starts the Fill operation
            case VRAM_FILL:
                //fall-through
            case VRAM_COPY:
                vdpProvider.setDmaFlag(1);
                setupDmaRegister(data);
                break;
            default:
                LOG.error("Unexpected DMA mode: " + dmaMode + ",vramMode: " + vramMode);
                dmaMode = null;
        }
        return dmaMode;
    }

    private void setupDmaRegister(long commandWord) {
        dmaLen = getDmaLength();
        int reg22 = vdpProvider.getRegisterData(22);
        int reg21 = vdpProvider.getRegisterData(21);
        sourceAddress = (reg22 & 0xFF) << 8 | reg21;
        sourceAddressLowerBound = getDmaSourceLowerBound(sourceAddress);
        sourceAddressWrap = getDmaSourceWrap(sourceAddress);
        destAddress = (int) ((commandWord & 0x3) << 14 | ((commandWord & 0x3FFF_0000L) >> 16));
        destAddressIncrement = vdpProvider.getRegisterData(15);
        if (dmaMode == DmaMode.MEM_TO_VRAM) {
            sourceAddress = ((vdpProvider.getRegisterData(23) & 0x7F) << 16) | sourceAddress;
            sourceAddress = sourceAddress << 1;  // needs to double it
            sourceAddressLowerBound = getDmaSourceLowerBound(sourceAddress);
            sourceAddressWrap = getDmaSourceWrap(sourceAddress);
        } else {
            printInfo(dmaMode == DmaMode.VRAM_FILL ? "SETUP" : "START");
        }
    }

    public void setupDmaDataPort(int dataWord) {
        dmaFillData = dataWord;
        printInfo("START");
        memoryInterface.writeVramByte(destAddress ^ 1, dataWord & 0xFF);
        memoryInterface.writeVramByte(destAddress, (dataWord >> 8) & 0xFF);
        dmaFillReady = true;
    }


    private int getDmaLength() {
        return vdpProvider.getRegisterData(20) << 8 | vdpProvider.getRegisterData(19);
    }

    private void printInfo(String head) {
        if (!verbose) {
            return;
        }
        String str = Objects.toString(dmaMode) + " " + head;
        String src = Long.toHexString(sourceAddress);
        String dest = Long.toHexString(destAddress);
        if (dmaMode == DmaMode.VRAM_COPY) {
            str += ", srcAddr: " + src + ", destAddr: " + dest +
                    ", destAddrInc: " + destAddressIncrement + ", dmaLen: " + dmaLen + ", vramDestination: " + vramDestination;
        }
        if (dmaMode == DmaMode.VRAM_FILL) {
            str += ", fillData: " + dmaFillData + ", destAddr: " + dest +
                    ", destAddrInc: " + destAddressIncrement + ", dmaLen: " + dmaLen + ", vramDestination: " + vramDestination;
        }
        if (dmaMode == DmaMode.MEM_TO_VRAM) {
            str += ", srcAddr: " + src + ", destAddr: " + dest +
                    ", destAddrInc: " + destAddressIncrement + ", dmaLen: " + dmaLen + ", vramDestination: " + vramDestination;
        }
        LOG.info(str);
    }

    public DmaMode getDmaMode() {
        return dmaMode;
    }

    public boolean doDma(VideoMode videoMode, boolean isBlanking) {
        boolean done = false;
        int byteSlots = getDmaSlotsPerLine(dmaMode, videoMode, isBlanking);
        switch (dmaMode) {
            case VRAM_FILL:
                if (dmaFillReady) {
                    done = dmaFill(byteSlots);
                    updateVdpRegisters();
                }
                break;
            case VRAM_COPY:
                done = dmaCopy(byteSlots);
                updateVdpRegisters();
                break;
            case MEM_TO_VRAM:
                done = dma68kToVram(byteSlots);
                updateVdpRegisters();
                break;
            default:
                LOG.error("Unexpected dma setting: {}", dmaMode);
        }
        if (done) {
            printInfo("DONE");
            dmaMode = null;
            dmaFillReady = false;
        }

        return done;
    }

    private boolean dmaFill(int byteSlots) {
        int count = byteSlots;
        boolean done;
        do {
            done = dmaFillSingleByte();
            count--;
        } while (count > 0 && !done);
        return done;
    }

    public boolean dmaFillSingleByte() {
        dmaLen = decreaseDmaLength();
//        printInfo("IN PROGRESS");
        int msb = (dmaFillData >> 8) & 0xFF;
        memoryInterface.writeVramByte(destAddress, msb);
        destAddress += destAddressIncrement;
        return dmaLen == 0;
    }

    private boolean dmaCopy(int byteSlots) {
        int count = byteSlots;
        boolean done;
        do {
            done = dmaCopySingleByte();
            count--;
        } while (count > 0 && !done);
        return done;
    }

    private boolean dmaCopySingleByte() {
        dmaLen = decreaseDmaLength();
//        printInfo("IN PROGRESS");
        int data = memoryInterface.readVramByte(sourceAddress);
        memoryInterface.writeVramByte(destAddress, data);
        sourceAddress++;
        sourceAddress = wrapSourceAddress(sourceAddress);
        destAddress += destAddressIncrement;
        return dmaLen == 0;
    }

    //The VDP decrements the length before checking if it's equal to 0,
    //which results in an integer underflow if the length is 0. In other words, if you set the DMA length to 0,
    //it will act like you set it to $10000.
    private int decreaseDmaLength() {
        return (dmaLen - 1) & (VdpProvider.VDP_VRAM_SIZE - 1);
    }

    private boolean dma68kToVram(int byteSlots) {
        byteSlots = vramDestination == VdpProvider.VdpRamType.VRAM ? byteSlots : byteSlots * 2;
        printInfo("START, Dma byteSlots: " + byteSlots);
        do {
            //dmaLen is words
            dmaLen = decreaseDmaLength();
            byteSlots -= 2;
            int dataWord = (int) busProvider.read(sourceAddress, Size.WORD);
            memoryInterface.writeVideoRamWord(vramDestination, dataWord, destAddress);
//            printInfo("IN PROGRESS");
            sourceAddress += 2;
            sourceAddress = wrapSourceAddress(sourceAddress);
            destAddress += destAddressIncrement;
        } while (dmaLen > 0 && byteSlots > 0);
        printInfo("Byte slots remaining: " + byteSlots);
        return dmaLen == 0;
    }

    private static int getDmaSourceLowerBound(int sourceAddress) {
        return sourceAddress - (sourceAddress % getDmaSourceWrap(sourceAddress));
    }

    private static int getDmaSourceWrap(int sourceAddress) {
        return sourceAddress >= BusProvider.ADDRESS_RAM_MAP_START ? DMA_RAM_SOURCE_ADDRESS_WRAP
                : DMA_ROM_SOURCE_ADDRESS_WRAP;
    }

    private int wrapSourceAddress(int sourceAddress) {
        return (sourceAddress % sourceAddressWrap) + sourceAddressLowerBound;
    }

    private void updateVdpRegisters() {
        dmaLen = Math.max(dmaLen, 0);
        vdpProvider.updateRegisterData(19, dmaLen & 0xFF);
        vdpProvider.updateRegisterData(20, dmaLen >> 8);

        int reg22 = (sourceAddress >> 8) & 0xFF;
        int reg21 = sourceAddress & 0xFF;
        vdpProvider.updateRegisterData(21, reg21);
        vdpProvider.updateRegisterData(22, reg22);
    }

    private DmaMode getDmaMode(int reg17, VdpProvider.VramMode vramMode) {
        int dmaBits = reg17 >> 6;
        DmaMode mode = null;
        switch (dmaBits) {
            case 3:
                //For DMA copy, CD0-CD3 are ignored.
                // You can only perform a DMA copy within VRAM.
                mode = DmaMode.VRAM_COPY;
                vramDestination = VdpProvider.VdpRamType.VRAM;
                break;
                //fall-through
            case 2:
                mode = DmaMode.VRAM_FILL;
                if (vramMode == VdpProvider.VramMode.vramWrite) {
                    vramDestination = vramMode.getRamType();
                    break;
                }
                //fall-through
            case 0:
                //fall-through
            case 1:
                if (vramMode.isWriteMode()) {
                    mode = DmaMode.MEM_TO_VRAM;
                    vramDestination = vramMode.getRamType();
                    break;
                }
                //fall-through
            default:
                LOG.error("Unexpected setup: " + mode + ", vramDestination: " + vramMode);
                mode = null;
        }
        return mode;
    }

    private int getDmaSlotsPerLine(DmaMode dmaMode, VideoMode videoMode, boolean isBlanking) {
        int slots = 0;
        switch (dmaMode) {
            case MEM_TO_VRAM:
                slots = videoMode.isH32() ?
                        (isBlanking ? 167 : 16) : //H32
                        (isBlanking ? 205 : 18);  //H40
                break;
            case VRAM_FILL:
                slots = videoMode.isH32() ?
                        (isBlanking ? 166 : 15) : //H32
                        (isBlanking ? 204 : 17);  //H40
                break;
            case VRAM_COPY:
                slots = videoMode.isH32() ?
                        (isBlanking ? 83 : 8) : //H32
                        (isBlanking ? 102 : 9);  //H40
                break;
        }
        printInfo("Dma byteSlots: " + slots + ", isBlanking: " + isBlanking);
        return slots;
    }


    public static void main(String[] args) {
        long firstWrite = 0x4002;
        long all = ((firstWrite << 16) | 0x8002);
//        Integer.MAX_VALUE = 7fff_ffff
        System.out.println(firstWrite);
        System.out.println(all);

    }
}
