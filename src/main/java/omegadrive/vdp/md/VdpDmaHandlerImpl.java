/*
 * VdpDmaHandlerImpl
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 11:37
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.vdp.md;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.util.BufferUtil.CpuDeviceAccess;
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.Size;
import omegadrive.util.VideoMode;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.VdpDmaHandler;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.slf4j.Logger;

import static omegadrive.util.BufferUtil.CpuDeviceAccess.M68K;
import static omegadrive.util.Util.th;
import static omegadrive.vdp.model.MdVdpProvider.VdpRamType.VRAM;
import static omegadrive.vdp.model.MdVdpProvider.VdpRegisterName.*;

public class VdpDmaHandlerImpl implements VdpDmaHandler {

    public static final boolean verbose = false;
    public static final boolean lessVerbose = false || verbose;
    public static final boolean printToSysOut = false;
    private final static Logger LOG = LogHelper.getLogger(VdpDmaHandlerImpl.class.getSimpleName());

    protected MdVdpProvider vdpProvider;
    protected VdpMemoryInterface memoryInterface;
    protected MdMainBusProvider busProvider;

    private int dmaFillData;
    private DmaMode dmaMode = null;
    private boolean dmaFillReady;

    //TODO this should be in the VDP
    private final VdpFifo.VdpFifoEntry pendingReadEntry = new VdpFifo.VdpFifoEntry();

    public static VdpDmaHandler createInstance(MdVdpProvider vdpProvider, VdpMemoryInterface memoryInterface,
                                               MdMainBusProvider busProvider) {
        VdpDmaHandlerImpl d = new VdpDmaHandlerImpl();
        d.vdpProvider = vdpProvider;
        d.busProvider = busProvider;
        d.memoryInterface = memoryInterface;
        return d;
    }

    public DmaMode setupDma(MdVdpProvider.VramMode vramMode, int data, boolean m1) {
        if (!m1) {
            if (verbose) LOG.warn("Attempting DMA but m1 not set: {}, data: {}", dmaMode, data);
            return null;
        }
        dmaMode = getDmaMode(vdpProvider.getRegisterData(DMA_SOURCE_HIGH), vramMode);
        //on DMA Fill, busy flag is actually immediately (?) set after the CTRL port write,
        //not the DATA port write that starts the Fill operation
        if (dmaMode != null) {
            vdpProvider.setDmaFlag(1);
            printLessVerboseInfo(dmaMode == DmaMode.VRAM_FILL ? "SETUP" : "START");
            handleDmaFastMemory();
        }
        return dmaMode;
    }

    private void handleDmaFastMemory() {
        if (dmaMode != DmaMode.MEM_TO_VRAM) {
            return;
        }
        int dmaSrcHigh = vdpProvider.getRegisterData(DMA_SOURCE_HIGH);
        /* Transfer from SVP ROM/RAM ($000000-$3fffff)*/
        boolean svpDma = busProvider.isSvp() && (dmaSrcHigh & 0x60) == 0;
        /* Transfer from MEGACD
                    WRAM_1M($600_000-$63f_fff)
                    WRAM_2M($200_000-$23f_fff)
                    PRGRAM ($020_000-$03f_fff)
        */
        boolean isMegaCd = busProvider.getSystem().getSystemType().isMegaCdAttached();
        //TODO mode1
        boolean megaCdDma = isMegaCd && (
                ((dmaSrcHigh & 0xF0) == 0x30) || //wram_1M
                        ((dmaSrcHigh & 0xF0) == 0x10)    //wram_2M
        );
        if (isMegaCd) {
            if ((dmaSrcHigh & 0xFF) == 2 || (dmaSrcHigh & 0xFF) == 3) {
                assert false; //TODO prgRam
            }
        }
        if (megaCdDma || svpDma) {
            decreaseDmaLength();
            increaseDestAddress();
        }
    }

    //https://gendev.spritesmind.net/forum/viewtopic.php?t=2663
    @Override
    public void setupDmaFillMaybe(boolean isDma, int data) {
        //this should proceed even with m1 =0
        if (isDma && dmaMode == VdpDmaHandler.DmaMode.VRAM_FILL) {
            if (!dmaFillReady) {
                printLessVerboseInfo("START");
                dmaFillReady = true;
            }
            dmaFillData = data;
        }
    }

    @Override
    public boolean dmaInProgress() {
        return (dmaMode != null && dmaMode == DmaMode.VRAM_FILL && dmaFillReady) || (dmaMode != null && dmaMode != DmaMode.VRAM_FILL);
    }


    private int getDmaLength() {
        return vdpProvider.getRegisterData(DMA_LENGTH_HIGH) << 8 | vdpProvider.getRegisterData(DMA_LENGTH_LOW);
    }

    private int getSourceAddressLow() {
        int reg22 = vdpProvider.getRegisterData(DMA_SOURCE_MID);
        int reg21 = vdpProvider.getRegisterData(DMA_SOURCE_LOW);
        return (reg22 & 0xFF) << 8 | reg21;
    }

    private int getSourceAddress() {
        int sourceAddress = getSourceAddressLow();
        if (dmaMode == DmaMode.MEM_TO_VRAM) {
            sourceAddress = ((vdpProvider.getRegisterData(DMA_SOURCE_HIGH) & 0x7F) << 16) | sourceAddress;
        }
        return sourceAddress;
    }

    private int getDestAddress() {
        return vdpProvider.getAddressRegister();
    }

    private void printInfo(String head) {
        printInfo(head, Integer.MIN_VALUE);
    }

    private void printLessVerboseInfo(String head) {
        if (!lessVerbose) {
            return;
        }
        printInfoLess(head, Integer.MIN_VALUE, null);
    }

    private void printInfo(String head, int srcAddress, int data) {
        if (!verbose) {
            return;
        }
        printInfoLess(head, srcAddress, data);
    }

    private void printInfo(String head, int srcAddress) {
        if (!verbose) {
            return;
        }
        printInfoLess(head, srcAddress, null);
    }

    private void printInfoLess(String head, int srcAddress, Integer data) {
        String str = getDmaStateString(head, srcAddress, data);
        LogHelper.logWarnOnce(LOG, str);
//        LOG.info(str);
        if (printToSysOut) {
            System.out.println(str);
        }
    }

    @Override
    public String getDmaStateString() {
        return getDmaStateString("", Integer.MIN_VALUE, null);
    }

    private String getDmaStateString(String head, int srcAddress, Integer data) {
        int dmaLen = getDmaLength();
        String str = dmaMode + " " + head;
        String src = th(srcAddress > Integer.MIN_VALUE ? srcAddress : getSourceAddress());
        String dest = th(getDestAddress());
        int destAddressIncrement = getDestAddressIncrement();

        str += dmaMode == DmaMode.VRAM_FILL ? " fillData: " + th(dmaFillData) : " srcAddr: " + src;
        str += ", destAddr: " + dest + ", destAddrInc: " + destAddressIncrement +
                ", dmaLen: " + dmaLen + (data != null ? ", data: " + th(data.intValue()) : "")
                + ", vramMode: " + vdpProvider.getVramMode();
        str += vdpProvider.getVdpStateString();
        return str;
    }

    @Override
    public DmaMode getDmaMode() {
        return dmaMode;
    }

    public boolean doDmaSlot(VideoMode videoMode) {
        //TODO handle "DMA" delay
        int delay = MdRuntimeData.getCpuDelayExt(M68K);
        CpuDeviceAccess prev = MdRuntimeData.setAccessTypeExt(M68K);
        boolean done = true;
        switch (dmaMode) {
            case VRAM_FILL:
                if (dmaFillReady) {
                    dmaFillSingleByte();
                }
                done = dmaFillReady;
                break;
            case VRAM_COPY:
                dmaCopySingleByte();
                break;
            case MEM_TO_VRAM:
                dma68kToVram();
                break;
            default:
                LOG.error("Unexpected dma setting: {}", dmaMode);
        }
        done &= getDmaLength() == 0;
        if (done) {
            printLessVerboseInfo("DONE");
            dmaMode = null; //Bug Hunt
            dmaFillReady = false;
        }
        MdRuntimeData.resetCpuDelayExt(M68K, delay);
        MdRuntimeData.setAccessTypeExt(prev);
        return done;
    }

    private void dmaFillSingleByte() {
        dmaVramWriteByte((byte) (dmaFillData >> 8));
    }

    private void dmaVramWriteByte(byte data) {
        int destAddress = getDestAddress() ^ 1;
        printInfo("IN PROGRESS - WRITE");
        memoryInterface.writeVideoRamByte(VRAM, destAddress, data);
        postDmaRegisters();
    }

    private void postDmaRegisters() {
        decreaseDmaLength();
        increaseSourceAddress(1);
        increaseDestAddress();
    }

    //on VRAM copy, VRAM source and destination address are actually adjacent address ( address ^ 1)
    //to internal address registers value. This does not matter for most VRAM Copy operations since
    //they are done on an even byte quantity but can be verified when doing a single byte copy for example.
    private void dmaCopySingleByte() {
        //needs two slots, first slot reads, second writes
        if (pendingReadEntry.vdpRamMode == null) {
            int sourceAddress = getSourceAddress() ^ 1;
            int data = memoryInterface.readVideoRamByte(VRAM, sourceAddress);
            pendingReadEntry.vdpRamMode = MdVdpProvider.VramMode.vramWrite;
            pendingReadEntry.data = data;
            printInfo("IN PROGRESS - READ");
        } else {
            dmaVramWriteByte((byte) pendingReadEntry.data);
            pendingReadEntry.vdpRamMode = null;
            pendingReadEntry.data = 0;
        }
    }

    //The VDP decrements the length before checking if it's equal to 0,
    //which results in an integer underflow if the length is 0. In other words, if you set the DMA length to 0,
    //it will act like you set it to $10000.
    private int decreaseDmaLength() {
        int dmaLen = getDmaLength();
        dmaLen = (dmaLen - 1) & (MdVdpProvider.VDP_VRAM_SIZE - 1);
        vdpProvider.updateRegisterData(DMA_LENGTH_LOW, dmaLen & 0xFF);
        vdpProvider.updateRegisterData(DMA_LENGTH_HIGH, dmaLen >> 8);
        return dmaLen;
    }

    private void increaseDestAddress() {
        int destAddress = (getDestAddress() + getDestAddressIncrement()) & 0xFFFF;
        vdpProvider.setAddressRegister(destAddress);
    }

    private void increaseSourceAddress(int inc) {
        int sourceAddress = (getSourceAddressLow() + inc) & 0xFFFF;
        setSourceAddress(sourceAddress);
    }

    private void setSourceAddress(int sourceAddress) {
        int reg22 = (sourceAddress >> 8) & 0xFF;
        int reg21 = sourceAddress & 0xFF;
        vdpProvider.updateRegisterData(DMA_SOURCE_LOW, reg21);
        vdpProvider.updateRegisterData(DMA_SOURCE_MID, reg22);
    }

    private int getDestAddressIncrement() {
        return vdpProvider.getRegisterData(AUTO_INCREMENT);
    }

    private void dma68kToVram() {
        int sourceAddress = getSourceAddress() << 1; //needs to double it
        int destAddress = getDestAddress();
        int dataWord = busProvider.read(sourceAddress, Size.WORD);
        vdpProvider.fifoPush(destAddress, dataWord);
        printInfo("IN PROGRESS: ", sourceAddress, dataWord);
        //increase by 1, becomes 2 (bytes) when doubling
        postDmaRegisters();
    }

    private DmaMode getDmaMode(int reg17, MdVdpProvider.VramMode vramMode) {
        int dmaBits = reg17 >> 6;
        DmaMode mode = null;
        switch (dmaBits) {
            case 3:
                //For DMA copy, CD0-CD3 are ignored.
                // You can only perform a DMA copy within VRAM.
                mode = DmaMode.VRAM_COPY;
                break;
            case 2:
                if (vramMode == MdVdpProvider.VramMode.vramWrite) {
                    mode = DmaMode.VRAM_FILL;
                }
                break;
            case 0:
                //fall-through
            case 1:
                if (vramMode != null && vramMode.isWriteMode()) {
                    mode = DmaMode.MEM_TO_VRAM;
                }
                break;
        }
        if (mode == null) {
            LOG.error("Unexpected setup: {}, vramDestination: {}", dmaBits, vramMode);
        }
        return mode;
    }

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
}
