/*
 * Copyright (c) 2018-2019 Federico Berti
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
import omegadrive.util.LogHelper;
import omegadrive.util.MdRuntimeData;
import omegadrive.util.SystemTestUtil;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.model.MdVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

import static omegadrive.SystemLoader.SystemType.MD;
import static omegadrive.system.SystemProvider.NO_CLOCK;
import static omegadrive.vdp.model.MdVdpProvider.VdpRamType.VRAM;
import static omegadrive.vdp.model.MdVdpProvider.VdpRegisterName.*;

@Ignore
public class BaseVdpDmaHandlerTest {

    private static final Logger LOG = LogHelper.getLogger(BaseVdpDmaHandlerTest.class.getSimpleName());

    protected MdVdpProvider vdpProvider;
    protected VdpMemoryInterface memoryInterface;

    @Before
    public void setup() {
        MdMainBusProvider busProvider = SystemTestUtil.setupNewMdSystem();
        Optional<MdVdpProvider> opt = busProvider.getBusDeviceIfAny(MdVdpProvider.class);
        Assert.assertTrue(opt.isPresent());
        vdpProvider = opt.get();
        memoryInterface = vdpProvider.getVdpMemory();
        MdVdpTestUtil.vdpMode5(vdpProvider);
        MdRuntimeData.newInstance(MD, NO_CLOCK);
    }

    protected void testDMAFillInternal(int dmaFillLong, int increment,
                                       int[] expected) {
        testDMAFillInternal(dmaFillLong, 0x8000, increment, 0x68ac, expected);
    }

    protected void testDMAFillInternal2(int dmaFillLong, int baseAddress, int increment, int fillValueWord,
                                        int[] expected) {
        int toAddress = baseAddress + expected.length;

        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdpProvider);
        MdVdpTestUtil.vdpEnableDma(vdpProvider, true);
        setVdpRegDma(AUTO_INCREMENT, increment);
        setVdpRegDma(DMA_LENGTH_LOW, 0);
        setVdpRegDma(DMA_LENGTH_HIGH, 0);
        setVdpRegDma(DMA_SOURCE_HIGH, 0x80);

        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(dmaFillLong >> 16);
        vdpProvider.writeControlPort(dmaFillLong & 0xFFFF);

        MdVdpTestUtil.runVdpSlot(vdpProvider);

        vdpProvider.writeDataPort(fillValueWord);

        MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);

        String[] actual = IntStream.range(baseAddress, toAddress).
                mapToObj(addr -> memoryInterface.readVideoRamByte(VRAM, addr)).map(Integer::toHexString).toArray(String[]::new);

        String[] exp = Arrays.stream(expected).mapToObj(Integer::toHexString).toArray(String[]::new);

//        System.out.println("Expected: " + Arrays.toString(exp));
//        System.out.println("Actual: " + Arrays.toString(actual));

        Assert.assertArrayEquals(exp, actual);
    }

    protected void testDMAFillInternal(int dmaFillLong, int baseAddress, int increment, int fillValueWord,
                                       int[] expected) {
        int toAddress = baseAddress + expected.length;
        setVdpRegDma(AUTO_INCREMENT, 2);
        vdpProvider.writeControlPort(16384);
        vdpProvider.writeControlPort(2);
        vdpProvider.writeDataPort(750);
        vdpProvider.writeDataPort(1260);
        vdpProvider.writeDataPort(1770);
        vdpProvider.writeDataPort(2280);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(2790);
        vdpProvider.writeDataPort(3300);
        vdpProvider.writeDataPort(3810);
        vdpProvider.writeDataPort(736);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(1230);
        vdpProvider.writeDataPort(1740);
        vdpProvider.writeDataPort(2250);
        vdpProvider.writeDataPort(2760);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(3270);
        vdpProvider.writeDataPort(3780);
        vdpProvider.writeDataPort(706);
        vdpProvider.writeDataPort(1216);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        String str = MdVdpTestUtil.printVdpMemory(memoryInterface, VRAM, baseAddress, toAddress);
        System.out.println(str);

        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdpProvider);
        MdVdpTestUtil.vdpEnableDma(vdpProvider, true);
        setVdpRegDma(AUTO_INCREMENT, increment);
        setVdpRegDma(DMA_LENGTH_LOW, 5);
        setVdpRegDma(DMA_LENGTH_HIGH, 0);
        setVdpRegDma(DMA_SOURCE_LOW, 0);
        setVdpRegDma(DMA_SOURCE_MID, 0);
        setVdpRegDma(DMA_SOURCE_HIGH, 0x80);

        vdpProvider.writeControlPort(dmaFillLong >> 16);
        vdpProvider.writeControlPort(dmaFillLong & 0xFFFF);

//        System.out.println("DestAddress: " + th(vdpProvider.getAddressRegisterValue()));

        vdpProvider.writeDataPort(fillValueWord);

//        System.out.println("DestAddress: " + th(vdpProvider.getAddressRegisterValue()));

        str = MdVdpTestUtil.printVdpMemory(memoryInterface, VRAM, baseAddress, toAddress);
        System.out.println(str);

        MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);

        str = MdVdpTestUtil.printVdpMemory(memoryInterface, VRAM, baseAddress, toAddress);
        System.out.println(str);

        String[] actual = IntStream.range(baseAddress, toAddress).
                mapToObj(addr -> memoryInterface.readVideoRamByte(VRAM, addr)).map(v -> Integer.toHexString(v & 0xFF)).toArray(String[]::new);

        String[] exp = Arrays.stream(expected).mapToObj(Integer::toHexString).toArray(String[]::new);


        System.out.println("Expected: " + Arrays.toString(exp));
        System.out.println("Actual: " + Arrays.toString(actual));

        Assert.assertArrayEquals(exp, actual);
    }


    protected void testDMACopyInternal(int increment, int[] expected) {
        setVdpRegDma(AUTO_INCREMENT, 2);
        vdpProvider.writeControlPort(0x4000);
        vdpProvider.writeControlPort(2);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        vdpProvider.writeDataPort(0xf00d);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0x5000);
        vdpProvider.writeControlPort(2);
        vdpProvider.writeDataPort(0x1122);
        vdpProvider.writeDataPort(0x3344);
        vdpProvider.writeDataPort(0x5566);
        vdpProvider.writeDataPort(0x7788);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeDataPort(0x99aa);
        vdpProvider.writeDataPort(0xbbcc);
        vdpProvider.writeDataPort(0xddee);
        vdpProvider.writeDataPort(0xff00);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        String str = MdVdpTestUtil.printVdpMemory(memoryInterface, VRAM, 0x8000, 0x8016);
        System.out.println(str);

        str = MdVdpTestUtil.printVdpMemory(memoryInterface, VRAM, 0x9000, 0x9016);
        System.out.println(str);

        MdVdpTestUtil.vdpDisplayEnableAndMode5(vdpProvider);
        MdVdpTestUtil.vdpEnableDma(vdpProvider, true);
        setVdpRegDma(AUTO_INCREMENT, increment);
        setVdpRegDma(DMA_LENGTH_LOW, 3);
        setVdpRegDma(DMA_LENGTH_HIGH, 0);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        setVdpRegDma(DMA_SOURCE_LOW, 0);
        setVdpRegDma(DMA_SOURCE_MID, 0x90);
        setVdpRegDma(DMA_SOURCE_HIGH, 0xC0);

        vdpProvider.writeControlPort(0);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0xc2);

        MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);

        str = MdVdpTestUtil.printVdpMemory(memoryInterface, VRAM, 0x8000, 0x8016);
        System.out.println(str);

        String[] exp = Arrays.stream(expected).mapToObj(Integer::toHexString).toArray(String[]::new);
        String[] actual = IntStream.range(0x8000, 0x8000 + expected.length).
                mapToObj(addr -> memoryInterface.readVideoRamByte(VRAM, addr)).map(v -> Integer.toHexString(v & 0xFF)).toArray(String[]::new);

        System.out.println("Expected: " + Arrays.toString(exp));
        System.out.println("Actual:   " + Arrays.toString(actual));

        Assert.assertArrayEquals(exp, actual);
    }

    protected void setVdpRegDma(MdVdpProvider.VdpRegisterName reg, int value) {
        MdVdpTestUtil.setVdpRegister(vdpProvider, reg, value);
    }

}
