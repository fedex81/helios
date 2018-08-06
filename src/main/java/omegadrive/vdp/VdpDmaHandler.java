package omegadrive.vdp;

import omegadrive.Genesis;
import omegadrive.bus.BusProvider;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 *
 * TODO thunderForce IV
 *
 */

/**
 * None of the DMA register settings are "cached", they are used live.
 * In particular, the DMA source address and transfer count are actively modified during DMA operations.
 * You can, for example, perform a DMA transfer for a count of 0x1000,
 * then only rewrite the lower transfer count byte with 0x80, and trigger another DMA transfer,
 * and it will only perform a transfer for 0x80 steps.
 * <p>
 * I can tell you that the DMA source address registers are never cleared under any circumstances,
 * but a DMA operation will actively modify them as it runs,
 * so you would expect the source address registers to be incremented by a DMA operation.
 * I can tell you that the correct behaviour is for the combined DMA source address registers 21 and 22
 * to be incremented by 1 on each DMA update step, with every DMA operation,
 * including a DMA fill for what it's worth, even though it doesn't use the source address.
 * DMA source address register 23 is never modified under any circumstances by the DMA operation.
 * This is why a DMA transfer wraps at 0x20000 byte boundaries:
 * it's unable to modify the upper DMA source address register.
 * If it was able to do this, it could inadvertantly modify the DMA mode during a DMA operation,
 * which would be very bad.
 * <p>
 * Here are some other mitigating factors which may cause you problems:
 * -DMA operations to invalid write targets still run to completion, the result is simply not stored,
 * so if a DMA operation is triggered to an invalid target, the DMA source address still needs to be updated.
 * -DMA operations always run to completion, they never abort, IE, when you reach the "end" of CRAM or VSRAM.
 * Writes to CRAM and VSRAM wrap at an 0x80 byte boundary.
 * Writes to the upper portion of VSRAM in this region (0x50-0x80) are discarded.
 * <p>
 * Any information you may read which is contrary to this info (IE, in genvdp.txt) is incorrect.
 * http://gendev.spritesmind.net/forum/viewtopic.php?f=5&t=908&p=15801&hilit=dma+wrap#p15801
 */
public class VdpDmaHandler {

    private static Logger LOG = LogManager.getLogger(VdpDmaHandler.class.getSimpleName());

    private VdpProvider vdpProvider;
    private BusProvider busProvider;

    private int dmaLen;
    private int sourceAddress;
    private int sourceAddressLowerBound;
    private int sourceAddressWrap;
    private int destAddress;
    private int dmaFillData;
    private int destAddressIncrement;
    private GenesisVdp.VramMode vramMode;

    private static boolean verbose = Genesis.verbose;

    //TODO this should be called by hcounter in runNew
    private static int BYTES_PER_CALL = 20;
    private static int DMA_ROM_SOURCE_ADDRESS_WRAP = 0x20000; //128Kbytes
    private static int DMA_RAM_SOURCE_ADDRESS_WRAP = 0x10000; //64Kbytes


    private DmaMode dmaMode;

    enum DmaMode {
        MEM_TO_VRAM, VRAM_FILL, VRAM_COPY;

        public static DmaMode getDmaMode(int reg17) {
            int dmaBits = reg17 >> 6;
            DmaMode mode = null;
            switch (dmaBits) {
                case 3:
                    mode = DmaMode.VRAM_COPY;
                    break;
                case 2:
                    mode = DmaMode.VRAM_FILL;
                    break;
                case 0: //fall-through
                case 1:
                    mode = DmaMode.MEM_TO_VRAM;
                    break;
            }
            return mode;
        }
    }

    public static VdpDmaHandler createInstance(VdpProvider vdpProvider, BusProvider busProvider) {
        VdpDmaHandler d = new VdpDmaHandler();
        d.vdpProvider = vdpProvider;
        d.busProvider = busProvider;
        return d;
    }

    public void setupDmaRegister(long commandWord) {
        dmaLen = getDmaLength();
        int reg22 = vdpProvider.getRegisterData(22);
        int reg21 = vdpProvider.getRegisterData(21);
        sourceAddress = (reg22 & 0xFF) << 8 | reg21;
        sourceAddressLowerBound = getDmaSourceLowerBound(sourceAddress);
        sourceAddressWrap = getDmaSourceWrap(sourceAddress);
        destAddress = (int) ((commandWord & 0x3) << 14 | ((commandWord & 0x3FFF_0000L) >> 16));
        destAddressIncrement = vdpProvider.getRegisterData(15);
        vramMode = vdpProvider.getVramMode();
        if (dmaMode == DmaMode.MEM_TO_VRAM) {
            sourceAddress = ((vdpProvider.getRegisterData(23) & 0x7F) << 16) | sourceAddress;
            dma68kToVram();
            dmaMode = null;
        } else {
            printInfo(dmaMode == DmaMode.VRAM_FILL ? "SETUP" : "START");
        }
    }

    public void setupDmaDataPort(int dataWord) {
        dmaFillData = dataWord;
        printInfo("START");
        dmaLen = decreaseDmaLength();
        vdpProvider.writeVramByte(destAddress ^ 1, dataWord & 0xFF);
        vdpProvider.writeVramByte(destAddress, (dataWord >> 8) & 0xFF);
        destAddress += 2;
    }


    private int getDmaLength() {
        return vdpProvider.getRegisterData(20) << 8 | vdpProvider.getRegisterData(19);
    }

    private void printInfo(String head) {
        if (!verbose) {
            return;
        }
        String str = dmaMode.name() + " " + head;
        String src = Long.toHexString(sourceAddress);
        String dest = Long.toHexString(destAddress);
        if (dmaMode == DmaMode.VRAM_COPY) {
            str += ", srcAddr: " + src + ", destAddr: " + dest +
                    ", destAddrInc: " + destAddressIncrement + ", dmaLen: " + dmaLen + ", vramMode: " + vramMode;
        }
        if (dmaMode == DmaMode.VRAM_FILL) {
            str += ", fillData: " + dmaFillData + ", destAddr: " + dest +
                    ", destAddrInc: " + destAddressIncrement + ", dmaLen: " + dmaLen + ", vramMode: " + vramMode;
        }
        if (dmaMode == DmaMode.MEM_TO_VRAM) {
            str += ", srcAddr: " + src + ", destAddr: " + dest +
                    ", destAddrInc: " + destAddressIncrement + ", dmaLen: " + dmaLen + ", vramMode: " + vramMode;
        }
        LOG.info(str);
    }

    public DmaMode getDmaMode() {
        return dmaMode;
    }

    public DmaMode getDmaMode(int reg17) {
        DmaMode mode = DmaMode.getDmaMode(reg17);
        if (dmaMode != mode) {
            Util.printLevelIfVerbose(LOG, Level.INFO, "Dma mode changed from : {} to {}", dmaMode, mode);
            dmaMode = mode;
        }
        return dmaMode;
    }

    //TODO this should be called by hcounter in runNew
    public boolean doDma() {
        boolean done = false;
        switch (dmaMode) {
            case VRAM_FILL:
                done = dmaFill();
                break;
            case VRAM_COPY:
                done = dmaCopy();
                break;
        }
        if (done) {
            printInfo("DONE");
            dmaMode = null;
        }
        updateVdpRegisters();
        return done;
    }

    private boolean dmaFill() {
        int count = BYTES_PER_CALL;
        boolean done;
        do {
            done = dmaFillSingleByte();
            count--;
        } while (count > 0 && !done);
        return done;
    }

    public boolean dmaFillSingleByte() {
        dmaLen--;
        printInfo("IN PROGRESS");
        int msb = (dmaFillData >> 8) & 0xFF;
        vdpProvider.writeVramByte(destAddress, msb);
        destAddress += destAddressIncrement;
        return dmaLen == 0;
    }

    //Games that use VRAM copies include Aleste, Bad Omen, and Viewpoint.
    //Langrisser II
    //James Pond 3 - Operation Starfish - some platforms requires correct VRAM Copy
    private boolean dmaCopy() {
        int count = BYTES_PER_CALL;
        boolean done;
        do {
            done = dmaCopySingleByte();
            count--;
        } while (count > 0 && !done);
        return done;
    }

    private boolean dmaCopySingleByte() {
        dmaLen--;
        int data = vdpProvider.readVramByte(sourceAddress);
        vdpProvider.writeVramByte(destAddress, data);
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

    private void dma68kToVram() {
        sourceAddress = sourceAddress << 1;  // needs to double it
        sourceAddressLowerBound = getDmaSourceLowerBound(sourceAddress);
        sourceAddressWrap = getDmaSourceWrap(sourceAddress);
        printInfo("START");
        boolean byteSwap = (destAddress & 1) == 0 && vramMode == GenesisVdp.VramMode.vramWrite;
        if (destAddress % 2 == 1) {
            LOG.warn("Should be even! " + destAddress);
        }
        do {
            dmaLen = decreaseDmaLength();
            int dataWord = (int) busProvider.read(sourceAddress, Size.WORD);
//            dataWord = byteSwap ? dataWord & 0xFF | dataWord >> 8 : dataWord;
            vdpProvider.videoRamWriteWordRaw(vramMode, dataWord, destAddress);
//            printInfo("IN PROGRESS");
            sourceAddress += 2;
            sourceAddress = wrapSourceAddress(sourceAddress);
            destAddress += destAddressIncrement;
        } while (dmaLen > 0);
        updateVdpRegisters();
        printInfo("DONE ");
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
        vdpProvider.updateRegisterData(19, dmaLen & 0xFF);
        vdpProvider.updateRegisterData(20, dmaLen >> 8);

        int reg22 = (sourceAddress >> 8) & 0xFF;
        int reg21 = sourceAddress & 0xFF;
        vdpProvider.updateRegisterData(21, reg21);
        vdpProvider.updateRegisterData(22, reg22);
    }

    public static void main(String[] args) {
        //srcAddr: 5fffc, destAddr: 8000, destAddrInc: 2, dmaLen: 4, vramMode: vramWrite
//        int start = 0xFFFFFC;
        int start = 0x5fffc;
        int lowerBound = getDmaSourceLowerBound(start);
        int wrap = getDmaSourceWrap(start);
        int cnt = 4;
        System.out.println(Long.toHexString(start));
        System.out.println(Long.toHexString(lowerBound));
        do {
            start += 2;
            long start1 = start;
            start = (start % wrap) + lowerBound;
            System.out.println(Long.toHexString(start) + " - " + Long.toHexString(start1));
        } while(--cnt > 0);
    }
}
