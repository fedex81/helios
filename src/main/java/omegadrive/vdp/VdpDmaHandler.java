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
public class VdpDmaHandler {

    private static Logger LOG = LogManager.getLogger(VdpDmaHandler.class.getSimpleName());

    private VdpProvider vdpProvider;
    private BusProvider busProvider;

    private int dmaLen;
    private int sourceAddress;
    private int destAddress;
    private int dmaFillData;
    private int destAddressIncrement;
    private GenesisVdp.VramMode vramMode;

    private static boolean verbose = Genesis.verbose;

    //TODO this should be called by hcounter in runNew
    private static int BYTES_PER_CALL = 2;


    private DmaMode dmaMode;

    enum DmaMode {
        MEM_TO_VRAM, VRAM_FILL, VRAM_COPY;
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
        vdpProvider.writeVramByte(destAddress, dataWord & 0xFF);
        vdpProvider.writeVramByte(destAddress ^ 1, (dataWord >> 8) & 0xFF);
        printInfo("START");
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

    public static DmaMode getDmaModeStatic(int reg17) {
        int dmaBits = reg17 >> 6;
        DmaMode mode = (dmaBits & 0b10) < 2 ? DmaMode.MEM_TO_VRAM : DmaMode.VRAM_FILL;
        mode = (dmaBits & 0b11) == 3 ? DmaMode.VRAM_COPY : mode;
        return mode;
    }

    public DmaMode getDmaMode(int reg17) {
        DmaMode mode = getDmaModeStatic(reg17);
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
        dmaLen = decreaseDmaLength();
//        printInfo("IN PROGRESS");
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
        dmaLen = decreaseDmaLength();
        int data = vdpProvider.readVramByte(sourceAddress);
        vdpProvider.writeVramByte(destAddress, data);
        sourceAddress++;
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
//            LOG.info("srcAdd: " + Integer.toHexString(sourceAddress));
            sourceAddress += 2;
            if (sourceAddress > BusProvider.ADDRESS_UPPER_LIMIT) {
                sourceAddress = (sourceAddress & BusProvider.ADDRESS_UPPER_LIMIT) + 0xFF0000;
                LOG.warn("DMA sourceAddr overflow");
            }
            destAddress += destAddressIncrement;
        } while (dmaLen > 0);
        printInfo("DONE");
    }

    public static void main(String[] args) {
        long res = 3221225600L;
        long res1 = 2013266051L;
        long d1 = (int) (res & 0x3) << 14 | ((res & 0x3FFF_0000L) >> 16);
        long d2 = (int) (res1 & 0x3) << 14 | ((res1 & 0x3FFF) >> 16);

        System.out.println(d1);
        System.out.println(d2);
        d1 = (int) (((res & 0x3) << 14) | ((res & 0x3FFF_0000L) >> 16));
        d2 = (int) (((res1 & 0x3) << 14) | ((res1 & 0x3FFF_0000L) >> 16));
        System.out.println(d1);
        System.out.println(d2);
    }
}
