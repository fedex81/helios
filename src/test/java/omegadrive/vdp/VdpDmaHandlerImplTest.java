package omegadrive.vdp;

import omegadrive.bus.BusProvider;
import omegadrive.util.RegionDetector;
import omegadrive.vdp.model.VdpDmaHandler;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class VdpDmaHandlerImplTest {

    private static Logger LOG = LogManager.getLogger(VdpDmaHandlerImplTest.class.getSimpleName());

    VdpProvider vdpProvider;
    VdpMemoryInterface memoryInterface;
    VdpDmaHandler dmaHandler;

    @Before
    public void setup() {
        BusProvider busProvider = BusProvider.createBus();
        memoryInterface = GenesisVdpMemoryInterface.createInstance();
        dmaHandler = new VdpDmaHandlerImpl();

        vdpProvider = GenesisVdp.createInstance(busProvider, memoryInterface, dmaHandler, RegionDetector.Region.EUROPE);

        ((VdpDmaHandlerImpl) dmaHandler).vdpProvider = vdpProvider;
        ((VdpDmaHandlerImpl) dmaHandler).memoryInterface = memoryInterface;
        vdpProvider.updateRegisterData(1, 4); //mode5
    }


    @Test
    public void testDMACopy_inc0() {
        int[] expected = {0xF0, 0x44, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D};
        testDMACopyInternal(0, expected);
    }

    @Test
    public void testDMACopy_inc1() {
        int[] expected = {0x11, 0x22, 0xF0, 0x44, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D};
        testDMACopyInternal(1, expected);
    }

    @Test
    public void testDMACopy_inc2() {
        //$F022, $F011, $F044
        int[] expected = {0xF0, 0x22, 0xF0, 0x11, 0xF0, 0x44, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D};
        testDMACopyInternal(2, expected);
    }

    @Test
    public void testDMACopy_inc4() {
        //$F022, $F00D, $F011, $F00D, $F044,
        int[] expected = {0xF0, 0x22, 0xF0, 0x0D, 0xF0, 0x11, 0xF0, 0x0D, 0xF0, 0x44, 0xF0, 0x0D, 0xF0, 0x0D, 0xF0, 0x0D};
        testDMACopyInternal(4, expected);
    }

    /**
     * VDPFIFOTesting #42
     * <p>
     * ThunderForce IV breaks if this fails
     */
    @Test
    public void testDMA_Fill_VRAM_Even_inc0() {
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        int[] expected = {0x2, 0xEE, 0x68, 0x68, 0x06, 0xEA};
        testDMAFillInternal(dmaFillCommand, 0, expected);
    }

    @Test
    public void testDMA_Fill_VRAM_inc1() {
        testDMA_Fill_VRAM_Even_inc1();
        testDMA_Fill_VRAM_Odd_inc1();
    }

    @Test
    public void testDMA_Fill_VRAM_Even_inc1() {
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        int[] expected = {0x2, 0xEE, 0x68, 0xAC, 0x68, 0x68, 0x68, 0x68, 0x0A, 0xE6};
        testDMAFillInternal(dmaFillCommand, 1, expected);
    }

    @Test
    public void testDMA_Fill_VRAM_Even_inc2() {
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        int[] expected = {0x2, 0xEE, 0x68, 0xAC, 0x06, 0x68, 0x08, 0x68,
                0x0A, 0x68, 0x0C, 0x68, 0x0E, 0x68, 0x02};
        testDMAFillInternal(dmaFillCommand, 2, expected);
    }

    @Test
    public void testDMA_Fill_VRAM_Even_inc4() {
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        int[] expected = {0x2, 0xEE, 0x68, 0xAC, 0x06, 0xEA, 0x08, 0x68, 0x0A, 0xE6,
                0x0C, 0x68, 0x0E, 0xE2, 0x02, 0x68,
                0x04, 0xCE, 0x06, 0x68, 0x08, 0xCA, 0x0A, 0x68, 0x0C, 0xC6};
        testDMAFillInternal(dmaFillCommand, 4, expected);
    }

    @Test
    public void testDMA_Fill_VRAM_Odd_inc0() {
        long dmaFillCommand = 0x40030082; //DMA fill at VRAM address 0x8003
        int[] expected = {0x2, 0xEE, 0x68, 0x68, 0x06, 0xEA};
        testDMAFillInternal(dmaFillCommand, 0, expected);
    }

    @Test
    public void testDMA_Fill_VRAM_Odd_inc1() {
        long dmaFillCommand = 0x40030082; //DMA fill at VRAM address 0x8003
        //dc.w $02EE, $AC68, $6868, $6868, $0A68, $0CE4, $0EE2,
        int[] expected = {0x2, 0xEE, 0xAC, 0x68, 0x68, 0x68, 0x68, 0x68, 0x0A, 0x68, 0x0C, 0xE4};
        testDMAFillInternal(dmaFillCommand, 1, expected);
    }

    @Test
    public void testDMA_Fill_VRAM_Odd_inc2() {
        long dmaFillCommand = 0x40030082; //DMA fill at VRAM address 0x8003
        //dc.w $02EE, $AC68, $68EA, $68E8, $68E6, $68E4, $68E2, $02E0
        int[] expected = {0x2, 0xEE, 0xAC, 0x68, 0x68, 0xEA, 0x68, 0xE8,
                0x68, 0xE6, 0x068, 0xE4, 0x068, 0xE2, 0x02, 0xE0};
        testDMAFillInternal(dmaFillCommand, 2, expected);
    }

    @Test
    public void testDMA_Fill_VRAM_Odd_inc4() {
        long dmaFillCommand = 0x40030082; //DMA fill at VRAM address 0x8003
        //dc.w $02EE, $AC68, $06EA, $68E8, $0AE6, $68E4, $0EE2, $68E0, $04CE, $68CC, $08CA, $68C8, $0CC6, $0EC4, $02C2, $04C0
        int[] expected = {0x2, 0xEE, 0xAC, 0x68, 0x06, 0xEA, 0x68, 0xE8, 0x0A, 0xE6,
                0x68, 0xE4, 0x0E, 0xE2, 0x68, 0xE0,
                0x04, 0xCE, 0x68, 0xCC, 0x08, 0xCA, 0x68, 0xC8, 0x0C, 0xC6};
        testDMAFillInternal(dmaFillCommand, 4, expected);
    }


    private void testDMAFillInternal(long dmaFillLong, int increment,
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

        String str = printMemory(VdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
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

        str = printMemory(VdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        VdpTestUtil.runVdpUntilDmaDone(vdpProvider);

        str = printMemory(VdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        String[] actual = IntStream.range(0x8000, 0x8000 + expected.length).
                mapToObj(memoryInterface::readVramByte).map(Integer::toHexString).toArray(String[]::new);

        String[] exp = Arrays.stream(expected).mapToObj(Integer::toHexString).toArray(String[]::new);


        System.out.println("Expected: " + Arrays.toString(exp));
        System.out.println("Actual: " + Arrays.toString(actual));

        Assert.assertArrayEquals(exp, actual);
    }


    private void testDMACopyInternal(int increment, int[] expected) {
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

        String str = printMemory(VdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        str = printMemory(VdpProvider.VdpRamType.VRAM, 0x9000, 0x9016);
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

        str = printMemory(VdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);

        String[] exp = Arrays.stream(expected).mapToObj(Integer::toHexString).toArray(String[]::new);
        String[] actual = IntStream.range(0x8000, 0x8000 + expected.length).
                mapToObj(memoryInterface::readVramByte).map(Integer::toHexString).toArray(String[]::new);

        System.out.println("Expected: " + Arrays.toString(exp));
        System.out.println("Actual:   " + Arrays.toString(actual));

        Assert.assertArrayEquals(exp, actual);
    }

    private String printMemory(VdpProvider.VdpRamType type, int from, int to) {
        Function<Integer, Integer> getByteFn = addr -> {
            int word = memoryInterface.readVideoRamWord(type, addr);
            return addr % 2 == 0 ? word >> 8 : word & 0xFF;
        };
        Function<Integer, String> toStringFn = v -> {
            String s = Integer.toHexString(v).toUpperCase();
            return s.length() < 2 ? '0' + s : s;
        };
        return IntStream.range(from, to).mapToObj(addr -> toStringFn.apply(getByteFn.apply(addr))).
                collect(Collectors.joining(","));
    }

}
