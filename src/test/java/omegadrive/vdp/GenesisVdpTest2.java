package omegadrive.vdp;

import omegadrive.bus.BusProvider;
import omegadrive.memory.GenesisMemoryProvider;
import omegadrive.util.LogHelper;
import omegadrive.util.RegionDetector;
import omegadrive.vdp.model.VdpDmaHandler;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static omegadrive.vdp.VdpProvider.VramMode.*;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class GenesisVdpTest2 {

    private static Logger LOG = LogManager.getLogger(VdpDmaHandlerImplTest.class.getSimpleName());

    VdpProvider vdpProvider;
    VdpMemoryInterface memoryInterface;
    VdpDmaHandler dmaHandler;
    BusProvider busProvider;

    @Before
    public void setup() {
        LogHelper.printToSytemOut = true;
        VdpDmaHandlerImpl.printToSysOut = true;
        VdpDmaHandlerImpl.verbose = true;
        GenesisMemoryProvider memory = new GenesisMemoryProvider();
        busProvider = BusProvider.createBus();
        busProvider.attachDevice(memory);
        memoryInterface = GenesisVdpMemoryInterface.createInstance();
        dmaHandler = new VdpDmaHandlerImpl();

        vdpProvider = GenesisVdpNew.createInstance(busProvider, memoryInterface, dmaHandler, RegionDetector.Region.EUROPE);

        ((VdpDmaHandlerImpl) dmaHandler).vdpProvider = vdpProvider;
        ((VdpDmaHandlerImpl) dmaHandler).memoryInterface = memoryInterface;
        ((VdpDmaHandlerImpl) dmaHandler).busProvider = busProvider;
        vdpProvider.init();
    }


    /**
     * F1 world championship
     * Kawasaki
     * <p>
     * 68k uses a move.l to write to VDP, this translates to 2 word writes
     * writeControlPort1: enables dma -> 68k gets stopped
     * writeControlPort2: modifies a register -> should wait for the DMA to be finished
     */
    @Test
    public void testWriteControlPortLongWordAndDMA() {
        VdpTestUtil.setH32(vdpProvider);

        int dmaAutoInc = 2;
        int afterDmaAutoInc = 0x20;

        vdpProvider.writeControlPort(0x8124);
        vdpProvider.writeControlPort(0x8134);
        vdpProvider.writeControlPort(0x8F00 + dmaAutoInc);
        vdpProvider.writeControlPort(0x93E8);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0x9400);
        vdpProvider.writeControlPort(0x951F);
        vdpProvider.writeControlPort(0x9683);
        vdpProvider.writeControlPort(0x977F);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        //setup DMA
        vdpProvider.writeControlPort(0x4400);
        //move.l, first word
        vdpProvider.writeControlPort(0x81);
        Assert.assertTrue(busProvider.shouldStop68k());

        //move.l second word, this changes the autoInc value -> needs to happen after DMA!
        vdpProvider.writeControlPort(0x8F00 + afterDmaAutoInc);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        //autoInc has not been changed
        Assert.assertEquals(dmaAutoInc, vdpProvider.getRegisterData(VdpProvider.VdpRegisterName.AUTO_INCREMENT));

        VdpTestUtil.runVdpUntilDmaDone(vdpProvider);

        //autoInc has now been changed
        Assert.assertEquals(afterDmaAutoInc, vdpProvider.getRegisterData(VdpProvider.VdpRegisterName.AUTO_INCREMENT));
    }

    /**
     * vramRead(0b0000, VdpRamType.VRAM),
     * cramRead(0b1000, VdpRamType.CRAM),
     * vsramRead(0b0100, VdpRamType.VSRAM),
     * vramWrite(0b0001, VdpRamType.VRAM),
     * cramWrite(0b0011, VdpRamType.CRAM),
     * vsramWrite(0b0101, VdpRamType.VSRAM),
     * vramRead_8bit(0b1100, VdpRamType.VRAM);
     */
    @Test
    public void testCodeRegisterUpdate() {
        VdpTestUtil.setH32(vdpProvider);

        //set vramRead
        testCodeRegisterUpdateInternal(vramRead.getAddressMode(), 0, 1280);
        Assert.assertEquals(vramRead, vdpProvider.getVramMode());

        // from vramRead_8bit -> cramRead
        testCodeRegisterUpdateInternal(cramRead.getAddressMode(), 0, 1312);

        // starts from vramRead -> vramWrite
        testCodeRegisterUpdateInternal(vramWrite.getAddressMode(), 16384, 0);

        testCodeRegisterUpdateInternal(vramRead_8bit.getAddressMode(), 0, 1328);

        //from vramWrite to cramWrite
        testCodeRegisterUpdateInternal(cramWrite.getAddressMode(), 49152, 0);

        // from cramRead -> vsramRead
        testCodeRegisterUpdateInternal(vsramRead.getAddressMode(), 0, 1296);

        // from vsramRead -> vramRead
        testCodeRegisterUpdateInternal(vramRead.getAddressMode(), 0, 1280);
    }

    private void testCodeRegisterUpdateInternal(int expected, int firstWord, int secondWord) {
        vdpProvider.writeControlPort(firstWord);
        vdpProvider.writeControlPort(secondWord);

        VdpProvider.VramMode vramMode = vdpProvider.getVramMode();
        Assert.assertEquals(VdpProvider.VramMode.getVramMode(expected), vramMode);
    }
}
