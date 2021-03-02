/*
 * GenesisVdpTest2
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

package omegadrive.vdp.md;

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.input.GamepadTest;
import omegadrive.memory.IMemoryProvider;
import omegadrive.memory.MemoryProvider;
import omegadrive.system.SystemProvider;
import omegadrive.util.RegionDetector;
import omegadrive.util.Size;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.VdpDmaHandlerTest;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpDmaHandler;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static omegadrive.bus.model.GenesisBusProvider.VDP_ADDRESS_SPACE_START;
import static omegadrive.vdp.model.GenesisVdpProvider.VramMode.*;

public class GenesisVdpTest2 {

    private static final Logger LOG = LogManager.getLogger(VdpDmaHandlerTest.class.getSimpleName());

    GenesisVdpProvider vdpProvider;
    VdpMemoryInterface memoryInterface;
    VdpDmaHandler dmaHandler;
    GenesisBusProvider busProvider;

    static final long VDP_CONTROL_PORT = VDP_ADDRESS_SPACE_START + 4;

    @Before
    public void setup() {
        SystemProvider emu = MdVdpTestUtil.createTestGenesisProvider();
        IMemoryProvider memory = MemoryProvider.createGenesisInstance();
        busProvider = GenesisBusProvider.createBus();
        busProvider.attachDevice(memory);
        memoryInterface = GenesisVdpMemoryInterface.createInstance();
        dmaHandler = new VdpDmaHandlerImpl();

        vdpProvider = GenesisVdp.createInstance(busProvider, memoryInterface, dmaHandler, RegionDetector.Region.EUROPE);
        busProvider.attachDevice(emu).attachDevice(vdpProvider).attachDevice(GamepadTest.createTestJoypadProvider());
        busProvider.init();

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
        MdVdpTestUtil.setH32(vdpProvider);

        int dmaAutoInc = 2;
        int afterDmaAutoInc = 0x20;

        vdpProvider.writeControlPort(0x8124);
        vdpProvider.writeControlPort(0x8134);
        vdpProvider.writeControlPort(0x8F00 + dmaAutoInc);
        vdpProvider.writeControlPort(0x93E8);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0x9400);
        vdpProvider.writeControlPort(0x951F);
        vdpProvider.writeControlPort(0x9683);
        vdpProvider.writeControlPort(0x977F);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        //setup DMA
        vdpProvider.writeControlPort(0x4400);
        //move.l, first word
        busProvider.write(VDP_CONTROL_PORT, 0x81, Size.WORD);

        //move.l second word, this changes the autoInc value -> needs to happen after DMA!
        busProvider.write(VDP_CONTROL_PORT, 0x8F00 + afterDmaAutoInc, Size.WORD);
        Assert.assertFalse(busProvider.is68kRunning());
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        //autoInc has not been changed
        Assert.assertEquals(dmaAutoInc, vdpProvider.getRegisterData(GenesisVdpProvider.VdpRegisterName.AUTO_INCREMENT));

        MdVdpTestUtil.runVdpUntilDmaDone(vdpProvider);
        Assert.assertTrue(busProvider.is68kRunning());

        //autoInc has now been changed
        Assert.assertEquals(afterDmaAutoInc, vdpProvider.getRegisterData(GenesisVdpProvider.VdpRegisterName.AUTO_INCREMENT));
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
        MdVdpTestUtil.setH32(vdpProvider);

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

        GenesisVdpProvider.VramMode vramMode = vdpProvider.getVramMode();
        Assert.assertEquals(GenesisVdpProvider.VramMode.getVramMode(expected), vramMode);
    }
}
