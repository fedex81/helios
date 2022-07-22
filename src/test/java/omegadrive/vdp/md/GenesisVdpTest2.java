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
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import omegadrive.vdp.MdVdpTestUtil;
import omegadrive.vdp.VdpDmaHandlerTest;
import omegadrive.vdp.model.GenesisVdpProvider;
import omegadrive.vdp.model.VdpMemoryInterface;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.Optional;

import static omegadrive.bus.model.GenesisBusProvider.VDP_ADDRESS_SPACE_START;
import static omegadrive.vdp.model.GenesisVdpProvider.VramMode.*;

public class GenesisVdpTest2 {

    private static final Logger LOG = LogHelper.getLogger(VdpDmaHandlerTest.class.getSimpleName());

    GenesisVdpProvider vdpProvider;
    VdpMemoryInterface memoryInterface;
    GenesisBusProvider busProvider;

    static final long VDP_CONTROL_PORT = VDP_ADDRESS_SPACE_START + 4;

    @Before
    public void setup() {
        busProvider = SystemTestUtil.setupNewMdSystem();
        Optional<GenesisVdpProvider> opt = busProvider.getBusDeviceIfAny(GenesisVdpProvider.class);
        Assert.assertTrue(opt.isPresent());
        vdpProvider = opt.get();
        memoryInterface = (VdpMemoryInterface) vdpProvider.getVdpMemory();
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

    @Test
    public void testCtrlPortVdpRegisterWriteUpdatesCodeAndAddrRegs() {
        MdVdpTestUtil.setH32(vdpProvider);

        //setup vpd
        int autoInc = 2;

        vdpProvider.writeControlPort(0x8124);
        vdpProvider.writeControlPort(0x8134);
        vdpProvider.writeControlPort(0x8F00 + autoInc);
        vdpProvider.writeControlPort(0x93E8);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        vdpProvider.writeControlPort(0); //addrReg = 0, codeReg = 0 (vramRead)
        vdpProvider.writeControlPort(0);

        memoryInterface.writeVramByte(0xf02, 0xFF);

        vdpProvider.writeControlPort(0x8f02); //set autoInc = 2, addressRegister -> 0xF02, codeReg = 2 (invalid)
        //codeReg should now be 2
        int res = vdpProvider.readVdpPortWord(GenesisVdpProvider.VdpPortType.DATA);
        Assert.assertEquals(0, res); //invalid read returns 0
    }

    /**
     * GoldenAxe2 attempts to write to VRAM after setting the CTRL port for a register write,
     * without setting the address and control registers in between.
     * The solution is to still update the address (lower 14 bits) and code (lower 2 bits)
     * registers when a VDP register write occurs, and ignore data port writes when
     * the code register value is not a valid write command value (0x1, 0x3 or 0x5).
     * <p>
     * writeReg: f, data: 2
     * Invalid writeDataPort, vramMode null, data: 8032, address: cf02
     * Invalid writeDataPort, vramMode null, data: 8004, address: cf04
     */
    @Test
    public void testInvalidModeDataPortWrite() {
        MdVdpTestUtil.setH32(vdpProvider);

        //setup vpd
        int autoInc = 2;

        vdpProvider.writeControlPort(0x8124);
        vdpProvider.writeControlPort(0x8134);
        vdpProvider.writeControlPort(0x8F00 + autoInc);
        vdpProvider.writeControlPort(0x93E8);
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        int addrReg = 0xf02;

        int vram0 = memoryInterface.readVramByte(addrReg);
        int vram2 = memoryInterface.readVramByte(addrReg + autoInc);
        int cram0 = memoryInterface.readCramByte(addrReg);
        int cram2 = memoryInterface.readCramByte(addrReg + autoInc);
        int vsram0 = memoryInterface.readVsramByte(addrReg);
        int vsram2 = memoryInterface.readVsramByte(addrReg + autoInc);

        //do work
        vdpProvider.writeControlPort(0x8f02); //set autoInc = 2, addressRegister -> 0xF02, codeReg = 2 (invalid)
        vdpProvider.writeDataPort(0x8032); //attempts to write to address 0xf02, should fail
        vdpProvider.writeDataPort(0x8004); //attempts to write to address 0xf04, should fail
        MdVdpTestUtil.runVdpUntilFifoEmpty(vdpProvider);

        //verify nothing was written to mem
        Assert.assertEquals(vram0, memoryInterface.readVramByte(addrReg));
        Assert.assertEquals(vram2, memoryInterface.readVramByte(addrReg + autoInc));
        Assert.assertEquals(vsram0, memoryInterface.readVsramByte(addrReg));
        Assert.assertEquals(vsram2, memoryInterface.readVsramByte(addrReg + autoInc));
        Assert.assertEquals(cram0, memoryInterface.readCramByte(addrReg));
        Assert.assertEquals(cram2, memoryInterface.readCramByte(addrReg + autoInc));
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
