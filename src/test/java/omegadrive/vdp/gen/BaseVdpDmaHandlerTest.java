package omegadrive.vdp.gen;

import omegadrive.bus.gen.GenesisBusProvider;
import omegadrive.util.RegionDetector;
import omegadrive.vdp.VdpTestUtil;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpDmaHandler;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
@Ignore
public class BaseVdpDmaHandlerTest {

    private static Logger LOG = LogManager.getLogger(BaseVdpDmaHandlerTest.class.getSimpleName());

    GenesisVdpProvider vdpProvider;
    VdpMemoryInterface memoryInterface;
    VdpDmaHandler dmaHandler;

    @Before
    public void setup() {
        GenesisBusProvider busProvider = GenesisBusProvider.createBus();
        memoryInterface = GenesisVdpMemoryInterface.createInstance();
        dmaHandler = new VdpDmaHandlerImpl();

        vdpProvider = GenesisVdp.createInstance(busProvider, memoryInterface, dmaHandler, RegionDetector.Region.EUROPE);

        ((VdpDmaHandlerImpl) dmaHandler).vdpProvider = vdpProvider;
        ((VdpDmaHandlerImpl) dmaHandler).memoryInterface = memoryInterface;
        vdpProvider.updateRegisterData(1, 4); //mode5
    }

    protected void testDMAFillInternal(long dmaFillLong, int increment,
                                       int[] expected) {
        vdpProvider.writeControlPort(0x8F02);
        vdpProvider.writeControlPort(16384);
        vdpProvider.writeControlPort(2);
        vdpProvider.writeDataPort(750);
        vdpProvider.writeDataPort(1260);
        vdpProvider.writeDataPort(1770);
        vdpProvider.writeDataPort(2280);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(2790);
        vdpProvider.writeDataPort(3300);
        vdpProvider.writeDataPort(3810);
        vdpProvider.writeDataPort(736);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(1230);
        vdpProvider.writeDataPort(1740);
        vdpProvider.writeDataPort(2250);
        vdpProvider.writeDataPort(2760);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(3270);
        vdpProvider.writeDataPort(3780);
        vdpProvider.writeDataPort(706);
        vdpProvider.writeDataPort(1216);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        String str = VdpTestUtil.printVdpMemory(memoryInterface, GenesisVdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        vdpProvider.writeControlPort(0x8F00 + increment);
        vdpProvider.writeControlPort(0x8154);
        vdpProvider.writeControlPort(0x9305);
        vdpProvider.writeControlPort(0x9400);
        vdpProvider.writeControlPort(0x9500);
        vdpProvider.writeControlPort(0x9600);
        vdpProvider.writeControlPort(0x9780);

        vdpProvider.writeControlPort(dmaFillLong >> 16);
        vdpProvider.writeControlPort(dmaFillLong & 0xFFFF);

//        System.out.println("DestAddress: " + Integer.toHexString(vdpProvider.getAddressRegisterValue()));

        vdpProvider.writeDataPort(0x68ac);

//        System.out.println("DestAddress: " + Integer.toHexString(vdpProvider.getAddressRegisterValue()));

        str = VdpTestUtil.printVdpMemory(memoryInterface, GenesisVdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        VdpTestUtil.runVdpUntilDmaDone(vdpProvider);

        str = VdpTestUtil.printVdpMemory(memoryInterface, GenesisVdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        String[] actual = IntStream.range(0x8000, 0x8000 + expected.length).
                mapToObj(memoryInterface::readVramByte).map(Integer::toHexString).toArray(String[]::new);

        String[] exp = Arrays.stream(expected).mapToObj(Integer::toHexString).toArray(String[]::new);


        System.out.println("Expected: " + Arrays.toString(exp));
        System.out.println("Actual: " + Arrays.toString(actual));

        Assert.assertArrayEquals(exp, actual);
    }


    protected void testDMACopyInternal(int increment, int[] expected) {
        vdpProvider.writeControlPort(0x8F02);
        vdpProvider.writeControlPort(0x4000);
        vdpProvider.writeControlPort(2);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0x5000);
        vdpProvider.writeControlPort(2);
        vdpProvider.writeDataPort(0x1122);
        vdpProvider.writeDataPort(0x3344);
        vdpProvider.writeDataPort(0x5566);
        vdpProvider.writeDataPort(0x7788);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(0x99aa);
        vdpProvider.writeDataPort(0xbbcc);
        vdpProvider.writeDataPort(0xddee);
        vdpProvider.writeDataPort(0xff00);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        String str = VdpTestUtil.printVdpMemory(memoryInterface, GenesisVdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        str = VdpTestUtil.printVdpMemory(memoryInterface, GenesisVdpProvider.VdpRamType.VRAM, 0x9000, 0x9016);
        System.out.println(str);

        vdpProvider.writeControlPort(0x8F00 + increment);
        vdpProvider.writeControlPort(0x8154);
        vdpProvider.writeControlPort(0x9303);
        vdpProvider.writeControlPort(0x9400);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0x9500);
        vdpProvider.writeControlPort(0x9690);
        vdpProvider.writeControlPort(0x97C0);

        vdpProvider.writeControlPort(0);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0xc2);

        VdpTestUtil.runVdpUntilDmaDone(vdpProvider);

        str = VdpTestUtil.printVdpMemory(memoryInterface, GenesisVdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        String[] exp = Arrays.stream(expected).mapToObj(Integer::toHexString).toArray(String[]::new);
        String[] actual = IntStream.range(0x8000, 0x8000 + expected.length).
                mapToObj(memoryInterface::readVramByte).map(Integer::toHexString).toArray(String[]::new);

        System.out.println("Expected: " + Arrays.toString(exp));
        System.out.println("Actual:   " + Arrays.toString(actual));

        Assert.assertArrayEquals(exp, actual);
    }

}
