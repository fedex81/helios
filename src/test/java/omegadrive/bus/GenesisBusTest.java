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

import omegadrive.bus.model.GenesisBusProvider;
import omegadrive.util.Size;
import omegadrive.util.SystemTestUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * GenesisBusTest
 *
 * @author Federico Berti
 */
public class GenesisBusTest {

    private GenesisBusProvider bus;

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
        bus.write(GenesisBusProvider.Z80_ADDRESS_SPACE_START, value, Size.BYTE);
        long res = bus.read(GenesisBusProvider.Z80_ADDRESS_SPACE_START, Size.BYTE);
        Assert.assertEquals(value, res);

        value = 2;
        bus.write(0xA08000, value, Size.BYTE);
        res = bus.read(GenesisBusProvider.Z80_ADDRESS_SPACE_START, Size.BYTE);
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
}
