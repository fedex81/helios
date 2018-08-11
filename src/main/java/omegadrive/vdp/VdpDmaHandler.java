package omegadrive.vdp;

import omegadrive.bus.BusProvider;
import omegadrive.util.Size;
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
 *
 *     //Games that use VRAM copies include Aleste, Bad Omen, and Viewpoint.
 *     //Langrisser II
 *     //James Pond 3 - Operation Starfish - some platforms requires correct VRAM Copy
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
    private GenesisVdp.VramMode vramDestination;

    private static boolean verbose = GenesisVdp.verbose;

    private static int DMA_ROM_SOURCE_ADDRESS_WRAP = 0x20000; //128Kbytes
    private static int DMA_RAM_SOURCE_ADDRESS_WRAP = 0x10000; //64Kbytes


    private DmaMode dmaMode = null;
    private boolean dmaFillReady;

    /**
     * Bits CD3-CD0
     * 0000b : VRAM read
     * 0001b : VRAM write
     * 0011b : CRAM write
     * 0100b : VSRAM read
     * 0101b : VSRAM write
     * 1000b : CRAM read
     */
    enum DmaMode {
        MEM_TO_VRAM, VRAM_FILL, VRAM_COPY;
    }

    public static VdpDmaHandler createInstance(VdpProvider vdpProvider, BusProvider busProvider) {
        VdpDmaHandler d = new VdpDmaHandler();
        d.vdpProvider = vdpProvider;
        d.busProvider = busProvider;
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

    public void setupDma(VdpProvider.VramMode vramMode, long data, boolean m1) {
        dmaMode = getDmaMode(vdpProvider.getRegisterData(23), vramMode);
        if (!checkSetup(m1, data)) {
            return;
        }
        switch (dmaMode) {
            case MEM_TO_VRAM:
                //doesnt need to set dma in the SR
                setupDmaRegister(data);
                break;
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
        }
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
            dma68kToVram();
        } else {
            printInfo(dmaMode == DmaMode.VRAM_FILL ? "SETUP" : "START");
        }
    }

    public void setupDmaDataPort(int dataWord) {
        dmaFillData = dataWord;
        printInfo("START");
        vdpProvider.writeVramByte(destAddress ^ 1, dataWord & 0xFF);
        vdpProvider.writeVramByte(destAddress, (dataWord >> 8) & 0xFF);
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


    //TODO add 68k_VPD dma
    public boolean doDma(int byteSlots) {
        boolean done = false;
        switch (dmaMode) {
            case VRAM_FILL:
                if (dmaFillReady) {
                    done = dmaFill(byteSlots);
                    updateVdpRegisters();
                }
                break;
            case VRAM_COPY:
                done = dmaCopy(byteSlots / 2);
                updateVdpRegisters();
                break;
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
        printInfo("IN PROGRESS");
        int msb = (dmaFillData >> 8) & 0xFF;
        vdpProvider.writeVramByte(destAddress, msb);
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
        do {
            dmaLen = decreaseDmaLength();
            int dataWord = (int) busProvider.read(sourceAddress, Size.WORD);
            vdpProvider.writeVideoRamWord(vramDestination, dataWord, destAddress);
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
                mode = DmaMode.VRAM_COPY;
                if (vramMode == VdpProvider.VramMode.vramRead) {
                    vramDestination = vramMode;
                    break;
                }
                //fall-through
            case 2:
                mode = DmaMode.VRAM_FILL;
                if (vramMode == VdpProvider.VramMode.vramWrite) {
                    vramDestination = vramMode;
                    break;
                }
                //fall-through
            case 0:
                //fall-through
            case 1:
                if (vramMode.isWriteMode()) {
                    mode = DmaMode.MEM_TO_VRAM;
                    vramDestination = vramMode;
                    break;
                }
                //fall-through
            default:
                LOG.error("Unexpected setup: " + mode + ", vramDestination: " + vramMode);
                mode = null;
        }
        return mode;
    }

    public static void main(String[] args) {
        //m1 masks cd5
        int firstWrite = 65000;
        int codeRegister = 0x3E;
        int m1 = 0;

        System.out.println(Integer.toBinaryString(firstWrite >> 14));
        System.out.println(Integer.toBinaryString(codeRegister << 2));
        codeRegister = (codeRegister << 2 | firstWrite >> 14) & 0x3F;
        System.out.println(Integer.toBinaryString(codeRegister));
        System.out.println(Integer.toBinaryString((m1 << 5) | 0x1F));
        codeRegister &= ((m1 << 5) | 0x1F);
        System.out.println(Integer.toBinaryString(codeRegister));
    }
}
