/*
 * BaseVdpDmaBandwidthTest
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 17/10/19 14:04
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

package omegadrive.vdp;

import omegadrive.bus.md.GenesisBus;
import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.input.GamepadTest;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.SystemProvider;
import omegadrive.vdp.md.GenesisVdp;
import omegadrive.vdp.md.TestGenesisVdpMemoryInterface;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpSlotType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;

import java.util.Arrays;

import static omegadrive.vdp.model.GenesisVdpProvider.VdpRegisterName.*;

/**
 *
 * http://www.tmeeco.eu/BitShit/VDPRATES.TXT
 * https://gendev.spritesmind.net/forum/viewtopic.php?t=1291&start=30
 */
@Ignore
public class BaseVdpDmaBandwidthTest {

    private static final Logger LOG = LogManager.getLogger(BaseVdpDmaBandwidthTest.class.getSimpleName());

    GenesisVdpProvider vdpProvider;
    TestGenesisVdpMemoryInterface memoryInterface;
    IMemoryProvider memoryProvider;

    static boolean verbose = true;

    static int ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H32_WORDS = (int) Arrays.stream(VdpSlotType.h32Slots).filter(t -> t == VdpSlotType.EXTERNAL).count();
    static int ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H40_WORDS = (int) Arrays.stream(VdpSlotType.h40Slots).filter(t -> t == VdpSlotType.EXTERNAL).count();

    static int BLANKING_VRAM_DMA_PER_LINE_H32_WORDS = (int) Arrays.stream(VdpSlotType.h32Slots).filter(t -> t != VdpSlotType.REFRESH).count();
    static int BLANKING_VRAM_DMA_PER_LINE_H40_WORDS = (int) Arrays.stream(VdpSlotType.h40Slots).filter(t -> t != VdpSlotType.REFRESH).count();

    static int REFRESH_SLOTS_H32 = (int) Arrays.stream(VdpSlotType.h32Slots).filter(t -> t == VdpSlotType.REFRESH).count();
    static int REFRESH_SLOTS_H40 = (int) Arrays.stream(VdpSlotType.h40Slots).filter(t -> t == VdpSlotType.REFRESH).count();

    //a DMA copy is vram read + vram write -> ie. a one byte transfer requires two slots
    // 16 slots -> 8 read + 8 write
    static int ACTIVE_SCREEN_DMA_COPY_PER_LINE_H32 = ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H32_WORDS / 2;
    static int ACTIVE_SCREEN_DMA_COPY_PER_LINE_H40 = ACTIVE_SCREEN_VRAM_DMA_PER_LINE_H40_WORDS / 2;
    static int BLANKING_DMA_VRAM_PER_LINE_H32 = BLANKING_VRAM_DMA_PER_LINE_H32_WORDS / 2;
    static int BLANKING_DMA_VRAM_PER_LINE_H40 = BLANKING_VRAM_DMA_PER_LINE_H40_WORDS / 2;

    @Before
    public void setup() {
        memoryProvider = MemoryProvider.createGenesisInstance();
        SystemProvider emu = MdVdpTestUtil.createTestGenesisProvider();
        GenesisBusProvider busProvider = new GenesisBus();
        memoryInterface = new TestGenesisVdpMemoryInterface();
        vdpProvider = GenesisVdp.createInstance(busProvider, memoryInterface);
        busProvider.attachDevice(emu).attachDevice(memoryProvider).
                attachDevice(vdpProvider).attachDevice(GamepadTest.createTestJoypadProvider());
        busProvider.init();
        vdpProvider.updateRegisterData(1, 4); //mode5
    }

    private void setup68kRam() {
        int val = 0xFF;
        for (int i = 0; i < GenesisVdpProvider.VDP_VRAM_SIZE; i++) {
            memoryProvider.writeRamByte(i, val);
            val = (val - 1) & 0xFF;
        }
    }

    //CRAM words == vdp Slots
    protected void test68kDma(GenesisVdpProvider.VdpRamType vdpRamType, int dmaLen, boolean h32, boolean blanking) {
        setup68kRam();
        int refreshSlots = h32 ? REFRESH_SLOTS_H32 : REFRESH_SLOTS_H40;
        int slotsPerLine = h32 ? GenesisVdpProvider.H32_SLOTS : GenesisVdpProvider.H40_SLOTS;
        int mode2 = blanking ? 0x34 : 0x74; //dma enabled
        vdpProvider.updateRegisterData(MODE_2, mode2);
        vdpProvider.updateRegisterData(AUTO_INCREMENT, 2);
        vdpProvider.updateRegisterData(DMA_LENGTH_LOW, dmaLen);
        vdpProvider.updateRegisterData(DMA_SOURCE_LOW, 0x80);
        vdpProvider.updateRegisterData(DMA_SOURCE_MID, 0xfd);
        vdpProvider.updateRegisterData(DMA_SOURCE_HIGH, 0x7f);
        System.out.println(vdpRamType + " before: " + MdVdpTestUtil.printVdpMemory(memoryInterface, vdpRamType, 0, 0xFF));

//        VdpTestUtil.runToStartFrame(vdpProvider);

        int commandLong = vdpRamType == GenesisVdpProvider.VdpRamType.CRAM ? 0xC000_0080 : (vdpRamType == GenesisVdpProvider.VdpRamType.VSRAM)
                ? 0x4000_0090 : 0x4000_0080;
        vdpProvider.writeControlPort(commandLong >> 16);
        vdpProvider.writeControlPort(commandLong & 0xFFFF);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        System.out.println(vdpProvider.getVdpStateString());
        int slots = MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);
        System.out.println("Slots: " + slots);
        System.out.println(vdpProvider.getVdpStateString());
        System.out.println(vdpRamType + " after: " + MdVdpTestUtil.printVdpMemory(memoryInterface, vdpRamType, 0, 0xFF));

        if (blanking) {
            int expected = vdpRamType == GenesisVdpProvider.VdpRamType.VRAM ? dmaLen * 2 + refreshSlots - 1 : dmaLen + refreshSlots;
            Assert.assertEquals(expected, slots);
        } else {
            Assert.assertTrue("Should be: " + slots + "> " + slotsPerLine, slots > slotsPerLine);
        }
    }

    protected void testDMAFillDuringActiveScreen(int dmaLen, boolean h32) {
        int slotsPerLine = h32 ? GenesisVdpProvider.H32_SLOTS : GenesisVdpProvider.H40_SLOTS;
        long dmaFillCommand = 0x40020082; //DMA fill at VRAM address 0x8002
        setupDMAFillInternal(dmaFillCommand, 2, dmaLen);
        int slots = startDmaFill(dmaLen, h32, false);
        // more than one line
        Assert.assertTrue(vdpProvider.getVCounter() > 0);
        Assert.assertTrue(slots > slotsPerLine);
    }

    protected void testDMACopyInternal(int dmaLen, boolean h32, boolean duringVBlank) {
        int slotsPerLine = h32 ? GenesisVdpProvider.H32_SLOTS : GenesisVdpProvider.H40_SLOTS;
        int refreshSlots = h32 ? REFRESH_SLOTS_H32 : REFRESH_SLOTS_H40;
        int slots = startDMACopy(1, dmaLen, duringVBlank);
        // more than one line
        Assert.assertTrue(vdpProvider.getVCounter() > 0);
        if (!duringVBlank) {
            Assert.assertTrue(slots > slotsPerLine);
        } else {
            Assert.assertEquals(dmaLen * 2 + refreshSlots + 1, slots);
        }
    }

    protected void setupDMAFillInternal(long dmaFillLong, int increment, int dmaLength) {
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);
        vdpProvider.updateRegisterData(MODE_2, 0x54); //display enable + dma enable

        vdpProvider.writeControlPort(0x8F00 + increment);
        vdpProvider.writeControlPort(0x9300 + dmaLength);
        vdpProvider.writeControlPort(0x9400);
        vdpProvider.writeControlPort(0x9500);
        vdpProvider.writeControlPort(0x9600);
        vdpProvider.writeControlPort(0x9780);

        vdpProvider.writeControlPort(dmaFillLong >> 16);
        vdpProvider.writeControlPort(dmaFillLong & 0xFFFF);

        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);
    }

    protected int startDmaFill(int dmaLen, boolean h32, boolean waitVBlank) {
//        System.out.println("DestAddress: " + Integer.toHexString(vdpProvider.getAddressRegisterValue()));
        int refreshSlots = h32 ? REFRESH_SLOTS_H32 : REFRESH_SLOTS_H40;
        if (waitVBlank) {
            MdVdpTestUtil.runVdpUntilVBlank(vdpProvider);
            System.out.println("VBlank start" + vdpProvider.getVdpStateString());
        } else {
            MdVdpTestUtil.runToStartFrame(vdpProvider);
            System.out.println("Active Screen start" + vdpProvider.getVdpStateString());
        }

        vdpProvider.writeDataPort(0x68ac);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);
        System.out.println("After data write, fifo empty" + vdpProvider.getVdpStateString());

        int slots = MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);
        System.out.println("Dma done" + vdpProvider.getVdpStateString());

        if (waitVBlank) {
            Assert.assertEquals(dmaLen + refreshSlots, slots);
        }
        return slots;
    }


    protected int startDMACopy(int increment, int dmaLen, boolean waitVBlank) {
        vdpProvider.writeControlPort(0x8154); //display enable + dma enable
        if (waitVBlank) {
            MdVdpTestUtil.runVdpUntilVBlank(vdpProvider);
            System.out.println("VBlank start" + vdpProvider.getVdpStateString());
        } else {
            MdVdpTestUtil.runToStartFrame(vdpProvider);
            System.out.println("Active Screen start" + vdpProvider.getVdpStateString());
        }
        vdpProvider.writeControlPort(0x8F00 + increment);

        vdpProvider.writeControlPort(0x9300 + dmaLen);
        vdpProvider.writeControlPort(0x9400);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0x9500);
        vdpProvider.writeControlPort(0x9690);
        vdpProvider.writeControlPort(0x97C0);

        vdpProvider.writeControlPort(0);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0xc2);
        System.out.println("Dma started" + vdpProvider.getVdpStateString());
        int slots = MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);
        System.out.println("Dma done" + vdpProvider.getVdpStateString());
        return slots;
    }

    protected void test68kDmaPerLine(GenesisVdpProvider.VdpRamType vdpRamType, boolean h32, boolean blanking) {
        setup68kRam();
        int refreshSlots = h32 ? REFRESH_SLOTS_H32 : REFRESH_SLOTS_H40;
        int slotsPerLine = h32 ? GenesisVdpProvider.H32_SLOTS : GenesisVdpProvider.H40_SLOTS;
        int bytesPerLine = blanking ? slotsPerLine - refreshSlots : (h32 ? 16 : 18);
        bytesPerLine = vdpRamType != GenesisVdpProvider.VdpRamType.VRAM ? bytesPerLine << 1 : bytesPerLine;
        int dmaLen = bytesPerLine + 1;
        int mode2 = blanking ? 0x34 : 0x74; //dma enabled
        vdpProvider.updateRegisterData(MODE_2, mode2);
        vdpProvider.updateRegisterData(AUTO_INCREMENT, 2);
        vdpProvider.updateRegisterData(DMA_LENGTH_LOW, dmaLen);
        vdpProvider.updateRegisterData(DMA_SOURCE_LOW, 0x80);
        vdpProvider.updateRegisterData(DMA_SOURCE_MID, 0xfd);
        vdpProvider.updateRegisterData(DMA_SOURCE_HIGH, 0x7f);
        if (!blanking) {
            MdVdpTestUtil.runToStartFrame(vdpProvider);
        }
//        LogHelper.printToSytemOut =true;
        System.out.println(vdpRamType + " before: " + MdVdpTestUtil.printVdpMemory(memoryInterface, vdpRamType, 0, 0xFF));

        int commandLong = vdpRamType == GenesisVdpProvider.VdpRamType.CRAM ? 0xC000_0080 : (vdpRamType == GenesisVdpProvider.VdpRamType.VSRAM)
                ? 0x4000_0090 : 0x4000_0080;
        vdpProvider.writeControlPort(commandLong >> 16);
        vdpProvider.writeControlPort(commandLong & 0xFFFF);
        System.out.println("Dma started" + vdpProvider.getVdpStateString());
        memoryInterface.resetStats();
        MdVdpTestUtil.runToStartNextLine(vdpProvider);
        Assert.assertEquals(bytesPerLine, memoryInterface.getMemoryWrites(vdpRamType));
        MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);
        System.out.println("Dma done" + vdpProvider.getVdpStateString());
    }
}

/**
 * ==================================================
 * SEGA Mega Drive / Genesis VDP access bandwidths
 * ==================================================
 * <p>
 * These are simple tables with all VDP data transfer
 * rates in bytes. Transfer counts from official MD
 * documentation are all wrong. These figures are
 * correct, as the rates I achieve match with what is
 * in the tables and DMA bitmap mode further verifies
 * these figures. All numbers are Bytes, except for
 * one table where values are amount of tiles one can
 * transfer. A tile is 32 bytes. Active means active
 * scanlines and Passive means vertical blanking and
 * overscan lines.
 * <p>
 * <p>
 * ==================================================
 * *** 68K to VRAM
 * ==================================================
 * <p>
 * +------------+---------+--------+
 * | Line width | Passive | Active |
 * +------------+---------+--------+
 * | 256 pixels |   161   |   16   |
 * | 320 pixels |   198   |   18   |
 * +------------+---------+--------+
 * <p>
 * +----+------------+---------+---------+---------+
 * | Hz | Resolution | Passive | Active  |  Total  |
 * +----+------------+---------+---------+---------+
 * | 60 | 256 * 224  |   6118  |   3584  |   9702  |
 * |    | 320 * 224  |   7524  |   4032  |  11556  |
 * +----+------------+---------+---------+---------+
 * | 50 | 256 * 224  |  14329  |   3584  |  17913  |
 * |    | 320 * 224  |  17622  |   4032  |  21654  |
 * |    | 256 * 240  |  11753  |   3840  |  15593  |
 * |    | 320 * 240  |  14454  |   4320  |  18774  |
 * +----+------------+---------+---------+---------+
 * Number of tiles that can be transferred :
 * +----+------------+---------+---------+---------+
 * | Hz | Resolution | Passive | Active  |  Total  |
 * +----+------------+---------+---------+---------+
 * | 60 | 256 * 224  |   191   |   112   |   303   |
 * |    | 320 * 224  |   235   |   126   |   361   |
 * +----+------------+---------+---------+---------+
 * | 50 | 256 * 224  |   447   |   112   |   559   |
 * |    | 320 * 224  |   550   |   126   |   676   |
 * |    | 256 * 240  |   367   |   120   |   487   |
 * |    | 320 * 240  |   451   |   135   |   586   |
 * +----+------------+---------+---------+---------+
 * <p>
 * <p>
 * ==================================================
 * *** VRAM Fill
 * ==================================================
 * <p>
 * +------------+---------+--------+
 * | Line width | Passive | Active |
 * +------------+---------+--------+
 * | 256 pixels |   166   |   15   |
 * | 320 pixels |   204   |   17   |
 * +------------+---------+--------+
 * <p>
 * +----+------------+---------+---------+---------+
 * | Hz | Resolution | Passive | Active  |  Total  |
 * +----+------------+---------+---------+---------+
 * | 60 | 256 * 224  |   6308  |   3360  |   9668  |  //38 blank + 224 active screen = 262
 * |    | 320 * 224  |   7752  |   3808  |  11560  |
 * +----+------------+---------+---------+---------+
 * | 50 | 256 * 224  |  14774  |   3360  |  18134  |
 * |    | 320 * 224  |  18156  |   3808  |  21964  |
 * |    | 256 * 240  |  12118  |   3600  |  15718  |
 * |    | 320 * 240  |  14892  |   4080  |  18972  |
 * +----+------------+---------+---------+---------+
 * <p>
 * <p>
 * ==================================================
 * *** VRAM Copy
 * ==================================================
 * <p>
 * +------------+---------+--------+
 * | Line width | Passive | Active |
 * +------------+---------+--------+
 * | 256 pixels |    83   |    8   |
 * | 320 pixels |   102   |    9   |
 * +------------+---------+--------+
 * <p>
 * +----+------------+---------+---------+---------+
 * | Hz | Resolution | Passive | Active  |  Total  |
 * +----+------------+---------+---------+---------+
 * | 60 | 256 * 224  |   3154  |   1792  |   4946  |
 * |    | 320 * 224  |   3876  |   2016  |   5892  |
 * +----+------------+---------+---------+---------+
 * | 50 | 256 * 224  |   7387  |   1792  |   9179  |
 * |    | 320 * 224  |   9078  |   2016  |  11094  |
 * |    | 256 * 240  |   6059  |   1920  |   7979  |
 * |    | 320 * 240  |   7446  |   2160  |   9606  |
 * +----+------------+---------+---------+---------+
 */
