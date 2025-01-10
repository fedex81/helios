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

package omegadrive.bus;

import omegadrive.bus.model.MdMainBusProvider;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static omegadrive.bus.model.MdMainBusProvider.SRAM_LOCK;

/**
 * MdBusTest
 *
 * @author Federico Berti
 */
public class MdBusTest {

    private MdMainBusProvider bus;

    @Before
    public void init() {
        bus = SystemTestUtil.setupNewMdSystem();
    }

    /**
     * When 68k writes to z80 address space 0x8000 - 0xFFFF mirrors 0x0000 - 0x7FFF
     */
    @Test
    public void test68kWriteToZ80() {
        int value = 1;
        bus.setZ80ResetState(false);
        bus.write(0xA11100, value, Size.BYTE); //busReq
        bus.write(MdMainBusProvider.Z80_ADDRESS_SPACE_START, value, Size.BYTE);
        long res = bus.read(MdMainBusProvider.Z80_ADDRESS_SPACE_START, Size.BYTE);
        Assert.assertEquals(value, res);

        value = 2;
        bus.write(0xA08000, value, Size.BYTE);
        res = bus.read(MdMainBusProvider.Z80_ADDRESS_SPACE_START, Size.BYTE);
        Assert.assertEquals(value, res);

        value = 3;
        bus.write(0xA08500, value, Size.BYTE);
        res = bus.read(0xA00500, Size.BYTE);
        Assert.assertEquals(value, res);

        value = 4;
        bus.write(0xA00A00, value, Size.BYTE);
        res = bus.read(0xA08A00, Size.BYTE);
        Assert.assertEquals(value, res);
    }

    /**
     * see md_softcheck.bin
     */
    @Test
    public void testIoReadWord() {
        int ctrlPort = 0xa10009;
        testIoReadInternal(ctrlPort, 0x55, Size.BYTE);
        testIoReadInternal(ctrlPort, 0xCC, Size.BYTE);
        testIoReadInternal(ctrlPort, 0x4466, Size.BYTE);
        testIoReadInternal(ctrlPort, 0xDDBB, Size.BYTE);
        testIoReadInternal(ctrlPort, 0xCCDDAAFF, Size.BYTE);
        testIoReadInternal(ctrlPort, 0x33, Size.WORD);
        testIoReadInternal(ctrlPort, 0xAA, Size.WORD);
        testIoReadInternal(ctrlPort, 0x2211, Size.WORD);
        testIoReadInternal(ctrlPort, 0xEECC, Size.WORD);
        testIoReadInternal(ctrlPort, 0xCCDDAAFF, Size.WORD);
    }

    /**
     * Sega Sound Tool (USA) (v2.2) (Program).md
     * <p>
     * 68M 00000d06   33fc 0001 00a130f0      move.w   #$0001,$00a130f0
     */
    @Test
    public void timeLineControlWrite_WORD() {
        int val = 1;
        bus.write(SRAM_LOCK - 1, val, Size.WORD);
        int res = bus.read(SRAM_LOCK, Size.BYTE);
        Assertions.assertEquals(val, res);
    }

    private void testIoReadInternal(int ctrlPort, int val, Size size) {
        int res, expWord, expByte = val & 0xFF;
        bus.write(ctrlPort, val, size);
        res = bus.read(ctrlPort, Size.BYTE);
        Assertions.assertEquals(expByte, res);
        res = bus.read(ctrlPort, Size.WORD);
        expWord = expByte | ((expByte << 8) & 0xFF00);
        Assertions.assertEquals(expWord, res);
    }


}
