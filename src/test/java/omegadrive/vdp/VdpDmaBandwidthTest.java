package omegadrive.vdp;

import omegadrive.GenesisProvider;
import omegadrive.bus.BusProvider;
import omegadrive.util.LogHelper;
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
public class VdpDmaBandwidthTest {

    private static Logger LOG = LogManager.getLogger(VdpDmaBandwidthTest.class.getSimpleName());

    VdpProvider vdpProvider;
    VdpMemoryInterface memoryInterface;

    boolean verbose = false;

    static int ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H32 = 16;
    static int ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H40 = 18;

    @Before
    public void setup() {
        GenesisProvider emu = VdpTestUtil.createTestGenesisProvider();
        BusProvider busProvider = BusProvider.createBus();
        memoryInterface = GenesisVdpMemoryInterface.createInstance();
        vdpProvider = GenesisVdpNew.createInstance(busProvider, memoryInterface);
        busProvider.attachDevice(emu);

        vdpProvider.updateRegisterData(1, 4); //mode5

        VdpDmaHandlerImpl.verbose = verbose;
        VdpDmaHandlerImpl.printToSysOut = verbose;
        LogHelper.printToSytemOut = verbose;
        GenesisVdpNew.verbose = verbose;
    }


    @Test
    public void testDMADuringVblank() {
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        int dmaLen = 200;
        setupDMAFillInternal(dmaFillCommand, 2, dmaLen);
        startDmaFill(dmaLen, true);
    }

    @Test
    public void testDMADuringActiveScreenH32() {
        VdpTestUtil.setH32(vdpProvider);
        testDMADuringActiveScreen(ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H32 + 1, true);
    }

    @Test
    public void testDMADuringActiveScreenH40() {
        VdpTestUtil.setH40(vdpProvider);
        testDMADuringActiveScreen(ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H40 + 1, false);
    }

    private void testDMADuringActiveScreen(int dmaLen, boolean h32) {
        int slotsPerLine = h32 ? VdpProvider.H32_SLOTS : VdpProvider.H40_SLOTS;
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        setupDMAFillInternal(dmaFillCommand, 2, dmaLen);
        int slots = startDmaFill(dmaLen, false);
        // more than one line
        Assert.assertTrue(vdpProvider.getVCounter() > 0);
        Assert.assertTrue(slots > slotsPerLine);
    }

    private void setupDMAFillInternal(long dmaFillLong, int increment, int dmaLength) {
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
        vdpProvider.writeControlPort(0x9300 + dmaLength);
        vdpProvider.writeControlPort(0x9400);
        vdpProvider.writeControlPort(0x9500);
        vdpProvider.writeControlPort(0x9600);
        vdpProvider.writeControlPort(0x9780);

        vdpProvider.writeControlPort(dmaFillLong >> 16);
        vdpProvider.writeControlPort(dmaFillLong & 0xFFFF);
    }

    private int startDmaFill(int dmaLen, boolean waitVBlank) {
//        System.out.println("DestAddress: " + Integer.toHexString(vdpProvider.getAddressRegisterValue()));
        if (waitVBlank) {
            VdpTestUtil.runVdpUntilVBlank(vdpProvider);
            System.out.println("VBlank start" + vdpProvider.getVdpStateString());
        } else {
            VdpTestUtil.runToStartFrame(vdpProvider);
            System.out.println("Active Screen start" + vdpProvider.getVdpStateString());
        }

        vdpProvider.writeDataPort(0x68ac);
        VdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);
        System.out.println("After data write, fifo empty" + vdpProvider.getVdpStateString());

        int slots = VdpTestUtil.runVdpUntilDmaDone(vdpProvider);
        System.out.println("Dma done" + vdpProvider.getVdpStateString());

        String str = printMemory(VdpProvider.VdpRamType.VRAM, 0x8000, 0x8016);
        System.out.println(str);
        if (waitVBlank) {
            Assert.assertEquals(dmaLen, slots);
        }
        return slots;
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
