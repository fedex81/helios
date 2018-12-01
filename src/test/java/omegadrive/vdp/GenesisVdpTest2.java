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

import java.util.stream.IntStream;

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
        GenesisMemoryProvider memory = new GenesisMemoryProvider();
        busProvider = BusProvider.createBus();
        busProvider.attachDevice(memory);
        memoryInterface = new GenesisVdpMemoryInterface();
        dmaHandler = new VdpDmaHandlerImpl();

        vdpProvider = new GenesisVdpNew(busProvider, memoryInterface, dmaHandler, RegionDetector.Region.EUROPE);

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
//        Set Video mode: PAL_H32_V28
        vdpProvider.writeControlPort(0x8C00);
        ((GenesisVdpNew) vdpProvider).resetMode();
        int dmaLen = 232 * 2;
        int dmaAutoInc = 2;
        int afterDmaAutoInc = 0x20;

        vdpProvider.writeControlPort(0x8124);
        vdpProvider.run(1);
        vdpProvider.writeControlPort(0x8134);
        vdpProvider.run(1);
        vdpProvider.writeControlPort(0x8F00 + dmaAutoInc);
        vdpProvider.run(1);
        vdpProvider.writeControlPort(0x93E8);
        vdpProvider.run(1);
        vdpProvider.writeControlPort(0x9400);
        vdpProvider.run(1);
        vdpProvider.writeControlPort(0x951F);
        vdpProvider.run(1);
        vdpProvider.writeControlPort(0x9683);
        vdpProvider.run(1);
        vdpProvider.writeControlPort(0x977F);
        vdpProvider.run(1);
        //setup DMA
        vdpProvider.writeControlPort(0x4400);
        vdpProvider.run(1);
        //move.l, first word
        vdpProvider.writeControlPort(0x81);
        vdpProvider.run(1);
        Assert.assertTrue(busProvider.shouldStop68k());

        //move.l second word, this changes the autoInc value -> needs to happen after DMA!
        vdpProvider.writeControlPort(0x8F00 + afterDmaAutoInc);
        vdpProvider.run(1);

        //autoInc has not been changed
        Assert.assertEquals(dmaAutoInc, vdpProvider.getRegisterData(VdpProvider.VdpRegisterName.AUTO_INCREMENT));

        //run dma
        IntStream.range(0, dmaLen).forEach(i -> vdpProvider.run(1));

        //autoInc has now been changed
        Assert.assertEquals(afterDmaAutoInc, vdpProvider.getRegisterData(VdpProvider.VdpRegisterName.AUTO_INCREMENT));
    }
}
